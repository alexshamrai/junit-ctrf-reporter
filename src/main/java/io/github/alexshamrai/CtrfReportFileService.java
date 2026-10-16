package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.json.CtrfJsonMapper;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Reads and writes the CTRF report file at {@code ctrf.report.path}.
 * <p>
 * Several JVMs can write the same report, for example parallel forks and retry rounds. {@code update} lets
 * them take turns: it holds an exclusive lock on {@code <report>.lock} while it reads the report, merges, and
 * replaces the report by moving a complete temp file over it. A JVM killed while writing therefore never
 * leaves a truncated report.
 */
public class CtrfReportFileService {

    static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration LOCK_POLL_INTERVAL = Duration.ofMillis(50);
    private static final int MOVE_ATTEMPTS = 10;
    private static final Duration MOVE_RETRY_INTERVAL = Duration.ofMillis(100);
    // The default of ctrf.report.path, used inside a directory that it names
    private static final String REPORT_FILE_IN_DIRECTORY = "ctrf-report.json";

    /**
     * Threads of this JVM take turns here before touching the file lock. Besides keeping them from colliding on it,
     * this guarantees the JVM has only one channel open on {@code <report>.lock}: on POSIX systems, closing any
     * channel to a file releases every lock the process holds on that file, so a second channel closed by a waiting
     * thread would silently drop the lock another thread holds.
     */
    private static final Object JVM_LOCK = new Object();
    private static final Logger LOGGER = Logger.getLogger(CtrfReportFileService.class.getName());

    private final ConfigReader configReader;
    private final Duration lockTimeout;

    public CtrfReportFileService(ConfigReader configReader) {
        this(configReader, DEFAULT_LOCK_TIMEOUT);
    }

    CtrfReportFileService(ConfigReader configReader, Duration lockTimeout) {
        this.configReader = configReader;
        this.lockTimeout = lockTimeout;
    }

    /**
     * Replaces the report with what {@code merge} makes of the current one.
     * <p>
     * Threads of this JVM take turns, and other JVMs are kept out by an exclusive lock on {@code <report>.lock}.
     * The report is read fresh while the lock is held. After the lock timeout, or on a file system that can't
     * lock files, the report is written without the lock. A {@code ctrf.report.path} that names a directory gets
     * {@code ctrf-report.json} inside it, with a warning. Errors, including one thrown by {@code merge}, are
     * logged as warnings and leave the report as it was.
     *
     * @param merge receives the current report, or {@code null} if there is none or it can't be parsed, and
     *              returns the report to write
     */
    public void update(UnaryOperator<CtrfJson> merge) {
        replace(this::readExisting, merge);
    }

    /**
     * Like {@link #update(UnaryOperator)}, but reads the current report only if it belongs to the run {@code runId}.
     * A report of another run, or one without a run id, is replaced after reading only its {@code runId}, however
     * large it is.
     *
     * @param runId the run being reported, not {@code null}
     * @param merge receives the current report if it belongs to {@code runId}; {@code null} if it doesn't, if there
     *              is none, or if it can't be parsed. Returns the report to write
     * @throws NullPointerException if {@code runId} is {@code null}; the report is left as it was
     */
    public void update(String runId, UnaryOperator<CtrfJson> merge) {
        Objects.requireNonNull(runId, "runId");
        replace(report -> readExistingOfRun(report, runId), merge);
    }

    private void replace(Function<Path, CtrfJson> readCurrent, UnaryOperator<CtrfJson> merge) {
        synchronized (JVM_LOCK) {
            Path report;
            try {
                report = reportFile();
                Files.createDirectories(report.getParent());
            } catch (InvalidPathException e) {
                LOGGER.warning("Failed to write results to file: " + configReader.getReportPath() + " - it isn't a valid path, "
                    + e.getReason());
                return;
            } catch (IOException e) {
                printWriteFailure(e);
                return;
            }
            FileLock lock = lockAcrossJvms(report);
            try {
                writeAtomically(report, merge.apply(readCurrent.apply(report)));
            } catch (RuntimeException e) {
                LOGGER.warning("Failed to write results to file: " + configReader.getReportPath() + " - " + e);
            } finally {
                release(lock);
            }
        }
    }

    /**
     * The file {@code ctrf.report.path} names, or {@code ctrf-report.json} inside it if it names a directory.
     */
    private Path reportFile() {
        Path configured = Paths.get(configReader.getReportPath()).toAbsolutePath();
        if (!Files.isDirectory(configured)) {
            return configured;
        }
        Path inside = configured.resolve(REPORT_FILE_IN_DIRECTORY);
        LOGGER.warning("ctrf.report.path names a directory, " + configured + ", so the report is written to " + inside
            + "; set ctrf.report.path to a file to name the report");
        return inside;
    }

    /**
     * Takes the exclusive lock on {@code <report>.lock}, waiting up to the lock timeout. Returns {@code null},
     * after logging why, when the file system can't lock files or the lock stays taken; the report is then
     * written without it.
     */
    private FileLock lockAcrossJvms(Path report) {
        Path lockFile = report.resolveSibling(report.getFileName() + ".lock");
        FileChannel channel = null;
        try {
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            long deadline = System.nanoTime() + lockTimeout.toNanos();
            while (true) {
                FileLock lock = tryLock(channel);
                if (lock != null) {
                    return lock;
                }
                if (System.nanoTime() >= deadline) {
                    LOGGER.warning("Could not lock " + lockFile + " within " + lockTimeout.toMillis()
                        + " ms; writing the report without the lock");
                    closeQuietly(channel);
                    return null;
                }
                Thread.sleep(LOCK_POLL_INTERVAL.toMillis());
            }
        } catch (IOException | UnsupportedOperationException e) {
            LOGGER.warning("Could not lock " + lockFile + "; writing the report without the lock - " + e.getMessage());
            closeQuietly(channel);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Interrupted while waiting for " + lockFile + "; writing the report without the lock");
            closeQuietly(channel);
            return null;
        }
    }

    // A lock held by this JVM through another channel makes tryLock throw instead of returning null
    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException e) {
            return null;
        }
    }

    // Closing the channel releases its lock
    private static void release(FileLock lock) {
        if (lock != null) {
            closeQuietly(lock.channel());
        }
    }

    private CtrfJson readExisting(Path report) {
        if (!Files.exists(report)) {
            return null;
        }
        try (Reader in = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
            return CtrfJsonMapper.read(in, CtrfJson.class);
        } catch (IOException | RuntimeException e) {
            return ignoreUnreadable(report, e);
        }
    }

    // A report whose runId isn't runId, or that has none, is replaced without reading its tests
    private CtrfJson readExistingOfRun(Path report, String runId) {
        if (!Files.exists(report)) {
            return null;
        }
        try (Reader in = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
            if (!runId.equals(CtrfJsonMapper.readRunId(in))) {
                return null;
            }
        } catch (IOException | RuntimeException e) {
            return ignoreUnreadable(report, e);
        }
        return readExisting(report);
    }

    private static CtrfJson ignoreUnreadable(Path report, Exception e) {
        LOGGER.warning("Ignoring the existing report, which can't be read: " + report + " - " + describe(e));
        return null;
    }

    /**
     * Streams the report into a hidden temp file next to it, then moves the temp file over the report. Not
     * {@code Files.createTempFile}, which would make the report readable by its owner only. A write that fails
     * part-way deletes the temp file and leaves the report as it was.
     */
    private void writeAtomically(Path report, CtrfJson ctrfJson) {
        Path temp = report.resolveSibling("." + report.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                CtrfJsonMapper.write(ctrfJson, out);
            }
            moveOver(temp, report);
        } catch (IOException e) {
            printWriteFailure(e);
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * Moves the temp file over the report. On Windows the move fails while another process, such as an antivirus
     * scanner or a viewer, has either file open, so a failure is retried while the lock is still held.
     */
    private static void moveOver(Path source, Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AtomicMoveNotSupportedException e) {
                LOGGER.warning("Can't replace " + target + " atomically; replacing it in place");
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (NoSuchFileException e) {
                throw e;
            } catch (FileSystemException e) {
                if (attempt >= MOVE_ATTEMPTS || !pauseBeforeRetry()) {
                    throw e;
                }
            }
        }
    }

    private static boolean pauseBeforeRetry() {
        try {
            Thread.sleep(MOVE_RETRY_INTERVAL.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void printWriteFailure(IOException e) {
        if (e instanceof AccessDeniedException) {
            LOGGER.warning("Access denied: " + configReader.getReportPath() + " - " + e.getMessage());
        } else {
            LOGGER.warning("Failed to write results to file: " + configReader.getReportPath() + " - " + describe(e));
        }
    }

    // The message of a file system exception can be just a path; its type then says what went wrong
    private static String describe(Exception e) {
        if (e instanceof FileSystemException fileSystemException && fileSystemException.getReason() == null) {
            return e.getMessage() + " (" + e.getClass().getSimpleName() + ")";
        }
        return e.getMessage();
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // A leftover temp file is harmless
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // Nothing left to release
        }
    }
}

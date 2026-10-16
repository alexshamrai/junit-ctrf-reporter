package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class CtrfReportFileServiceTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    @RegisterExtension
    final LogCapture logs = new LogCapture(CtrfReportFileService.class);

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("update creates missing directories and writes the report, with its lock file next to it")
    void updateWritesTheReportAndCreatesDirectories() throws IOException {
        Path report = tempDir.resolve("nested/dir/ctrf.json");

        serviceFor(report).update(existing -> marker("first"));

        assertThat(readMarker(report)).isEqualTo("first");
        assertThat(report.resolveSibling("ctrf.json.lock")).exists();
        assertThat(tempFilesNextTo(report)).isEmpty();
        assertThat(logs.text()).isEmpty();
    }

    @Test
    @DisplayName("update passes the current report to the merge function and writes what it returns")
    void updatePassesTheCurrentReportToTheMerge() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        var service = serviceFor(report);
        service.update(existing -> marker("first"));
        List<String> seen = new ArrayList<>();

        service.update(existing -> {
            seen.add(existing.getGeneratedBy());
            return marker("second");
        });

        assertThat(seen).containsExactly("first");
        assertThat(readMarker(report)).isEqualTo("second");
    }

    @Test
    @DisplayName("A report that can't be parsed reaches the merge function as null")
    void unparsableReportReachesTheMergeAsNull() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{ not json");
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(readMarker(report)).isEqualTo("fresh");
        assertThat(logs.text()).contains("can't be read");
    }

    @Test
    @DisplayName("A 0.4 report whose buildNumber isn't a number can't be read, so it reaches the merge function as null")
    void legacyReportWithANonNumericBuildNumberReachesTheMergeAsNull() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{\"reportFormat\":\"CTRF\",\"specVersion\":\"0.0.0\","
            + "\"results\":{\"environment\":{\"buildNumber\":\"2026.10.15-3\",\"healthy\":true}}}");
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(readMarker(report)).isEqualTo("fresh");
        assertThat(logs.text()).contains("can't be read");
    }

    @Test
    @DisplayName("A report with fields this version doesn't know reaches the merge function, instead of being replaced")
    void reportWithUnknownFieldsReachesTheMerge() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{\"generatedBy\":\"first\",\"fieldFromTheFuture\":{\"a\":[1]}}");
        List<String> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing.getGeneratedBy());
            return marker("second");
        });

        assertThat(seen).containsExactly("first");
        assertThat(logs.text()).isEmpty();
    }

    @Test
    @DisplayName("Review Focus 3: a report nested too deeply to read is replaced, with a warning")
    void deeplyNestedReportIsReplaced() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{\"extra\":{\"a\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}}");
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(readMarker(report)).isEqualTo("fresh");
        assertThat(logs.text()).contains("can't be read");
    }

    @Test
    @DisplayName("Text with a lone surrogate is written as an escape")
    void loneSurrogateDoesNotFailTheReport() throws IOException {
        Path report = tempDir.resolve("ctrf.json");

        serviceFor(report).update(existing -> marker("broken " + (char) 0xD83D));

        assertThat(Files.readString(report, StandardCharsets.UTF_8)).contains("\"generatedBy\":\"broken \\uD83D\"");
        assertThat(logs.text()).isEmpty();
    }

    @Test
    @DisplayName("The report is written as UTF-8")
    void reportIsWrittenAsUtf8() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        String text = "Gr" + (char) 0xFC + (char) 0xDF + "e " + (char) 0x65E5 + (char) 0x672C;

        serviceFor(report).update(existing -> marker(text));

        assertThat(new String(Files.readAllBytes(report), StandardCharsets.UTF_8)).contains(text);
        assertThat(readMarker(report)).isEqualTo(text);
    }

    @Test
    @DisplayName("A report that isn't valid UTF-8 reaches the merge function as null")
    void reportThatIsNotUtf8ReachesTheMergeAsNull() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.write(report, new byte[] {'{', '"', 'a', '"', ':', '"', (byte) 0xC3, (byte) 0x28, '"', '}'});
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(readMarker(report)).isEqualTo("fresh");
        assertThat(logs.text()).contains("can't be read");
    }

    @Test
    @DisplayName("Review Focus 2: non-ASCII text longer than the read buffers is read back whole")
    void longNonAsciiTextIsReadBackWhole() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        String text = (String.valueOf((char) 0x65E5) + (char) 0xFC + (char) 0x672C).repeat(10_000);
        var service = serviceFor(report);
        service.update(existing -> marker(text));
        List<String> seen = new ArrayList<>();

        service.update(existing -> {
            seen.add(existing.getGeneratedBy());
            return existing;
        });

        assertThat(seen).containsExactly(text);
    }

    @Test
    @DisplayName("Review Focus 3: a report that another tool saved with a byte order mark reaches the merge function")
    void reportWithAByteOrderMarkReachesTheMerge() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, (char) 0xFEFF + "{\"generatedBy\":\"first\"}", StandardCharsets.UTF_8);
        List<String> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing.getGeneratedBy());
            return marker("second");
        });

        assertThat(seen).containsExactly("first");
        assertThat(logs.text()).isEmpty();
    }

    @Test
    @DisplayName("A write that fails halfway leaves the previous report parseable and unchanged")
    void writeThatFailsHalfwayLeavesThePreviousReport() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        var service = serviceFor(report);
        service.update(existing -> marker("first"));

        service.update(existing -> CtrfJson.builder().generatedBy("second").extra(Map.of("value", new Exploding())).build());

        assertThat(readMarker(report)).isEqualTo("first");
        assertThat(tempFilesNextTo(report)).isEmpty();
        assertThat(logs.text()).contains("Failed to write results to file");
    }

    @Test
    @DisplayName("A merge function that throws leaves the report as it was and releases the lock")
    void mergeThatThrowsLeavesTheReportAndReleasesTheLock() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        var service = new CtrfReportFileService(configReaderWithPath(report.toString()), Duration.ofMillis(300));
        service.update(existing -> marker("first"));

        assertThatCode(() -> service.update(existing -> {
            throw new IllegalStateException("composing failed");
        })).doesNotThrowAnyException();

        assertThat(readMarker(report)).isEqualTo("first");
        assertThat(logs.text()).contains("composing failed");
        service.update(existing -> marker("third"));
        assertThat(readMarker(report)).isEqualTo("third");
        assertThat(logs.text()).doesNotContain("Could not lock");
        assertThat(tempFilesNextTo(report)).isEmpty();
    }

    @Test
    @DisplayName("A move that keeps failing is retried for about a second, then reported, leaving no temp file")
    void moveThatKeepsFailingIsRetriedThenReported() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.createDirectory(report);
        Path inside = Files.writeString(report.resolve("keep.txt"), "x");
        var service = serviceFor(report);

        long begin = System.nanoTime();
        assertThatCode(() -> service.update(existing -> marker("x"))).doesNotThrowAnyException();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - begin).toMillis();

        assertThat(report).isDirectory();
        assertThat(inside).exists();
        assertThat(tempFilesNextTo(report)).isEmpty();
        assertThat(logs.text()).contains("Failed to write results to file");
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(900L);
    }

    @Test
    @DisplayName("Updates from many threads at once all land")
    void concurrentUpdatesFromSeveralThreadsAllLand() throws Exception {
        Path report = tempDir.resolve("ctrf.json");
        var service = serviceFor(report);
        UnaryOperator<CtrfJson> increment = existing -> {
            int count = existing == null ? 0 : (Integer) existing.getExtra().get("count");
            return CtrfJson.builder().extra(Map.of("count", count + 1)).build();
        };
        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                service.update(increment);
                return null;
            });
        }

        start.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(objectMapper.readValue(report.toFile(), CtrfJson.class).getExtra()).containsEntry("count", threads);
    }

    @Test
    @DisplayName("When another holder keeps the lock, update waits for the timeout and then writes without it")
    void waitsForTheLockAndThenWritesWithoutIt() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Path lockFile = tempDir.resolve("ctrf.json.lock");
        var service = new CtrfReportFileService(configReaderWithPath(report.toString()), Duration.ofMillis(300));

        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var held = channel.lock()) {
            long started = System.nanoTime();
            service.update(existing -> marker("written anyway"));
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(held.isValid()).isTrue();
            assertThat(waitedMillis).isGreaterThanOrEqualTo(300);
        }

        assertThat(readMarker(report)).isEqualTo("written anyway");
        assertThat(logs.text()).contains("Could not lock");
    }

    @Test
    @DisplayName("A report given as a bare file name, as the default ctrf-report.json is, is written in the working directory")
    void updatesAReportGivenAsABareFileName() throws IOException {
        String name = "ctrf-bare-" + UUID.randomUUID() + ".json";
        Path report = Path.of(name);
        Path lockFile = Path.of(name + ".lock");
        try {
            new CtrfReportFileService(configReaderWithPath(name)).update(existing -> marker("bare"));

            assertThat(readMarker(report)).isEqualTo("bare");
            assertThat(lockFile).exists();
        } finally {
            Files.deleteIfExists(report);
            Files.deleteIfExists(lockFile);
        }
    }

    @Test
    @DisplayName("A directory that can't be written is reported, not thrown")
    void reportInAReadOnlyDirectoryIsReportedNotThrown() throws IOException {
        Path readOnly = Files.createDirectory(tempDir.resolve("read-only"));
        assumeFalse(!readOnly.toFile().setWritable(false) || readOnly.toFile().canWrite(), "the file system ignores permissions");
        try {
            assertThatCode(() -> serviceFor(readOnly.resolve("sub/ctrf.json")).update(existing -> marker("never")))
                .doesNotThrowAnyException();

            assertThat(logs.text()).contains("Access denied");
        } finally {
            readOnly.toFile().setWritable(true);
        }
    }

    private CtrfReportFileService serviceFor(Path report) {
        return new CtrfReportFileService(configReaderWithPath(report.toString()));
    }

    private static ConfigReader configReaderWithPath(String path) {
        return new ConfigReader(Map.of("ctrf.report.path", path), Map.of(), Map.of());
    }

    private static CtrfJson marker(String value) {
        return CtrfJson.builder().generatedBy(value).build();
    }

    private String readMarker(Path report) throws IOException {
        return objectMapper.readValue(report.toFile(), CtrfJson.class).getGeneratedBy();
    }

    private static List<Path> tempFilesNextTo(Path report) throws IOException {
        try (Stream<Path> files = Files.list(report.getParent())) {
            return files.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList();
        }
    }

    /**
     * Fails while the report is turned into JSON.
     */
    static final class Exploding {

        @Override
        public String toString() {
            throw new IllegalStateException("boom");
        }
    }
}

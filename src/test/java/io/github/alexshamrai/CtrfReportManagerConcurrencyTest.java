package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import io.github.alexshamrai.json.CtrfJsonMapper;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The manager under parallel test execution (TI-1): the real processor, composer and file service, called from many
 * threads at the same moment, as JUnit's parallel execution calls the entry points.
 */
class CtrfReportManagerConcurrencyTest {

    private static final int THREADS = 8;
    private static final int TESTS_PER_THREAD = 500;
    private static final int ROUNDS = 200;
    private static final String RUN_ID = "run-1";

    @TempDir
    Path tempDir;

    @org.junit.jupiter.api.Test
    @DisplayName("Tests started and finished on many threads at once are each reported once, with their status")
    void testsFromManyThreadsAreEachReportedOnce() throws Exception {
        Path report = tempDir.resolve("ctrf.json");
        var manager = managerWith(new CtrfReportFileService(configWritingTo(report)), report);
        Object owner = new Object();
        manager.startTestRun(owner, "Listener");

        onThreadsAtOnce(thread -> {
            for (int i = 0; i < TESTS_PER_THREAD; i++) {
                String id = idOf(thread, i);
                var details = new TestDetails(System.currentTimeMillis(), Set.of(), "a.T" + thread, id, "t" + i + "()");
                if (i % 4 == 3) {
                    manager.onTestSkipped(details, Optional.of("disabled"));
                } else {
                    manager.onTestStart(details);
                    finish(manager, id, i % 4);
                }
                if (thread == 0 && i == TESTS_PER_THREAD / 2) {
                    manager.markEnvironmentUnhealthyInternal();
                }
            }
        });
        manager.finishTestRun(owner);

        var results = read(report).getResults();
        int total = THREADS * TESTS_PER_THREAD;
        assertThat(results.getTests()).extracting(Test::getTestId).doesNotHaveDuplicates().hasSize(total);
        assertThat(results.getTests()).allSatisfy(test -> {
            assertThat(test.getStatus()).as(test.getTestId()).isEqualTo(expectedStatus(test.getTestId()));
            // A start record lost between threads would leave only the ID: an "Unknown Test" without a file
            assertThat(test.getName()).as(test.getTestId()).isEqualTo(test.getTestId().replaceAll(".*\\[method:(.*)]", "$1"));
            assertThat(test.getFilePath()).as(test.getTestId()).isEqualTo(test.getTestId().replaceAll(".*\\[class:(.*?)].*", "$1"));
        });
        assertThat(results.getSummary().getTests()).isEqualTo(total);
        assertThat(results.getSummary().getPassed()).isEqualTo(total / 4);
        assertThat(results.getSummary().getFailed()).isEqualTo(total / 4);
        assertThat(results.getSummary().getSkipped()).isEqualTo(total / 2);
        assertThat(results.getEnvironment().isHealthy()).isFalse();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Of threads finishing the same run at once, exactly one writes the report, round after round")
    void concurrentFinishesWriteTheReportOnce() throws Exception {
        Path report = tempDir.resolve("ctrf.json");
        var writes = new AtomicInteger();
        var countingFileService = new CtrfReportFileService(configWritingTo(report)) {
            @Override
            public void update(String runId, UnaryOperator<CtrfJson> merge) {
                writes.incrementAndGet();
                super.update(runId, merge);
            }
        };
        var manager = managerWith(countingFileService, report);

        for (int round = 1; round <= ROUNDS; round++) {
            Object owner = new Object();
            assertThat(manager.startTestRun(owner, "Listener")).as("round %d starts", round).isTrue();
            String id = idOf(0, round);
            manager.onTestStart(new TestDetails(System.currentTimeMillis(), Set.of(), "a.T0", id, "t" + round + "()"));
            manager.onTestSuccess(id);

            onThreadsAtOnce(thread -> manager.finishTestRun(owner));

            assertThat(writes).as("writes after round %d", round).hasValue(round);
        }
        // Every round is the same run, so each one merged into the report the previous rounds wrote
        assertThat(read(report).getResults().getTests()).hasSize(ROUNDS);
    }

    private static void finish(CtrfReportManager manager, String id, int outcome) {
        switch (outcome) {
            case 0 -> manager.onTestSuccess(id);
            case 1 -> manager.onTestFailure(id, new AssertionError("expected 1 but was 2"));
            default -> manager.onTestAborted(id, new TestAbortedException("Assumption failed"));
        }
    }

    // Outcomes cycle through passed, failed, aborted and skipped, so a test's index gives its status
    private static TestStatus expectedStatus(String testId) {
        int index = Integer.parseInt(testId.substring(testId.lastIndexOf("[method:t") + 9, testId.length() - 3));
        return switch (index % 4) {
            case 0 -> TestStatus.PASSED;
            case 1 -> TestStatus.FAILED;
            default -> TestStatus.SKIPPED;
        };
    }

    private static String idOf(int thread, int index) {
        return "[engine:junit-jupiter]/[class:a.T" + thread + "]/[method:t" + index + "()]";
    }

    /**
     * Runs {@code work} on {@link #THREADS} threads, released at the same moment, and rethrows what any of them threw.
     */
    private static void onThreadsAtOnce(IntConsumer work) throws Exception {
        var pool = Executors.newFixedThreadPool(THREADS);
        var start = new CountDownLatch(1);
        List<Future<?>> done = new ArrayList<>();
        for (int thread = 0; thread < THREADS; thread++) {
            int index = thread;
            done.add(pool.submit(() -> {
                start.await();
                work.accept(index);
                return null;
            }));
        }
        start.countDown();
        try {
            for (Future<?> future : done) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static CtrfReportManager managerWith(CtrfReportFileService fileService, Path report) {
        var config = configWritingTo(report);
        return new CtrfReportManager(new TestProcessor(config), fileService,
            generator -> new CtrfJsonComposer(config, new StartupDurationProcessor(), generator), RUN_ID, true);
    }

    private static ConfigReader configWritingTo(Path report) {
        return new ConfigReader(Map.of("ctrf.report.path", report.toString()), Map.of(), Map.of());
    }

    private static CtrfJson read(Path report) throws IOException {
        try (Reader in = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
            return CtrfJsonMapper.read(in, CtrfJson.class);
        }
    }
}

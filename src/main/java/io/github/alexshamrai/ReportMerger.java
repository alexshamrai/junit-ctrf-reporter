package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;

/**
 * Decides what one JVM's execution makes of the report on disk. It is pure: no files and no clock.
 * <p>
 * A report written by the same run ({@code runId}), for example by a parallel fork or an earlier retry round,
 * is merged: its tests are unfolded into attempts, this execution's attempts are appended, and the result is
 * folded again, so each test appears once with its whole history. A report of another run, or one without a
 * {@code runId}, is replaced.
 * <p>
 * A container that failed in an earlier attempt and finished without failing in this execution gets a final
 * passed attempt, so its failure is reported as recovered: passed and flaky (HP-11).
 */
final class ReportMerger {

    private static final Logger LOGGER = Logger.getLogger(ReportMerger.class.getName());

    private ReportMerger() {
        // Utility class - prevent instantiation
    }

    /**
     * Merges this execution into the report on disk.
     *
     * @param existing  the report on disk, or {@code null}
     * @param runId     this JVM's run id
     * @param attempts  this execution's attempts, in the order they were recorded
     * @param successes the containers that finished without failing in this execution
     * @param start     when this execution started
     * @param stop      when it finished
     * @param healthy   whether the environment stayed healthy in this execution
     * @return the tests and run-level values to write
     */
    static MergedRun merge(CtrfJson existing, String runId, List<Test> attempts, List<ContainerSuccess> successes,
                           long start, long stop, boolean healthy) {
        if (existing != null && runId.equals(existing.getRunId())) {
            try {
                return mergeWithSameRun(existing, attempts, successes, start, stop, healthy);
            } catch (RuntimeException e) {
                LOGGER.warning("Ignoring the existing report, which can't be merged: " + e);
            }
        }
        return new MergedRun(AttemptFolder.fold(withRecoveries(attempts, successes)), start, stop, healthy);
    }

    private static MergedRun mergeWithSameRun(CtrfJson existing, List<Test> attempts, List<ContainerSuccess> successes,
                                              long start, long stop, boolean healthy) {
        var results = existing.getResults();
        List<Test> allAttempts = new ArrayList<>();
        if (results != null && results.getTests() != null) {
            allAttempts.addAll(AttemptFolder.unfold(results.getTests()));
        }
        allAttempts.addAll(attempts);

        var summary = results != null ? results.getSummary() : null;
        long mergedStart = summary != null && summary.getStart() > 0 ? Math.min(summary.getStart(), start) : start;
        long mergedStop = summary != null ? Math.max(summary.getStop(), stop) : stop;
        var environment = results != null ? results.getEnvironment() : null;
        boolean mergedHealthy = healthy && (environment == null || environment.isHealthy());

        return new MergedRun(AttemptFolder.fold(withRecoveries(allAttempts, successes)), mergedStart, mergedStop, mergedHealthy);
    }

    /**
     * Appends a final passed attempt for each container that finished without failing and already has a failed
     * attempt. Only containers with a failed attempt are recovered: a skipped container stays skipped. The new
     * attempt keeps the identity of the container's latest attempt, including its name: "initializationError"
     * or "teardownError".
     */
    private static List<Test> withRecoveries(List<Test> attempts, List<ContainerSuccess> successes) {
        if (successes.isEmpty()) {
            return attempts;
        }
        Map<String, Test> latestAttempts = new HashMap<>();
        Set<String> failedIds = new HashSet<>();
        for (Test attempt : attempts) {
            if (attempt.getTestId() != null) {
                latestAttempts.put(attempt.getTestId(), attempt);
                if (attempt.getStatus() == FAILED) {
                    failedIds.add(attempt.getTestId());
                }
            }
        }
        List<Test> withRecoveries = new ArrayList<>(attempts);
        for (ContainerSuccess success : successes) {
            Test latest = latestAttempts.get(success.uniqueId());
            if (latest != null && failedIds.contains(success.uniqueId())) {
                withRecoveries.add(Test.builder()
                    .testId(latest.getTestId())
                    .name(latest.getName())
                    .filePath(latest.getFilePath())
                    .tags(latest.getTags())
                    .suite(latest.getSuite())
                    .status(PASSED)
                    .start(success.start())
                    .stop(success.stop())
                    .duration(success.stop() - success.start())
                    .build());
            }
        }
        return withRecoveries;
    }

    /**
     * The tests and run-level values to write.
     *
     * @param tests   one test object per test
     * @param start   when the run started
     * @param stop    when it finished
     * @param healthy whether the environment stayed healthy
     */
    record MergedRun(List<Test> tests, long start, long stop, boolean healthy) {
    }
}

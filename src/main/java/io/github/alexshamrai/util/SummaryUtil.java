package io.github.alexshamrai.util;

import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.OTHER;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PENDING;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;

public class SummaryUtil {

    /**
     * Creates a summary from test results using single-pass counting for optimal performance.
     * <p>
     * Uses a single stream iteration with grouping collector instead of multiple filter/count
     * operations, reducing complexity from O(5n) to O(n).
     *
     * @param tests the list of test results to summarize
     * @param startTime the test run start timestamp
     * @param stopTime the test run stop timestamp
     * @return a Summary object with status counts and timing information
     */
    public static Summary createSummary(List<Test> tests, long startTime, long stopTime) {
        Map<Test.TestStatus, Long> statusCounts = tests.stream()
            .map(Test::getStatus)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(status -> status, Collectors.counting()));

        return Summary.builder()
            .tests(tests.size())
            .passed(getCount(statusCounts, PASSED))
            .failed(getCount(statusCounts, FAILED))
            .pending(getCount(statusCounts, PENDING))
            .skipped(getCount(statusCounts, SKIPPED))
            .other(getCount(statusCounts, OTHER))
            .start(startTime)
            .stop(stopTime)
            .build();
    }

    /**
     * Safely retrieves the count for a given test status from the status counts map.
     *
     * @param statusCounts the map of status counts
     * @param status the status to retrieve count for
     * @return the count for the status, or 0 if not present
     */
    private static int getCount(Map<Test.TestStatus, Long> statusCounts, Test.TestStatus status) {
        return statusCounts.getOrDefault(status, 0L).intValue();
    }
}

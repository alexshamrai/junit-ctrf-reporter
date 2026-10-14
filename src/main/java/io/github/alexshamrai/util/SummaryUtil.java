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
     * Creates a summary from test results: how many tests ended with each status, and how many are flaky.
     * <p>
     * Each test object is one test, represented by its final attempt, so earlier attempts of a retried test
     * are not counted. Each count takes one pass over the tests.
     *
     * @param tests the test results to summarize, one per test
     * @param startTime the test run start timestamp
     * @param stopTime the test run stop timestamp
     * @return a Summary object with status counts, the flaky count and timing information
     */
    public static Summary createSummary(List<Test> tests, long startTime, long stopTime) {
        Map<Test.TestStatus, Long> statusCounts = tests.stream()
            .map(Test::getStatus)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(status -> status, Collectors.counting()));
        long flaky = tests.stream()
            .filter(test -> Boolean.TRUE.equals(test.getFlaky()))
            .count();

        return Summary.builder()
            .tests(tests.size())
            .passed(getCount(statusCounts, PASSED))
            .failed(getCount(statusCounts, FAILED))
            .pending(getCount(statusCounts, PENDING))
            .skipped(getCount(statusCounts, SKIPPED))
            .other(getCount(statusCounts, OTHER))
            .flaky((int) flaky)
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

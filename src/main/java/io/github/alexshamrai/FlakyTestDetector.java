package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;

import java.util.List;
import java.util.stream.Collectors;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;

/**
 * Detects and marks flaky tests based on retry patterns.
 * <p>
 * A test is considered flaky if:
 * <ul>
 *   <li>It passed but had previous failed attempts with the same name</li>
 *   <li>It passed but has retries &gt; 0</li>
 * </ul>
 * <p>
 * This class is stateless and provides pure functions for flaky test detection.
 */
final class FlakyTestDetector {

    private FlakyTestDetector() {
        // Utility class - prevent instantiation
    }

    /**
     * Analyzes a test against existing tests to detect and mark flaky behavior.
     * <p>
     * This method:
     * <ol>
     *   <li>Finds all previous tests with the same name</li>
     *   <li>Sets retry count based on number of previous attempts</li>
     *   <li>Marks test as flaky if it passed after previous failures</li>
     * </ol>
     *
     * @param newTest      the test to analyze (will be modified if flaky)
     * @param existingTests all previously completed tests
     */
    static void detectAndMarkFlaky(Test newTest, List<Test> existingTests) {
        var previousTests = findTestsByName(newTest.getName(), existingTests);

        if (!previousTests.isEmpty()) {
            newTest.setRetries(previousTests.size());
        }

        if (PASSED.equals(newTest.getStatus())) {
            boolean hadPreviousFailures = previousTests.stream()
                .anyMatch(t -> FAILED.equals(t.getStatus()));
            if (hadPreviousFailures || (newTest.getRetries() != null && newTest.getRetries() > 0)) {
                newTest.setFlaky(true);
            }
        }
    }

    /**
     * Finds all tests with the given name.
     *
     * @param name          the test name to search for
     * @param existingTests the list of tests to search
     * @return list of tests with matching name
     */
    private static List<Test> findTestsByName(String name, List<Test> existingTests) {
        return existingTests.stream()
            .filter(t -> t.getName() != null && t.getName().equals(name))
            .collect(Collectors.toList());
    }
}

package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;

import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;

/**
 * Detects and marks flaky tests based on retry patterns.
 * <p>
 * Earlier report entries count as attempts of a test only when they are the same test: they share its
 * {@code testId} (the JUnit unique ID). Entries from reports written before {@code testId} existed
 * fall back to file path and name; a display name alone never identifies a test.
 * <p>
 * A test is considered flaky only if it passed after at least one failed attempt.
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
     *   <li>Finds all earlier attempts of the same test</li>
     *   <li>Sets retry count based on number of earlier attempts</li>
     *   <li>Marks test as flaky if it passed after an earlier failed attempt</li>
     * </ol>
     *
     * @param newTest      the test to analyze (will be modified if flaky)
     * @param existingTests all previously completed tests
     */
    static void detectAndMarkFlaky(Test newTest, List<Test> existingTests) {
        var previousAttempts = existingTests.stream()
            .filter(previous -> isSameTest(previous, newTest))
            .toList();

        if (!previousAttempts.isEmpty()) {
            newTest.setRetries(previousAttempts.size());
        }

        boolean failedBefore = previousAttempts.stream()
            .anyMatch(t -> FAILED.equals(t.getStatus()));
        if (PASSED.equals(newTest.getStatus()) && failedBefore) {
            newTest.setFlaky(true);
        }
    }

    private static boolean isSameTest(Test previous, Test current) {
        if (previous.getTestId() != null && current.getTestId() != null) {
            return previous.getTestId().equals(current.getTestId());
        }
        return previous.getFilepath() != null
            && previous.getFilepath().equals(current.getFilepath())
            && previous.getName() != null
            && previous.getName().equals(current.getName());
    }
}

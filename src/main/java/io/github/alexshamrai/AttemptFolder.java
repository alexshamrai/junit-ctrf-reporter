package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;

/**
 * Turns attempts into CTRF test objects and back.
 * <p>
 * An attempt is one execution of a test, recorded as a {@link Test}. Attempts of the same test share its
 * {@code testId} (the JUnit unique ID). Entries from reports written before {@code testId} existed are
 * grouped by file path and name among themselves, never by name alone, and never joined to attempts that
 * have a {@code testId}.
 * <p>
 * Folding follows the CTRF specification (sections 9.20 to 9.22 and 11): the final attempt is the test
 * object, the earlier ones go into {@code retryAttempts}, numbered from 1 in the order they ran, and the
 * test is flaky only if its final attempt passed after a failed one.
 * <p>
 * This class is stateless and never modifies the objects it is given.
 */
final class AttemptFolder {

    private AttemptFolder() {
        // Utility class - prevent instantiation
    }

    /**
     * Folds attempts into one test object per test. Tests keep the order of their first attempt.
     *
     * @param attempts the attempts, in the order they ran
     * @return one test object per test: its final attempt, with {@code retries}, {@code retryAttempts}
     *         and {@code flaky} set from the earlier attempts
     */
    static List<Test> fold(List<Test> attempts) {
        Map<String, List<Test>> attemptsByTest = new LinkedHashMap<>();
        for (int i = 0; i < attempts.size(); i++) {
            Test attempt = attempts.get(i);
            String key = keyOf(attempt);
            attemptsByTest.computeIfAbsent(key != null ? key : "unmatched:" + i, k -> new ArrayList<>()).add(attempt);
        }

        List<Test> tests = new ArrayList<>(attemptsByTest.size());
        for (List<Test> testAttempts : attemptsByTest.values()) {
            tests.add(foldAttemptsOfOneTest(testAttempts));
        }
        return tests;
    }

    /**
     * Splits test objects back into their attempts, so that new attempts can be folded together with them,
     * for example in a retry round that reads the report of the earlier rounds.
     *
     * @param tests test objects, for example from an existing report
     * @return the attempts: for each test, its earlier attempts in the order of {@code retryAttempts}, then
     *         the test itself without {@code retries}, {@code retryAttempts} and {@code flaky}
     */
    static List<Test> unfold(List<Test> tests) {
        List<Test> attempts = new ArrayList<>(tests.size());
        for (Test test : tests) {
            if (test.getRetryAttempts() != null) {
                for (RetryAttempt earlier : test.getRetryAttempts()) {
                    attempts.add(toAttempt(test, earlier));
                }
            }
            attempts.add(test.toBuilder().retries(null).retryAttempts(null).flaky(null).build());
        }
        return attempts;
    }

    private static Test foldAttemptsOfOneTest(List<Test> testAttempts) {
        Test last = testAttempts.get(testAttempts.size() - 1);
        if (testAttempts.size() == 1) {
            return last.toBuilder().retries(null).retryAttempts(null).flaky(null).build();
        }

        List<Test> earlier = testAttempts.subList(0, testAttempts.size() - 1);
        List<RetryAttempt> retryAttempts = new ArrayList<>(earlier.size());
        for (int i = 0; i < earlier.size(); i++) {
            retryAttempts.add(toRetryAttempt(i + 1, earlier.get(i)));
        }
        boolean failedBefore = earlier.stream().anyMatch(attempt -> attempt.getStatus() == FAILED);

        return last.toBuilder()
            .retries(retryAttempts.size())
            .retryAttempts(retryAttempts)
            .flaky(last.getStatus() == PASSED && failedBefore ? Boolean.TRUE : null)
            .build();
    }

    private static RetryAttempt toRetryAttempt(int number, Test attempt) {
        return RetryAttempt.builder()
            .attempt(number)
            .status(attempt.getStatus())
            .duration(attempt.getDuration())
            .start(attempt.getStart())
            .stop(attempt.getStop())
            .message(attempt.getMessage())
            .trace(attempt.getTrace())
            .build();
    }

    private static Test toAttempt(Test test, RetryAttempt earlier) {
        return Test.builder()
            .testId(test.getTestId())
            .name(test.getName())
            .filePath(test.getFilePath())
            .tags(test.getTags())
            .status(earlier.getStatus())
            .duration(earlier.getDuration())
            .start(earlier.getStart())
            .stop(earlier.getStop())
            .message(earlier.getMessage())
            .trace(earlier.getTrace())
            .build();
    }

    /**
     * The attempts of one test share this key: its {@code testId}, or, for entries from reports written
     * before {@code testId} existed, its file path and name. An entry with neither is never folded.
     */
    private static String keyOf(Test attempt) {
        if (attempt.getTestId() != null) {
            return "testId:" + attempt.getTestId();
        }
        if (attempt.getFilePath() != null && attempt.getName() != null) {
            return "filepath:" + attempt.getFilePath() + "#" + attempt.getName();
        }
        return null;
    }
}

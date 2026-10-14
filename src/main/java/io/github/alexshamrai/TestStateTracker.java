package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages test execution state during a test run.
 * <p>
 * It collects attempts, one per execution of a test, and the details of tests in progress. Attempts are
 * folded into one test object per test only when the report is written (see {@link AttemptFolder}), so
 * recording one costs O(1):
 * <ul>
 *   <li>{@link ConcurrentLinkedQueue} for attempts, in the order they finished</li>
 *   <li>{@link ConcurrentHashMap} for in-progress test details</li>
 * </ul>
 * <p>
 * Thread-safe: All operations use concurrent collections without additional synchronization.
 */
final class TestStateTracker {

    private final Queue<Test> attempts = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, TestDetails> testDetailsMap = new ConcurrentHashMap<>();

    /**
     * Records a finished attempt of a test.
     *
     * @param attempt the attempt's result
     */
    void addAttempt(Test attempt) {
        attempts.add(attempt);
    }

    /**
     * Records several attempts, for example those of earlier retry rounds read from an existing report.
     *
     * @param attemptsToAdd the attempts, in the order they ran
     */
    void addAllAttempts(List<Test> attemptsToAdd) {
        attempts.addAll(attemptsToAdd);
    }

    /**
     * Returns a snapshot of the recorded attempts, in the order they were recorded.
     *
     * @return a copy of all recorded attempts
     */
    List<Test> getAllAttempts() {
        return new ArrayList<>(attempts);
    }

    /**
     * Stores test details for an in-progress test.
     *
     * @param uniqueId the unique identifier for the test
     * @param details  the test details to store
     */
    void putTestDetails(String uniqueId, TestDetails details) {
        testDetailsMap.put(uniqueId, details);
    }

    /**
     * Retrieves and removes test details for a completed test.
     *
     * @param uniqueId the unique identifier for the test
     * @return the test details, or null if not found
     */
    TestDetails removeTestDetails(String uniqueId) {
        return testDetailsMap.remove(uniqueId);
    }

    /**
     * Clears all test state.
     * <p>
     * This is called after report generation to prepare for the next test run.
     */
    void clear() {
        attempts.clear();
        testDetailsMap.clear();
    }
}

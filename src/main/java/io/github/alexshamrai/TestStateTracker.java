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
 * It collects attempts, one per execution of a test, the containers that finished without failing, and the
 * details of tests in progress. Attempts are folded into one test object per test only when the report is
 * written (see {@link AttemptFolder}), so recording one costs O(1):
 * <ul>
 *   <li>{@link ConcurrentLinkedQueue} for attempts, in the order they finished</li>
 *   <li>{@link ConcurrentLinkedQueue} for containers that finished without failing</li>
 *   <li>{@link ConcurrentHashMap} for in-progress test details</li>
 * </ul>
 * <p>
 * Thread-safe: All operations use concurrent collections without additional synchronization.
 */
final class TestStateTracker {

    private final Queue<Test> attempts = new ConcurrentLinkedQueue<>();
    private final Queue<ContainerSuccess> containerSuccesses = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, TestDetails> testDetailsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CapturedOutput> outputs = new ConcurrentHashMap<>();

    /**
     * Records a finished attempt of a test.
     *
     * @param attempt the attempt's result
     */
    void addAttempt(Test attempt) {
        attempts.add(attempt);
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
     * Records a container that finished without failing.
     *
     * @param success the container and when it ran
     */
    void addContainerSuccess(ContainerSuccess success) {
        containerSuccesses.add(success);
    }

    /**
     * Returns a snapshot of the recorded container successes, in the order they were recorded.
     *
     * @return a copy of all recorded container successes
     */
    List<ContainerSuccess> getAllContainerSuccesses() {
        return new ArrayList<>(containerSuccesses);
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
     * Keeps the output that JUnit captured for a test or container until its result is recorded.
     *
     * @param uniqueId the test's or container's unique ID
     * @param output   the captured output
     */
    void putOutput(String uniqueId, CapturedOutput output) {
        outputs.put(uniqueId, output);
    }

    /**
     * Removes and returns the output that JUnit captured for a test or container.
     *
     * @param uniqueId the test's or container's unique ID
     * @return the output, or null if there is none
     */
    CapturedOutput removeOutput(String uniqueId) {
        return outputs.remove(uniqueId);
    }

    /**
     * Removes and returns the details of every test in progress, each once, even while tests finish on other threads.
     *
     * @return the tests that started and haven't finished
     */
    List<TestDetails> removeTestsInProgress() {
        List<TestDetails> inProgress = new ArrayList<>();
        for (String uniqueId : testDetailsMap.keySet()) {
            TestDetails details = testDetailsMap.remove(uniqueId);
            if (details != null) {
                inProgress.add(details);
            }
        }
        return inProgress;
    }
}

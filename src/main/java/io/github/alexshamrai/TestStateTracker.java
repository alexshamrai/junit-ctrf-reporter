package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages test execution state during a test run.
 * <p>
 * This class is responsible for tracking tests and their details during execution.
 * It uses thread-safe collections to support parallel test execution:
 * <ul>
 *   <li>{@link CopyOnWriteArrayList} for completed tests (optimized for reads)</li>
 *   <li>{@link ConcurrentHashMap} for in-progress test details</li>
 * </ul>
 * <p>
 * Thread-safe: All operations use concurrent collections without additional synchronization.
 */
final class TestStateTracker {

    private final List<Test> tests = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, TestDetails> testDetailsMap = new ConcurrentHashMap<>();

    /**
     * Adds a completed test to the collection.
     *
     * @param test the test result to add
     */
    void addTest(Test test) {
        tests.add(test);
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
     * Returns all completed tests.
     * <p>
     * The returned list is a snapshot and can be safely iterated even while
     * tests are being added by other threads.
     *
     * @return list of all completed tests
     */
    List<Test> getAllTests() {
        return tests;
    }

    /**
     * Adds multiple tests to the collection.
     * <p>
     * This is typically used to restore tests from a previous run when
     * supporting test reruns.
     *
     * @param testsToAdd the tests to add
     */
    void addAllTests(List<Test> testsToAdd) {
        tests.addAll(testsToAdd);
    }

    /**
     * Clears all test state.
     * <p>
     * This is called after report generation to prepare for the next test run.
     */
    void clear() {
        tests.clear();
        testDetailsMap.clear();
    }
}

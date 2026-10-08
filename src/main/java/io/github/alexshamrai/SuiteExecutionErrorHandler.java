package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import lombok.RequiredArgsConstructor;

/**
 * Handles errors that occur during test suite initialization and execution.
 *
 * <p>This class is responsible for capturing and processing errors that occur outside
 * the context of individual test methods, particularly during test suite setup and overall execution.
 * It creates test objects that represent these errors to ensure they are properly reported
 * in the test results.</p>
 *
 * <p>It deliberately takes plain values instead of JUnit Jupiter types, so that the code path
 * used by the JUnit Platform listener works without JUnit Jupiter on the classpath.</p>
 */
@RequiredArgsConstructor
public class SuiteExecutionErrorHandler {

    private static final String INITIALIZATION_ERROR = "initializationError";
    private final TestProcessor testProcessor;

    /**
     * Handles errors that occur during test suite initialization (e.g., @BeforeAll failures).
     *
     * <p>Creates a synthetic "initializationError" test entry to capture the failure,
     * matching the behavior of JUnit's XML reporter.</p>
     *
     * @param filepath         the name of the test class that failed, or {@code null} if unknown
     * @param cause            the error that made the suite fail
     * @param testRunStartTime the timestamp when the test run started
     * @param testRunStopTime  the timestamp when the test run stopped
     * @return a failed Test object representing the error
     */
    public Test handleInitializationError(String filepath, Throwable cause, long testRunStartTime, long testRunStopTime) {
        var failureTest = Test.builder()
            .name(INITIALIZATION_ERROR)
            .filepath(filepath)
            .status(TestStatus.FAILED)
            .start(testRunStartTime)
            .stop(testRunStopTime)
            .duration(testRunStopTime - testRunStartTime)
            .build();
        testProcessor.setFailureDetails(failureTest, cause);
        return failureTest;
    }
}

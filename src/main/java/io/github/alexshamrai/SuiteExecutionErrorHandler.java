package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

/**
 * Handles errors that occur during test suite initialization and execution.
 *
 * <p>This class is responsible for capturing and processing errors that occur outside
 * the context of individual test methods, particularly during test suite setup and overall execution.
 * It creates test objects that represent these errors to ensure they are properly reported
 * in the test results.</p>
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
     * @param context          the JUnit extension context containing execution information
     * @param testRunStartTime the timestamp when the test run started
     * @param testRunStopTime  the timestamp when the test run stopped
     * @return an Optional containing a Test object if an initialization error occurred, or empty if no error
     */
    public Optional<Test> handleInitializationError(ExtensionContext context, long testRunStartTime, long testRunStopTime) {
        return context.getExecutionException().map(cause -> {
            String filepath = context.getTestClass()
                .map(Class::getName)
                .orElse(null);

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
        });
    }
}

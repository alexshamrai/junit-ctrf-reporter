package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

public class SuiteExecutionErrorHandlerTest {

    @Mock
    private TestProcessor testProcessor;

    private SuiteExecutionErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        errorHandler = new SuiteExecutionErrorHandler(testProcessor);
    }

    @org.junit.jupiter.api.Test
    void handleInitializationError_returnsFailedInitializationErrorTest() {
        var cause = new RuntimeException("Test exception");

        Test test = errorHandler.handleInitializationError("com.example.FailingTest", cause, 1_000L, 2_000L);

        assertEquals("initializationError", test.getName());
        assertEquals("com.example.FailingTest", test.getFilepath());
        assertEquals(TestStatus.FAILED, test.getStatus());
        assertEquals(1_000L, test.getStart());
        assertEquals(2_000L, test.getStop());
        assertEquals(1_000L, test.getDuration());
        verify(testProcessor).setFailureDetails(test, cause);
    }

    @org.junit.jupiter.api.Test
    void handleInitializationError_withoutClassName_leavesFilepathEmpty() {
        Test test = errorHandler.handleInitializationError(null, new RuntimeException("Test exception"), 1_000L, 2_000L);

        assertNull(test.getFilepath());
        assertEquals(TestStatus.FAILED, test.getStatus());
    }
}

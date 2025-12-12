package io.github.alexshamrai.jupiter;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CtrfExtensionTest {

    private static final String TEST_DISPLAY_NAME = "Test Display Name";
    private static final String TEST_UNIQUE_ID = "[unique-id-123]";

    @Mock
    private CtrfReportManager reportManager;

    @Mock
    private ExtensionContext extensionContext;

    private CtrfExtension ctrfExtension;
    private MockedStatic<CtrfReportManager> mockedStaticManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockedStaticManager = Mockito.mockStatic(CtrfReportManager.class);
        mockedStaticManager.when(CtrfReportManager::getInstance).thenReturn(reportManager);

        ctrfExtension = new CtrfExtension();

        // Configure the mock ExtensionContext with predictable data
        when(extensionContext.getDisplayName()).thenReturn(TEST_DISPLAY_NAME);
        when(extensionContext.getUniqueId()).thenReturn(TEST_UNIQUE_ID);
        when(extensionContext.getTags()).thenReturn(Collections.singleton("smoke-test"));
        when(extensionContext.getTestClass()).thenReturn(Optional.of(this.getClass()));
    }

    @AfterEach
    void tearDown() {
        // It's crucial to close the static mock after each test to avoid leakage
        mockedStaticManager.close();
    }

    @Test
    void beforeAllTests_shouldDelegateToManager() {
        ctrfExtension.beforeAllTests(extensionContext);
        verify(reportManager).startTestRun("io.github.alexshamrai.jupiter.CtrfExtension");
    }

    @Test
    void afterAllTests_shouldDelegateToManager() {
        ctrfExtension.afterAllTests(extensionContext);
        verify(reportManager).finishTestRun(Optional.of(extensionContext));
    }

    @Test
    void beforeEach_shouldDelegateToManagerWithCorrectDetails() {
        ctrfExtension.beforeEach(extensionContext);

        var captor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(captor.capture());

        TestDetails details = captor.getValue();
        assertEquals(TEST_UNIQUE_ID, details.uniqueId());
        assertEquals(TEST_DISPLAY_NAME, details.displayName());
        assertEquals(this.getClass().getName(), details.filePath());
        assertTrue(details.tags().contains("smoke-test"));
    }

    @Test
    void testSuccessful_shouldDelegateToManager() {
        ctrfExtension.testSuccessful(extensionContext);
        verify(reportManager).onTestSuccess(eq(TEST_UNIQUE_ID));
    }

    @Test
    void testFailed_shouldDelegateToManager() {
        var cause = new RuntimeException("Test failed");
        ctrfExtension.testFailed(extensionContext, cause);
        verify(reportManager).onTestFailure(eq(TEST_UNIQUE_ID), eq(cause));
    }

    @Test
    void testAborted_shouldDelegateToManager() {
        var cause = new InterruptedException("Test aborted");
        ctrfExtension.testAborted(extensionContext, cause);
        verify(reportManager).onTestAborted(eq(TEST_UNIQUE_ID), eq(cause));
    }

    @Test
    void testDisabled_shouldDelegateToManagerWithCorrectDetails() {
        Optional<String> reason = Optional.of("This test is disabled");
        ctrfExtension.testDisabled(extensionContext, reason);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        var reasonCaptor = ArgumentCaptor.forClass(Optional.class);

        verify(reportManager).onTestSkipped(detailsCaptor.capture(), reasonCaptor.capture());

        TestDetails details = detailsCaptor.getValue();
        assertEquals(TEST_UNIQUE_ID, details.uniqueId());
        assertEquals(TEST_DISPLAY_NAME, details.displayName());
        assertEquals(reason, reasonCaptor.getValue());
    }

    @Test
    void handleBeforeAllMethodExecutionException_shouldCaptureInitializationError() {
        var cause = new RuntimeException("Spring context failed to load");

        assertThrows(RuntimeException.class, () ->
            ctrfExtension.handleBeforeAllMethodExecutionException(extensionContext, cause)
        );

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(detailsCaptor.capture());

        TestDetails details = detailsCaptor.getValue();
        assertEquals("initializationError", details.displayName());
        assertEquals(this.getClass().getName(), details.filePath());
        assertTrue(details.uniqueId().endsWith("/initializationError"));
        assertTrue(details.tags().contains("smoke-test"));

        verify(reportManager).onTestFailure(eq(details.uniqueId()), eq(cause));
    }

    @Test
    void handleBeforeAllMethodExecutionException_shouldRethrowException() {
        var cause = new IllegalStateException("Configuration error");

        var thrown = assertThrows(IllegalStateException.class, () ->
            ctrfExtension.handleBeforeAllMethodExecutionException(extensionContext, cause)
        );

        assertEquals(cause, thrown);
    }

    @Test
    void handleBeforeAllMethodExecutionException_shouldHandleMissingTestClass() {
        when(extensionContext.getTestClass()).thenReturn(Optional.empty());
        var cause = new RuntimeException("Initialization failed");

        assertThrows(RuntimeException.class, () ->
            ctrfExtension.handleBeforeAllMethodExecutionException(extensionContext, cause)
        );

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(detailsCaptor.capture());

        TestDetails details = detailsCaptor.getValue();
        // When test class is not available, should fall back to display name
        assertEquals(TEST_DISPLAY_NAME, details.filePath());
    }
}
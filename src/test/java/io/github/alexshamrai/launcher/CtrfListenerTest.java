package io.github.alexshamrai.launcher;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CtrfListenerTest {

    private static final String TEST_DISPLAY_NAME = "Test Display Name";
    private static final String TEST_UNIQUE_ID = "[unique-id-123]";
    private static final String TEST_CLASS_NAME = "io.github.alexshamrai.TestClass";

    @Mock
    private CtrfReportManager reportManager;

    @Mock
    private TestPlan testPlan;

    @Mock
    private TestIdentifier testIdentifier;

    @Mock
    private TestExecutionResult testExecutionResult;

    private CtrfListener ctrfListener;
    private MockedStatic<CtrfReportManager> mockedStaticManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockedStaticManager = Mockito.mockStatic(CtrfReportManager.class);
        mockedStaticManager.when(CtrfReportManager::getInstance).thenReturn(reportManager);

        ctrfListener = new CtrfListener();

        // Configure the mock TestIdentifier with predictable data
        when(testIdentifier.getDisplayName()).thenReturn(TEST_DISPLAY_NAME);
        when(testIdentifier.getUniqueId()).thenReturn(TEST_UNIQUE_ID);
        when(testIdentifier.isTest()).thenReturn(true);
        when(testIdentifier.getTags()).thenReturn(Set.of(TestTag.create("smoke-test")));
    }

    @AfterEach
    void tearDown() {
        // It's crucial to close the static mock after each test to avoid leakage
        mockedStaticManager.close();
    }

    @Test
    void testPlanExecutionStarted_shouldDelegateToManager() {
        ctrfListener.testPlanExecutionStarted(testPlan);
        verify(reportManager).startTestRun("io.github.alexshamrai.launcher.CtrfListener");
    }

    @Test
    void testPlanExecutionFinished_shouldDelegateToManager() {
        ctrfListener.testPlanExecutionFinished(testPlan);
        verify(reportManager).finishTestRun(Optional.empty());
    }

    @Test
    void executionStarted_shouldDelegateToManagerWithCorrectDetails_whenIsTest() {
        MethodSource methodSource = MethodSource.from(TEST_CLASS_NAME, "testMethod");
        when(testIdentifier.getSource()).thenReturn(Optional.of(methodSource));

        ctrfListener.executionStarted(testIdentifier);

        var captor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(captor.capture());

        TestDetails details = captor.getValue();
        assertEquals(TEST_UNIQUE_ID, details.uniqueId());
        assertEquals(TEST_DISPLAY_NAME, details.displayName());
        assertEquals(TEST_CLASS_NAME, details.filePath());
        assertEquals(1, details.tags().size());
        assertTrue(details.tags().stream().anyMatch(tag -> tag.contains("smoke-test")));
    }

    @Test
    void executionStarted_shouldIgnoreNonTestIdentifiers() {
        when(testIdentifier.isTest()).thenReturn(false);

        ctrfListener.executionStarted(testIdentifier);

        verify(reportManager, never()).onTestStart(any());
    }

    @Test
    void executionFinished_shouldDelegateToManagerForSuccessfulTest() {
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);

        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager).onTestSuccess(eq(TEST_UNIQUE_ID));
    }

    @Test
    void executionFinished_shouldDelegateToManagerForFailedTest() {
        var cause = new RuntimeException("Test failed");
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(cause));

        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager).onTestFailure(eq(TEST_UNIQUE_ID), eq(cause));
    }

    @Test
    void executionFinished_shouldDelegateToManagerForAbortedTest() {
        var cause = new InterruptedException("Test aborted");
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.ABORTED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(cause));

        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager).onTestAborted(eq(TEST_UNIQUE_ID), eq(cause));
    }

    @Test
    void executionFinished_shouldHandleFailedTestWithoutThrowable() {
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.empty());

        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager).onTestFailure(eq(TEST_UNIQUE_ID), eq(null));
    }

    @Test
    void executionFinished_shouldIgnoreNonTestNonContainerFailures() {
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(false);

        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager, never()).onTestSuccess(any());
        verify(reportManager, never()).onTestFailure(any(), any());
        verify(reportManager, never()).onTestAborted(any(), any());
    }

    @Test
    void executionFinished_shouldHandleContainerFailure() {
        ClassSource classSource = ClassSource.from(TEST_CLASS_NAME);
        var cause = new RuntimeException("Context initialization failed");

        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getSource()).thenReturn(Optional.of(classSource));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(cause));

        // First, trigger executionStarted to record container start time
        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(detailsCaptor.capture());

        TestDetails details = detailsCaptor.getValue();
        assertEquals("initializationError", details.displayName());
        assertEquals(TEST_CLASS_NAME, details.filePath());
        assertTrue(details.uniqueId().endsWith("/initializationError"));

        verify(reportManager).onTestFailure(eq(details.uniqueId()), eq(cause));
    }

    @Test
    void executionFinished_shouldIgnoreContainerSuccess() {
        ClassSource classSource = ClassSource.from(TEST_CLASS_NAME);

        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getSource()).thenReturn(Optional.of(classSource));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager, never()).onTestStart(any());
        verify(reportManager, never()).onTestFailure(any(), any());
    }

    @Test
    void executionFinished_shouldIgnoreNonClassContainerFailure() {
        // Container without ClassSource (e.g., engine or package container) should be ignored
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getSource()).thenReturn(Optional.empty());
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager, never()).onTestStart(any());
        verify(reportManager, never()).onTestFailure(any(), any());
    }

    @Test
    void executionFinished_shouldHandleContainerFailureWithoutStartTime() {
        ClassSource classSource = ClassSource.from(TEST_CLASS_NAME);
        var cause = new RuntimeException("Context initialization failed");

        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getSource()).thenReturn(Optional.of(classSource));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(cause));

        // No executionStarted call, so no tracked start time
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(detailsCaptor.capture());

        TestDetails details = detailsCaptor.getValue();
        assertEquals("initializationError", details.displayName());
        assertTrue(details.startTime() > 0);
    }

    @Test
    void executionSkipped_shouldDelegateToManagerWithReason() {
        String reason = "Test is skipped";
        MethodSource methodSource = MethodSource.from(TEST_CLASS_NAME, "skippedMethod");
        when(testIdentifier.getSource()).thenReturn(Optional.of(methodSource));

        ctrfListener.executionSkipped(testIdentifier, reason);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        var reasonCaptor = ArgumentCaptor.forClass(Optional.class);

        verify(reportManager).onTestSkipped(detailsCaptor.capture(), reasonCaptor.capture());

        TestDetails details = detailsCaptor.getValue();
        assertEquals(TEST_UNIQUE_ID, details.uniqueId());
        assertEquals(TEST_DISPLAY_NAME, details.displayName());
        assertEquals(TEST_CLASS_NAME, details.filePath());
        assertEquals(Optional.of(reason), reasonCaptor.getValue());
    }

    @Test
    void executionSkipped_shouldHandleNullReason() {
        MethodSource methodSource = MethodSource.from(TEST_CLASS_NAME, "skippedMethod");
        when(testIdentifier.getSource()).thenReturn(Optional.of(methodSource));

        ctrfListener.executionSkipped(testIdentifier, null);

        var reasonCaptor = ArgumentCaptor.forClass(Optional.class);
        verify(reportManager).onTestSkipped(any(TestDetails.class), reasonCaptor.capture());

        assertEquals(Optional.empty(), reasonCaptor.getValue());
    }

    @Test
    void executionSkipped_shouldIgnoreNonTestIdentifiers() {
        when(testIdentifier.isTest()).thenReturn(false);

        ctrfListener.executionSkipped(testIdentifier, "reason");

        verify(reportManager, never()).onTestSkipped(any(), any());
    }

    @Test
    void createTestDetails_shouldExtractFromMethodSource() {
        MethodSource methodSource = MethodSource.from(TEST_CLASS_NAME, "testMethod");
        when(testIdentifier.getSource()).thenReturn(Optional.of(methodSource));

        ctrfListener.executionStarted(testIdentifier);

        var captor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(captor.capture());

        TestDetails details = captor.getValue();
        assertEquals(TEST_CLASS_NAME, details.filePath());
    }

    @Test
    void createTestDetails_shouldExtractFromClassSource() {
        ClassSource classSource = ClassSource.from(TEST_CLASS_NAME);
        when(testIdentifier.getSource()).thenReturn(Optional.of(classSource));

        ctrfListener.executionStarted(testIdentifier);

        var captor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(captor.capture());

        TestDetails details = captor.getValue();
        assertEquals(TEST_CLASS_NAME, details.filePath());
    }

    @Test
    void createTestDetails_shouldHandleUnknownTestSource() {
        TestSource unknownSource = new TestSource() {};
        when(testIdentifier.getSource()).thenReturn(Optional.of(unknownSource));

        ctrfListener.executionStarted(testIdentifier);

        var captor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(captor.capture());

        TestDetails details = captor.getValue();
        assertEquals(unknownSource.toString(), details.filePath());
    }

    @Test
    void createTestDetails_shouldHandleEmptySource() {
        when(testIdentifier.getSource()).thenReturn(Optional.empty());

        ctrfListener.executionStarted(testIdentifier);

        var captor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(captor.capture());

        TestDetails details = captor.getValue();
        assertNull(details.filePath());
    }

    @Test
    void createTestDetails_shouldHandleEmptyTags() {
        when(testIdentifier.getTags()).thenReturn(Collections.emptySet());
        when(testIdentifier.getSource()).thenReturn(Optional.empty());

        ctrfListener.executionStarted(testIdentifier);

        var captor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(captor.capture());

        TestDetails details = captor.getValue();
        assertTrue(details.tags().isEmpty());
    }
}

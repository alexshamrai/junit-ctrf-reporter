package io.github.alexshamrai.launcher;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    /**
     * The shutdown hooks the listeners added and haven't removed, in place of the JVM's.
     */
    private final List<Thread> exitHooks = new ArrayList<>();

    private final ShutdownHooks shutdownHooks = new ShutdownHooks() {
        @Override
        public void add(Thread hook) {
            exitHooks.add(hook);
        }

        @Override
        public void remove(Thread hook) {
            exitHooks.remove(hook);
        }
    };

    private CtrfListener ctrfListener;

    /**
     * The owner of the run that {@link #setUp()} started.
     */
    private Object runOwner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        ctrfListener = new CtrfListener(reportManager, shutdownHooks);

        // The listener reports only while it owns the run
        when(reportManager.startTestRun(any(), anyString())).thenReturn(true);
        ctrfListener.testPlanExecutionStarted(testPlan);
        var owner = ArgumentCaptor.forClass(Object.class);
        verify(reportManager).startTestRun(owner.capture(), anyString());
        runOwner = owner.getValue();
        clearInvocations(reportManager);

        // Configure the mock TestIdentifier with predictable data
        when(testIdentifier.getDisplayName()).thenReturn(TEST_DISPLAY_NAME);
        when(testIdentifier.getUniqueId()).thenReturn(TEST_UNIQUE_ID);
        when(testIdentifier.isTest()).thenReturn(true);
        when(testIdentifier.getTags()).thenReturn(Set.of(TestTag.create("smoke-test")));
    }

    @Test
    void testPlanExecutionStarted_startsARunOwnedByThisListenersOwnToken() {
        ctrfListener.testPlanExecutionStarted(testPlan);

        var owner = ArgumentCaptor.forClass(Object.class);
        verify(reportManager).startTestRun(owner.capture(), eq("io.github.alexshamrai.launcher.CtrfListener"));
        // Not the TestPlan, which a second listener on the same launcher sees too
        assertNotSame(testPlan, owner.getValue());
    }

    @Test
    void testPlanExecutionFinished_finishesTheRunWithTheOwnerThatStartedIt() {
        ctrfListener.testPlanExecutionStarted(testPlan);
        ctrfListener.testPlanExecutionFinished(testPlan);

        var started = ArgumentCaptor.forClass(Object.class);
        var finished = ArgumentCaptor.forClass(Object.class);
        verify(reportManager).startTestRun(started.capture(), anyString());
        verify(reportManager).finishTestRun(finished.capture());
        assertSame(started.getValue(), finished.getValue());
    }

    @Test
    void listenerWhoseRunStartIsRefused_reportsNothing() {
        TestPlan nestedPlan = mock(TestPlan.class);
        when(reportManager.startTestRun(any(), anyString())).thenReturn(false);
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);
        var nested = new CtrfListener(reportManager, shutdownHooks);

        nested.testPlanExecutionStarted(nestedPlan);
        assertEquals(1, exitHooks.size(), "only the run's owner adds an exit hook");
        nested.executionStarted(testIdentifier);
        nested.executionFinished(testIdentifier, testExecutionResult);
        nested.executionSkipped(testIdentifier, "disabled");
        nested.testPlanExecutionFinished(nestedPlan);

        verify(reportManager).startTestRun(notNull(), eq("io.github.alexshamrai.launcher.CtrfListener"));
        verifyNoMoreInteractions(reportManager);
    }

    @Test
    void owningARun_addsAnExitHookThatTheRunsFinishRemoves() {
        assertEquals(1, exitHooks.size(), "the run that setUp started added one");

        ctrfListener.testPlanExecutionFinished(testPlan);

        assertTrue(exitHooks.isEmpty());
    }

    @Test
    void exitHook_reportsTheTestsThatNeverStartedAsSkippedThenFinishesTheRunOnExit() {
        TestIdentifier engine = mock(TestIdentifier.class);
        TestIdentifier started = childTest("started()");
        TestIdentifier notStarted = childTest("notStarted()");
        TestIdentifier templateNotStarted = templateMethod("notStartedTemplate(int)");
        TestIdentifier templateRunning = templateMethod("runningTemplate(int)");
        when(testPlan.getRoots()).thenReturn(Set.of(engine));
        when(testPlan.getChildren(engine)).thenReturn(Set.of(started, notStarted, templateNotStarted, templateRunning));
        when(testPlan.getDescendants(templateNotStarted)).thenReturn(Set.of());
        when(testPlan.getDescendants(templateRunning)).thenReturn(Set.of());
        ctrfListener.executionStarted(started);
        ctrfListener.executionStarted(templateRunning);

        exitHooks.get(0).run();

        var skipped = ArgumentCaptor.forClass(TestDetails.class);
        InOrder inOrder = inOrder(reportManager);
        inOrder.verify(reportManager, times(2))
            .onTestSkipped(skipped.capture(), eq(Optional.of("The JVM exited before this test started")));
        inOrder.verify(reportManager).finishTestRunOnExit(runOwner);
        assertEquals(Set.of("notStarted()", "notStartedTemplate(int)"),
            skipped.getAllValues().stream().map(TestDetails::displayName).collect(Collectors.toSet()));
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

        verify(reportManager, never()).onTestSuccess(anyString());
        verify(reportManager, never()).onTestFailure(anyString(), any());
        verify(reportManager, never()).onTestAborted(anyString(), any());
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
        assertEquals(TEST_UNIQUE_ID, details.uniqueId(), "a class failure is identified by the class itself");

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
        verify(reportManager, never()).onTestFailure(anyString(), any());
    }

    @Test
    void executionFinished_shouldReportFailedContainerWithoutClassSourceUnderItsDisplayName() {
        var cause = new RuntimeException("Engine failed");
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getSource()).thenReturn(Optional.empty());
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(cause));

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(detailsCaptor.capture());
        TestDetails details = detailsCaptor.getValue();
        assertEquals(TEST_DISPLAY_NAME, details.displayName());
        assertEquals(TEST_UNIQUE_ID, details.uniqueId());
        assertNull(details.filePath());
        verify(reportManager).onTestFailure(TEST_UNIQUE_ID, cause);
    }

    @Test
    void executionFinished_shouldReportFailedMethodLevelContainerWithItsClassAsFilePath() {
        var cause = new IllegalStateException("cannot load test data");
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getSource()).thenReturn(Optional.of(MethodSource.from(TEST_CLASS_NAME, "usesData", "int")));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(cause));

        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(detailsCaptor.capture());
        assertEquals(TEST_DISPLAY_NAME, detailsCaptor.getValue().displayName());
        assertEquals(TEST_CLASS_NAME, detailsCaptor.getValue().filePath());
        verify(reportManager).onTestFailure(TEST_UNIQUE_ID, cause);
    }

    @Test
    void executionFinished_shouldNameClassFailureAfterItsTestsStartedTeardownError() {
        var cause = new IllegalStateException("cleanup failed");
        TestIdentifier childTest = mock(TestIdentifier.class);
        when(childTest.isTest()).thenReturn(true);
        when(childTest.getUniqueId()).thenReturn(TEST_UNIQUE_ID + "/[method:ok()]");
        when(childTest.getParentId()).thenReturn(Optional.of(TEST_UNIQUE_ID));
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getSource()).thenReturn(Optional.of(ClassSource.from(TEST_CLASS_NAME)));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(cause));

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionStarted(childTest);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager, times(2)).onTestStart(detailsCaptor.capture());
        TestDetails details = detailsCaptor.getAllValues().get(1);
        assertEquals("teardownError", details.displayName());
        assertEquals(TEST_UNIQUE_ID, details.uniqueId(), "a class failure is identified by the class itself");
        verify(reportManager).onTestFailure(TEST_UNIQUE_ID, cause);
    }

    @Test
    void executionFinished_shouldNameClassFailureAfterItsTestsWereSkippedTeardownError() {
        var cause = new IllegalStateException("cleanup failed");
        TestIdentifier skippedTest = mock(TestIdentifier.class);
        when(skippedTest.isTest()).thenReturn(true);
        when(skippedTest.getUniqueId()).thenReturn(TEST_UNIQUE_ID + "/[method:disabled()]");
        when(skippedTest.getParentId()).thenReturn(Optional.of(TEST_UNIQUE_ID));
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getSource()).thenReturn(Optional.of(ClassSource.from(TEST_CLASS_NAME)));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(cause));

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionSkipped(skippedTest, "not ready");
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestStart(detailsCaptor.capture());
        assertEquals("teardownError", detailsCaptor.getValue().displayName());
        verify(reportManager).onTestFailure(TEST_UNIQUE_ID, cause);
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
    void executionSkipped_shouldReportTestsOfASkippedContainerAsSkipped() {
        TestIdentifier childTest = childTest("one()");
        TestIdentifier nestedContainer = mock(TestIdentifier.class);
        when(nestedContainer.isTest()).thenReturn(false);
        when(testIdentifier.isTest()).thenReturn(false);
        when(testPlan.getDescendants(testIdentifier)).thenReturn(Set.of(childTest, nestedContainer));

        ctrfListener.testPlanExecutionStarted(testPlan);
        ctrfListener.executionSkipped(testIdentifier, "whole class disabled");

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestSkipped(detailsCaptor.capture(), eq(Optional.of("whole class disabled")));
        assertEquals("one()", detailsCaptor.getValue().displayName());
    }

    @Test
    void executionFinished_shouldReportTestsOfAnAbortedContainerAsSkipped() {
        TestIdentifier childTest = childTest("one()");
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testPlan.getDescendants(testIdentifier)).thenReturn(Set.of(childTest));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.ABORTED);
        when(testExecutionResult.getThrowable())
            .thenReturn(Optional.of(new RuntimeException("Assumption failed: Docker is not available")));

        ctrfListener.testPlanExecutionStarted(testPlan);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager).onTestSkipped(any(TestDetails.class), eq(Optional.of("Assumption failed: Docker is not available")));
    }

    @Test
    void executionFinished_shouldNotReportTestsThatAlreadyStartedWhenTheirContainerIsAborted() {
        TestIdentifier childTest = childTest("one()");
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testPlan.getDescendants(testIdentifier)).thenReturn(Set.of(childTest));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.ABORTED);

        ctrfListener.testPlanExecutionStarted(testPlan);
        ctrfListener.executionStarted(childTest);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager, never()).onTestSkipped(any(), any());
    }

    @Test
    void exitHook_waitsForTheCallbackInProgressAndTheCallbacksAfterItRecordNothing() throws Exception {
        TestIdentifier first = childTest("first()");
        TestIdentifier second = childTest("second()");
        var recording = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            recording.countDown();
            release.await(10, TimeUnit.SECONDS);
            return null;
        }).when(reportManager).onTestStart(any());
        var callback = new Thread(() -> ctrfListener.executionStarted(first));
        callback.start();
        recording.await(10, TimeUnit.SECONDS);

        var hook = exitHooks.get(0);
        hook.start();

        // As under SIGTERM, a JUnit thread is still recording when the hook starts
        verify(reportManager, after(200).never()).finishTestRunOnExit(any());
        release.countDown();
        verify(reportManager, timeout(10_000)).finishTestRunOnExit(runOwner);
        hook.join(10_000);
        callback.join(10_000);
        ctrfListener.executionStarted(second);
        verify(reportManager, times(1)).onTestStart(any());
    }

    @Test
    void exitHook_writesTheRunEvenIfFindingTheTestsThatNeverStartedFails() {
        when(testPlan.getRoots()).thenThrow(new ConcurrentModificationException());

        assertThrows(ConcurrentModificationException.class, () -> exitHooks.get(0).run());

        verify(reportManager).finishTestRunOnExit(runOwner);
    }

    @Test
    void exitHook_afterTheRunFinished_doesNothing() {
        var hook = exitHooks.get(0);
        ctrfListener.testPlanExecutionFinished(testPlan);
        clearInvocations(reportManager);

        // The JVM runs a hook that it was shutting down too late to remove
        hook.run();

        verifyNoMoreInteractions(reportManager);
    }

    @Test
    void exitHook_addsAnEntryForTheInnermostContainerThatWasRunningWithoutATest() {
        TestIdentifier engine = container("[engine:junit-jupiter]", null);
        TestIdentifier outer = container("[engine:junit-jupiter]/[class:Outer]", "[engine:junit-jupiter]");
        TestIdentifier inner = container("[engine:junit-jupiter]/[class:Outer]/[nested-class:Inner]",
            "[engine:junit-jupiter]/[class:Outer]");
        when(testPlan.getRoots()).thenReturn(Set.of(engine));
        ctrfListener.executionStarted(engine);
        ctrfListener.executionStarted(outer);
        ctrfListener.executionStarted(inner);

        exitHooks.get(0).run();

        // As in Inner's @BeforeAll: it's where the JVM was, and its tests hadn't started
        var entry = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestInterrupted(entry.capture(), eq("The JVM exited while this container was running"));
        assertEquals("[engine:junit-jupiter]/[class:Outer]/[nested-class:Inner]", entry.getValue().uniqueId());
        assertEquals("initializationError", entry.getValue().displayName());
    }

    @Test
    void exitHook_addsNoEntryForAnEngine() {
        TestIdentifier engine = container("[engine:junit-jupiter]", null);
        when(testPlan.getRoots()).thenReturn(Set.of(engine));
        ctrfListener.executionStarted(engine);

        // As between two classes: every fork runs the engine, which isn't where the JVM was
        exitHooks.get(0).run();

        verify(reportManager, never()).onTestInterrupted(any(), any());
    }

    @Test
    void exitHook_addsNoEntryForAContainerWithATestRunningBelowIt() {
        TestIdentifier running = childTest("running()");
        TestIdentifier finished = childTest("finished()");
        TestIdentifier theClass = container(TEST_UNIQUE_ID, "[engine:junit-jupiter]");
        when(testPlan.getRoots()).thenReturn(Set.of());
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);
        ctrfListener.executionStarted(theClass);
        ctrfListener.executionStarted(finished);
        ctrfListener.executionFinished(finished, testExecutionResult);
        ctrfListener.executionStarted(running);

        exitHooks.get(0).run();

        verify(reportManager, never()).onTestInterrupted(any(), any());
    }

    @Test
    void exitHook_addsAnEntryForAClassWhoseTestsAllFinished() {
        TestIdentifier finished = childTest("finished()");
        when(finished.getParentId()).thenReturn(Optional.of(TEST_UNIQUE_ID));
        TestIdentifier theClass = container(TEST_UNIQUE_ID, "[engine:junit-jupiter]");
        when(testPlan.getRoots()).thenReturn(Set.of());
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);
        ctrfListener.executionStarted(theClass);
        ctrfListener.executionStarted(finished);
        ctrfListener.executionFinished(finished, testExecutionResult);

        exitHooks.get(0).run();

        // As in the class's @AfterAll
        var entry = ArgumentCaptor.forClass(TestDetails.class);
        verify(reportManager).onTestInterrupted(entry.capture(), any());
        assertEquals("teardownError", entry.getValue().displayName());
    }

    @Test
    void exitHook_leavesOutTheTestsOfAClassThatAlreadyFailed() {
        TestIdentifier engine = mock(TestIdentifier.class);
        TestIdentifier failedClass = mock(TestIdentifier.class);
        TestIdentifier testOfTheFailedClass = childTest("neverRunByJUnit()");
        when(failedClass.isContainer()).thenReturn(true);
        when(failedClass.getUniqueId()).thenReturn("[class:Failed]");
        when(failedClass.getDisplayName()).thenReturn("Failed");
        when(failedClass.getSource()).thenReturn(Optional.of(ClassSource.from(TEST_CLASS_NAME)));
        when(testPlan.getRoots()).thenReturn(Set.of(engine));
        when(testPlan.getChildren(engine)).thenReturn(Set.of(failedClass));
        when(testPlan.getChildren(failedClass)).thenReturn(Set.of(testOfTheFailedClass));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        ctrfListener.executionStarted(failedClass);
        ctrfListener.executionFinished(failedClass, testExecutionResult);

        exitHooks.get(0).run();

        // As in a run that finishes, JUnit reports only the class's initializationError
        verify(reportManager, never()).onTestSkipped(any(), any());
        verify(reportManager).finishTestRunOnExit(runOwner);
    }

    /**
     * A test class, or an engine when it has no parent.
     */
    private static TestIdentifier container(String uniqueId, String parentId) {
        TestIdentifier container = mock(TestIdentifier.class);
        when(container.isContainer()).thenReturn(true);
        when(container.getUniqueId()).thenReturn(uniqueId);
        when(container.getParentId()).thenReturn(Optional.ofNullable(parentId));
        when(container.getDisplayName()).thenReturn(uniqueId);
        when(container.getTags()).thenReturn(Set.of());
        when(container.getSource()).thenReturn(parentId == null ? Optional.empty() : Optional.of(ClassSource.from(TEST_CLASS_NAME)));
        return container;
    }

    private static TestIdentifier templateMethod(String displayName) {
        TestIdentifier template = mock(TestIdentifier.class);
        when(template.isContainer()).thenReturn(true);
        when(template.getUniqueId()).thenReturn(TEST_UNIQUE_ID + "/[test-template:" + displayName + "]");
        when(template.getDisplayName()).thenReturn(displayName);
        when(template.getTags()).thenReturn(Set.of());
        when(template.getSource()).thenReturn(Optional.of(MethodSource.from(TEST_CLASS_NAME, displayName)));
        return template;
    }

    private static TestIdentifier childTest(String displayName) {
        TestIdentifier childTest = mock(TestIdentifier.class);
        when(childTest.isTest()).thenReturn(true);
        when(childTest.getUniqueId()).thenReturn(TEST_UNIQUE_ID + "/[method:" + displayName + "]");
        when(childTest.getDisplayName()).thenReturn(displayName);
        when(childTest.getTags()).thenReturn(Set.of());
        when(childTest.getSource()).thenReturn(Optional.empty());
        return childTest;
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

    @Test
    void executionFinished_shouldRecordSuccessOfAClass() {
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getParentId()).thenReturn(Optional.of("[engine:junit-jupiter]"));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager).onContainerSuccess(eq(TEST_UNIQUE_ID), notNull());
    }

    @Test
    void executionFinished_shouldNotRecordSuccessOfAnEngine() {
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getParentId()).thenReturn(Optional.empty());
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager, never()).onContainerSuccess(anyString(), any());
    }

    @Test
    void executionFinished_shouldNotRecordSuccessOfAFailedContainer() {
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getParentId()).thenReturn(Optional.of("[engine:junit-jupiter]"));
        when(testIdentifier.getSource()).thenReturn(Optional.of(ClassSource.from(TEST_CLASS_NAME)));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(new RuntimeException("setup failed")));

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager, never()).onContainerSuccess(anyString(), any());
    }

    @Test
    void reportingEntryPublished_passesCapturedOutputOn() {
        ctrfListener.reportingEntryPublished(testIdentifier, ReportEntry.from(Map.of("stdout", "out", "stderr", "err")));

        verify(reportManager).onTestOutput(TEST_UNIQUE_ID, "out", "err");
    }

    @Test
    void reportingEntryPublished_ignoresOtherEntries() {
        ctrfListener.reportingEntryPublished(testIdentifier, ReportEntry.from("owner", "payments"));

        verify(reportManager, never()).onTestOutput(any(), any(), any());
    }

    @Test
    void reportingEntryPublished_afterItsRunFinished_isIgnored() {
        ctrfListener.testPlanExecutionFinished(testPlan);

        ctrfListener.reportingEntryPublished(testIdentifier, ReportEntry.from("stdout", "out"));

        verify(reportManager, never()).onTestOutput(any(), any(), any());
    }

    @Test
    void containerThatDoesNotFail_dropsItsOutput() {
        TestIdentifier aborted = container("[engine:junit-jupiter]/[class:Aborted]", "[engine:junit-jupiter]");
        TestIdentifier engine = container("[engine:junit-jupiter]", null);
        TestExecutionResult abortedResult = mock(TestExecutionResult.class);
        when(abortedResult.getStatus()).thenReturn(TestExecutionResult.Status.ABORTED);
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);

        ctrfListener.executionFinished(aborted, abortedResult);
        ctrfListener.executionFinished(engine, testExecutionResult);

        verify(reportManager).dropOutput("[engine:junit-jupiter]/[class:Aborted]");
        verify(reportManager).dropOutput("[engine:junit-jupiter]");
    }

    @Test
    void containerThatFails_keepsItsOutputForItsEntry() {
        TestIdentifier failed = container("[engine:junit-jupiter]/[class:Failed]", "[engine:junit-jupiter]");
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);

        ctrfListener.executionFinished(failed, testExecutionResult);

        verify(reportManager, never()).dropOutput(any());
    }
}

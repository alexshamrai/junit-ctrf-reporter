package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Results;
import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CtrfReportManagerTest {

    private static final String RUN_ID = "run-1";

    @Mock
    private ConfigReader configReader;
    @Mock
    private CtrfReportFileService ctrfReportFileService;
    @Mock
    private TestProcessor testProcessor;

    private CtrfReportManager reportManager;

    /**
     * Stands in for the report file: each update hands it to the merge function and keeps what comes back.
     */
    private CtrfJson reportOnDisk;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        var composer = new CtrfJsonComposer(configReader, new StartupDurationProcessor(), "Listener");
        reportManager = new CtrfReportManager(configReader, ctrfReportFileService, testProcessor, composer, RUN_ID);
        doAnswer(invocation -> {
            UnaryOperator<CtrfJson> merge = invocation.getArgument(0);
            reportOnDisk = merge.apply(reportOnDisk);
            return null;
        }).when(ctrfReportFileService).update(any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestStart should store test details in the map")
    void onTestStart_storesDetails() {
        reportManager.startTestRun("Listener");
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Test Details");
        var testResult = new Test();

        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestStart(details);
        reportManager.onTestSuccess("id-1");

        verify(testProcessor).createTest(anyString(), any(TestDetails.class), anyLong());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestSuccess should process a successful test result")
    void onTestSuccess_processesResult() {
        reportManager.startTestRun("Listener");
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Success Test");
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestStart(details);
        reportManager.onTestSuccess("id-1");

        verify(testProcessor).createTest(eq("Success Test"), any(TestDetails.class), anyLong());
        assertEquals(PASSED, testResult.getStatus());
        assertNull(testResult.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A result without a recorded start keeps the identity it was given, with start equal to stop")
    void onTestSuccess_withoutRecordedStart_usesGivenIdentity() {
        reportManager.startTestRun("Listener");
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(new Test());
        var given = new TestDetails(5_000L, Set.of("payments"), "com.example.PaymentTest", "id-1", "importantBusinessTest()");

        reportManager.onTestSuccess(given);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        var stopCaptor = ArgumentCaptor.forClass(Long.class);
        verify(testProcessor).createTest(eq("importantBusinessTest()"), detailsCaptor.capture(), stopCaptor.capture());
        assertEquals("com.example.PaymentTest", detailsCaptor.getValue().filePath());
        assertEquals(Set.of("payments"), detailsCaptor.getValue().tags());
        assertEquals(stopCaptor.getValue(), detailsCaptor.getValue().startTime());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A result with a recorded start keeps the recorded start time")
    void onTestSuccess_withRecordedStart_usesRecordedStartTime() {
        reportManager.startTestRun("Listener");
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(new Test());
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), "com.example.PaymentTest", "id-1", "importantBusinessTest()"));

        reportManager.onTestSuccess(new TestDetails(5_000L, Set.of(), "com.example.PaymentTest", "id-1", "importantBusinessTest()"));

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(testProcessor).createTest(eq("importantBusinessTest()"), detailsCaptor.capture(), anyLong());
        assertEquals(1_000L, detailsCaptor.getValue().startTime());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestFailure should process a failed test result")
    void onTestFailure_processesResult() {
        reportManager.startTestRun("Listener");
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Failure Test");
        var testResult = new Test();
        var cause = new RuntimeException("error");
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestStart(details);
        reportManager.onTestFailure("id-1", cause);

        assertEquals(FAILED, testResult.getStatus());
        verify(testProcessor).setFailureDetails(testResult, cause);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestSkipped should create a skipped test")
    void onTestSkipped_createsSkippedTest() {
        reportManager.startTestRun("Listener");
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Skipped Test");
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestSkipped(details, Optional.of("reason"));

        assertEquals(Test.TestStatus.SKIPPED, testResult.getStatus());
        assertEquals("reason", testResult.getMessage());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestAborted should record the test as skipped with the assumption message")
    void onTestAborted_recordsSkippedTestWithAssumptionMessage() {
        reportManager.startTestRun("Listener");
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Aborted Test");
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestStart(details);
        reportManager.onTestAborted("id-1", new RuntimeException("Assumption failed: Docker not available"));

        assertEquals(Test.TestStatus.SKIPPED, testResult.getStatus());
        assertEquals("aborted", testResult.getRawStatus());
        assertEquals("Assumption failed: Docker not available", testResult.getMessage());
        verify(testProcessor, never()).setFailureDetails(any(), any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("startTestRun reads no report: it is read only at the end, under the lock")
    void startTestRun_readsNoFile() {
        reportManager.startTestRun("Listener");
        reportManager.startTestRun("Listener");

        verifyNoInteractions(ctrfReportFileService);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun writes this execution's tests, with the run id at the root")
    void finishTestRun_writesTheReportWithTheRunId() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenReturn(Test.builder().testId("id-1").name("test").build());
        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "test"));
        reportManager.onTestSuccess("id-1");

        reportManager.finishTestRun();

        verify(ctrfReportFileService).update(any());
        assertEquals(RUN_ID, reportOnDisk.getRunId());
        assertEquals(List.of("id-1"), testIdsOnDisk());
        assertEquals(1, reportOnDisk.getResults().getSummary().getPassed());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun writes nothing when no run was started")
    void finishTestRun_withoutStart_writesNothing() {
        reportManager.finishTestRun();

        verifyNoInteractions(ctrfReportFileService);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("An unhealthy report of the same run keeps the merged report unhealthy")
    void finishTestRun_isUnhealthyWhenTheSameRunsReportIs() {
        reportOnDisk = reportOfRun(RUN_ID, false);
        reportManager.startTestRun("Listener");

        finishWithEnvironmentVariableHealthy();

        assertFalse(reportOnDisk.getResults().getEnvironment().isHealthy());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("The health of another run's report is not passed on")
    void finishTestRun_ignoresTheHealthOfAnotherRunsReport() {
        reportOnDisk = reportOfRun("run-0", false);
        reportManager.startTestRun("Listener");

        finishWithEnvironmentVariableHealthy();

        assertTrue(reportOnDisk.getResults().getEnvironment().isHealthy());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun should re-read ENV_HEALTHY and mark unhealthy if set to false")
    void finishTestRun_reReadsEnvHealthy() {
        reportManager.startTestRun("Listener");

        try (MockedStatic<EnvironmentHealthTracker> healthTracker = Mockito.mockStatic(EnvironmentHealthTracker.class)) {
            healthTracker.when(EnvironmentHealthTracker::isEnvironmentVariableUnhealthy).thenReturn(true);

            reportManager.finishTestRun();

            healthTracker.verify(EnvironmentHealthTracker::isEnvironmentVariableUnhealthy);
        }
        assertFalse(reportOnDisk.getResults().getEnvironment().isHealthy());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun should respect programmatic markEnvironmentUnhealthy call")
    void finishTestRun_respectsProgrammaticHealthChange() {
        reportManager.startTestRun("Listener");
        reportManager.markEnvironmentUnhealthyInternal();

        finishWithEnvironmentVariableHealthy();

        assertFalse(reportOnDisk.getResults().getEnvironment().isHealthy());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun reports the attempts of one test as one test with its earlier attempts")
    void finishTestRun_foldsAttemptsOfTheSameTest() {
        reportManager.startTestRun("Listener");
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("id-1").name("Rerun Test").start(1_000L).build(),
            Test.builder().testId("id-1").name("Rerun Test").start(2_000L).build());

        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestFailure("id-1", new RuntimeException("boom"));
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestSuccess("id-1");
        reportManager.finishTestRun();

        var test = testsOnDisk().get(0);
        assertEquals(1, testsOnDisk().size());
        assertEquals(PASSED, test.getStatus());
        assertEquals(1, test.getRetries());
        assertTrue(test.getFlaky());
        assertEquals(List.of(FAILED), test.getRetryAttempts().stream().map(RetryAttempt::getStatus).toList());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test in the same run's report gets this execution's attempt appended to its history")
    void finishTestRun_extendsTheHistoryInTheSameRunsReport() {
        reportOnDisk = reportOfRun(RUN_ID, true, Test.builder().testId("id-1").name("Rerun Test").status(FAILED).start(2_000L)
            .retries(1)
            .retryAttempts(List.of(RetryAttempt.builder().attempt(1).status(FAILED).start(1_000L).build()))
            .build());
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenReturn(Test.builder().testId("id-1").name("Rerun Test").start(3_000L).build());
        reportManager.startTestRun("Listener");

        reportManager.onTestStart(new TestDetails(3_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestSuccess("id-1");
        reportManager.finishTestRun();

        var test = testsOnDisk().get(0);
        assertEquals(1, testsOnDisk().size());
        assertEquals(PASSED, test.getStatus());
        assertEquals(2, test.getRetries());
        assertEquals(List.of(1_000L, 2_000L), test.getRetryAttempts().stream().map(RetryAttempt::getStart).toList());
        assertTrue(test.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Another run's report is replaced, not merged")
    void finishTestRun_replacesAnotherRunsReport() {
        reportOnDisk = reportOfRun("run-0", true, Test.builder().testId("old").name("old").status(FAILED).build());
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenReturn(Test.builder().testId("id-1").name("test").build());
        reportManager.startTestRun("Listener");

        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "test"));
        reportManager.onTestSuccess("id-1");
        reportManager.finishTestRun();

        assertEquals(List.of("id-1"), testIdsOnDisk());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A second test plan execution in the same JVM does not record the first one's attempts again")
    void finishTestRun_doesNotRepeatAttemptsOfAnEarlierExecution() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("id-1").name("First").build(),
            Test.builder().testId("id-2").name("Second").build());
        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "First"));
        reportManager.onTestSuccess("id-1");
        reportManager.finishTestRun();

        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "id-2", "Second"));
        reportManager.onTestSuccess("id-2");
        reportManager.finishTestRun();

        assertEquals(List.of("id-1", "id-2"), testIdsOnDisk());
        assertNull(testsOnDisk().get(0).getRetries());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A second execution in the same JVM, as Surefire's reruns are, merges into what the first one wrote")
    void secondExecutionInTheSameJvmKeepsTheFirstExecutionsResults() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("stable").name("stable()").build(),
            Test.builder().testId("flaky").name("flakyOnce()").build(),
            Test.builder().testId("flaky").name("flakyOnce()").build());
        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "stable", "stable()"));
        reportManager.onTestSuccess("stable");
        reportManager.onTestStart(new TestDetails(1_100L, Set.of(), null, "flaky", "flakyOnce()"));
        reportManager.onTestFailure("flaky", new AssertionError("fails on its first call"));
        reportManager.finishTestRun();

        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "flaky", "flakyOnce()"));
        reportManager.onTestSuccess("flaky");
        reportManager.finishTestRun();

        assertEquals(List.of("stable", "flaky"), testIdsOnDisk());
        var flaky = testsOnDisk().get(1);
        assertEquals(1, flaky.getRetries());
        assertTrue(flaky.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A container success without a start time uses its stop time")
    void onContainerSuccess_withoutStartUsesTheStopTime() {
        reportOnDisk = reportOfRun(RUN_ID, true, Test.builder().testId("class-1").name("initializationError").status(FAILED).build());
        reportManager.startTestRun("Listener");

        reportManager.onContainerSuccess("class-1", null);
        reportManager.finishTestRun();

        var recovered = testsOnDisk().get(0);
        assertEquals(PASSED, recovered.getStatus());
        assertTrue(recovered.getFlaky());
        assertEquals(recovered.getStart(), recovered.getStop());
        assertEquals(0L, recovered.getDuration());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("While one owner's run is active, another owner can neither start a run nor finish it")
    void anotherOwnerCanNeitherStartNorFinishTheActiveRun() {
        Object outer = new Object();
        Object nested = new Object();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenReturn(Test.builder().testId("id-1").name("test").build());

        assertTrue(reportManager.startTestRun(outer, "Listener"));
        assertFalse(reportManager.startTestRun(nested, "Listener"));
        reportManager.finishTestRun(nested);
        verifyNoInteractions(ctrfReportFileService);

        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "test"));
        reportManager.onTestSuccess("id-1");
        assertTrue(reportManager.startTestRun(outer, "Listener"));
        reportManager.finishTestRun(outer);

        verify(ctrfReportFileService).update(any());
        assertEquals(List.of("id-1"), testIdsOnDisk());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Once the active run has finished, another owner starts the next one")
    void anotherOwnerStartsTheNextRunOnceTheActiveOneFinished() {
        Object first = new Object();
        Object second = new Object();
        reportManager.startTestRun(first, "Listener");
        reportManager.finishTestRun(first);

        assertTrue(reportManager.startTestRun(second, "Listener"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Results recorded while no run is active are dropped")
    void resultsOutsideARunAreDropped() {
        Object owner = new Object();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenAnswer(call -> Test.builder().testId(call.<TestDetails>getArgument(1).uniqueId()).name("test").build());

        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "outside", "outside"));
        reportManager.onTestSuccess("outside");
        reportManager.onTestSkipped(new TestDetails(1_000L, Set.of(), null, "skipped", "skipped"), Optional.empty());
        reportManager.onContainerSuccess("class-1", 1_000L);
        reportManager.startTestRun(owner, "Listener");
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "inside", "inside"));
        reportManager.onTestSuccess("inside");
        reportManager.finishTestRun(owner);

        assertEquals(List.of("inside"), testIdsOnDisk());
        verify(testProcessor).createTest(anyString(), any(TestDetails.class), anyLong());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Of owners starting runs at the same time, exactly one gets the run")
    void concurrentStartsGiveTheRunToExactlyOneOwner() throws Exception {
        int owners = 8;
        var pool = Executors.newFixedThreadPool(owners);
        var start = new CountDownLatch(1);
        List<Future<Boolean>> started = new ArrayList<>();
        for (int i = 0; i < owners; i++) {
            Object owner = new Object();
            started.add(pool.submit(() -> {
                start.await();
                return reportManager.startTestRun(owner, "Listener");
            }));
        }

        start.countDown();
        int winners = 0;
        for (Future<Boolean> result : started) {
            if (result.get(10, TimeUnit.SECONDS)) {
                winners++;
            }
        }
        pool.shutdown();

        assertEquals(1, winners);
    }

    private void finishWithEnvironmentVariableHealthy() {
        try (MockedStatic<EnvironmentHealthTracker> healthTracker = Mockito.mockStatic(EnvironmentHealthTracker.class)) {
            healthTracker.when(EnvironmentHealthTracker::isEnvironmentVariableUnhealthy).thenReturn(false);
            reportManager.finishTestRun();
        }
    }

    private static CtrfJson reportOfRun(String runId, boolean healthy, Test... tests) {
        return CtrfJson.builder()
            .runId(runId)
            .results(Results.builder()
                .tests(List.of(tests))
                .summary(Summary.builder().start(500L).stop(600L).build())
                .environment(Environment.builder().healthy(healthy).build())
                .build())
            .build();
    }

    private List<Test> testsOnDisk() {
        return reportOnDisk.getResults().getTests();
    }

    private List<String> testIdsOnDisk() {
        return testsOnDisk().stream().map(Test::getTestId).toList();
    }
}

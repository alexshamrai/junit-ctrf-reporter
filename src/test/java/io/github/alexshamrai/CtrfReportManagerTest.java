package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import io.github.alexshamrai.util.SummaryUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CtrfReportManagerTest {

    @Mock
    private ConfigReader configReader;
    @Mock
    private CtrfReportFileService ctrfReportFileService;
    @Mock
    private TestProcessor testProcessor;
    @Mock
    private CtrfJsonComposer ctrfJsonComposer;

    private CtrfReportManager reportManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Create a new, clean instance for each test using the new constructor
        reportManager = new CtrfReportManager(
            configReader,
            ctrfReportFileService,
            testProcessor,
            ctrfJsonComposer
        );
    }

    @org.junit.jupiter.api.Test
    @DisplayName("startTestRun should initialize state only on the first call")
    void startTestRun_initializesOnce() {
        when(ctrfReportFileService.getExistingStartTime()).thenReturn(1000L);
        when(ctrfReportFileService.getExistingTests()).thenReturn(Collections.emptyList());
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);

        reportManager.startTestRun("Listener");
        reportManager.startTestRun("Listener"); // This second call should do nothing

        verify(ctrfReportFileService, times(1)).getExistingStartTime();
        verify(ctrfReportFileService, times(1)).getExistingTests();
        verify(ctrfReportFileService, times(1)).getExistingEnvironmentHealth();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestStart should store test details in the map")
    void onTestStart_storesDetails() {
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
    @DisplayName("finishTestRun should generate and write report")
    void finishTestRun_generatesReport() {
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(), anyLong())).thenReturn(testResult);
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);
        reportManager.onTestStart(new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "test"));
        reportManager.onTestSuccess("id-1");

        reportManager.startTestRun("Listener");

        try (MockedStatic<SummaryUtil> summaryUtil = Mockito.mockStatic(SummaryUtil.class)) {
            var mockReport = CtrfJson.builder().build();
            summaryUtil.when(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong())).thenReturn(new Summary());
            when(ctrfJsonComposer.generateCtrfJson(any(Summary.class), anyList(), eq(true))).thenReturn(mockReport);

            reportManager.finishTestRun();

            summaryUtil.verify(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong()));
            verify(ctrfJsonComposer).generateCtrfJson(any(Summary.class), anyList(), eq(true));
            verify(ctrfReportFileService).writeResultsToFile(mockReport);
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("startTestRun should preserve unhealthy state from previous run")
    void startTestRun_preservesUnhealthyState() {
        when(ctrfReportFileService.getExistingStartTime()).thenReturn(1000L);
        when(ctrfReportFileService.getExistingTests()).thenReturn(Collections.emptyList());
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(false);

        reportManager.startTestRun("Listener");

        try (MockedStatic<SummaryUtil> summaryUtil = Mockito.mockStatic(SummaryUtil.class)) {
            var mockReport = CtrfJson.builder().build();
            summaryUtil.when(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong())).thenReturn(new Summary());
            when(ctrfJsonComposer.generateCtrfJson(any(Summary.class), anyList(), eq(false))).thenReturn(mockReport);

            reportManager.finishTestRun();

            verify(ctrfJsonComposer).generateCtrfJson(any(Summary.class), anyList(), eq(false));
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("startTestRun should keep healthy state when previous run was healthy")
    void startTestRun_keepsHealthyState() {
        when(ctrfReportFileService.getExistingStartTime()).thenReturn(1000L);
        when(ctrfReportFileService.getExistingTests()).thenReturn(Collections.emptyList());
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);

        reportManager.startTestRun("Listener");

        try (MockedStatic<SummaryUtil> summaryUtil = Mockito.mockStatic(SummaryUtil.class)) {
            var mockReport = CtrfJson.builder().build();
            summaryUtil.when(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong())).thenReturn(new Summary());
            when(ctrfJsonComposer.generateCtrfJson(any(Summary.class), anyList(), eq(true))).thenReturn(mockReport);

            reportManager.finishTestRun();

            verify(ctrfJsonComposer).generateCtrfJson(any(Summary.class), anyList(), eq(true));
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun should re-read ENV_HEALTHY and mark unhealthy if set to false")
    void finishTestRun_reReadsEnvHealthy() {
        when(ctrfReportFileService.getExistingStartTime()).thenReturn(1000L);
        when(ctrfReportFileService.getExistingTests()).thenReturn(Collections.emptyList());
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);

        // Start with healthy state
        reportManager.startTestRun("Listener");

        try (MockedStatic<SummaryUtil> summaryUtil = Mockito.mockStatic(SummaryUtil.class);
             MockedStatic<EnvironmentHealthTracker> healthTracker = Mockito.mockStatic(EnvironmentHealthTracker.class)) {

            var mockReport = CtrfJson.builder().build();
            summaryUtil.when(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong())).thenReturn(new Summary());

            // Mock ENV_HEALTHY being set to false during test execution
            healthTracker.when(EnvironmentHealthTracker::isEnvironmentVariableUnhealthy).thenReturn(true);

            when(ctrfJsonComposer.generateCtrfJson(any(Summary.class), anyList(), eq(false))).thenReturn(mockReport);

            reportManager.finishTestRun();

            // Verify that ENV_HEALTHY was re-read (via isEnvironmentVariableUnhealthy call)
            healthTracker.verify(EnvironmentHealthTracker::isEnvironmentVariableUnhealthy);
            // Verify report was generated with unhealthy state
            verify(ctrfJsonComposer).generateCtrfJson(any(Summary.class), anyList(), eq(false));
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun should respect programmatic markEnvironmentUnhealthy call")
    void finishTestRun_respectsProgrammaticHealthChange() {
        when(ctrfReportFileService.getExistingStartTime()).thenReturn(1000L);
        when(ctrfReportFileService.getExistingTests()).thenReturn(Collections.emptyList());
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);

        // Start with healthy state
        reportManager.startTestRun("Listener");

        // Programmatically mark unhealthy during test execution
        reportManager.markEnvironmentUnhealthyInternal();

        try (MockedStatic<SummaryUtil> summaryUtil = Mockito.mockStatic(SummaryUtil.class);
             MockedStatic<EnvironmentHealthTracker> healthTracker = Mockito.mockStatic(EnvironmentHealthTracker.class)) {

            var mockReport = CtrfJson.builder().build();
            summaryUtil.when(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong())).thenReturn(new Summary());

            // ENV_HEALTHY is not set (remains healthy from environment perspective)
            healthTracker.when(EnvironmentHealthTracker::isEnvironmentVariableUnhealthy).thenReturn(false);

            when(ctrfJsonComposer.generateCtrfJson(any(Summary.class), anyList(), eq(false))).thenReturn(mockReport);

            reportManager.finishTestRun();

            // Verify report was generated with unhealthy state from programmatic call
            verify(ctrfJsonComposer).generateCtrfJson(any(Summary.class), anyList(), eq(false));
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun reports the attempts of one test as one test with its earlier attempts")
    void finishTestRun_foldsAttemptsOfTheSameTest() {
        startRunWithExistingTests(List.of());
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("id-1").name("Rerun Test").start(1_000L).build(),
            Test.builder().testId("id-1").name("Rerun Test").start(2_000L).build());

        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestFailure("id-1", new RuntimeException("boom"));
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestSuccess("id-1");

        var tests = finishAndCaptureReportedTests();
        assertEquals(1, tests.size());
        var test = tests.get(0);
        assertEquals(PASSED, test.getStatus());
        assertEquals(1, test.getRetries());
        assertTrue(test.getFlaky());
        assertEquals(List.of(FAILED), test.getRetryAttempts().stream().map(RetryAttempt::getStatus).toList());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test from the existing report is unfolded, so a new attempt extends its history")
    void startTestRun_unfoldsTheExistingReportSoANewAttemptExtendsIt() {
        var fromEarlierRounds = Test.builder().testId("id-1").name("Rerun Test").status(FAILED).start(2_000L)
            .retries(1)
            .retryAttempts(List.of(RetryAttempt.builder().attempt(1).status(FAILED).start(1_000L).build()))
            .build();
        startRunWithExistingTests(List.of(fromEarlierRounds));
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenReturn(Test.builder().testId("id-1").name("Rerun Test").start(3_000L).build());

        reportManager.onTestStart(new TestDetails(3_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestSuccess("id-1");

        var tests = finishAndCaptureReportedTests();
        assertEquals(1, tests.size());
        var test = tests.get(0);
        assertEquals(PASSED, test.getStatus());
        assertEquals(2, test.getRetries());
        assertEquals(List.of(1_000L, 2_000L), test.getRetryAttempts().stream().map(RetryAttempt::getStart).toList());
        assertTrue(test.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A second test plan execution in the same JVM does not report the first one's attempts again")
    void finishTestRun_doesNotRepeatAttemptsOfAnEarlierExecution() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("id-1").name("First").build(),
            Test.builder().testId("id-2").name("Second").build());
        startRunWithExistingTests(List.of());
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "First"));
        reportManager.onTestSuccess("id-1");
        finishAndCaptureReportedTests();

        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "id-2", "Second"));
        reportManager.onTestSuccess("id-2");

        var tests = finishAndCaptureReportedTests();
        assertEquals(List.of("id-2"), tests.stream().map(Test::getTestId).toList());
    }

    private void startRunWithExistingTests(List<Test> existingTests) {
        when(ctrfReportFileService.getExistingTests()).thenReturn(existingTests);
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);
        reportManager.startTestRun("Listener");
    }

    @SuppressWarnings("unchecked")
    private List<Test> finishAndCaptureReportedTests() {
        when(ctrfJsonComposer.generateCtrfJson(any(Summary.class), anyList(), anyBoolean()))
            .thenReturn(CtrfJson.builder().build());
        reportManager.finishTestRun();

        ArgumentCaptor<List<Test>> testsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ctrfJsonComposer, atLeastOnce()).generateCtrfJson(any(Summary.class), testsCaptor.capture(), anyBoolean());
        return testsCaptor.getValue();
    }
}

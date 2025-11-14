package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import io.github.alexshamrai.util.SummaryUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CtrfReportManagerTest {

    @Mock
    private CtrfReportFileService ctrfReportFileService;
    @Mock
    private TestProcessor testProcessor;
    @Mock
    private SuiteExecutionErrorHandler suiteExecutionErrorHandler;
    @Mock
    private CtrfJsonComposer ctrfJsonComposer;
    @Mock
    private ExtensionContext extensionContext;

    private CtrfReportManager reportManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Create a new, clean instance for each test using the new constructor
        reportManager = new CtrfReportManager(
            ctrfReportFileService,
            testProcessor,
            suiteExecutionErrorHandler,
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
        var details = TestDetails.builder().uniqueId("id-1").displayName("Test Details").build();
        var testResult = new Test();
        
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);
        
        reportManager.onTestStart(details);
        reportManager.onTestSuccess("id-1");

        verify(testProcessor).createTest(anyString(), any(TestDetails.class), anyLong());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestSuccess should process a successful test result")
    void onTestSuccess_processesResult() {
        var details = TestDetails.builder().uniqueId("id-1").displayName("Success Test").build();
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestStart(details);
        reportManager.onTestSuccess("id-1");

        verify(testProcessor).createTest(eq("Success Test"), any(TestDetails.class), anyLong());
        assertEquals(PASSED, testResult.getStatus());
        assertNull(testResult.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestFailure should process a failed test result")
    void onTestFailure_processesResult() {
        var details = TestDetails.builder().uniqueId("id-1").displayName("Failure Test").build();
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
        var details = TestDetails.builder().uniqueId("id-1").displayName("Skipped Test").build();
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestSkipped(details, Optional.of("reason"));

        assertEquals(Test.TestStatus.SKIPPED, testResult.getStatus());
        assertEquals("reason", testResult.getMessage());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Rerunning a test should set the retry count and flaky flag")
    void processTestResult_setsRetryCount() {
        // Mock the first run
        var firstRun = Test.builder().name("Rerun Test").status(FAILED).build();
        when(testProcessor.createTest(eq("Rerun Test"), any(TestDetails.class), anyLong())).thenReturn(firstRun);
        var details1 = TestDetails.builder().uniqueId("id-1").displayName("Rerun Test").build();
        reportManager.onTestStart(details1);
        reportManager.onTestFailure("id-1", new RuntimeException());

        // Mock the second run
        var secondRun = new Test();
        secondRun.setName("Rerun Test");
        when(testProcessor.createTest(eq("Rerun Test"), any(TestDetails.class), anyLong())).thenReturn(secondRun);
        var details2 = TestDetails.builder().uniqueId("id-2").displayName("Rerun Test").build();
        reportManager.onTestStart(details2);
        reportManager.onTestSuccess("id-2");

        assertEquals(1, secondRun.getRetries());
        assertTrue(secondRun.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun should generate and write report")
    void finishTestRun_generatesReport() {
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(), anyLong())).thenReturn(testResult);
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);
        reportManager.onTestStart(TestDetails.builder().uniqueId("id-1").displayName("test").build());
        reportManager.onTestSuccess("id-1");

        reportManager.startTestRun("Listener");

        try (MockedStatic<SummaryUtil> summaryUtil = Mockito.mockStatic(SummaryUtil.class)) {
            var mockReport = CtrfJson.builder().build();
            summaryUtil.when(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong())).thenReturn(new Summary());
            when(ctrfJsonComposer.generateCtrfJson(any(Summary.class), anyList(), eq(true))).thenReturn(mockReport);

            reportManager.finishTestRun(Optional.empty());

            summaryUtil.verify(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong()));
            verify(ctrfJsonComposer).generateCtrfJson(any(Summary.class), anyList(), eq(true));
            verify(ctrfReportFileService).writeResultsToFile(mockReport);
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun should handle initialization error if no tests were run")
    void finishTestRun_handlesInitializationError() {
        when(ctrfReportFileService.getExistingTests()).thenReturn(Collections.emptyList());
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);

        reportManager.startTestRun("Listener");
        reportManager.finishTestRun(Optional.of(extensionContext));

        verify(suiteExecutionErrorHandler).handleInitializationError(eq(extensionContext), anyLong(), anyLong());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun should handle execution error on suite-level exception")
    void finishTestRun_handlesExecutionError() {
        var testResult = Test.builder().stop(12345L).build();
        when(testProcessor.createTest(anyString(), any(), anyLong())).thenReturn(testResult);
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);
        reportManager.onTestStart(TestDetails.builder().uniqueId("id-1").displayName("test").build());
        reportManager.onTestSuccess("id-1");

        when(extensionContext.getExecutionException()).thenReturn(Optional.of(new RuntimeException()));

        reportManager.startTestRun("Listener");
        reportManager.finishTestRun(Optional.of(extensionContext));

        verify(suiteExecutionErrorHandler).handleExecutionError(eq(extensionContext), eq(12345L), anyLong());
        verify(suiteExecutionErrorHandler, never()).handleInitializationError(any(), anyLong(), anyLong());
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

            reportManager.finishTestRun(Optional.empty());

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

            reportManager.finishTestRun(Optional.empty());

            verify(ctrfJsonComposer).generateCtrfJson(any(Summary.class), anyList(), eq(true));
        }
    }
}
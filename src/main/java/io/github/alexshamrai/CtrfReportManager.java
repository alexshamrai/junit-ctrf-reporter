package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;

/**
 * Coordinates CTRF test report generation and test execution tracking.
 * <p>
 * This class follows the singleton pattern and serves as the main coordinator
 * for the CTRF reporting library. It delegates specific responsibilities to
 * specialized classes:
 * <ul>
 *   <li>{@link TestStateTracker} - manages test state and collections</li>
 *   <li>{@link FlakyTestDetector} - detects flaky tests based on retry patterns</li>
 *   <li>{@link TestRerunHandler} - handles test rerun scenarios</li>
 *   <li>{@link ReportOrchestrator} - generates and writes final reports</li>
 * </ul>
 *
 */
public final class CtrfReportManager {

    private static final CtrfReportManager INSTANCE = new CtrfReportManager();

    private final TestStateTracker stateTracker;
    private final TestRerunHandler rerunHandler;
    private final ReportOrchestrator reportOrchestrator;
    private final TestProcessor testProcessor;
    private final SuiteExecutionErrorHandler suiteExecutionErrorHandler;

    private long testRunStartTime;
    private final AtomicBoolean isTestRunStarted = new AtomicBoolean(false);
    private String generator;
    private final AtomicBoolean isEnvironmentHealthy = new AtomicBoolean(true);

    private CtrfReportManager() {
        var configReader = new ConfigReader();
        var fileService = new CtrfReportFileService(configReader);

        this.stateTracker = new TestStateTracker();
        this.rerunHandler = new TestRerunHandler(fileService);
        this.reportOrchestrator = new ReportOrchestrator(configReader, fileService, null);
        this.testProcessor = new TestProcessor(configReader);
        this.suiteExecutionErrorHandler = new SuiteExecutionErrorHandler(testProcessor);

        if (EnvironmentHealthTracker.isEnvironmentVariableUnhealthy()) {
            isEnvironmentHealthy.set(false);
        }
    }

    /**
     * Package-private constructor for testing purposes, allowing dependency injection.
     */
    CtrfReportManager(ConfigReader configReader,
                      CtrfReportFileService ctrfReportFileService,
                      TestProcessor testProcessor,
                      SuiteExecutionErrorHandler suiteExecutionErrorHandler,
                      CtrfJsonComposer ctrfJsonComposer) {
        this.stateTracker = new TestStateTracker();
        this.rerunHandler = new TestRerunHandler(ctrfReportFileService);
        this.reportOrchestrator = new ReportOrchestrator(configReader, ctrfReportFileService, ctrfJsonComposer);
        this.testProcessor = testProcessor;
        this.suiteExecutionErrorHandler = suiteExecutionErrorHandler;
    }

    public static CtrfReportManager getInstance() {
        return INSTANCE;
    }

    void markEnvironmentUnhealthyInternal() {
        isEnvironmentHealthy.set(false);
    }

    boolean isEnvironmentHealthyInternal() {
        return isEnvironmentHealthy.get();
    }

    void resetEnvironmentHealthForTesting() {
        isEnvironmentHealthy.set(true);
    }

    public void onTestStart(TestDetails testDetails) {
        stateTracker.putTestDetails(testDetails.uniqueId(), testDetails);
    }

    public void onTestSkipped(TestDetails testDetails, Optional<String> reason) {
        var test = testProcessor.createTest(testDetails.displayName(), testDetails, testDetails.startTime());
        test.setStatus(SKIPPED);
        reason.ifPresent(test::setMessage);
        stateTracker.addTest(test);
    }

    private void processTestResult(String uniqueId, Optional<Throwable> cause, Test.TestStatus status) {
        long stopTime = System.currentTimeMillis();
        TestDetails details = stateTracker.removeTestDetails(uniqueId);
        if (details == null) {
            details = new TestDetails(stopTime, Set.of(), null, uniqueId, "Unknown Test");
        }

        var newTest = testProcessor.createTest(details.displayName(), details, stopTime);
        newTest.setStatus(status);
        cause.ifPresent(c -> testProcessor.setFailureDetails(newTest, c));

        FlakyTestDetector.detectAndMarkFlaky(newTest, stateTracker.getAllTests());
        stateTracker.addTest(newTest);
    }

    public void onTestSuccess(String uniqueId) {
        processTestResult(uniqueId, Optional.empty(), PASSED);
    }

    public void onTestFailure(String uniqueId, Throwable cause) {
        processTestResult(uniqueId, Optional.ofNullable(cause), FAILED);
    }

    public void onTestAborted(String uniqueId, Throwable cause) {
        processTestResult(uniqueId, Optional.ofNullable(cause), FAILED);
    }

    public void startTestRun(String generator) {
        if (isTestRunStarted.compareAndSet(false, true)) {
            this.generator = generator;
            this.testRunStartTime = rerunHandler.loadOrCreateStartTime();

            // Load existing tests from previous run (if any)
            stateTracker.addAllTests(rerunHandler.getFileService().getExistingTests());

            // Preserve environment health from previous run
            if (rerunHandler.wasPreviousRunUnhealthy()) {
                isEnvironmentHealthy.set(false);
            }
        }
    }

    public void finishTestRun(Optional<ExtensionContext> contextOpt) {
        if (!isTestRunStarted.compareAndSet(true, false)) {
            return;
        }

        long testRunStopTime = System.currentTimeMillis();

        captureUncaughtInitializationError(contextOpt, stateTracker.getAllTests(), testRunStopTime);
        refreshEnvironmentHealthFromEnvVar();

        reportOrchestrator.generateAndWriteReport(
            stateTracker.getAllTests(),
            testRunStartTime,
            testRunStopTime,
            isEnvironmentHealthy.get(),
            generator
        );

        stateTracker.clear();
    }

    private void refreshEnvironmentHealthFromEnvVar() {
        if (EnvironmentHealthTracker.isEnvironmentVariableUnhealthy()) {
            isEnvironmentHealthy.set(false);
        }
    }

    private void captureUncaughtInitializationError(Optional<ExtensionContext> contextOpt,
                                                    List<Test> tests,
                                                    long testRunStopTime) {
        if (contextOpt.flatMap(ExtensionContext::getExecutionException).isEmpty()) {
            return;
        }

        boolean alreadyCaptured = tests.stream()
            .anyMatch(t -> "initializationError".equals(t.getName()));
        if (alreadyCaptured) {
            return;
        }

        ExtensionContext context = contextOpt.get();
        long startTime = tests.isEmpty() ? testRunStartTime : tests.get(tests.size() - 1).getStop();
        suiteExecutionErrorHandler.handleInitializationError(context, startTime, testRunStopTime)
            .ifPresent(stateTracker::addTest);
    }
}

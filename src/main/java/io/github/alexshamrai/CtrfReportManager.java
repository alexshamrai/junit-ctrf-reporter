package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;
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
 * @since 0.1.0
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
        testDetails.setStartTime(System.currentTimeMillis());
        stateTracker.putTestDetails(testDetails.getUniqueId(), testDetails);
    }

    public void onTestSkipped(TestDetails testDetails, Optional<String> reason) {
        long time = System.currentTimeMillis();
        testDetails.setStartTime(time);

        var test = testProcessor.createTest(testDetails.getDisplayName(), testDetails, time);
        test.setStatus(SKIPPED);
        reason.ifPresent(test::setMessage);
        stateTracker.addTest(test);
    }

    private void processTestResult(String uniqueId, Optional<Throwable> cause, Test.TestStatus status) {
        long stopTime = System.currentTimeMillis();
        TestDetails details = stateTracker.removeTestDetails(uniqueId);
        if (details == null) {
            details = TestDetails.builder().displayName("Unknown Test").startTime(stopTime).build();
        }

        var newTest = testProcessor.createTest(details.getDisplayName(), details, stopTime);
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
        var tests = stateTracker.getAllTests();

        // Handle suite-level errors
        if (tests.isEmpty()) {
            contextOpt.ifPresentOrElse(
                context -> suiteExecutionErrorHandler.handleInitializationError(context, testRunStartTime, testRunStopTime)
                    .ifPresent(stateTracker::addTest),
                () -> { /* Listener has no context for this, can add a synthetic test if needed */ }
            );
        } else if (contextOpt.flatMap(ExtensionContext::getExecutionException).isPresent()) {
            ExtensionContext context = contextOpt.get();
            long lastTestStopTime = tests.get(tests.size() - 1).getStop();
            suiteExecutionErrorHandler.handleExecutionError(context, lastTestStopTime, testRunStopTime)
                .ifPresent(stateTracker::addTest);
        }

        // Re-read ENV_HEALTHY at the end of test run to catch any changes made during execution
        if (EnvironmentHealthTracker.isEnvironmentVariableUnhealthy()) {
            isEnvironmentHealthy.set(false);
        }

        // Generate and write report
        reportOrchestrator.generateAndWriteReport(
            stateTracker.getAllTests(),
            testRunStartTime,
            testRunStopTime,
            isEnvironmentHealthy.get(),
            generator
        );

        stateTracker.clear();
    }
}

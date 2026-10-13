package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
    private static final String ABORTED = "aborted";

    private final TestStateTracker stateTracker;
    private final TestRerunHandler rerunHandler;
    private final ReportOrchestrator reportOrchestrator;
    private final TestProcessor testProcessor;

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
                      CtrfJsonComposer ctrfJsonComposer) {
        this.stateTracker = new TestStateTracker();
        this.rerunHandler = new TestRerunHandler(ctrfReportFileService);
        this.reportOrchestrator = new ReportOrchestrator(configReader, ctrfReportFileService, ctrfJsonComposer);
        this.testProcessor = testProcessor;
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

    /**
     * Records a finished test. The start recorded by {@link #onTestStart} wins; when there is none, the test
     * keeps the identity it was reported with and its start time is the stop time.
     */
    private void processTestResult(TestDetails reported, Test.TestStatus status, Consumer<Test> outcomeDetails) {
        long stopTime = System.currentTimeMillis();
        TestDetails details = stateTracker.removeTestDetails(reported.uniqueId());
        if (details == null) {
            details = new TestDetails(stopTime, reported.tags(), reported.filePath(), reported.uniqueId(), reported.displayName());
        }

        var newTest = testProcessor.createTest(details.displayName(), details, stopTime);
        newTest.setStatus(status);
        outcomeDetails.accept(newTest);

        FlakyTestDetector.detectAndMarkFlaky(newTest, stateTracker.getAllTests());
        stateTracker.addTest(newTest);
    }

    public void onTestSuccess(String uniqueId) {
        onTestSuccess(unknownTest(uniqueId));
    }

    /**
     * Records a passed test.
     *
     * @param testDetails the test's identity, used when no start was recorded for it
     */
    public void onTestSuccess(TestDetails testDetails) {
        processTestResult(testDetails, PASSED, test -> { });
    }

    public void onTestFailure(String uniqueId, Throwable cause) {
        onTestFailure(unknownTest(uniqueId), cause);
    }

    /**
     * Records a failed test.
     *
     * @param testDetails the test's identity, used when no start was recorded for it
     * @param cause       the failure, or {@code null}
     */
    public void onTestFailure(TestDetails testDetails, Throwable cause) {
        processTestResult(testDetails, FAILED, test -> {
            if (cause != null) {
                testProcessor.setFailureDetails(test, cause);
            }
        });
    }

    public void onTestAborted(String uniqueId, Throwable cause) {
        onTestAborted(unknownTest(uniqueId), cause);
    }

    /**
     * Records a test that JUnit aborted, typically because an assumption failed. Like Gradle and Surefire,
     * the report counts it as skipped; {@code rawStatus} keeps JUnit's "aborted" and {@code message} the reason.
     *
     * @param testDetails the test's identity, used when no start was recorded for it
     * @param cause       the exception that aborted the test, or {@code null}
     */
    public void onTestAborted(TestDetails testDetails, Throwable cause) {
        processTestResult(testDetails, SKIPPED, test -> {
            test.setRawStatus(ABORTED);
            if (cause != null) {
                test.setMessage(cause.getMessage());
            }
        });
    }

    /**
     * The identity of a test known only by its unique ID, for callers that recorded its start beforehand.
     */
    private static TestDetails unknownTest(String uniqueId) {
        return new TestDetails(0L, Set.of(), null, uniqueId, "Unknown Test");
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

    public void finishTestRun() {
        if (!isTestRunStarted.compareAndSet(true, false)) {
            return;
        }

        long testRunStopTime = System.currentTimeMillis();

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
}

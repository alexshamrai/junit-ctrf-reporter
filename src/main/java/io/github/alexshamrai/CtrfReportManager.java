package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import io.github.alexshamrai.util.SummaryUtil;

import java.util.List;
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
 *   <li>{@link TestStateTracker} - collects this execution's attempts and container successes</li>
 *   <li>{@link RunIdResolver} - decides which JVMs share one report</li>
 *   <li>{@link ReportMerger} - merges this execution into the report written by the same run</li>
 *   <li>{@link CtrfReportFileService} - reads and writes the report under a lock</li>
 * </ul>
 */
public final class CtrfReportManager {

    private static final CtrfReportManager INSTANCE = new CtrfReportManager();
    private static final String ABORTED = "aborted";

    private final ConfigReader configReader;
    private final TestStateTracker stateTracker;
    private final CtrfReportFileService fileService;
    private final TestProcessor testProcessor;
    private final CtrfJsonComposer ctrfJsonComposer;
    private final String runId;

    private long testRunStartTime;
    private final AtomicBoolean isTestRunStarted = new AtomicBoolean(false);
    private String generator;
    private final AtomicBoolean isEnvironmentHealthy = new AtomicBoolean(true);

    private CtrfReportManager() {
        this.configReader = new ConfigReader();
        this.stateTracker = new TestStateTracker();
        this.fileService = new CtrfReportFileService(configReader);
        this.testProcessor = new TestProcessor(configReader);
        this.ctrfJsonComposer = null;
        this.runId = RunIdResolver.resolve(configReader);

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
                      CtrfJsonComposer ctrfJsonComposer,
                      String runId) {
        this.configReader = configReader;
        this.stateTracker = new TestStateTracker();
        this.fileService = ctrfReportFileService;
        this.testProcessor = testProcessor;
        this.ctrfJsonComposer = ctrfJsonComposer;
        this.runId = runId;
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
        stateTracker.addAttempt(test);
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

        stateTracker.addAttempt(newTest);
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
     * Records a container that finished without failing, such as a test class whose {@code @BeforeAll} and
     * {@code @AfterAll} methods passed. If the same container failed in an earlier retry round, its failure is
     * then reported as recovered.
     *
     * @param uniqueId  the container's JUnit unique ID
     * @param startTime when the container started, or {@code null} if unknown; the stop time is used then
     */
    public void onContainerSuccess(String uniqueId, Long startTime) {
        long stopTime = System.currentTimeMillis();
        stateTracker.addContainerSuccess(new ContainerSuccess(uniqueId, startTime != null ? startTime : stopTime, stopTime));
    }

    /**
     * The identity of a test known only by its unique ID, for callers that recorded its start beforehand.
     */
    private static TestDetails unknownTest(String uniqueId) {
        return new TestDetails(0L, Set.of(), null, uniqueId, "Unknown Test");
    }

    /**
     * Starts recording an execution. Reads no file: the report is read only at the end, under the lock.
     *
     * @param generator the entry point that reports, written as {@code generatedBy}
     */
    public void startTestRun(String generator) {
        if (isTestRunStarted.compareAndSet(false, true)) {
            this.generator = generator;
            this.testRunStartTime = System.currentTimeMillis();
        }
    }

    /**
     * Writes this execution's results. The report on disk is read while it is locked: if it was written by the
     * same run, for example by a parallel fork or an earlier retry round, this execution is merged into it;
     * otherwise it is replaced.
     */
    public void finishTestRun() {
        if (!isTestRunStarted.compareAndSet(true, false)) {
            return;
        }

        long testRunStopTime = System.currentTimeMillis();
        refreshEnvironmentHealthFromEnvVar();

        List<Test> attempts = stateTracker.getAllAttempts();
        List<ContainerSuccess> successes = stateTracker.getAllContainerSuccesses();
        long startTime = testRunStartTime;
        boolean healthy = isEnvironmentHealthy.get();
        CtrfJsonComposer composer = composer();

        fileService.update(existing -> {
            var run = ReportMerger.merge(existing, runId, attempts, successes, startTime, testRunStopTime, healthy);
            var summary = SummaryUtil.createSummary(run.tests(), run.start(), run.stop());
            return composer.generateCtrfJson(summary, run.tests(), run.healthy(), runId);
        });

        stateTracker.clear();
    }

    private CtrfJsonComposer composer() {
        return ctrfJsonComposer != null
            ? ctrfJsonComposer
            : new CtrfJsonComposer(configReader, new StartupDurationProcessor(), generator);
    }

    private void refreshEnvironmentHealthFromEnvVar() {
        if (EnvironmentHealthTracker.isEnvironmentVariableUnhealthy()) {
            isEnvironmentHealthy.set(false);
        }
    }
}

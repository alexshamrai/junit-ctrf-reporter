package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import io.github.alexshamrai.util.SummaryUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;

/**
 * Coordinates CTRF test report generation and test execution tracking.
 * <p>
 * This class is the main coordinator of the CTRF reporting library. {@link #getInstance()} is the JVM's default
 * instance; its collaborators are passed to its constructor. It delegates specific responsibilities to
 * specialized classes:
 * <ul>
 *   <li>{@link TestRun} - one run's owner, start time, and the attempts and container successes it recorded</li>
 *   <li>{@link RunIdResolver} - decides which JVMs share one report</li>
 *   <li>{@link ReportMerger} - merges this execution into the report written by the same run</li>
 *   <li>{@link CtrfReportFileService} - reads and writes the report under a lock</li>
 * </ul>
 * <p>
 * At most one run is active, and only its owner can end it. A JUnit run started from inside a test, such as a test
 * that runs JUnit itself, is refused, so it neither ends the run around it nor adds its tests (CB-2).
 */
public final class CtrfReportManager {

    private static final String ABORTED = "aborted";

    /**
     * The owner of the runs started through {@link #startTestRun(String)}, which names none.
     */
    private static final Object UNNAMED_OWNER = new Object();

    private static final CtrfReportManager INSTANCE = createDefault();

    private final TestProcessor testProcessor;
    private final CtrfReportFileService fileService;
    private final Function<String, CtrfJsonComposer> composerFactory;
    private final String runId;

    private final AtomicReference<TestRun> activeRun = new AtomicReference<>();
    private final AtomicBoolean isEnvironmentHealthy;

    /**
     * Creates a manager from its collaborators. The default instance and the tests both use this constructor.
     *
     * @param testProcessor   turns JUnit results into CTRF tests
     * @param fileService     reads and writes the report
     * @param composerFactory makes the composer of a run, given the run's generator ({@code generatedBy})
     * @param runId           this JVM's run id
     * @param healthy         whether the environment is healthy to begin with
     */
    CtrfReportManager(TestProcessor testProcessor,
                      CtrfReportFileService fileService,
                      Function<String, CtrfJsonComposer> composerFactory,
                      String runId,
                      boolean healthy) {
        this.testProcessor = testProcessor;
        this.fileService = fileService;
        this.composerFactory = composerFactory;
        this.runId = runId;
        this.isEnvironmentHealthy = new AtomicBoolean(healthy);
    }

    /**
     * Builds the JVM's default manager from this JVM's configuration.
     */
    private static CtrfReportManager createDefault() {
        var config = new ConfigReader();
        return new CtrfReportManager(
            new TestProcessor(config),
            new CtrfReportFileService(config),
            generator -> new CtrfJsonComposer(config, new StartupDurationProcessor(), generator),
            RunIdResolver.resolve(config),
            !EnvironmentHealthTracker.isEnvironmentVariableUnhealthy());
    }

    /**
     * Returns the JVM's default manager. The entry points' no-argument constructors, which JUnit calls, and
     * {@link EnvironmentHealthTracker} use it, so that they all report into the same run.
     *
     * @return the default manager
     */
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
        TestRun run = activeRun.get();
        if (run != null) {
            run.state().putTestDetails(testDetails.uniqueId(), testDetails);
        }
    }

    public void onTestSkipped(TestDetails testDetails, Optional<String> reason) {
        TestRun run = activeRun.get();
        if (run == null) {
            return;
        }
        var test = testProcessor.createTest(testDetails.displayName(), testDetails, testDetails.startTime());
        test.setStatus(SKIPPED);
        reason.ifPresent(test::setMessage);
        run.state().addAttempt(test);
    }

    /**
     * Records a finished test in the active run. The start recorded by {@link #onTestStart} wins; when there is none,
     * the test keeps the identity it was reported with and its start time is the stop time. Does nothing when no run
     * is active.
     */
    private void processTestResult(TestDetails reported, Test.TestStatus status, Consumer<Test> outcomeDetails) {
        TestRun run = activeRun.get();
        if (run == null) {
            return;
        }
        long stopTime = System.currentTimeMillis();
        TestDetails details = run.state().removeTestDetails(reported.uniqueId());
        if (details == null) {
            details = new TestDetails(stopTime, reported.tags(), reported.filePath(), reported.uniqueId(), reported.displayName());
        }

        var newTest = testProcessor.createTest(details.displayName(), details, stopTime);
        newTest.setStatus(status);
        outcomeDetails.accept(newTest);

        run.state().addAttempt(newTest);
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
     * then reported as recovered. Does nothing when no run is active.
     *
     * @param uniqueId  the container's JUnit unique ID
     * @param startTime when the container started, or {@code null} if unknown; the stop time is used then
     */
    public void onContainerSuccess(String uniqueId, Long startTime) {
        TestRun run = activeRun.get();
        if (run == null) {
            return;
        }
        long stopTime = System.currentTimeMillis();
        run.state().addContainerSuccess(new ContainerSuccess(uniqueId, startTime != null ? startTime : stopTime, stopTime));
    }

    /**
     * The identity of a test known only by its unique ID, for callers that recorded its start beforehand.
     */
    private static TestDetails unknownTest(String uniqueId) {
        return new TestDetails(0L, Set.of(), null, uniqueId, "Unknown Test");
    }

    /**
     * Starts recording a run whose caller names no owner. All such calls share one owner, so they behave as in 0.5.0;
     * the entry points use {@link #startTestRun(Object, String)}.
     *
     * @param generator the entry point that reports, written as {@code generatedBy}
     */
    public void startTestRun(String generator) {
        startTestRun(UNNAMED_OWNER, generator);
    }

    /**
     * Starts recording a run owned by {@code owner}, unless another owner's run is active. Reads no file: the report
     * is read only at the end, under the lock.
     *
     * @param owner     what starts the run, compared by identity: the listener's {@code TestPlan} or the extension's
     *                  root {@code ExtensionContext}
     * @param generator the entry point that reports, written as {@code generatedBy}
     * @return whether {@code owner} owns the active run: true if the run was just started or {@code owner} started it
     *     before, false if another owner's run is active, as for a JUnit run started from inside a test
     */
    public boolean startTestRun(Object owner, String generator) {
        Objects.requireNonNull(owner, "owner");
        TestRun candidate = new TestRun(owner, generator, System.currentTimeMillis());
        while (true) {
            if (activeRun.compareAndSet(null, candidate)) {
                return true;
            }
            TestRun active = activeRun.get();
            if (active != null) {
                return active.owner() == owner;
            }
        }
    }

    /**
     * Writes the results of the run started through {@link #startTestRun(String)}, if it is the active run.
     */
    public void finishTestRun() {
        finishTestRun(UNNAMED_OWNER);
    }

    /**
     * Writes the results of the run {@code owner} owns, and ends it. Does nothing if {@code owner} doesn't own the
     * active run. The report on disk is read while it is locked: if it was written by the same run, for example by a
     * parallel fork or an earlier retry round, this execution is merged into it; otherwise it is replaced.
     *
     * @param owner the owner given to {@link #startTestRun(Object, String)}
     */
    public void finishTestRun(Object owner) {
        TestRun run = activeRun.get();
        if (run == null || run.owner() != owner || !activeRun.compareAndSet(run, null)) {
            return;
        }

        long testRunStopTime = System.currentTimeMillis();
        refreshEnvironmentHealthFromEnvVar();

        List<Test> attempts = run.state().getAllAttempts();
        List<ContainerSuccess> successes = run.state().getAllContainerSuccesses();
        long startTime = run.startTime();
        boolean healthy = isEnvironmentHealthy.get();
        CtrfJsonComposer composer = composerFactory.apply(run.generator());

        fileService.update(runId, existing -> {
            var merged = ReportMerger.merge(existing, runId, attempts, successes, startTime, testRunStopTime, healthy);
            var summary = SummaryUtil.createSummary(merged.tests(), merged.start(), merged.stop());
            return composer.generateCtrfJson(summary, merged.tests(), merged.healthy(), runId);
        });
    }

    private void refreshEnvironmentHealthFromEnvVar() {
        if (EnvironmentHealthTracker.isEnvironmentVariableUnhealthy()) {
            isEnvironmentHealthy.set(false);
        }
    }
}

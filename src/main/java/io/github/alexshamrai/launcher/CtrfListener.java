package io.github.alexshamrai.launcher;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.adapter.TestIdentifierAdapter;
import io.github.alexshamrai.ctrf.model.Attachment;
import io.github.alexshamrai.model.TestDetails;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.reporting.FileEntry;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.LauncherConstants;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JUnit Platform TestExecutionListener that generates test reports in the CTRF (Common Test Report Format) format.
 * <p>
 * This listener tracks test execution, captures test results, and generates a JSON report
 * following the CTRF standard. It handles test statuses, and captures relevant test metadata.
 * <p>
 * The jar registers this listener in {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}, so the
 * JUnit Platform loads it whenever the jar is on the test classpath, and {@code LauncherFactory.create()} does too.
 * Register it by hand only on a launcher whose automatic registration is turned off:
 * <pre>
 * {@code
 * Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
 *     .enableTestExecutionListenerAutoRegistration(false)
 *     .addTestExecutionListeners(new CtrfListener())
 *     .build());
 * }
 * </pre>
 * Registered twice on one launcher, it reports once. JUnit's
 * {@code junit.platform.execution.listeners.deactivate=io.github.alexshamrai.launcher.CtrfListener} turns it off.
 * <p>
 * A container that finishes successfully is recorded too, so that its failure in an earlier retry round is
 * reported as recovered: passed and flaky.
 * <p>
 * Only one run is reported at a time. A listener whose test plan starts while another run is active, such as the
 * listener of a JUnit run that a test starts itself, reports nothing, and the run around it goes on.
 * <p>
 * If the JVM exits before the test plan finishes, as after {@code System.exit} in a test or a SIGTERM, a shutdown hook
 * still writes the run: the tests still running, and a container running code outside any test, as {@code other}, and the
 * tests that never started as skipped.
 * <p>
 * The listener can be configured through a {@code ctrf.properties} file placed in the classpath.
 * See the README for all available configuration options.
 */
public class CtrfListener implements TestExecutionListener {

    private final CtrfReportManager reportManager;
    private static final String GENERATED_BY = "io.github.alexshamrai.launcher.CtrfListener";
    private static final String INITIALIZATION_ERROR = "initializationError";
    private static final String TEARDOWN_ERROR = "teardownError";
    private static final String JVM_EXITED = "The JVM exited before this test started";
    private static final String CONTAINER_INTERRUPTED = "The JVM exited while this container was running";
    private static final String OCTET_STREAM = "application/octet-stream";

    private final ShutdownHooks shutdownHooks;

    /**
     * The containers that started and haven't finished, with their start times, for the duration of a container
     * failure and for the exit hook.
     */
    private final Map<String, RunningContainer> runningContainers = new ConcurrentHashMap<>();

    /**
     * The tests that started and haven't finished, so that the exit hook knows which containers had none running.
     */
    private final Set<String> testsInProgress = ConcurrentHashMap.newKeySet();

    /**
     * Containers whose tests or child containers have started or were skipped. JUnit gets to a class's
     * children only after the class's setup succeeded, so a class that fails after that failed in its
     * teardown rather than in its setup.
     */
    private final Set<String> containersWithStartedChildren = ConcurrentHashMap.newKeySet();

    /**
     * Tests and containers whose outcome is already handled: tests that started or were reported as skipped,
     * and containers that ran. A container that is skipped or aborted later reports only what is left.
     */
    private final Set<String> reportedIds = ConcurrentHashMap.newKeySet();

    /**
     * Whether this listener owns the active run. The listener of a JUnit run started from inside a test doesn't, and
     * reports nothing (CB-2); nor does a second listener on the same launcher.
     */
    private volatile boolean ownsRun;

    /**
     * What owns the run this listener started: a new token for each test plan it runs. Not the {@code TestPlan},
     * which every listener of the launcher sees, so that of two listeners on one launcher only the first reports.
     */
    private volatile Object runOwner;

    private volatile TestPlan testPlan;

    /**
     * Writes the run if the JVM exits before its test plan finishes (CB-8). Added for each test plan whose run this
     * listener owns, and removed when the plan finishes.
     */
    private volatile Thread exitHook;

    /**
     * The event callbacks hold its read lock, and the exit hook its write lock. The hook waits for the callbacks in
     * progress, such as a JUnit thread that is still recording under SIGTERM, or the run's normal write, and the
     * callbacks after it record nothing.
     */
    private final ReadWriteLock exitLock = new ReentrantReadWriteLock();

    /**
     * Creates the listener, reporting through the JVM's report manager. JUnit calls this constructor when it loads the
     * listener from {@code META-INF/services}.
     */
    public CtrfListener() {
        this(CtrfReportManager.getInstance(), ShutdownHooks.RUNTIME);
    }

    /**
     * Creates the listener with the report manager it reports through and the shutdown hooks it adds its own to, for
     * tests.
     */
    CtrfListener(CtrfReportManager reportManager, ShutdownHooks shutdownHooks) {
        this.reportManager = reportManager;
        this.shutdownHooks = shutdownHooks;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        whileRecording(() -> startRun(testPlan));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        whileRecording(this::finishRun);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        whileRecording(() -> started(testIdentifier));
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        whileRecording(() -> finished(testIdentifier, testExecutionResult));
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        whileRecording(() -> skipped(testIdentifier, reason));
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
        whileRecording(() -> published(testIdentifier, entry));
    }

    /**
     * Attaches a file that a test or container published with {@code TestReporter.publishFile} (MP-10): its name, its
     * media type or {@code application/octet-stream}, and its absolute path. A directory, from
     * {@code publishDirectory}, is left out.
     * <p>
     * JUnit 5.12 added this callback. It isn't marked {@code @Override}: the reporter compiles against JUnit 5.10, whose
     * listener interface doesn't have it, and JUnit 5.10 and 5.11 never call it.
     *
     * @param testIdentifier the test or container that published the file
     * @param file           the file
     */
    public void fileEntryPublished(TestIdentifier testIdentifier, FileEntry file) {
        whileRecording(() -> publishedFile(testIdentifier, file));
    }

    private void whileRecording(Runnable callback) {
        Lock lock = exitLock.readLock();
        lock.lock();
        try {
            callback.run();
        } finally {
            lock.unlock();
        }
    }

    private void startRun(TestPlan plan) {
        testPlan = plan;
        Object owner = new Object();
        runOwner = owner;
        ownsRun = reportManager.startTestRun(owner, GENERATED_BY);
        if (ownsRun) {
            exitHook = new Thread(this::onJvmExit, "ctrf-jvm-exit");
            shutdownHooks.add(exitHook);
        }
    }

    private void finishRun() {
        boolean owned = ownsRun;
        ownsRun = false;
        try {
            if (owned) {
                reportManager.finishTestRun(runOwner);
            }
        } finally {
            if (owned) {
                shutdownHooks.remove(exitHook);
            }
            reportedIds.clear();
        }
    }

    /**
     * Runs when the JVM exits before the test plan finished: reports each test that never started as skipped, then
     * has the manager write the run, with the tests still running as failed. The run is written even if finding the
     * tests that never started fails.
     */
    void onJvmExit() {
        Lock lock = exitLock.writeLock();
        lock.lock();
        try {
            TestPlan plan = testPlan;
            if (!ownsRun || plan == null) {
                return;
            }
            ownsRun = false;
            try {
                plan.getRoots().forEach(root -> reportNotStartedAsSkipped(plan, root));
                reportInterruptedContainers();
            } finally {
                reportManager.finishTestRunOnExit(runOwner);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reports the tests below {@code node} that never started as skipped. The tests of a container that finished are
     * left out: they ran, were skipped with it, or, under a class whose setup failed, aren't reported in any run. A test
     * method whose tests JUnit creates only while running it counts as one test, as for a skipped class, unless it
     * started.
     */
    private void reportNotStartedAsSkipped(TestPlan plan, TestIdentifier node) {
        for (TestIdentifier child : plan.getChildren(node)) {
            String id = child.getUniqueId();
            if (reportedIds.contains(id)) {
                continue;
            }
            if (child.isTest() || isTestMethodWithoutTests(plan, child) && !runningContainers.containsKey(id)) {
                reportSkipped(child, JVM_EXITED);
            } else {
                reportNotStartedAsSkipped(plan, child);
            }
        }
    }

    /**
     * Adds an entry for each container that was running with no test or container running below it, such as a class
     * in its {@code @BeforeAll} or a parameterized test in its {@code @MethodSource}: where the JVM was. It's named as
     * if the container had failed. Engines are left out.
     */
    private void reportInterruptedContainers() {
        for (RunningContainer container : runningContainers.values()) {
            TestIdentifier identifier = container.identifier();
            String below = identifier.getUniqueId() + "/";
            boolean busyBelow = testsInProgress.stream().anyMatch(id -> id.startsWith(below))
                || runningContainers.keySet().stream().anyMatch(id -> id.startsWith(below));
            if (identifier.getParentId().isPresent() && !busyBelow) {
                boolean childrenStarted = containersWithStartedChildren.contains(identifier.getUniqueId());
                reportManager.onTestInterrupted(containerEntry(identifier, container.startTime(), childrenStarted),
                    CONTAINER_INTERRUPTED);
            }
        }
    }

    private void started(TestIdentifier testIdentifier) {
        if (!ownsRun) {
            return;
        }
        testIdentifier.getParentId().ifPresent(containersWithStartedChildren::add);
        if (testIdentifier.isTest()) {
            reportedIds.add(testIdentifier.getUniqueId());
            testsInProgress.add(testIdentifier.getUniqueId());
            reportManager.onTestStart(createTestDetails(testIdentifier));
        } else if (testIdentifier.isContainer()) {
            runningContainers.put(testIdentifier.getUniqueId(),
                new RunningContainer(testIdentifier, System.currentTimeMillis()));
        }
    }

    private void finished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!ownsRun) {
            return;
        }
        if (testIdentifier.isTest()) {
            testsInProgress.remove(testIdentifier.getUniqueId());
            handleTestFinished(testIdentifier, testExecutionResult);
            return;
        }
        String uniqueId = testIdentifier.getUniqueId();
        RunningContainer running = runningContainers.remove(uniqueId);
        Long startTime = running != null ? running.startTime() : null;
        boolean childrenStarted = containersWithStartedChildren.remove(uniqueId);
        if (testIdentifier.isContainer() && testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED) {
            handleContainerFailure(testIdentifier, testExecutionResult, startTime, childrenStarted);
        } else if (testIdentifier.isContainer() && testExecutionResult.getStatus() == TestExecutionResult.Status.ABORTED) {
            String reason = testExecutionResult.getThrowable().map(Throwable::getMessage).orElse(null);
            reportTestsNotStartedAsSkipped(testIdentifier, reason);
        } else if (testIdentifier.isContainer() && testExecutionResult.getStatus() == TestExecutionResult.Status.SUCCESSFUL
            && testIdentifier.getParentId().isPresent()) {
            // Engines are left out: every fork runs them, so one fork's engine success must not look like the
            // recovery of another fork's engine failure
            reportManager.onContainerSuccess(uniqueId, startTime);
        }
        if (testExecutionResult.getStatus() != TestExecutionResult.Status.FAILED) {
            reportManager.dropPublished(uniqueId);
        }
        // It ran: an enclosing container aborted later must not report it as skipped
        reportedIds.add(uniqueId);
    }

    private void handleTestFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        String uniqueId = testIdentifier.getUniqueId();
        switch (testExecutionResult.getStatus()) {
            case SUCCESSFUL:
                reportManager.onTestSuccess(uniqueId);
                break;
            case FAILED:
                reportManager.onTestFailure(uniqueId, testExecutionResult.getThrowable().orElse(null));
                break;
            case ABORTED:
                reportManager.onTestAborted(uniqueId, testExecutionResult.getThrowable().orElse(null));
                break;
        }
    }

    /**
     * Reports a failed container as a failed entry, so that failures outside tests are not lost:
     * a class whose setup or teardown failed, a {@code @MethodSource} or {@code @TestFactory} that threw,
     * a failing engine.
     */
    private void handleContainerFailure(TestIdentifier container, TestExecutionResult testExecutionResult,
                                        Long startTime, boolean childrenStarted) {
        TestDetails details = containerEntry(container, startTime, childrenStarted);
        reportManager.onTestStart(details);
        reportManager.onTestFailure(details.uniqueId(), testExecutionResult.getThrowable().orElse(null));
    }

    /**
     * The entry of a container that failed, or that was running when the JVM exited.
     * <p>
     * A class is named "initializationError" before any of its tests or nested classes started or were skipped (for
     * example in {@code @BeforeAll}), matching JUnit's XML reporter, and "teardownError" afterwards (for example in
     * {@code @AfterAll}). Any other container keeps its display name.
     * Every entry is identified by the container's own unique ID.
     */
    private TestDetails containerEntry(TestIdentifier container, Long startTime, boolean childrenStarted) {
        boolean isClass = container.getSource().filter(ClassSource.class::isInstance).isPresent();
        String name = container.getDisplayName();
        if (isClass) {
            name = childrenStarted ? TEARDOWN_ERROR : INITIALIZATION_ERROR;
        }
        String uniqueId = container.getUniqueId();
        // A class error is grouped under its class, with the class's tests; any other container like a test
        var adapter = new TestIdentifierAdapter(container, testPlan);
        List<String> suite = isClass ? adapter.getSuiteOfChildren() : adapter.getSuite();

        String className = container.getSource()
            .map(CtrfListener::classNameOf)
            .orElse(null);

        var tags = container.getTags().stream()
            .map(Object::toString)
            .collect(Collectors.toSet());

        long start = startTime != null ? startTime : System.currentTimeMillis();
        return new TestDetails(start, tags, className, uniqueId, name, suite);
    }

    private static String classNameOf(TestSource source) {
        if (source instanceof ClassSource) {
            return ((ClassSource) source).getClassName();
        } else if (source instanceof MethodSource) {
            return ((MethodSource) source).getClassName();
        }
        return null;
    }

    /**
     * Passes on the output that JUnit captured for a test or container. With its output capture on, JUnit publishes it
     * as a report entry with the key {@code stdout}, {@code stderr} or both (HP-13). Other entries are ignored.
     */
    private void published(TestIdentifier testIdentifier, ReportEntry entry) {
        if (!ownsRun) {
            return;
        }
        Map<String, String> pairs = entry.getKeyValuePairs();
        String stdout = pairs.get(LauncherConstants.STDOUT_REPORT_ENTRY_KEY);
        String stderr = pairs.get(LauncherConstants.STDERR_REPORT_ENTRY_KEY);
        if (stdout != null || stderr != null) {
            reportManager.onTestOutput(testIdentifier.getUniqueId(), stdout, stderr);
        }
    }

    private void publishedFile(TestIdentifier testIdentifier, FileEntry file) {
        if (!ownsRun) {
            return;
        }
        Path path = file.getPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        reportManager.onTestAttachment(testIdentifier.getUniqueId(), Attachment.builder()
            .name(String.valueOf(path.getFileName()))
            .contentType(file.getMediaType().orElse(OCTET_STREAM))
            .path(path.toAbsolutePath().toString())
            .build());
    }

    private void skipped(TestIdentifier testIdentifier, String reason) {
        if (!ownsRun) {
            return;
        }
        testIdentifier.getParentId().ifPresent(containersWithStartedChildren::add);
        if (testIdentifier.isTest()) {
            reportSkipped(testIdentifier, reason);
        } else {
            reportTestsNotStartedAsSkipped(testIdentifier, reason);
        }
    }

    /**
     * When a container is skipped or aborted, JUnit reports only the container and none of the tests inside it.
     * Report each of those tests that nobody reported yet as skipped, with the container's reason, as Gradle does.
     * A test method whose tests JUnit creates only while running it ({@code @ParameterizedTest},
     * {@code @RepeatedTest}, {@code @TestFactory}) has no tests in the plan, so it is reported as one skipped entry.
     */
    private void reportTestsNotStartedAsSkipped(TestIdentifier container, String reason) {
        TestPlan plan = testPlan;
        if (plan == null) {
            return;
        }
        Stream.concat(Stream.of(container), plan.getDescendants(container).stream())
            .filter(identifier -> identifier.isTest() || isTestMethodWithoutTests(plan, identifier))
            .forEach(identifier -> reportSkipped(identifier, reason));
    }

    private static boolean isTestMethodWithoutTests(TestPlan plan, TestIdentifier identifier) {
        return identifier.isContainer()
            && identifier.getSource().filter(MethodSource.class::isInstance).isPresent()
            && plan.getDescendants(identifier).stream().noneMatch(TestIdentifier::isTest);
    }

    private void reportSkipped(TestIdentifier test, String reason) {
        if (reportedIds.add(test.getUniqueId())) {
            reportManager.onTestSkipped(createTestDetails(test), Optional.ofNullable(reason));
        }
    }

    private TestDetails createTestDetails(TestIdentifier testIdentifier) {
        return TestDetails.fromAdapter(new TestIdentifierAdapter(testIdentifier, testPlan));
    }

    /**
     * A container that started and hasn't finished.
     */
    private record RunningContainer(TestIdentifier identifier, long startTime) {
    }
}

package io.github.alexshamrai.launcher;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.adapter.TestIdentifierAdapter;
import io.github.alexshamrai.model.TestDetails;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * The listener can be configured through a {@code ctrf.properties} file placed in the classpath.
 * See the README for all available configuration options.
 */
public class CtrfListener implements TestExecutionListener {

    private final CtrfReportManager reportManager;
    private static final String GENERATED_BY = "io.github.alexshamrai.launcher.CtrfListener";
    private static final String INITIALIZATION_ERROR = "initializationError";
    private static final String TEARDOWN_ERROR = "teardownError";

    /**
     * Tracks container start times for accurate duration calculation of container failures.
     */
    private final Map<String, Long> containerStartTimes = new ConcurrentHashMap<>();

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
     * Creates the listener, reporting through the JVM's report manager. JUnit calls this constructor when it loads the
     * listener from {@code META-INF/services}.
     */
    public CtrfListener() {
        this(CtrfReportManager.getInstance());
    }

    /**
     * Creates the listener with the report manager it reports through, for tests.
     */
    CtrfListener(CtrfReportManager reportManager) {
        this.reportManager = reportManager;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan;
        Object owner = new Object();
        runOwner = owner;
        ownsRun = reportManager.startTestRun(owner, GENERATED_BY);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (ownsRun) {
            reportManager.finishTestRun(runOwner);
        }
        ownsRun = false;
        reportedIds.clear();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (!ownsRun) {
            return;
        }
        testIdentifier.getParentId().ifPresent(containersWithStartedChildren::add);
        if (testIdentifier.isTest()) {
            reportedIds.add(testIdentifier.getUniqueId());
            reportManager.onTestStart(createTestDetails(testIdentifier));
        } else if (testIdentifier.isContainer()) {
            containerStartTimes.put(testIdentifier.getUniqueId(), System.currentTimeMillis());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!ownsRun) {
            return;
        }
        if (testIdentifier.isTest()) {
            handleTestFinished(testIdentifier, testExecutionResult);
            return;
        }
        String uniqueId = testIdentifier.getUniqueId();
        Long startTime = containerStartTimes.remove(uniqueId);
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
     * <p>
     * A failed class is named "initializationError" when it failed before any of its tests or nested
     * classes started or were skipped (for example in {@code @BeforeAll}), matching JUnit's XML reporter, and
     * "teardownError" when it failed afterwards (for example in {@code @AfterAll}). Any other container keeps
     * its display name.
     * Every entry is identified by the container's own unique ID.
     */
    private void handleContainerFailure(TestIdentifier container, TestExecutionResult testExecutionResult,
                                        Long startTime, boolean childrenStarted) {
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
        TestDetails details = new TestDetails(start, tags, className, uniqueId, name, suite);

        reportManager.onTestStart(details);
        reportManager.onTestFailure(uniqueId, testExecutionResult.getThrowable().orElse(null));
    }

    private static String classNameOf(TestSource source) {
        if (source instanceof ClassSource) {
            return ((ClassSource) source).getClassName();
        } else if (source instanceof MethodSource) {
            return ((MethodSource) source).getClassName();
        }
        return null;
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
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
}
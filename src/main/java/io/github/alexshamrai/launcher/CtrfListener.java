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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * JUnit Platform TestExecutionListener that generates test reports in the CTRF (Common Test Report Format) format.
 * <p>
 * This listener tracks test execution, captures test results, and generates a JSON report
 * following the CTRF standard. It handles test statuses, and captures relevant test metadata.
 * <p>
 * To use this listener, register it with JUnit Platform launcher or via system property:
 * <pre>
 * {@code
 * -Djunit.platform.execution.listeners.deactivate=
 * -Djunit.platform.launcher.listeners.discovery=io.github.alexshamrai.launcher.CtrfListener
 * }
 * </pre>
 * <p>
 * Or register it programmatically:
 * <pre>
 * {@code
 * LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
 *     .selectors(selectPackage("com.example"))
 *     .build();
 *
 * Launcher launcher = LauncherFactory.create();
 * launcher.registerTestExecutionListeners(new CtrfListener());
 * launcher.execute(request);
 * }
 * </pre>
 * <p>
 * The listener can be configured through a {@code ctrf.properties} file placed in the classpath.
 * See the README for all available configuration options.
 */
public class CtrfListener implements TestExecutionListener {

    private final CtrfReportManager reportManager = CtrfReportManager.getInstance();
    private static final String GENERATED_BY = "io.github.alexshamrai.launcher.CtrfListener";
    private static final String INITIALIZATION_ERROR = "initializationError";
    private static final String TEARDOWN_ERROR = "teardownError";

    /**
     * Tracks container start times for accurate duration calculation of container failures.
     */
    private final Map<String, Long> containerStartTimes = new ConcurrentHashMap<>();

    /**
     * Containers whose tests or child containers have started. A class that fails after that
     * failed in its teardown rather than in its setup.
     */
    private final Set<String> containersWithStartedChildren = ConcurrentHashMap.newKeySet();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        reportManager.startTestRun(GENERATED_BY);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        reportManager.finishTestRun();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        testIdentifier.getParentId().ifPresent(containersWithStartedChildren::add);
        if (testIdentifier.isTest()) {
            reportManager.onTestStart(createTestDetails(testIdentifier));
        } else if (testIdentifier.isContainer()) {
            containerStartTimes.put(testIdentifier.getUniqueId(), System.currentTimeMillis());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            handleTestFinished(testIdentifier, testExecutionResult);
            return;
        }
        String uniqueId = testIdentifier.getUniqueId();
        Long startTime = containerStartTimes.remove(uniqueId);
        boolean childrenStarted = containersWithStartedChildren.remove(uniqueId);
        if (testIdentifier.isContainer() && testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED) {
            handleContainerFailure(testIdentifier, testExecutionResult, startTime, childrenStarted);
        }
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
     * classes started (for example in {@code @BeforeAll}), matching JUnit's XML reporter, and "teardownError"
     * when it failed afterwards (for example in {@code @AfterAll}). Any other container keeps its display name.
     */
    private void handleContainerFailure(TestIdentifier container, TestExecutionResult testExecutionResult,
                                        Long startTime, boolean childrenStarted) {
        boolean isClass = container.getSource().filter(ClassSource.class::isInstance).isPresent();
        String name = container.getDisplayName();
        String uniqueId = container.getUniqueId();
        if (isClass) {
            name = childrenStarted ? TEARDOWN_ERROR : INITIALIZATION_ERROR;
            uniqueId = uniqueId + "/" + name;
        }

        String className = container.getSource()
            .map(CtrfListener::classNameOf)
            .orElse(null);

        var tags = container.getTags().stream()
            .map(Object::toString)
            .collect(Collectors.toSet());

        long start = startTime != null ? startTime : System.currentTimeMillis();
        TestDetails details = new TestDetails(start, tags, className, uniqueId, name);

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
        if (testIdentifier.isTest()) {
            reportManager.onTestSkipped(createTestDetails(testIdentifier), Optional.ofNullable(reason));
        }
    }

    private TestDetails createTestDetails(TestIdentifier testIdentifier) {
        return TestDetails.fromAdapter(new TestIdentifierAdapter(testIdentifier));
    }
}
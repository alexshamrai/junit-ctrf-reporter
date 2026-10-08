package io.github.alexshamrai.launcher;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.adapter.TestIdentifierAdapter;
import io.github.alexshamrai.model.TestDetails;
import org.junit.platform.engine.TestExecutionResult;
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
import java.util.stream.Stream;

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

    /**
     * Tracks container start times for accurate duration calculation on initialization errors.
     */
    private final Map<String, Long> containerStartTimes = new ConcurrentHashMap<>();

    /**
     * Tests and containers whose outcome is already handled: tests that started or were reported as skipped,
     * and containers that ran. A container that is skipped or aborted later reports only what is left.
     */
    private final Set<String> reportedIds = ConcurrentHashMap.newKeySet();

    private volatile TestPlan testPlan;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan;
        reportManager.startTestRun(GENERATED_BY);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        reportManager.finishTestRun(Optional.empty());
        reportedIds.clear();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            reportedIds.add(testIdentifier.getUniqueId());
            reportManager.onTestStart(createTestDetails(testIdentifier));
        } else if (testIdentifier.isContainer()) {
            containerStartTimes.put(testIdentifier.getUniqueId(), System.currentTimeMillis());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            handleTestFinished(testIdentifier, testExecutionResult);
        } else if (isContainerFailure(testIdentifier, testExecutionResult)) {
            handleContainerFailure(testIdentifier, testExecutionResult);
        } else {
            containerStartTimes.remove(testIdentifier.getUniqueId());
            if (testIdentifier.isContainer() && testExecutionResult.getStatus() == TestExecutionResult.Status.ABORTED) {
                String reason = testExecutionResult.getThrowable().map(Throwable::getMessage).orElse(null);
                reportTestsNotStartedAsSkipped(testIdentifier, reason);
            }
        }
        // It ran: an enclosing container aborted later must not report it as skipped
        reportedIds.add(testIdentifier.getUniqueId());
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
     * Checks if this is a container-level failure that should be reported.
     * Only report failures for class-level containers (not engine or package containers).
     */
    private boolean isContainerFailure(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        return testIdentifier.isContainer()
            && testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED
            && testIdentifier.getSource().filter(ClassSource.class::isInstance).isPresent();
    }

    /**
     * Handles failures that occur at the container level (e.g., @BeforeAll failures,
     * Spring context initialization errors, parameterized test setup failures).
     * <p>
     * Creates a synthetic "initializationError" test entry to capture the failure,
     * matching the behavior of JUnit's XML reporter.
     */
    private void handleContainerFailure(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        Long startTime = containerStartTimes.remove(testIdentifier.getUniqueId());
        if (startTime == null) {
            startTime = System.currentTimeMillis();
        }

        String className = testIdentifier.getSource()
            .filter(ClassSource.class::isInstance)
            .map(source -> ((ClassSource) source).getClassName())
            .orElse(testIdentifier.getDisplayName());

        String uniqueId = testIdentifier.getUniqueId() + "/" + INITIALIZATION_ERROR;

        var tags = testIdentifier.getTags().stream()
            .map(Object::toString)
            .collect(Collectors.toSet());

        TestDetails details = new TestDetails(startTime, tags, className, uniqueId, INITIALIZATION_ERROR);

        reportManager.onTestStart(details);
        reportManager.onTestFailure(uniqueId, testExecutionResult.getThrowable().orElse(null));
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
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
        return TestDetails.fromAdapter(new TestIdentifierAdapter(testIdentifier));
    }
}
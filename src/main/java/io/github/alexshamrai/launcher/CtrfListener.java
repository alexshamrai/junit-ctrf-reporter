package io.github.alexshamrai.launcher;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.adapter.TestIdentifierAdapter;
import io.github.alexshamrai.model.TestDetails;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.Optional;

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

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        reportManager.startTestRun(GENERATED_BY);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        reportManager.finishTestRun(Optional.empty());
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            reportManager.onTestStart(createTestDetails(testIdentifier));
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
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
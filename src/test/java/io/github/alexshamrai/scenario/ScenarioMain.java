package io.github.alexshamrai.scenario;

import io.github.alexshamrai.launcher.CtrfListener;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.util.Arrays;
import java.util.List;

/**
 * Entry point of the JVM that {@link ScenarioRunner} starts: runs fixture classes on the JUnit Platform, reporting
 * through {@link CtrfListener}. In "listener-rerun" mode it then runs the failed tests again in the same JVM, through
 * the same launcher, as Surefire's {@code rerunFailingTestsCount} does. In "listener-twice" mode the launcher has two
 * {@link CtrfListener}s, as when the jar registers one and the user registers another.
 *
 * <p>Usage: {@code ScenarioMain <listener|listener-rerun|listener-twice> <fixture class name>...}
 */
public final class ScenarioMain {

    static final String SUMMARY_PREFIX = "JUNIT_SUMMARY ";

    private ScenarioMain() {
    }

    public static void main(String[] args) {
        String mode = args[0];
        var config = LauncherConfig.builder()
            .enableTestExecutionListenerAutoRegistration(false)
            .addTestExecutionListeners(new CtrfListener());
        if ("listener-twice".equals(mode)) {
            config.addTestExecutionListeners(new CtrfListener());
        }
        Launcher launcher = LauncherFactory.create(config.build());

        var summary = execute(launcher, Arrays.stream(args, 1, args.length).map(DiscoverySelectors::selectClass).toList());
        if ("listener-rerun".equals(mode) && !summary.getFailures().isEmpty()) {
            summary = execute(launcher, summary.getFailures().stream()
                .map(failure -> DiscoverySelectors.selectUniqueId(failure.getTestIdentifier().getUniqueId()))
                .toList());
        }

        System.out.println(SUMMARY_PREFIX
            + "found=" + summary.getTestsFoundCount()
            + " succeeded=" + summary.getTestsSucceededCount()
            + " failed=" + summary.getTestsFailedCount()
            + " aborted=" + summary.getTestsAbortedCount()
            + " skipped=" + summary.getTestsSkippedCount()
            + " containersFailed=" + summary.getContainersFailedCount()
            + " containersSkipped=" + summary.getContainersSkippedCount());
    }

    private static TestExecutionSummary execute(Launcher launcher, List<? extends DiscoverySelector> selectors) {
        var request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectors)
            .configurationParameter("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer$ClassName")
            .configurationParameter("junit.jupiter.testmethod.order.default", "org.junit.jupiter.api.MethodOrderer$MethodName")
            .build();
        var junitSummary = new SummaryGeneratingListener();
        launcher.execute(request, junitSummary);
        return junitSummary.getSummary();
    }
}

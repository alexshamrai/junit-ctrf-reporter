package io.github.alexshamrai.scenario;

import io.github.alexshamrai.launcher.CtrfListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.util.Arrays;

/**
 * Entry point of the JVM that {@link ScenarioRunner} starts: runs fixture classes on the JUnit Platform,
 * reporting through {@link CtrfListener} ("listener") or only through the fixtures' own extensions ("extension").
 *
 * <p>Usage: {@code ScenarioMain <listener|extension> <fixture class name>...}
 */
public final class ScenarioMain {

    static final String SUMMARY_PREFIX = "JUNIT_SUMMARY ";

    private ScenarioMain() {
    }

    public static void main(String[] args) {
        var config = LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(false);
        if ("listener".equals(args[0])) {
            config.addTestExecutionListeners(new CtrfListener());
        }
        var request = LauncherDiscoveryRequestBuilder.request()
            .selectors(Arrays.stream(args, 1, args.length).map(DiscoverySelectors::selectClass).toList())
            .configurationParameter("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer$ClassName")
            .configurationParameter("junit.jupiter.testmethod.order.default", "org.junit.jupiter.api.MethodOrderer$MethodName")
            .build();
        var junitSummary = new SummaryGeneratingListener();
        LauncherFactory.create(config.build()).execute(request, junitSummary);

        var summary = junitSummary.getSummary();
        System.out.println(SUMMARY_PREFIX
            + "found=" + summary.getTestsFoundCount()
            + " succeeded=" + summary.getTestsSucceededCount()
            + " failed=" + summary.getTestsFailedCount()
            + " aborted=" + summary.getTestsAbortedCount()
            + " skipped=" + summary.getTestsSkippedCount()
            + " containersFailed=" + summary.getContainersFailedCount()
            + " containersSkipped=" + summary.getContainersSkippedCount());
    }
}

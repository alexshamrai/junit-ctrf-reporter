package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A test that runs JUnit itself (CB-2). Its launcher registers its own CtrfListener, through the test classpath's
 * {@code META-INF/services}, and runs {@link NestedFixture}.
 */
@Tag("scenario-fixture")
public class NestedRunBFixture {

    @Test
    void runsNestedLauncher() {
        var summary = new SummaryGeneratingListener();

        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(NestedFixture.class))
            .build(), summary);

        assertEquals(2, summary.getSummary().getTestsFoundCount());
    }
}

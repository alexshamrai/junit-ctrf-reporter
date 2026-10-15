package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A test that runs JUnit itself (CB-2), reporting through the extension. Its launcher registers no listener, so
 * the only reporter in the nested run is {@link ExtNestedFixture}'s own extension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtNestedRunBFixture {

    @Test
    void runsNestedLauncher() {
        var summary = new SummaryGeneratingListener();
        var withoutListeners = LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(false).build();

        LauncherFactory.create(withoutListeners).execute(LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(ExtNestedFixture.class))
            .build(), summary);

        assertEquals(2, summary.getSummary().getTestsFoundCount());
    }
}

package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.ScenarioRunner.Jvm;
import io.github.alexshamrai.scenario.fixtures.PassingFixture;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How {@code CtrfListener} gets registered, and what happens when it is registered more than once.
 */
class ListenerRegistrationScenarioTest {

    private static final String LISTENER = "io.github.alexshamrai.launcher.CtrfListener";

    @org.junit.jupiter.api.Test
    void secondListenerOnTheSameLauncherReportsNothing() throws Exception {
        assertReportedOnce(ScenarioRunner.runOneAfterAnother(jvm("listener-twice")));
    }

    @org.junit.jupiter.api.Test
    void jarRegistersTheListenerItself() throws Exception {
        assertReportedOnce(ScenarioRunner.runOneAfterAnother(jvm("auto")));
    }

    @org.junit.jupiter.api.Test
    void listenerRegisteredByHandAsWellReportsEachTestOnce() throws Exception {
        assertReportedOnce(ScenarioRunner.runOneAfterAnother(jvm("auto-and-registered")));
    }

    @org.junit.jupiter.api.Test
    void junitsDeactivateSettingTurnsTheListenerOff() {
        var deactivated = jvm("auto").withSystemProperty("junit.platform.execution.listeners.deactivate", LISTENER);

        assertThatThrownBy(() -> ScenarioRunner.runOneAfterAnother(deactivated))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("wrote no report");
    }

    private static Jvm jvm(String mode) {
        return new Jvm(mode, List.of(PassingFixture.class), Map.of(), Map.of());
    }

    private static void assertReportedOnce(ScenarioRunner.Result result) {
        assertThat(result.report().getGeneratedBy()).isEqualTo(LISTENER);
        assertThat(result.tests()).singleElement().satisfies(test -> {
            assertThat(test.getName()).isEqualTo("passes()");
            assertThat(test.getStatus()).isEqualTo(Test.TestStatus.PASSED);
            assertThat(test.getRetryAttempts()).as("attempts of %s", test.getName()).isNullOrEmpty();
        });
    }
}

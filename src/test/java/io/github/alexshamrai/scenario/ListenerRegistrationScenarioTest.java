package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.ScenarioRunner.Jvm;
import io.github.alexshamrai.scenario.fixtures.PassingFixture;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How {@code CtrfListener} gets registered, and what happens when it is registered more than once.
 */
class ListenerRegistrationScenarioTest {

    @org.junit.jupiter.api.Test
    void secondListenerOnTheSameLauncherReportsNothing() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(new Jvm("listener-twice", List.of(PassingFixture.class), Map.of(), Map.of()));

        assertThat(result.tests()).singleElement().satisfies(test -> {
            assertThat(test.getName()).isEqualTo("passes()");
            assertThat(test.getStatus()).isEqualTo(Test.TestStatus.PASSED);
            assertThat(test.getRetryAttempts()).as("attempts of %s", test.getName()).isNullOrEmpty();
        });
    }
}

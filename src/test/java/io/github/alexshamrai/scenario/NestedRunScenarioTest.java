package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.NestedRunAFixture;
import io.github.alexshamrai.scenario.fixtures.NestedRunBFixture;
import io.github.alexshamrai.scenario.fixtures.NestedRunCFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A test that runs JUnit itself starts a nested run in the same JVM. Only the run that started reporting is reported:
 * the nested run neither ends it nor adds its tests (CB-2).
 */
class NestedRunScenarioTest {

    @org.junit.jupiter.api.Test
    void nestedRunThroughTheListenerNeitherEndsTheRunNorAddsItsTests() throws Exception {
        var result = ScenarioRunner.runWithListener(NestedRunAFixture.class, NestedRunBFixture.class, NestedRunCFixture.class);

        assertOnlyTheOuterRunIsReported(result);
    }

    private static void assertOnlyTheOuterRunIsReported(ScenarioRunner.Result result) {
        assertThat(result.junitCounts()).containsEntry("found", 5L).containsEntry("failed", 0L);
        assertThat(result.tests()).extracting(Test::getName)
            .containsExactlyInAnyOrder("first()", "second()", "runsNestedLauncher()", "third()", "fourth()");
        assertThat(result.report().getResults().getSummary().getTests()).isEqualTo(5);
        assertThat(result.report().getResults().getSummary().getFailed()).isZero();
    }
}

package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.AbortedClassFixture;
import io.github.alexshamrai.scenario.fixtures.ConditionalClassFixture;
import io.github.alexshamrai.scenario.fixtures.DisabledClassFixture;
import io.github.alexshamrai.scenario.fixtures.PassingFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests inside a skipped or aborted container are reported as skipped (HP-8).
 */
class SkippedContainerScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerCountsMatchJunitWhenWholeClassesAreSkipped() throws Exception {
        var result = ScenarioRunner.runWithListener(
            DisabledClassFixture.class, ConditionalClassFixture.class, PassingFixture.class);

        var summary = result.report().getResults().getSummary();
        assertThat(result.junitCounts()).containsEntry("skipped", 4L);
        assertThat(summary.getSkipped()).isEqualTo(4);
        assertThat((long) summary.getTests()).isEqualTo(result.junitCounts().get("found"));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsEachTestOfADisabledClassWithTheClassReason() throws Exception {
        var result = ScenarioRunner.runWithListener(DisabledClassFixture.class);

        var entries = result.testsOf(DisabledClassFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("one()", SKIPPED), tuple("two()", SKIPPED));
        assertThat(entries).allSatisfy(test -> assertThat(test.getMessage()).contains("whole class disabled"));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsEachTestOfAConditionallySkippedClassWithTheConditionReason() throws Exception {
        var result = ScenarioRunner.runWithListener(ConditionalClassFixture.class);

        var entries = result.testsOf(ConditionalClassFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("one()", SKIPPED), tuple("two()", SKIPPED));
        assertThat(entries).allSatisfy(test -> assertThat(test.getMessage()).contains("ctrf.scenario.never.set"));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsEachTestOfAnAbortedClassAsSkippedWithTheAssumptionMessage() throws Exception {
        var result = ScenarioRunner.runWithListener(AbortedClassFixture.class);

        assertThat(result.junitCounts()).containsEntry("found", 2L);
        var entries = result.testsOf(AbortedClassFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("one()", SKIPPED), tuple("two()", SKIPPED));
        assertThat(entries).allSatisfy(test -> assertThat(test.getMessage()).contains("Docker is not available"));
    }
}

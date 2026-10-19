package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.AfterAllFailureFixture;
import io.github.alexshamrai.scenario.fixtures.AfterAllFailureWithDisabledTestsFixture;
import io.github.alexshamrai.scenario.fixtures.BrokenSourceFixture;
import io.github.alexshamrai.scenario.fixtures.PassingFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Every failed container ends up in the report as a failed entry (CB-5).
 */
class ContainerFailureScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerReportsFailedParameterizedAndFactoryContainers() throws Exception {
        var result = ScenarioRunner.runWithListener(BrokenSourceFixture.class, PassingFixture.class);

        assertThat(result.junitCounts()).containsEntry("containersFailed", 2L);
        var brokenSourceEntries = result.testsOf(BrokenSourceFixture.class);
        assertThat(brokenSourceEntries)
            .extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("usesData(int)", FAILED), tuple("factory()", FAILED));
        assertThat(brokenSourceEntries)
            .extracting(Test::getMessage)
            .anySatisfy(message -> assertThat(message).contains("cannot load test data"))
            .anySatisfy(message -> assertThat(message).contains("factory exploded"));
    }

    @org.junit.jupiter.api.Test
    void listenerFailedCountMatchesJunitFailedTestsPlusFailedContainers() throws Exception {
        var result = ScenarioRunner.runWithListener(
            BrokenSourceFixture.class, AfterAllFailureFixture.class, PassingFixture.class);

        long junitFailures = result.junitCounts().get("failed") + result.junitCounts().get("containersFailed");
        assertThat(junitFailures).isEqualTo(3);
        assertThat(result.report().getResults().getSummary().getFailed()).isEqualTo(3);
    }

    @org.junit.jupiter.api.Test
    void listenerReportsAfterAllFailureAsTeardownError() throws Exception {
        var result = ScenarioRunner.runWithListener(AfterAllFailureFixture.class);

        assertThat(result.tests())
            .extracting(Test::getName, Test::getStatus, Test::getFilePath)
            .containsExactlyInAnyOrder(
                tuple("ok()", PASSED, AfterAllFailureFixture.class.getName()),
                tuple("teardownError", FAILED, AfterAllFailureFixture.class.getName()));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsAfterAllFailureOfAClassWhoseTestsWereSkippedAsTeardownError() throws Exception {
        var result = ScenarioRunner.runWithListener(AfterAllFailureWithDisabledTestsFixture.class);

        assertThat(result.tests())
            .extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("disabled()", SKIPPED), tuple("teardownError", FAILED));
    }
}

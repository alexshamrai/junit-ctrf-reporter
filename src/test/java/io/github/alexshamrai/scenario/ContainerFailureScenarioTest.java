package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.AfterAllFailureFixture;
import io.github.alexshamrai.scenario.fixtures.BrokenSourceFixture;
import io.github.alexshamrai.scenario.fixtures.ExtFirstFixture;
import io.github.alexshamrai.scenario.fixtures.ExtSecondAfterAllFailureFixture;
import io.github.alexshamrai.scenario.fixtures.ExtThirdCallbackFailureFixture;
import io.github.alexshamrai.scenario.fixtures.PassingFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
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
            .extracting(Test::getName, Test::getStatus, Test::getFilepath)
            .containsExactlyInAnyOrder(
                tuple("ok()", PASSED, AfterAllFailureFixture.class.getName()),
                tuple("teardownError", FAILED, AfterAllFailureFixture.class.getName()));
    }

    @org.junit.jupiter.api.Test
    void extensionReportsAfterAllFailureOfAClassThatIsNotFirst() throws Exception {
        var result = ScenarioRunner.runWithExtension(ExtFirstFixture.class, ExtSecondAfterAllFailureFixture.class);

        assertThat(result.testsOf(ExtSecondAfterAllFailureFixture.class))
            .extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("ok()", PASSED), tuple("teardownError", FAILED));
    }

    @org.junit.jupiter.api.Test
    void extensionReportsClassSetupFailedByAnotherExtension() throws Exception {
        var result = ScenarioRunner.runWithExtension(ExtFirstFixture.class, ExtThirdCallbackFailureFixture.class);

        var entries = result.testsOf(ExtThirdCallbackFailureFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus).containsExactly(tuple("initializationError", FAILED));
        assertThat(entries.get(0).getMessage()).contains("callback failed");
    }
}

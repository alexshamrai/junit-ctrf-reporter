package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.ExtSetupAlwaysFailsFixture;
import io.github.alexshamrai.scenario.fixtures.ExtSetupRecoversFixture;
import io.github.alexshamrai.scenario.fixtures.ExtTeardownAlwaysFailsFixture;
import io.github.alexshamrai.scenario.fixtures.ExtTeardownRecoversFixture;
import io.github.alexshamrai.scenario.fixtures.SetupAlwaysFailsFixture;
import io.github.alexshamrai.scenario.fixtures.SetupRecoversFixture;
import io.github.alexshamrai.scenario.fixtures.TeardownAlwaysFailsFixture;
import io.github.alexshamrai.scenario.fixtures.TeardownRecoversFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A class whose setup or teardown fails in one retry round and succeeds in the next is reported as passed and
 * flaky (HP-11); one that fails in every round stays failed.
 */
class ContainerRecoveryScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerReportsASetupFailureThatRecoversAsPassedAndFlaky() throws Exception {
        assertRecovered(ScenarioRunner.runRoundsWithListener(2, SetupRecoversFixture.class),
            "initializationError", "setup fails in the first round", SetupRecoversFixture.class);
    }

    @org.junit.jupiter.api.Test
    void extensionReportsASetupFailureThatRecoversAsPassedAndFlaky() throws Exception {
        assertRecovered(ScenarioRunner.runRoundsWithExtension(2, ExtSetupRecoversFixture.class),
            "initializationError", "setup fails in the first round", ExtSetupRecoversFixture.class);
    }

    @org.junit.jupiter.api.Test
    void listenerReportsATeardownFailureThatRecoversAsPassedAndFlaky() throws Exception {
        assertRecovered(ScenarioRunner.runRoundsWithListener(2, TeardownRecoversFixture.class),
            "teardownError", "teardown fails in the first round", TeardownRecoversFixture.class);
    }

    @org.junit.jupiter.api.Test
    void extensionReportsATeardownFailureThatRecoversAsPassedAndFlaky() throws Exception {
        assertRecovered(ScenarioRunner.runRoundsWithExtension(2, ExtTeardownRecoversFixture.class),
            "teardownError", "teardown fails in the first round", ExtTeardownRecoversFixture.class);
    }

    @org.junit.jupiter.api.Test
    void listenerReportsASetupFailureInEveryRoundAsFailed() throws Exception {
        assertFailedInEveryRound(ScenarioRunner.runRoundsWithListener(2, SetupAlwaysFailsFixture.class),
            "initializationError");
    }

    @org.junit.jupiter.api.Test
    void extensionReportsASetupFailureInEveryRoundAsFailed() throws Exception {
        assertFailedInEveryRound(ScenarioRunner.runRoundsWithExtension(2, ExtSetupAlwaysFailsFixture.class),
            "initializationError");
    }

    @org.junit.jupiter.api.Test
    void listenerReportsATeardownFailureInEveryRoundAsFailed() throws Exception {
        assertFailedInEveryRound(ScenarioRunner.runRoundsWithListener(2, TeardownAlwaysFailsFixture.class), "teardownError");
    }

    @org.junit.jupiter.api.Test
    void extensionReportsATeardownFailureInEveryRoundAsFailed() throws Exception {
        assertFailedInEveryRound(ScenarioRunner.runRoundsWithExtension(2, ExtTeardownAlwaysFailsFixture.class), "teardownError");
    }

    private static void assertRecovered(ScenarioRunner.Result result, String name, String firstFailure, Class<?> fixture) {
        Test entry = onlyTestNamed(result, name);
        assertThat(entry.getStatus()).isEqualTo(PASSED);
        assertThat(entry.getSuite()).as("a recovered class keeps its suite").containsExactly(fixture.getSimpleName());
        assertThat(entry.getRetries()).isEqualTo(1);
        assertThat(entry.getFlaky()).isTrue();
        assertThat(entry.getRetryAttempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getStatus()).isEqualTo(FAILED);
            assertThat(attempt.getMessage()).contains(firstFailure);
        });
        var summary = result.report().getResults().getSummary();
        assertThat(summary.getFailed()).isZero();
        assertThat(summary.getFlaky()).isEqualTo(1);
    }

    private static void assertFailedInEveryRound(ScenarioRunner.Result result, String name) {
        Test entry = onlyTestNamed(result, name);
        assertThat(entry.getStatus()).isEqualTo(FAILED);
        assertThat(entry.getRetries()).isEqualTo(1);
        assertThat(entry.getFlaky()).isNull();
        assertThat(entry.getRetryAttempts()).extracting(RetryAttempt::getStatus).containsExactly(FAILED);
        var summary = result.report().getResults().getSummary();
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getFlaky()).isZero();
    }

    private static Test onlyTestNamed(ScenarioRunner.Result result, String name) {
        var matches = result.tests().stream().filter(test -> name.equals(test.getName())).toList();
        assertThat(matches).as("entries named %s", name).hasSize(1);
        return matches.get(0);
    }
}

package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.RetryRoundsFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Retry rounds in new JVMs, as the Gradle test-retry plugin runs them, fold into one entry per test (HP-6).
 */
class RetryScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerFoldsRetryRoundsIntoOneEntryPerTest() throws Exception {
        assertFoldedAfterTwoRounds(ScenarioRunner.runRoundsWithListener(2, RetryRoundsFixture.class), RetryRoundsFixture.class);
    }

    @org.junit.jupiter.api.Test
    void listenerExtendsTheHistoryInAThirdRound() throws Exception {
        assertHistoryExtendedAfterThreeRounds(ScenarioRunner.runRoundsWithListener(3, RetryRoundsFixture.class), RetryRoundsFixture.class);
    }

    private static void assertHistoryExtendedAfterThreeRounds(ScenarioRunner.Result result, Class<?> fixture) {
        assertThat(result.testsOf(fixture)).hasSize(2);
        assertThat(onlyTestNamed(result, "alwaysFails()").getRetryAttempts())
            .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus)
            .containsExactly(tuple(1, FAILED), tuple(2, FAILED));
        Test flaky = onlyTestNamed(result, "passesFromTheSecondRound()");
        assertThat(flaky.getRetries()).isEqualTo(2);
        assertThat(flaky.getRetryAttempts())
            .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus)
            .containsExactly(tuple(1, FAILED), tuple(2, PASSED));
        assertThat(flaky.getFlaky()).isTrue();
    }

    private static void assertFoldedAfterTwoRounds(ScenarioRunner.Result result, Class<?> fixture) {
        assertThat(result.testsOf(fixture))
            .extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("passesFromTheSecondRound()", PASSED), tuple("alwaysFails()", FAILED));

        Test flaky = onlyTestNamed(result, "passesFromTheSecondRound()");
        assertThat(flaky.getRetries()).isEqualTo(1);
        assertThat(flaky.getFlaky()).isTrue();
        assertThat(flaky.getRetryAttempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getAttempt()).isEqualTo(1);
            assertThat(attempt.getStatus()).isEqualTo(FAILED);
            assertThat(attempt.getMessage()).contains("fails in the first round");
        });

        Test failing = onlyTestNamed(result, "alwaysFails()");
        assertThat(failing.getRetries()).isEqualTo(1);
        assertThat(failing.getFlaky()).isNull();
        assertThat(failing.getRetryAttempts()).extracting(RetryAttempt::getStatus).containsExactly(FAILED);

        var summary = result.report().getResults().getSummary();
        assertThat(summary.getTests()).isEqualTo(2);
        assertThat(summary.getPassed()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getFlaky()).isEqualTo(1);
    }

    private static Test onlyTestNamed(ScenarioRunner.Result result, String name) {
        var matches = result.tests().stream().filter(test -> name.equals(test.getName())).toList();
        assertThat(matches).as("entries named %s", name).hasSize(1);
        return matches.get(0);
    }
}

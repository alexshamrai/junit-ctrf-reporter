package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.ScenarioRunner.Jvm;
import io.github.alexshamrai.scenario.fixtures.ForkOneFixture;
import io.github.alexshamrai.scenario.fixtures.ForkThreeFixture;
import io.github.alexshamrai.scenario.fixtures.ForkTwoFixture;
import io.github.alexshamrai.scenario.fixtures.RerunFixture;
import io.github.alexshamrai.scenario.fixtures.RetryRoundsFixture;

import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Several JVMs writing one report (CB-6). Forks of one run merge, a new run replaces the report, and a second
 * execution in the same JVM merges into the first.
 */
class PersistenceScenarioTest {

    @org.junit.jupiter.api.Test
    void forksOfOneRunFinishingTogetherAllLandInOneReport() throws Exception {
        var result = ScenarioRunner.runAtOnce(
            Jvm.listener(ForkOneFixture.class).withSystemProperty("ctrf.run.id", "forks"),
            Jvm.listener(ForkTwoFixture.class).withSystemProperty("ctrf.run.id", "forks"),
            Jvm.listener(ForkThreeFixture.class).withSystemProperty("ctrf.run.id", "forks"));

        assertThat(result.report().getRunId()).isEqualTo("forks");
        for (Class<?> fixture : List.of(ForkOneFixture.class, ForkTwoFixture.class, ForkThreeFixture.class)) {
            assertThat(result.testsOf(fixture)).as("tests of %s", fixture.getSimpleName()).hasSize(2);
        }
        var summary = result.report().getResults().getSummary();
        assertThat(summary.getTests()).isEqualTo(6);
        assertThat(summary.getPassed()).isEqualTo(3);
        assertThat(summary.getFailed()).isEqualTo(3);
    }

    @org.junit.jupiter.api.Test
    void newBuildReplacesTheReportOfAnEarlierBuild() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(
            Jvm.listener(RetryRoundsFixture.class).withSystemProperty("ctrf.run.id", "build-1").withEnvironment("ENV_HEALTHY", "false"),
            Jvm.listener(RerunFixture.class).withSystemProperty("ctrf.run.id", "build-2"));

        assertThat(result.report().getRunId()).isEqualTo("build-2");
        assertThat(result.testsOf(RetryRoundsFixture.class)).isEmpty();
        assertThat(result.testsOf(RerunFixture.class)).hasSize(2);
        assertThat(result.tests()).allSatisfy(test -> assertThat(test.getRetries()).isNull());
        assertThat(result.report().getResults().getEnvironment().isHealthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void newCiRunReplacesTheReportOfAnEarlierCiRun() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(
            Jvm.listener(RetryRoundsFixture.class).withEnvironment("GITHUB_RUN_ID", "100").withEnvironment("GITHUB_RUN_ATTEMPT", "1"),
            Jvm.listener(RerunFixture.class).withEnvironment("GITHUB_RUN_ID", "101").withEnvironment("GITHUB_RUN_ATTEMPT", "1"));

        assertThat(result.report().getRunId()).isEqualTo("github-101-1");
        assertThat(result.testsOf(RetryRoundsFixture.class)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void sameCiRunMergesItsJvms() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(
            Jvm.listener(RetryRoundsFixture.class).withEnvironment("GITHUB_RUN_ID", "100").withEnvironment("GITHUB_RUN_ATTEMPT", "1"),
            Jvm.listener(RerunFixture.class).withEnvironment("GITHUB_RUN_ID", "100").withEnvironment("GITHUB_RUN_ATTEMPT", "1"));

        assertThat(result.report().getRunId()).isEqualTo("github-100-1");
        assertThat(result.testsOf(RetryRoundsFixture.class)).hasSize(2);
        assertThat(result.testsOf(RerunFixture.class)).hasSize(2);
    }

    @org.junit.jupiter.api.Test
    void secondExecutionInTheSameJvmKeepsTheFirstExecutionsResults() throws Exception {
        var result = ScenarioRunner.runListenerRerunningFailures(RerunFixture.class);

        Test stable = onlyTestNamed(result, "stable()");
        assertThat(stable.getStatus()).isEqualTo(PASSED);
        assertThat(stable.getRetries()).isNull();
        Test flaky = onlyTestNamed(result, "flakyOnce()");
        assertThat(flaky.getStatus()).isEqualTo(PASSED);
        assertThat(flaky.getRetries()).isEqualTo(1);
        assertThat(flaky.getFlaky()).isTrue();
        assertThat(result.report().getResults().getSummary().getFlaky()).isEqualTo(1);
    }

    private static Test onlyTestNamed(ScenarioRunner.Result result, String name) {
        var matches = result.tests().stream().filter(test -> name.equals(test.getName())).toList();
        assertThat(matches).as("entries named %s", name).hasSize(1);
        return matches.get(0);
    }
}

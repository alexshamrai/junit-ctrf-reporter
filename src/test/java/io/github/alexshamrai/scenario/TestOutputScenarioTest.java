package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.ScenarioRunner.Jvm;
import io.github.alexshamrai.scenario.fixtures.OutputFixture;
import io.github.alexshamrai.scenario.fixtures.OutputRetryFixture;
import io.github.alexshamrai.scenario.fixtures.OutputSetupFailsFixture;

import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * With JUnit's output capture on, a failed attempt keeps the output JUnit captured for it in {@code stdout} and
 * {@code stderr}; a passed one drops it (HP-13).
 */
class TestOutputScenarioTest {

    @org.junit.jupiter.api.Test
    void failedTestKeepsItsCapturedOutputAndAPassedOneDropsIt() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(capturing(OutputFixture.class));

        Test failed = named(result, "failsAfterPrinting()");
        assertThat(failed.getStdout()).containsExactly("out 1", "out 2");
        assertThat(failed.getStderr()).containsExactly("err 1");
        Test passed = named(result, "passesAfterPrinting()");
        assertThat(passed.getStdout()).isNull();
        assertThat(passed.getStderr()).isNull();
    }

    @org.junit.jupiter.api.Test
    void withoutCaptureThereIsNoOutput() throws Exception {
        var result = ScenarioRunner.runWithListener(OutputFixture.class);

        assertThat(result.tests()).allSatisfy(test -> {
            assertThat(test.getStdout()).isNull();
            assertThat(test.getStderr()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    void eachAttemptKeepsItsOwnOutput() throws Exception {
        // In the third round, the first round's output is read back from its retry attempt in the report
        var result = ScenarioRunner.runOneAfterAnother(
            capturing(OutputRetryFixture.class), capturing(OutputRetryFixture.class), capturing(OutputRetryFixture.class));

        Test test = named(result, "printsAndPassesFromTheSecondRound()");
        assertThat(test.getStatus()).isEqualTo(PASSED);
        assertThat(test.getStdout()).as("the final, passed attempt drops its output").isNull();
        assertThat(test.getRetryAttempts()).extracting(RetryAttempt::getStdout)
            .containsExactly(List.of("first round"), null);
    }

    @org.junit.jupiter.api.Test
    void outputOfAFailedClassSetupGoesToItsInitializationError() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(capturing(OutputSetupFailsFixture.class));

        assertThat(named(result, "initializationError").getStdout()).containsExactly("connecting to the database");
    }

    private static Jvm capturing(Class<?> fixture) {
        return Jvm.listener(fixture)
            .withSystemProperty("junit.platform.output.capture.stdout", "true")
            .withSystemProperty("junit.platform.output.capture.stderr", "true");
    }

    private static Test named(ScenarioRunner.Result result, String name) {
        var matches = result.tests().stream().filter(test -> name.equals(test.getName())).toList();
        assertThat(matches).as("entries named %s", name).hasSize(1);
        return matches.get(0);
    }
}

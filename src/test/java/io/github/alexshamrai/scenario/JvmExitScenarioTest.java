package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.ScenarioRunner.Jvm;
import io.github.alexshamrai.scenario.fixtures.JvmExitAfterFailedSetupFixture;
import io.github.alexshamrai.scenario.fixtures.JvmExitFixture;
import io.github.alexshamrai.scenario.fixtures.JvmExitInProviderFixture;
import io.github.alexshamrai.scenario.fixtures.JvmExitInSetupFixture;
import io.github.alexshamrai.scenario.fixtures.JvmTerminatedFixture;
import io.github.alexshamrai.scenario.fixtures.PassingFixture;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.OTHER;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A JVM that ends in the middle of its run, through {@code System.exit} or SIGTERM, still reports its tests: those
 * that finished, those that were running as other, and those that never started as skipped. A container that was
 * running without a test, as in {@code @BeforeAll}, gets an entry as other too (CB-8).
 */
class JvmExitScenarioTest {

    private static final String RUNNING = "The JVM exited while this test was running";
    private static final String NOT_STARTED = "The JVM exited before this test started";
    private static final String CONTAINER_RUNNING = "The JVM exited while this container was running";

    @org.junit.jupiter.api.Test
    void jvmThatExitsMidRunReportsItsTests() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(Jvm.listener(JvmExitFixture.class).exitingWith(3));

        assertExitFixtureReported(result.tests());
        assertThat(result.tests()).hasSize(5);
        var summary = result.report().getResults().getSummary();
        assertThat(summary.getPassed()).isEqualTo(1);
        assertThat(summary.getFailed()).isZero();
        assertThat(summary.getOther()).isEqualTo(1);
        assertThat(summary.getSkipped()).isEqualTo(3);
    }

    @org.junit.jupiter.api.Test
    void forkThatExitsMidRunKeepsItsTestsBesideTheOtherForks() throws Exception {
        var result = ScenarioRunner.runAtOnce(
            Jvm.listener(PassingFixture.class),
            Jvm.listener(JvmExitFixture.class).exitingWith(3));

        assertExitFixtureReported(result.tests().stream()
            .filter(test -> test.getFilePath().startsWith(JvmExitFixture.class.getName()))
            .toList());
        assertThat(result.testsOf(PassingFixture.class)).singleElement()
            .satisfies(test -> assertThat(test.getStatus()).isEqualTo(PASSED));
        assertThat(result.tests()).hasSize(6);
    }

    @org.junit.jupiter.api.Test
    void classWhoseSetupFailedBeforeTheExitKeepsOnlyItsInitializationError() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(Jvm.listener(JvmExitAfterFailedSetupFixture.class).exitingWith(3));

        // As in a run that finishes: JUnit never ran the test of the class whose setup failed
        assertThat(result.tests()).extracting(Test::getName, Test::getStatus).containsExactlyInAnyOrder(
            tuple("initializationError", FAILED),
            tuple("exitsTheJvm()", OTHER));
    }

    @org.junit.jupiter.api.Test
    void exitInAClassSetupAddsAnEntryForTheClass() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(Jvm.listener(JvmExitInSetupFixture.class).exitingWith(3));

        assertReported(result.tests(), "initializationError", OTHER, CONTAINER_RUNNING);
        assertReported(result.tests(), "neverStarts()", SKIPPED, NOT_STARTED);
        assertThat(result.tests()).hasSize(2);
    }

    @org.junit.jupiter.api.Test
    void exitInAParameterSourceAddsAnEntryForTheParameterizedTest() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(Jvm.listener(JvmExitInProviderFixture.class).exitingWith(3));

        assertReported(result.tests(), "usesData(int)", OTHER, CONTAINER_RUNNING);
        assertThat(result.tests()).hasSize(1);
    }

    @org.junit.jupiter.api.Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Windows ends a process without running its shutdown hooks")
    void jvmTerminatedMidTestReportsTheTestAsOther() throws Exception {
        var result = ScenarioRunner.runAndTerminate(Jvm.listener(JvmTerminatedFixture.class), JvmTerminatedFixture.WAITING);

        assertReported(result.tests(), "passes()", PASSED, null);
        assertReported(result.tests(), "waitsToBeTerminated()", OTHER, RUNNING);
        assertReported(result.tests(), "neverStarts()", SKIPPED, NOT_STARTED);
        assertThat(result.tests()).hasSize(3);
    }

    private static void assertExitFixtureReported(List<Test> tests) {
        assertReported(tests, "passes()", PASSED, null);
        assertReported(tests, "exitsTheJvm()", OTHER, RUNNING);
        assertReported(tests, "neverStarts()", SKIPPED, NOT_STARTED);
        assertReported(tests, "neverStartsEither(int)", SKIPPED, NOT_STARTED);
        assertReported(tests, "neverStartsInANestedClass()", SKIPPED, NOT_STARTED);
    }

    private static void assertReported(List<Test> tests, String name, Test.TestStatus status, String message) {
        var matches = tests.stream().filter(test -> name.equals(test.getName())).toList();
        assertThat(matches).as("entries named %s", name).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).as("status of %s", name).isEqualTo(status);
            assertThat(test.getMessage()).as("message of %s", name).isEqualTo(message);
        });
    }
}

package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.ScenarioRunner.Jvm;
import io.github.alexshamrai.scenario.fixtures.ParallelFixture;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JUnit's parallel execution calls the listener from many threads at once (TI-1). Each test is reported once,
 * with the counts JUnit itself gives, and the environment health a test set on one of the threads.
 */
class ParallelExecutionScenarioTest {

    private static final Map<String, String> PARALLEL = Map.of(
        "junit.jupiter.execution.parallel.enabled", "true",
        "junit.jupiter.execution.parallel.mode.default", "concurrent",
        "junit.jupiter.execution.parallel.mode.classes.default", "concurrent",
        "junit.jupiter.execution.parallel.config.strategy", "fixed",
        "junit.jupiter.execution.parallel.config.fixed.parallelism", "8");

    @org.junit.jupiter.api.Test
    void listenerReportsEveryTestOnceUnderParallelExecution() throws Exception {
        assertReportedOnceWithJunitsCounts(ScenarioRunner.runOneAfterAnother(parallel(Jvm.listener(ParallelFixture.class))));
    }

    private static Jvm parallel(Jvm jvm) {
        Jvm configured = jvm;
        for (var setting : PARALLEL.entrySet()) {
            configured = configured.withSystemProperty(setting.getKey(), setting.getValue());
        }
        return configured;
    }

    private static void assertReportedOnceWithJunitsCounts(ScenarioRunner.Result result) {
        var junit = result.junitCounts();
        var summary = result.report().getResults().getSummary();

        assertThat(junit.get("found")).as("JUnit's test count").isEqualTo(802L);
        assertThat(result.tests()).extracting(Test::getTestId).doesNotHaveDuplicates().hasSize(802);
        assertThat((long) summary.getPassed()).isEqualTo(junit.get("succeeded"));
        assertThat((long) summary.getFailed()).isEqualTo(junit.get("failed")).isEqualTo(10L);
        assertThat((long) summary.getSkipped()).isEqualTo(junit.get("aborted") + junit.get("skipped"));
        assertThat(result.tests()).extracting(Test::getThreadId).as("the threads the tests ran on").doesNotContainNull()
            .satisfies(threads -> assertThat(Set.copyOf(threads)).hasSizeGreaterThan(1));
        assertThat(result.report().getResults().getEnvironment().isHealthy()).isFalse();
    }
}

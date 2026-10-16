package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.AssumptionFixture;
import io.github.alexshamrai.scenario.fixtures.ExtAbortedClassSetupFixture;
import io.github.alexshamrai.scenario.fixtures.ExtAbortedClassTeardownFixture;
import io.github.alexshamrai.scenario.fixtures.ExtAssumptionFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A test aborted by a failed assumption is reported as skipped, by both entry points (HP-7). A class whose setup or
 * teardown aborts isn't a failure either.
 */
class AbortedTestScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerReportsAFailedAssumptionAsSkipped() throws Exception {
        assertReportedAsSkipped(ScenarioRunner.runWithListener(AssumptionFixture.class), AssumptionFixture.class);
    }

    @org.junit.jupiter.api.Test
    void extensionReportsAFailedAssumptionAsSkipped() throws Exception {
        assertReportedAsSkipped(ScenarioRunner.runWithExtension(ExtAssumptionFixture.class), ExtAssumptionFixture.class);
    }

    @org.junit.jupiter.api.Test
    void extensionReportsAClassWhoseSetupAbortedAsOneSkippedEntry() throws Exception {
        var result = ScenarioRunner.runWithExtension(ExtAbortedClassSetupFixture.class);

        assertThat(result.junitCounts()).containsEntry("failed", 0L).containsEntry("containersFailed", 0L);
        assertThat(result.tests())
            .extracting(Test::getName, Test::getStatus, Test::getRawStatus)
            .containsExactly(tuple("initializationError", SKIPPED, "aborted"));
        assertThat(result.tests().get(0).getMessage()).contains("no database on this agent");
        assertThat(result.report().getResults().getSummary().getFailed()).isZero();
    }

    @org.junit.jupiter.api.Test
    void extensionReportsNothingMoreForAClassWhoseTeardownAbortedAfterItsTestsRan() throws Exception {
        var result = ScenarioRunner.runWithExtension(ExtAbortedClassTeardownFixture.class);

        assertThat(result.junitCounts()).containsEntry("succeeded", 1L).containsEntry("containersFailed", 0L);
        assertThat(result.tests())
            .extracting(Test::getName, Test::getStatus)
            .containsExactly(tuple("passes()", PASSED));
        assertThat(result.report().getResults().getSummary().getFailed()).isZero();
    }

    private static void assertReportedAsSkipped(ScenarioRunner.Result result, Class<?> fixture) {
        assertThat(result.junitCounts()).containsEntry("aborted", 1L);
        Test aborted = result.testsOf(fixture).stream()
            .filter(test -> "requiresDocker()".equals(test.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(aborted.getStatus()).isEqualTo(SKIPPED);
        assertThat(aborted.getRawStatus()).isEqualTo("aborted");
        assertThat(aborted.getMessage()).contains("Docker not available on this agent");

        var summary = result.report().getResults().getSummary();
        assertThat(summary.getFailed()).isZero();
        assertThat(summary.getSkipped()).isEqualTo(1);
    }
}

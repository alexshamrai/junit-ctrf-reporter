package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.AssumptionFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A test aborted by a failed assumption is reported as skipped (HP-7).
 */
class AbortedTestScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerReportsAFailedAssumptionAsSkipped() throws Exception {
        assertReportedAsSkipped(ScenarioRunner.runWithListener(AssumptionFixture.class), AssumptionFixture.class);
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

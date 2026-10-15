package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.ScenarioRunner.Jvm;
import io.github.alexshamrai.scenario.fixtures.PassingFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration from a JVM's real environment variables (MP-9).
 */
class ConfigurationScenarioTest {

    @org.junit.jupiter.api.Test
    void ctrfEnvironmentVariablesConfigureTheReport() throws Exception {
        // ScenarioRunner passes -Dctrf.report.path and reads the report there, so CTRF_REPORT_PATH must lose to it
        var result = ScenarioRunner.runOneAfterAnother(Jvm.listener(PassingFixture.class)
            .withEnvironment("CTRF_REPORT_NAME", "from CTRF_REPORT_NAME")
            .withEnvironment("CTRF_REPORT_PATH", "not-the-report.json")
            .withEnvironment("CTRF_MAX_MESSAGE_LENGTH", "abc"));

        assertThat(result.report().getResults().getEnvironment().getReportName()).isEqualTo("from CTRF_REPORT_NAME");
        assertThat(result.tests()).extracting(Test::getName).containsExactly("passes()");
        assertThat(result.log()).contains("ctrf.max.message.length must be a whole number");
    }
}

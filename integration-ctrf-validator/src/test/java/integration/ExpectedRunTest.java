package integration;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectedRunTest {

    @Test
    void plainLocalRunExpectsTheModulePropertiesOneThreadAndAHealthyListener() {
        var expected = ExpectedRun.from(moduleProperties(), new Properties(), null);

        assertThat(expected).isEqualTo(new ExpectedRun(1, "feature-branch", 123L, null, "http://ci.example.com/build/123", true,
            ExpectedRun.LISTENER_ENTRY_POINT));
    }

    @Test
    void systemPropertiesOverrideTheModuleProperties() {
        var system = properties("threads", "2", "ctrf.build.name", "system-build", "ctrf.build.number", "777",
            "ctrf.build.url", "https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/1");

        var expected = ExpectedRun.from(moduleProperties(), system, null);

        assertThat(expected.threads()).isEqualTo(2);
        assertThat(expected.buildName()).isEqualTo("system-build");
        assertThat(expected.buildNumber()).isEqualTo(777L);
        assertThat(expected.buildUrl()).isEqualTo("https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/1");
    }

    @Test
    void envHealthyFalseInAnyCaseMeansUnhealthy() {
        assertThat(ExpectedRun.from(moduleProperties(), new Properties(), "FALSE").healthy()).isFalse();
        assertThat(ExpectedRun.from(moduleProperties(), new Properties(), "true").healthy()).isTrue();
    }

    @Test
    void nonNumericBuildNumberIsExpectedAsBuildId() {
        var expected = ExpectedRun.from(moduleProperties(), properties("ctrf.build.number", " 2026.10.15-3 "), null);

        assertThat(expected.buildNumber()).isNull();
        assertThat(expected.buildId()).isEqualTo("2026.10.15-3");
    }

    @Test
    void threadsThatArentANumberMeanOne() {
        assertThat(ExpectedRun.from(moduleProperties(), properties("threads", "many"), null).threads()).isEqualTo(1);
    }

    private static Properties moduleProperties() {
        return properties("ctrf.build.name", "feature-branch", "ctrf.build.number", "123",
            "ctrf.build.url", "http://ci.example.com/build/123");
    }

    private static Properties properties(String... keysAndValues) {
        var properties = new Properties();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }
        return properties;
    }
}

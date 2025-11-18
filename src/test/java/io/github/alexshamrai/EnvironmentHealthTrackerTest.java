package io.github.alexshamrai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentHealthTrackerTest {

    @BeforeEach
    void setUp() {
        // Reset health state before each test to ensure clean state
        CtrfReportManager.getInstance().resetEnvironmentHealthForTesting();
    }

    @AfterEach
    void cleanup() {
        // Reset health state after each test to avoid test interference
        CtrfReportManager.getInstance().resetEnvironmentHealthForTesting();
    }

    @Test
    @DisplayName("isEnvironmentHealthy should return true by default")
    void isEnvironmentHealthy_defaultsToTrue() {
        assertThat(EnvironmentHealthTracker.isEnvironmentHealthy())
            .as("Environment should be healthy by default")
            .isTrue();
    }

    @Test
    @DisplayName("markEnvironmentUnhealthy should set environment to unhealthy")
    void markEnvironmentUnhealthy_setsToFalse() {
        assertThat(EnvironmentHealthTracker.isEnvironmentHealthy())
            .as("Environment should start healthy")
            .isTrue();

        EnvironmentHealthTracker.markEnvironmentUnhealthy();

        assertThat(EnvironmentHealthTracker.isEnvironmentHealthy())
            .as("Environment should be unhealthy after marking")
            .isFalse();
    }

    @Test
    @DisplayName("markEnvironmentUnhealthy should be idempotent")
    void markEnvironmentUnhealthy_isIdempotent() {
        EnvironmentHealthTracker.markEnvironmentUnhealthy();
        assertThat(EnvironmentHealthTracker.isEnvironmentHealthy()).isFalse();

        // Call again - should still be unhealthy
        EnvironmentHealthTracker.markEnvironmentUnhealthy();
        assertThat(EnvironmentHealthTracker.isEnvironmentHealthy()).isFalse();
    }

    @Test
    @DisplayName("isEnvironmentVariableUnhealthy should check ENV_HEALTHY environment variable")
    void isEnvironmentVariableUnhealthy_checksEnvVariable() {
        var result = EnvironmentHealthTracker.isEnvironmentVariableUnhealthy();

        var envHealthy = System.getenv("ENV_HEALTHY");
        var expected = envHealthy != null && "false".equalsIgnoreCase(envHealthy);

        assertThat(result)
            .as("isEnvironmentVariableUnhealthy should match ENV_HEALTHY environment variable")
            .isEqualTo(expected);
    }

    @Test
    @DisplayName("isEnvironmentVariableUnhealthy should return false when ENV_HEALTHY is not set")
    void isEnvironmentVariableUnhealthy_returnsFalseWhenNotSet() {
        var envHealthy = System.getenv("ENV_HEALTHY");

        if (envHealthy == null || !"false".equalsIgnoreCase(envHealthy)) {
            assertThat(EnvironmentHealthTracker.isEnvironmentVariableUnhealthy())
                .as("isEnvironmentVariableUnhealthy should return false when ENV_HEALTHY is not 'false'")
                .isFalse();
        }
    }
}
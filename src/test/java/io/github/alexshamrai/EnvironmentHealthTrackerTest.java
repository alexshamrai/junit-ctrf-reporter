package io.github.alexshamrai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Each test uses a manager of its own, never the JVM's default one, which reports this test run: the tests share no
 * state, need no cleanup, and can run in any order or in parallel.
 */
class EnvironmentHealthTrackerTest {

    @Test
    @DisplayName("A manager created healthy stays healthy until a test marks it unhealthy")
    void environmentIsHealthyUntilMarkedUnhealthy() {
        var manager = manager(true);

        assertThat(manager.isEnvironmentHealthyInternal()).isTrue();
        manager.markEnvironmentUnhealthyInternal();
        assertThat(manager.isEnvironmentHealthyInternal()).isFalse();
    }

    @Test
    @DisplayName("A manager created unhealthy, as with ENV_HEALTHY=false, is unhealthy from the start")
    void environmentCanStartUnhealthy() {
        assertThat(manager(false).isEnvironmentHealthyInternal()).isFalse();
    }

    @Test
    @DisplayName("Marking the environment unhealthy again keeps it unhealthy")
    void markingUnhealthyIsIdempotent() {
        var manager = manager(true);

        manager.markEnvironmentUnhealthyInternal();
        manager.markEnvironmentUnhealthyInternal();

        assertThat(manager.isEnvironmentHealthyInternal()).isFalse();
    }

    @Test
    @DisplayName("The static methods mark and read the health of the JVM's default manager")
    void staticMethodsUseTheDefaultManager() {
        var manager = manager(true);
        try (MockedStatic<CtrfReportManager> defaultManager = mockStatic(CtrfReportManager.class)) {
            defaultManager.when(CtrfReportManager::getInstance).thenReturn(manager);

            assertThat(EnvironmentHealthTracker.isEnvironmentHealthy()).isTrue();
            EnvironmentHealthTracker.markEnvironmentUnhealthy();

            assertThat(EnvironmentHealthTracker.isEnvironmentHealthy()).isFalse();
            assertThat(manager.isEnvironmentHealthyInternal()).isFalse();
        }
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"false", "FALSE", "False"})
    @DisplayName("ENV_HEALTHY=false, in any case, means unhealthy")
    void falseMeansUnhealthy(String value) {
        assertThat(EnvironmentHealthTracker.isUnhealthy(value)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"true", "no", " false"})
    @DisplayName("ENV_HEALTHY unset, or set to anything but false, means healthy")
    void anythingElseMeansHealthy(String value) {
        assertThat(EnvironmentHealthTracker.isUnhealthy(value)).isFalse();
    }

    private static CtrfReportManager manager(boolean healthy) {
        return new CtrfReportManager(mock(TestProcessor.class), mock(CtrfReportFileService.class), generator -> null,
            "run-1", healthy);
    }
}

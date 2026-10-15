package integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationRunTest {

    @TempDir
    Path tempDir;

    @Test
    void findsTheModuleAndTheEventLogNextToTheReport() throws IOException {
        var report = report(tempDir.resolve("integration-tests-extension/build/test-results/ctrf-report.json"));

        var run = IntegrationRun.locate(report.toString(), null);

        assertThat(run.module()).isEqualTo(IntegrationRun.EXTENSION_MODULE);
        assertThat(run.isListener()).isFalse();
        assertThat(run.moduleDir()).isEqualTo(tempDir.resolve("integration-tests-extension"));
        assertThat(run.eventsDir()).isEqualTo(report.getParent().resolve("junit-platform"));
        assertThat(run.reportFile().toPath()).isEqualTo(report);
    }

    @Test
    void eventsDirPropertyOverridesTheDefault() throws IOException {
        var report = report(tempDir.resolve("integration-tests-listener/build/test-results/ctrf-report.json"));
        var events = tempDir.resolve("elsewhere");

        var run = IntegrationRun.locate(report.toString(), events.toString());

        assertThat(run.isListener()).isTrue();
        assertThat(run.eventsDir()).isEqualTo(events);
    }

    @Test
    void missingReportFailsWithTheCommandToRun() {
        var missing = tempDir.resolve("integration-tests-listener/build/test-results/ctrf-report.json");

        assertThatThrownBy(() -> IntegrationRun.locate(missing.toString(), null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No report at " + missing)
            .hasMessageContaining("./gradlew :integration-tests-listener:test")
            .hasMessageContaining("-Dctrf.report.path=");
    }

    @Test
    void reportOutsideTheModulesFailsClearly() throws IOException {
        var report = report(tempDir.resolve("copies/ctrf-report.json"));

        assertThatThrownBy(() -> IntegrationRun.locate(report.toString(), null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Can't tell which integration module wrote " + report)
            .hasMessageContaining("integration-tests-listener/build")
            .hasMessageContaining("integration-tests-extension/build");
    }

    @Test
    void defaultIsTheListenerReportSeenFromTheValidatorModule() {
        assertThat(IntegrationRun.DEFAULT_REPORT_PATH).isEqualTo("../integration-tests-listener/build/test-results/ctrf-report.json");
    }

    private static Path report(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, "{}");
    }
}

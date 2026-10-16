package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ScenarioRunnerTest {

    private static final String REPORT = """
        {
          "reportFormat": "CTRF",
          "specVersion": "0.2.0",
          "results": {
            "tool": {"name": "JUnit"},
            "summary": {"tests": 1, "passed": 1, "failed": 0, "skipped": 0, "pending": 0, "other": 0, "start": 1000, "stop": 1100},
            "tests": [{"name": "pays()", "status": "passed", "duration": 10, "filePath": "com.example.PaymentTest"}]
          }
        }
        """;

    @TempDir
    Path directory;

    @org.junit.jupiter.api.Test
    @DisplayName("A report that breaks the official CTRF schema fails the scenario, naming the error")
    void reportBreakingTheSchemaFailsTheScenario() throws IOException {
        Path report = write(REPORT.replace("\"filePath\"", "\"filepath\""));

        assertThatThrownBy(() -> ScenarioRunner.readReport(report, "the scenario JVM's output"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("official CTRF schema")
            .hasMessageContaining("filepath")
            .hasMessageContaining(report.toString())
            .hasMessageContaining("the scenario JVM's output");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report that matches the official CTRF schema is read")
    void reportMatchingTheSchemaIsRead() throws IOException {
        var report = ScenarioRunner.readReport(write(REPORT), "");

        assertThat(report.getResults().getTests()).extracting(Test::getName, Test::getFilePath)
            .containsExactly(tuple("pays()", "com.example.PaymentTest"));
    }

    private Path write(String content) throws IOException {
        return Files.writeString(directory.resolve("ctrf-report.json"), content);
    }
}

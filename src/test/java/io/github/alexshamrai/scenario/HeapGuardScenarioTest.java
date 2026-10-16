package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.CtrfJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the heap that a large report needs from growing again. 0.5.1 held the report as text and as a tree of maps,
 * and needed 280 to 328 MB for 100,000 tests. Here each JVM records its tests under a fixed maximum heap, and must
 * still write a complete report.
 */
class HeapGuardScenarioTest {

    /**
     * The most this code needs on Java 17, 21 or 25 for any of the three cases, plus about 25%. Measured 2026-10-16
     * with the heap benchmark of the 0.5.2 design, under G1, which the guard JVMs therefore use: on a machine with
     * one CPU or little memory, the JVM would otherwise choose another collector.
     */
    private static final int MAX_HEAP_MB = 176;
    private static final int TESTS = 100_000;

    @TempDir
    Path directory;

    @Test
    @DisplayName("A first write of 100,000 tests fits the heap")
    void firstWriteFitsTheHeap() throws Exception {
        Path report = directory.resolve("ctrf-report.json");

        record(report, "first-run", TESTS, 0);

        CtrfJson written = read(report);
        assertThat(written.getRunId()).isEqualTo("first-run");
        assertThat(written.getResults().getTests()).hasSize(TESTS);
    }

    @Test
    @DisplayName("A rerun that replaces another run's report of 100,000 tests fits the heap")
    void replacingAnotherRunsReportFitsTheHeap() throws Exception {
        Path report = directory.resolve("ctrf-report.json");
        record(report, "earlier-run", TESTS, 0);

        record(report, "rerun", TESTS, TESTS);

        CtrfJson written = read(report);
        assertThat(written.getRunId()).isEqualTo("rerun");
        assertThat(written.getResults().getTests()).hasSize(TESTS);
    }

    @Test
    @DisplayName("Two forks of 50,000 tests each, merged into one report, fit the heap")
    void mergingForksFitsTheHeap() throws Exception {
        Path report = directory.resolve("ctrf-report.json");
        record(report, "forks", TESTS / 2, 0);

        record(report, "forks", TESTS / 2, TESTS / 2);

        assertThat(read(report).getResults().getTests()).hasSize(TESTS);
    }

    private void record(Path report, String runId, int count, int first) throws IOException, InterruptedException {
        Path output = directory.resolve(runId + "-" + first + ".txt");
        Process process = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Xmx" + MAX_HEAP_MB + "m",
            "-XX:+UseG1GC",
            "-cp", System.getProperty("java.class.path"),
            "-Dctrf.report.path=" + report,
            "-Dctrf.run.id=" + runId,
            HeapGuardMain.class.getName(), String.valueOf(count), String.valueOf(first))
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
        if (!process.waitFor(3, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("The guard JVM timed out:\n" + Files.readString(output));
        }
        assertThat(process.exitValue()).as(Files.readString(output)).isZero();
    }

    private static CtrfJson read(Path report) {
        return JsonMapper.builder().build().readValue(report.toFile(), CtrfJson.class);
    }
}

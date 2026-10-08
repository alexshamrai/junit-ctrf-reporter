package io.github.alexshamrai.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs fixture classes in a separate JVM and returns the CTRF report they produced.
 *
 * <p>The reporter keeps its state in a JVM-wide singleton that this test run itself reports into,
 * so running fixtures in-process would mix the two runs.
 */
final class ScenarioRunner {

    private ScenarioRunner() {
    }

    static Result runWithListener(Class<?>... fixtures) throws IOException, InterruptedException {
        return run("listener", fixtures);
    }

    static Result runWithExtension(Class<?>... fixtures) throws IOException, InterruptedException {
        return run("extension", fixtures);
    }

    private static Result run(String mode, Class<?>... fixtures) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("ctrf-scenario");
        Path report = workDir.resolve("ctrf-report.json");
        Path output = workDir.resolve("output.txt");

        List<String> command = new ArrayList<>(List.of(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"),
            "-Dctrf.report.path=" + report,
            ScenarioMain.class.getName(),
            mode));
        Arrays.stream(fixtures).map(Class::getName).forEach(command::add);

        var processBuilder = new ProcessBuilder(command)
            .directory(workDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile());
        processBuilder.environment().remove("ENV_HEALTHY");
        Process process = processBuilder.start();
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("Scenario JVM timed out:\n" + Files.readString(output));
        }
        String log = Files.readString(output);
        if (process.exitValue() != 0 || !Files.exists(report)) {
            throw new IllegalStateException("Scenario JVM exited with " + process.exitValue() + " and wrote no report:\n" + log);
        }
        return new Result(new ObjectMapper().readValue(report.toFile(), CtrfJson.class), junitCounts(log), log);
    }

    private static Map<String, Long> junitCounts(String log) {
        String summaryLine = log.lines()
            .filter(line -> line.startsWith(ScenarioMain.SUMMARY_PREFIX))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No JUnit summary in scenario output:\n" + log));
        Map<String, Long> counts = new HashMap<>();
        for (String pair : summaryLine.substring(ScenarioMain.SUMMARY_PREFIX.length()).split(" ")) {
            String[] keyAndValue = pair.split("=");
            counts.put(keyAndValue[0], Long.parseLong(keyAndValue[1]));
        }
        return counts;
    }

    /**
     * The CTRF report a scenario produced, and JUnit's own counts for the same run.
     */
    record Result(CtrfJson report, Map<String, Long> junitCounts, String log) {

        List<Test> tests() {
            return report.getResults().getTests();
        }

        List<Test> testsOf(Class<?> fixture) {
            return tests().stream().filter(test -> fixture.getName().equals(test.getFilepath())).toList();
        }
    }
}

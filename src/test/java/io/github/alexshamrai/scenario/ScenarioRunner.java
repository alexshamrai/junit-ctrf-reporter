package io.github.alexshamrai.scenario;

import io.github.alexshamrai.CtrfSchema;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs fixture classes in separate JVMs and returns the CTRF report they produced.
 *
 * <p>The reporter keeps its state in a JVM-wide singleton that this test run itself reports into,
 * so running fixtures in-process would mix the two runs. All JVMs of one call share a new working directory
 * and report file, as the JVMs of one build do.
 */
final class ScenarioRunner {

    /**
     * The CI variables the reporter derives a run id from. They are removed from the scenario JVMs, so that a
     * scenario behaves the same on a developer machine and in CI.
     */
    private static final List<String> CI_VARIABLES = List.of(
        "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "CI_JOB_ID", "BUILD_TAG", "BUILD_BUILDID", "SYSTEM_JOBATTEMPT",
        "CIRCLE_WORKFLOW_JOB_ID", "BUILDKITE_JOB_ID");

    private ScenarioRunner() {
    }

    static Result runWithListener(Class<?>... fixtures) throws IOException, InterruptedException {
        return runOneAfterAnother(Jvm.listener(fixtures));
    }

    /**
     * Runs the fixtures {@code rounds} times through the listener, each round in a new JVM that shares the
     * working directory and the report file with the earlier rounds, as the Gradle test-retry plugin does.
     */
    static Result runRoundsWithListener(int rounds, Class<?>... fixtures) throws IOException, InterruptedException {
        return runOneAfterAnother(repeat(Jvm.listener(fixtures), rounds));
    }

    /**
     * Runs the fixtures through the listener in one JVM, then runs the failed tests again in the same JVM, as
     * Surefire's {@code rerunFailingTestsCount} does.
     */
    static Result runListenerRerunningFailures(Class<?>... fixtures) throws IOException, InterruptedException {
        return runOneAfterAnother(new Jvm("listener-rerun", List.of(fixtures), Map.of(), Map.of()));
    }

    /**
     * Starts the JVMs one after another, each after the previous one finished, and returns the report and the
     * JUnit counts of the last one.
     */
    static Result runOneAfterAnother(Jvm... jvms) throws IOException, InterruptedException {
        Workspace workspace = Workspace.create();
        String log = "";
        for (int i = 0; i < jvms.length; i++) {
            log = workspace.await(workspace.start(jvms[i], i), i, jvms[i].exitCode());
        }
        return workspace.result(junitCountsOf(jvms[jvms.length - 1], log), log);
    }

    /**
     * Starts all JVMs at once, as parallel forks are, waits for all of them, and returns the report with the
     * JUnit counts of all JVMs added up.
     */
    static Result runAtOnce(Jvm... jvms) throws IOException, InterruptedException {
        Workspace workspace = Workspace.create();
        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < jvms.length; i++) {
            processes.add(workspace.start(jvms[i], i));
        }
        Map<String, Long> counts = new HashMap<>();
        StringBuilder logs = new StringBuilder();
        for (int i = 0; i < processes.size(); i++) {
            String log = workspace.await(processes.get(i), i, jvms[i].exitCode());
            junitCountsOf(jvms[i], log).forEach((key, value) -> counts.merge(key, value, Long::sum));
            logs.append(log);
        }
        return workspace.result(counts, logs.toString());
    }

    /**
     * Starts the JVM, waits until its output contains {@code marker}, and then terminates it, as a build tool
     * terminates a fork that timed out: with SIGTERM on Linux and macOS. The JVM ends before JUnit's summary, so the
     * result has no JUnit counts.
     */
    static Result runAndTerminate(Jvm jvm, String marker) throws IOException, InterruptedException {
        Workspace workspace = Workspace.create();
        Process process = workspace.start(jvm, 0);
        workspace.awaitOutput(process, 0, marker);
        process.destroy();
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("Scenario JVM 0 didn't end when terminated:\n" + workspace.log(0));
        }
        return workspace.result(Map.of(), workspace.log(0));
    }

    /**
     * Reads the report that the scenario JVMs wrote, which must match the official CTRF schema; {@code log} is their
     * output, for the failure message.
     */
    static CtrfJson readReport(Path report, String log) throws IOException {
        if (!Files.exists(report)) {
            throw new IllegalStateException("The scenario JVMs wrote no report:\n" + log);
        }
        String json = Files.readString(report);
        List<String> errors = CtrfSchema.errorsOf(json);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("The report " + report + " breaks the official CTRF schema:\n"
                + String.join("\n", errors) + "\n" + log);
        }
        return JsonMapper.builder().build().readValue(json, CtrfJson.class);
    }

    private static Jvm[] repeat(Jvm jvm, int times) {
        Jvm[] jvms = new Jvm[times];
        Arrays.fill(jvms, jvm);
        return jvms;
    }

    /**
     * JUnit's counts from the JVM's output; none for a JVM that is expected to exit before JUnit's summary.
     */
    private static Map<String, Long> junitCountsOf(Jvm jvm, String log) {
        return jvm.exitCode() == 0 ? junitCounts(log) : Map.of();
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
     * One JVM to start: how it runs ("listener" or "listener-rerun", see {@link ScenarioMain}),
     * the fixtures it runs, the system properties and environment variables it gets in addition, and the exit code
     * it is expected to end with: 0, unless a fixture ends the JVM itself.
     */
    record Jvm(String mode, List<Class<?>> fixtures, Map<String, String> systemProperties, Map<String, String> environment,
               int exitCode) {

        Jvm(String mode, List<Class<?>> fixtures, Map<String, String> systemProperties, Map<String, String> environment) {
            this(mode, fixtures, systemProperties, environment, 0);
        }

        static Jvm listener(Class<?>... fixtures) {
            return new Jvm("listener", List.of(fixtures), Map.of(), Map.of());
        }

        Jvm withSystemProperty(String key, String value) {
            Map<String, String> properties = new LinkedHashMap<>(systemProperties);
            properties.put(key, value);
            return new Jvm(mode, fixtures, properties, environment, exitCode);
        }

        Jvm withEnvironment(String key, String value) {
            Map<String, String> variables = new LinkedHashMap<>(environment);
            variables.put(key, value);
            return new Jvm(mode, fixtures, systemProperties, variables, exitCode);
        }

        /**
         * The same JVM, expected to end with {@code code}, as a fixture that calls {@code System.exit(code)} makes it.
         */
        Jvm exitingWith(int code) {
            return new Jvm(mode, fixtures, systemProperties, environment, code);
        }
    }

    /**
     * A new working directory and report file, shared by the JVMs of one scenario.
     */
    private record Workspace(Path directory, Path report) {

        static Workspace create() throws IOException {
            Path directory = Files.createTempDirectory("ctrf-scenario");
            return new Workspace(directory, directory.resolve("ctrf-report.json"));
        }

        Process start(Jvm jvm, int index) throws IOException {
            List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                "-Dctrf.report.path=" + report));
            jvm.systemProperties().forEach((key, value) -> command.add("-D" + key + "=" + value));
            command.add(ScenarioMain.class.getName());
            command.add(jvm.mode());
            jvm.fixtures().stream().map(Class::getName).forEach(command::add);

            var processBuilder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output(index).toFile());
            Map<String, String> environment = processBuilder.environment();
            environment.remove("ENV_HEALTHY");
            CI_VARIABLES.forEach(environment::remove);
            environment.putAll(jvm.environment());
            return processBuilder.start();
        }

        String await(Process process, int index, int expectedExitCode) throws IOException, InterruptedException {
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("Scenario JVM " + index + " timed out:\n" + log(index));
            }
            String log = log(index);
            if (process.exitValue() != expectedExitCode) {
                throw new IllegalStateException("Scenario JVM " + index + " exited with " + process.exitValue()
                    + " instead of " + expectedExitCode + ":\n" + log);
            }
            return log;
        }

        void awaitOutput(Process process, int index, String marker) throws IOException, InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
            while (!log(index).contains(marker)) {
                if (!process.isAlive() || System.nanoTime() > deadline) {
                    process.destroyForcibly();
                    throw new IllegalStateException("Scenario JVM " + index + " never printed \"" + marker + "\":\n"
                        + log(index));
                }
                Thread.sleep(50);
            }
        }

        String log(int index) throws IOException {
            return Files.readString(output(index));
        }

        Result result(Map<String, Long> junitCounts, String log) throws IOException {
            return new Result(readReport(report, log), junitCounts, log);
        }

        private Path output(int index) {
            return directory.resolve("output-" + index + ".txt");
        }
    }

    /**
     * The CTRF report a scenario produced, and JUnit's own counts for the same run.
     */
    record Result(CtrfJson report, Map<String, Long> junitCounts, String log) {

        List<Test> tests() {
            return report.getResults().getTests();
        }

        List<Test> testsOf(Class<?> fixture) {
            return tests().stream().filter(test -> fixture.getName().equals(test.getFilePath())).toList();
        }
    }
}

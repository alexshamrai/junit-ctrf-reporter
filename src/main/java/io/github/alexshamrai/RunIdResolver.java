package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resolves the run identifier ({@code runId}, CTRF section 5.4) that decides which JVMs share one report.
 * <p>
 * The JVMs of one build, such as parallel forks and retry rounds, resolve the same value, so their results are
 * merged into one report. A new build resolves a new value, so its report replaces the previous one. Sources,
 * first match wins:
 * <ol>
 *   <li>{@code ctrf.run.id}, used as is;</li>
 *   <li>a CI variable (GitHub Actions, GitLab, Jenkins, Azure Pipelines, CircleCI, Buildkite);</li>
 *   <li>the nearest ancestor process that is a JVM, typically the build tool: Gradle's daemon or Maven's JVM.
 *       Surefire starts each fork through its own shell, so the direct parent is not enough;</li>
 *   <li>this JVM alone.</li>
 * </ol>
 */
final class RunIdResolver {

    private static final int MAX_ANCESTORS = 32;

    private static final List<CiSource> CI_SOURCES = List.of(
        new CiSource("github-", List.of("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT")),
        new CiSource("gitlab-", List.of("CI_JOB_ID")),
        new CiSource("", List.of("BUILD_TAG")),
        new CiSource("azure-", List.of("BUILD_BUILDID", "SYSTEM_JOBATTEMPT")),
        new CiSource("circleci-", List.of("CIRCLE_WORKFLOW_JOB_ID")),
        new CiSource("buildkite-", List.of("BUILDKITE_JOB_ID"))
    );

    private RunIdResolver() {
        // Utility class - prevent instantiation
    }

    /**
     * Resolves the run id of this JVM from its configuration, environment and ancestor processes.
     *
     * @param configReader the configuration, for {@code ctrf.run.id}
     * @return the run id
     */
    static String resolve(ConfigReader configReader) {
        return resolve(configReader, ProcessHandle::current, System::getenv);
    }

    /**
     * Resolves the run id like {@link #resolve(ConfigReader)}, with the process and environment given. Never
     * throws: if inspecting the process or the environment fails, for example because a security manager denies
     * it, the result is {@code ctrf.run.id} if set, otherwise an id for this JVM alone.
     *
     * @param configReader the configuration, for {@code ctrf.run.id}
     * @param self         gives this JVM's process
     * @param environment  gives the environment variables
     * @return the run id
     */
    static String resolve(ConfigReader configReader, Supplier<ProcessHandle> self, Supplier<Map<String, String>> environment) {
        try {
            ProcessHandle process = self.get();
            String start = process.info().startInstant().map(instant -> String.valueOf(instant.toEpochMilli())).orElse("0");
            String ownRunId = "jvm-" + process.pid() + "-" + start + "-" + UUID.randomUUID().toString().substring(0, 8);
            return resolve(configReader.getRunId(), environment.get(), ancestorsOf(process), ownRunId);
        } catch (RuntimeException e) {
            String configured = configReader.getRunId();
            return isBlank(configured) ? "jvm-" + UUID.randomUUID() : configured;
        }
    }

    /**
     * Resolves a run id from the given sources; see the class documentation for the order.
     *
     * @param configured  the value of {@code ctrf.run.id}, or {@code null}
     * @param environment the environment variables
     * @param ancestors   the ancestor processes, nearest first
     * @param ownRunId    the run id to use when no other source gives one
     * @return the run id
     */
    static String resolve(String configured, Map<String, String> environment, List<Ancestor> ancestors, String ownRunId) {
        if (!isBlank(configured)) {
            return configured;
        }
        for (CiSource source : CI_SOURCES) {
            Optional<String> runId = source.runId(environment);
            if (runId.isPresent()) {
                return runId.get();
            }
        }
        for (Ancestor ancestor : ancestors) {
            if (ancestor.isJvm()) {
                return ancestor.startMillis() != null
                    ? "process-" + ancestor.pid() + "-" + ancestor.startMillis()
                    : "process-" + ancestor.pid();
            }
        }
        return ownRunId;
    }

    /**
     * Lists the ancestors of a process, nearest first. Stops early if the platform refuses to tell.
     *
     * @param process the process whose ancestors to list
     * @return the ancestors, at most 32
     */
    static List<Ancestor> ancestorsOf(ProcessHandle process) {
        List<Ancestor> ancestors = new ArrayList<>();
        try {
            Optional<ProcessHandle> parent = process.parent();
            while (parent.isPresent() && ancestors.size() < MAX_ANCESTORS) {
                ProcessHandle handle = parent.get();
                ProcessHandle.Info info = handle.info();
                Long startMillis = info.startInstant().map(Instant::toEpochMilli).orElse(null);
                ancestors.add(new Ancestor(handle.pid(), startMillis, info.command().orElse(null)));
                parent = handle.parent();
            }
        } catch (RuntimeException e) {
            // A security manager or the platform refused: use the ancestors found so far
        }
        return ancestors;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * An ancestor process.
     *
     * @param pid         its process id
     * @param startMillis when it started, in milliseconds since the epoch, or {@code null} if the platform doesn't say
     * @param command     the executable it runs, or {@code null} if it can't be read
     */
    record Ancestor(long pid, Long startMillis, String command) {

        boolean isJvm() {
            if (command == null) {
                return false;
            }
            String name = command.substring(Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\')) + 1)
                .toLowerCase(Locale.ROOT);
            return name.equals("java") || name.equals("java.exe") || name.equals("javaw.exe");
        }
    }

    /**
     * A CI system's run id: its prefix followed by the values of its variables, joined by "-". It applies only
     * when all of its variables are set.
     */
    private record CiSource(String prefix, List<String> variables) {

        Optional<String> runId(Map<String, String> environment) {
            List<String> values = new ArrayList<>();
            for (String variable : variables) {
                String value = environment.get(variable);
                if (isBlank(value)) {
                    return Optional.empty();
                }
                values.add(value);
            }
            return Optional.of(prefix + String.join("-", values));
        }
    }
}

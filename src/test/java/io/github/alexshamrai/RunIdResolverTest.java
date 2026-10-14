package io.github.alexshamrai;

import io.github.alexshamrai.RunIdResolver.Ancestor;
import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.config.CtrfConfig;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RunIdResolverTest {

    private static final Ancestor GRADLE_DAEMON = new Ancestor(10, 1_000L, "/opt/jdk-21/bin/java");
    private static final Ancestor SHELL = new Ancestor(11, 2_000L, "/bin/bash");

    @Test
    @DisplayName("ctrf.run.id wins over CI variables and ancestors")
    void explicitRunIdWinsOverEverything() {
        var runId = RunIdResolver.resolve("nightly-42", Map.of("CI_JOB_ID", "7"), List.of(GRADLE_DAEMON), "own");

        assertThat(runId).isEqualTo("nightly-42");
    }

    @Test
    @DisplayName("A blank ctrf.run.id is skipped")
    void blankExplicitRunIdIsSkipped() {
        assertThat(RunIdResolver.resolve("  ", Map.of("CI_JOB_ID", "7"), List.of(), "own")).isEqualTo("gitlab-7");
    }

    @Test
    @DisplayName("Each CI gives its run id from its own variables")
    void ciVariablesGiveTheirRunId() {
        assertThat(resolveWith(Map.of("GITHUB_RUN_ID", "100", "GITHUB_RUN_ATTEMPT", "2"))).isEqualTo("github-100-2");
        assertThat(resolveWith(Map.of("CI_JOB_ID", "7"))).isEqualTo("gitlab-7");
        assertThat(resolveWith(Map.of("BUILD_TAG", "jenkins-payments-15"))).isEqualTo("jenkins-payments-15");
        assertThat(resolveWith(Map.of("BUILD_BUILDID", "500", "SYSTEM_JOBATTEMPT", "1"))).isEqualTo("azure-500-1");
        assertThat(resolveWith(Map.of("CIRCLE_WORKFLOW_JOB_ID", "abc"))).isEqualTo("circleci-abc");
        assertThat(resolveWith(Map.of("BUILDKITE_JOB_ID", "def"))).isEqualTo("buildkite-def");
    }

    @Test
    @DisplayName("A CI source applies only when all its variables are set")
    void ciSourceNeedsAllItsVariables() {
        assertThat(resolveWith(Map.of("GITHUB_RUN_ID", "100"))).isEqualTo("process-10-1000");
    }

    @Test
    @DisplayName("A blank CI variable is skipped")
    void blankCiVariableIsSkipped() {
        assertThat(resolveWith(Map.of("CI_JOB_ID", "", "BUILD_TAG", "jenkins-job-5"))).isEqualTo("jenkins-job-5");
    }

    @Test
    @DisplayName("The nearest JVM ancestor is found behind a shell, as Surefire starts its forks")
    void nearestJvmAncestorIsFoundBehindAShell() {
        assertThat(RunIdResolver.resolve(null, Map.of(), List.of(SHELL, GRADLE_DAEMON), "own")).isEqualTo("process-10-1000");
    }

    @Test
    @DisplayName("An ancestor without a start time is identified by its pid alone")
    void ancestorWithoutStartTimeUsesItsPid() {
        var maven = new Ancestor(20, null, "C:\\jdk\\bin\\java.exe");

        assertThat(RunIdResolver.resolve(null, Map.of(), List.of(maven), "own")).isEqualTo("process-20");
    }

    @Test
    @DisplayName("An ancestor whose command can't be read is skipped")
    void ancestorWithUnreadableCommandIsSkipped() {
        var unreadable = new Ancestor(12, 3_000L, null);

        assertThat(RunIdResolver.resolve(null, Map.of(), List.of(unreadable, GRADLE_DAEMON), "own")).isEqualTo("process-10-1000");
    }

    @Test
    @DisplayName("Without a JVM ancestor, the JVM is its own run")
    void noJvmAncestorGivesOwnRunId() {
        var launchd = new Ancestor(1, 0L, "/sbin/launchd");

        assertThat(RunIdResolver.resolve(null, Map.of(), List.of(SHELL, launchd), "own")).isEqualTo("own");
    }

    @Test
    @DisplayName("Only java executables count as JVMs")
    void recognisesJavaExecutables() {
        assertThat(new Ancestor(1, null, "/usr/lib/jvm/bin/java").isJvm()).isTrue();
        assertThat(new Ancestor(1, null, "C:\\jdk\\bin\\JAVA.EXE").isJvm()).isTrue();
        assertThat(new Ancestor(1, null, "C:\\jdk\\bin\\javaw.exe").isJvm()).isTrue();
        assertThat(new Ancestor(1, null, "/opt/jdk/bin/javac").isJvm()).isFalse();
        assertThat(new Ancestor(1, null, "/Applications/IntelliJ IDEA.app/Contents/MacOS/idea").isJvm()).isFalse();
        assertThat(new Ancestor(1, null, "/bin/bash").isJvm()).isFalse();
    }

    @Test
    @DisplayName("The ancestors of this JVM start with its parent")
    void ancestorsOfThisJvmStartWithItsParent() {
        var parent = ProcessHandle.current().parent().orElseThrow();

        assertThat(RunIdResolver.ancestorsOf(ProcessHandle.current()))
            .first()
            .extracting(Ancestor::pid)
            .isEqualTo(parent.pid());
    }

    @Test
    @DisplayName("ctrf.run.id is read from the configuration")
    void resolvesFromConfiguration() {
        var config = ConfigFactory.create(CtrfConfig.class, Map.of("ctrf.run.id", "from-config"));

        assertThat(RunIdResolver.resolve(new ConfigReader(config))).isEqualTo("from-config");
    }

    @Test
    @DisplayName("When process inspection throws, the run id falls back to this JVM, or to ctrf.run.id if set")
    void processInspectionThatThrowsFallsBackToThisJvm() {
        Supplier<ProcessHandle> denied = () -> {
            throw new SecurityException("denied");
        };
        var withoutRunId = new ConfigReader(ConfigFactory.create(CtrfConfig.class, Map.of()));
        var withRunId = new ConfigReader(ConfigFactory.create(CtrfConfig.class, Map.of("ctrf.run.id", "from-config")));

        assertThat(RunIdResolver.resolve(withoutRunId, denied, Map::of)).startsWith("jvm-");
        assertThat(RunIdResolver.resolve(withRunId, denied, Map::of)).isEqualTo("from-config");
    }

    private static String resolveWith(Map<String, String> environment) {
        return RunIdResolver.resolve(null, environment, List.of(GRADLE_DAEMON), "own");
    }
}

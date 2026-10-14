# CB-6 + HP-11: One Report per Run — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One correct CTRF report per run, whatever the build does: parallel forks, retry rounds in new JVMs, reruns inside one JVM, or a JVM killed while writing. A class whose setup or teardown fails in one round and succeeds in a later one is reported as passed and flaky.

**Architecture:** Each JVM resolves a `runId` once: `ctrf.run.id`, then a CI variable, then the nearest ancestor JVM, then itself. While tests run, attempts and (for HP-11) container successes are recorded in memory in O(1). At the end of each execution, `CtrfReportFileService.update` does the following under a JVM lock and a file lock on `<report>.lock`:
1. It reads the report fresh.
2. It passes the report to a function built on the pure `ReportMerger`. If the report has the same `runId`, the merger unfolds its tests, appends this execution's attempts and recoveries, and folds. Otherwise only this execution's results are kept.
3. It writes a complete temp file and moves it over the report.

Nothing is read when a run starts. `ReportOrchestrator` and `TestRerunHandler` are deleted.

**Tech Stack:** Java 17 (toolchain 21), Gradle, Lombok, Jackson, JUnit 5/6, AssertJ, Mockito, Checkstyle, Maven (compat consumers), Python 3 (compat checker).

**Spec:** `IMPROVEMENT_PLAN.md` on branch `feature/improvements-plan`, in the main checkout `/Users/oleksiishamrai/personal_dev/junit-ctrf-extension`. Two sections apply:
- "CB-6: Rework Report Persistence (Forks, Accumulation, Same-JVM Reruns)", commits `c081a69` and `1fd6479`;
- "HP-11: A Class Failure That Recovers on Retry Stays Failed".

Read both before starting. The CTRF spec 0.1.0 (`ctrf-io/ctrf`, `spec/ctrf.md`) is the background reference, sections 4.9, 5.3 and 5.4.

## Global Constraints

- **Where:** the worktree `/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes`, on branch `fix/cb-6-report-persistence`, created from `origin/master` (`58e36f0` or later). Ignore the untracked `ctrf-report.json` at its root, and never commit it.
- **Report path:** `ctrf.report.path` keeps its default, `ctrf-report.json`. Each execution merges into that one report.
- **Run identity**, verbatim from the spec. The first source that gives a non-blank value wins:
  1. `ctrf.run.id`, used as is;
  2. CI variables: `github-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}`, `gitlab-${CI_JOB_ID}`, `${BUILD_TAG}`, `azure-${BUILD_BUILDID}-${SYSTEM_JOBATTEMPT}`, `circleci-${CIRCLE_WORKFLOW_JOB_ID}`, `buildkite-${BUILDKITE_JOB_ID}`;
  3. the nearest ancestor process whose command is `java`, `java.exe` or `javaw.exe`: `process-<pid>-<start millis>`, or `process-<pid>`;
  4. otherwise `jvm-<pid>-<start millis>-<random>`.

  The value goes into the root `runId` of every report.
- **Locks:** a JVM-wide Java lock, then an exclusive lock on `<report>.lock`, polled with `tryLock` for up to 60 s. After the timeout, or on a file system without locks, the report is written without the file lock.
- **Writes:** to a hidden temp file `.<report name>.<random>.tmp` in the same directory, then `Files.move` with `ATOMIC_MOVE` and `REPLACE_EXISTING`, falling back to a plain replace. The temp file is not created with `Files.createTempFile`, which would make the report readable only by its owner.
- **Failures:** the reporter never throws file or merge errors into JUnit, and never leaves a report that can't be parsed. Each fallback prints one line to `System.err`.
- **HP-11:** a container below the engine that finishes SUCCESSFUL records a success. Engine containers never do. A recovered entry has status `passed`, `retries: n` and `flaky: true`, and keeps the name of its last failure.
- **Out of scope:**
  - HP-5 (official schema, `filePath`);
  - MP-5 (lenient reading of unknown fields);
  - CB-2 (nested runs);
  - issues that only occur with the listener and the extension registered together;
  - a merger tool of our own.
- **Release:** no version bump. The 0.5.0 release notes list the removed public methods:
  - `CtrfReportFileService`: `getExistingTests`, `getExistingStartTime`, `getExistingEnvironmentHealth` and `writeResultsToFile`;
  - `CtrfJsonComposer.generateCtrfJson` gains a `runId` parameter.
- **Commits:** no `Co-Authored-By` trailer and no "Generated with Claude Code" line, anywhere. The per-task commits are squashed into one commit in Task 9.
- **zsh:** in loops use `${m}`, never `$m:...`.
- **Builds:** `./gradlew :build` runs the unit tests, the scenario tests and checkstyle. The root `./gradlew build`, without `:`, also runs the validator and fails without a report, so don't use it.
- **Checkstyle:**
  - lines of at most 140 characters;
  - method names match `^[a-z][a-z0-9][a-zA-Z0-9_]*$`, so the second character is lowercase or a digit;
  - no more than two capitals in a row in names;
  - no empty catch blocks (a comment counts as content);
  - wrapped operators (`+`, `&&`, `?`, `:`) start the new line.

## Review Focus

1. **The default, bare report path** `ctrf-report.json`, with no directory. The lock and temp files must land in the working directory, and the update must work. Test: `CtrfReportFileServiceTest.updatesAReportGivenAsABareFileName` (Task 2).
2. **The merge function throws inside `update`**, for example when composing fails. The report stays as it was, no temp file is left, no exception reaches JUnit, and the lock is released so the next update works. Test: `CtrfReportFileServiceTest.mergeThatThrowsLeavesTheReportAndReleasesTheLock` (Task 2).
3. **A same-run report without `summary` or `environment`**, hand-edited or written by a tool that omits them. It merges without a crash; start, stop and health come from this JVM. Test: `ReportMergerTest.sameRunReportWithoutSummaryOrEnvironmentStillMerges` (Task 3).
4. **An engine container that succeeds.** Every fork runs the engine, so the listener must not record its success. Otherwise another fork's engine failure would look recovered. Test: `CtrfListenerTest.executionFinished_shouldNotRecordSuccessOfAnEngine` (Task 5).
5. **The extension's `afterAll` for a class without `ClassState`**, because its `beforeAll` never ran. It records a success without a start, and the manager then uses the stop time. Tests: `CtrfExtensionTest.afterAll_withoutClassState_recordsSuccessWithoutStart` (Task 5) and `CtrfReportManagerTest.onContainerSuccess_withoutStartUsesTheStopTime` (Task 4).

---

### Task 1: Run identity

**Files:**
- Create: `src/main/java/io/github/alexshamrai/RunIdResolver.java`
- Modify: `src/main/java/io/github/alexshamrai/ctrf/model/CtrfJson.java` (new field after `reportId`)
- Modify: `src/main/java/io/github/alexshamrai/config/CtrfConfig.java` (new key at the end)
- Modify: `src/main/java/io/github/alexshamrai/config/ConfigReader.java` (new getter at the end)
- Create: `src/test/java/io/github/alexshamrai/RunIdResolverTest.java`
- Modify: `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`

**Interfaces:**
- Produces:
  - `String CtrfJson.getRunId()`, `setRunId(String)` and `CtrfJson.builder().runId(String)`;
  - `String ConfigReader.getRunId()`, which is `null` when the key isn't set;
  - `static String RunIdResolver.resolve(ConfigReader configReader)`, which inspects the real process and environment;
  - `static String RunIdResolver.resolve(String configured, Map<String, String> environment, List<RunIdResolver.Ancestor> ancestors, String ownRunId)`, which is pure;
  - `record RunIdResolver.Ancestor(long pid, Long startMillis, String command)` with `boolean isJvm()`;
  - `static List<RunIdResolver.Ancestor> RunIdResolver.ancestorsOf(ProcessHandle process)`, which lists the nearest ancestor first.

  All are package-private, except the model and config members.

- [ ] **Step 1: Create the branch**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git fetch origin --prune
git switch -c fix/cb-6-report-persistence origin/master
git branch --unset-upstream
git log --oneline -1
```

Expected: the branch starts at the current `origin/master` and has no upstream, so a bare `git push` can't go to `master`.

- [ ] **Step 2: Write the failing tests**

Create `src/test/java/io/github/alexshamrai/RunIdResolverTest.java`:

```java
package io.github.alexshamrai;

import io.github.alexshamrai.RunIdResolver.Ancestor;
import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.config.CtrfConfig;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    private static String resolveWith(Map<String, String> environment) {
        return RunIdResolver.resolve(null, environment, List.of(GRADLE_DAEMON), "own");
    }
}
```

Append to `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`, inside the class:

```java
    @org.junit.jupiter.api.Test
    void runIdIsWrittenAtTheRootOnlyWhenSet() throws Exception {
        var withRunId = objectMapper.readTree(objectMapper.writeValueAsString(CtrfJson.builder().runId("run-1").build()));
        var withoutRunId = objectMapper.readTree(objectMapper.writeValueAsString(CtrfJson.builder().build()));

        assertThat(withRunId.get("runId").asText()).isEqualTo("run-1");
        assertThat(withoutRunId.has("runId")).isFalse();
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :test --tests 'io.github.alexshamrai.RunIdResolverTest' --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest'`

Expected: FAIL at `:compileTestJava`, with "cannot find symbol" for `RunIdResolver` and for `runId(String)` on `CtrfJson`'s builder.

- [ ] **Step 4: Implement**

In `src/main/java/io/github/alexshamrai/ctrf/model/CtrfJson.java`, replace

```java
    private String reportId;
```

with

```java
    private String reportId;

    /**
     * Identifies the logical run this report belongs to (CTRF section 5.4). The JVMs of one run, such as
     * parallel forks and retry rounds, merge their results into one report.
     */
    private String runId;
```

In `src/main/java/io/github/alexshamrai/config/CtrfConfig.java`, replace

```java
    @Key("ctrf.calculate.startup.duration")
    @DefaultValue("false")
    boolean calculateStartupDuration();
}
```

with

```java
    @Key("ctrf.calculate.startup.duration")
    @DefaultValue("false")
    boolean calculateStartupDuration();

    @Key("ctrf.run.id")
    String runId();
}
```

In `src/main/java/io/github/alexshamrai/config/ConfigReader.java`, replace

```java
    public boolean calculateStartupDuration() {
        return config.calculateStartupDuration();
    }
}
```

with

```java
    public boolean calculateStartupDuration() {
        return config.calculateStartupDuration();
    }

    public String getRunId() {
        return config.runId();
    }
}
```

Create `src/main/java/io/github/alexshamrai/RunIdResolver.java`:

```java
package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        ProcessHandle self = ProcessHandle.current();
        String start = self.info().startInstant().map(instant -> String.valueOf(instant.toEpochMilli())).orElse("0");
        String ownRunId = "jvm-" + self.pid() + "-" + start + "-" + UUID.randomUUID().toString().substring(0, 8);
        return resolve(configReader.getRunId(), System.getenv(), ancestorsOf(self), ownRunId);
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
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.RunIdResolverTest' --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest'`

Expected: PASS. `RunIdResolverTest` has 12 tests, and `CtrfModelJsonTest` has 5.

- [ ] **Step 6: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL. Nothing uses the resolver yet.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/alexshamrai/RunIdResolver.java src/main/java/io/github/alexshamrai/ctrf/model/CtrfJson.java \
        src/main/java/io/github/alexshamrai/config src/test/java/io/github/alexshamrai/RunIdResolverTest.java \
        src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java
git commit -m "feat: resolve a runId per JVM and add it to the report model (CB-6)"
```

---

### Task 2: `CtrfReportFileService.update`: locked, fresh read; atomic write

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java`:
  - imports;
  - the class Javadoc;
  - `@RequiredArgsConstructor` replaced by two constructors;
  - new fields and methods.

  The old read methods stay until Task 4.
- Replace: `src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java`. Its old tests cover `writeResultsToFile` and the `getExisting*` methods, which Task 4 removes. Ruling: they are replaced now, not two tasks later.

**Interfaces:**
- Produces:
  - `public void CtrfReportFileService.update(java.util.function.UnaryOperator<CtrfJson> merge)`, whose function receives the current report, or `null` when there is none or it can't be parsed;
  - the package-private `CtrfReportFileService(ConfigReader configReader, java.time.Duration lockTimeout)`.
  - `public CtrfReportFileService(ConfigReader configReader)` is unchanged and uses a 60 s lock timeout.

- [ ] **Step 1: Write the failing tests**

Replace the whole content of `src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java` with:

```java
package io.github.alexshamrai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.config.CtrfConfig;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class CtrfReportFileServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalErr = System.err;

    @TempDir
    Path tempDir;

    @BeforeEach
    void captureStandardError() {
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStandardError() {
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("update creates missing directories and writes the report, with its lock file next to it")
    void updateWritesTheReportAndCreatesDirectories() throws IOException {
        Path report = tempDir.resolve("nested/dir/ctrf.json");

        serviceFor(report).update(existing -> marker("first"));

        assertThat(readMarker(report)).isEqualTo("first");
        assertThat(report.resolveSibling("ctrf.json.lock")).exists();
        assertThat(tempFilesNextTo(report)).isEmpty();
        assertThat(errContent.toString()).isEmpty();
    }

    @Test
    @DisplayName("update passes the current report to the merge function and writes what it returns")
    void updatePassesTheCurrentReportToTheMerge() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        var service = serviceFor(report);
        service.update(existing -> marker("first"));
        List<String> seen = new ArrayList<>();

        service.update(existing -> {
            seen.add(existing.getGeneratedBy());
            return marker("second");
        });

        assertThat(seen).containsExactly("first");
        assertThat(readMarker(report)).isEqualTo("second");
    }

    @Test
    @DisplayName("A report that can't be parsed reaches the merge function as null")
    void unparsableReportReachesTheMergeAsNull() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{ not json");
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(readMarker(report)).isEqualTo("fresh");
        assertThat(errContent.toString()).contains("can't be read");
    }

    @Test
    @DisplayName("A write that fails halfway leaves the previous report parseable and unchanged")
    void writeThatFailsHalfwayLeavesThePreviousReport() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        var service = serviceFor(report);
        service.update(existing -> marker("first"));

        service.update(existing -> CtrfJson.builder().generatedBy("second").extra(Map.of("value", new Exploding())).build());

        assertThat(readMarker(report)).isEqualTo("first");
        assertThat(tempFilesNextTo(report)).isEmpty();
        assertThat(errContent.toString()).contains("Failed to write results to file");
    }

    @Test
    @DisplayName("A merge function that throws leaves the report as it was and releases the lock")
    void mergeThatThrowsLeavesTheReportAndReleasesTheLock() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        var service = serviceFor(report);
        service.update(existing -> marker("first"));

        assertThatCode(() -> service.update(existing -> {
            throw new IllegalStateException("composing failed");
        })).doesNotThrowAnyException();

        assertThat(readMarker(report)).isEqualTo("first");
        assertThat(errContent.toString()).contains("composing failed");
        service.update(existing -> marker("third"));
        assertThat(readMarker(report)).isEqualTo("third");
    }

    @Test
    @DisplayName("Updates from many threads at once all land")
    void concurrentUpdatesFromSeveralThreadsAllLand() throws Exception {
        Path report = tempDir.resolve("ctrf.json");
        var service = serviceFor(report);
        UnaryOperator<CtrfJson> increment = existing -> {
            int count = existing == null ? 0 : (Integer) existing.getExtra().get("count");
            return CtrfJson.builder().extra(Map.of("count", count + 1)).build();
        };
        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                service.update(increment);
                return null;
            });
        }

        start.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(objectMapper.readValue(report.toFile(), CtrfJson.class).getExtra()).containsEntry("count", threads);
    }

    @Test
    @DisplayName("When another holder keeps the lock, update waits for the timeout and then writes without it")
    void waitsForTheLockAndThenWritesWithoutIt() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Path lockFile = tempDir.resolve("ctrf.json.lock");
        var service = new CtrfReportFileService(configReaderWithPath(report.toString()), Duration.ofMillis(300));

        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var held = channel.lock()) {
            long started = System.nanoTime();
            service.update(existing -> marker("written anyway"));
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(held.isValid()).isTrue();
            assertThat(waitedMillis).isGreaterThanOrEqualTo(300);
        }

        assertThat(readMarker(report)).isEqualTo("written anyway");
        assertThat(errContent.toString()).contains("Could not lock");
    }

    @Test
    @DisplayName("A report given as a bare file name, as the default ctrf-report.json is, is written in the working directory")
    void updatesAReportGivenAsABareFileName() throws IOException {
        String name = "ctrf-bare-" + UUID.randomUUID() + ".json";
        Path report = Path.of(name);
        Path lockFile = Path.of(name + ".lock");
        try {
            new CtrfReportFileService(configReaderWithPath(name)).update(existing -> marker("bare"));

            assertThat(readMarker(report)).isEqualTo("bare");
            assertThat(lockFile).exists();
        } finally {
            Files.deleteIfExists(report);
            Files.deleteIfExists(lockFile);
        }
    }

    @Test
    @DisplayName("A directory that can't be written is reported, not thrown")
    void reportInAReadOnlyDirectoryIsReportedNotThrown() throws IOException {
        Path readOnly = Files.createDirectory(tempDir.resolve("read-only"));
        assumeFalse(!readOnly.toFile().setWritable(false) || readOnly.toFile().canWrite(), "the file system ignores permissions");
        try {
            assertThatCode(() -> serviceFor(readOnly.resolve("sub/ctrf.json")).update(existing -> marker("never")))
                .doesNotThrowAnyException();

            assertThat(errContent.toString()).contains("Access denied");
        } finally {
            readOnly.toFile().setWritable(true);
        }
    }

    private CtrfReportFileService serviceFor(Path report) {
        return new CtrfReportFileService(configReaderWithPath(report.toString()));
    }

    private static ConfigReader configReaderWithPath(String path) {
        return new ConfigReader(ConfigFactory.create(CtrfConfig.class, Map.of("ctrf.report.path", path)));
    }

    private static CtrfJson marker(String value) {
        return CtrfJson.builder().generatedBy(value).build();
    }

    private String readMarker(Path report) throws IOException {
        return objectMapper.readValue(report.toFile(), CtrfJson.class).getGeneratedBy();
    }

    private static List<Path> tempFilesNextTo(Path report) throws IOException {
        try (Stream<Path> files = Files.list(report.getParent())) {
            return files.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList();
        }
    }

    /**
     * Fails while Jackson serializes it, after the report's first fields are already written.
     */
    static final class Exploding {

        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :test --tests 'io.github.alexshamrai.CtrfReportFileServiceTest'`

Expected: FAIL at `:compileTestJava` with "cannot find symbol: method update" and "constructor CtrfReportFileService(ConfigReader,Duration)".

- [ ] **Step 3: Implement `update`**

In `src/main/java/io/github/alexshamrai/CtrfReportFileService.java`:

Replace the import block, from `import com.fasterxml.jackson.databind.ObjectMapper;` through `import java.util.List;`, with:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Test;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
```

Replace the class Javadoc, the `@RequiredArgsConstructor` line and the field declarations, everything from `/**` above the class through `private boolean reportReadAttempted = false;`, with:

```java
/**
 * Reads and writes the CTRF report file at {@code ctrf.report.path}.
 * <p>
 * Several JVMs can write the same report, for example parallel forks and retry rounds. {@link #update} lets
 * them take turns: it holds an exclusive lock on {@code <report>.lock} while it reads the report, merges, and
 * replaces the report by moving a complete temp file over it. A JVM killed while writing therefore never
 * leaves a truncated report.
 */
public class CtrfReportFileService {

    static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration LOCK_POLL_INTERVAL = Duration.ofMillis(50);

    /**
     * A JVM can hold a file lock only once, and a second attempt throws, so threads of this JVM queue here first.
     */
    private static final Object JVM_LOCK = new Object();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigReader configReader;
    private final Duration lockTimeout;
    private CtrfJson cachedReport;
    private boolean reportReadAttempted = false;

    public CtrfReportFileService(ConfigReader configReader) {
        this(configReader, DEFAULT_LOCK_TIMEOUT);
    }

    CtrfReportFileService(ConfigReader configReader, Duration lockTimeout) {
        this.configReader = configReader;
        this.lockTimeout = lockTimeout;
    }

    /**
     * Replaces the report with what {@code merge} makes of the current one.
     * <p>
     * Threads of this JVM take turns, and other JVMs are kept out by an exclusive lock on {@code <report>.lock}.
     * The report is read fresh while the lock is held. After the lock timeout, or on a file system that can't
     * lock files, the report is written without the lock. Errors, including one thrown by {@code merge}, are
     * printed to the standard error and leave the report as it was.
     *
     * @param merge receives the current report, or {@code null} if there is none or it can't be parsed, and
     *              returns the report to write
     */
    public void update(UnaryOperator<CtrfJson> merge) {
        Path report = Paths.get(configReader.getReportPath()).toAbsolutePath();
        synchronized (JVM_LOCK) {
            try {
                Files.createDirectories(report.getParent());
            } catch (IOException e) {
                printWriteFailure(e);
                return;
            }
            FileLock lock = lockAcrossJvms(report);
            try {
                writeAtomically(report, merge.apply(readExisting(report)));
            } catch (RuntimeException e) {
                System.err.println("Failed to write results to file: " + configReader.getReportPath() + " - " + e);
            } finally {
                release(lock);
            }
        }
    }
```

Then, directly before the class's closing `}`, add:

```java

    /**
     * Takes the exclusive lock on {@code <report>.lock}, waiting up to the lock timeout. Returns {@code null},
     * after printing why, when the file system can't lock files or the lock stays taken; the report is then
     * written without it.
     */
    private FileLock lockAcrossJvms(Path report) {
        Path lockFile = report.resolveSibling(report.getFileName() + ".lock");
        FileChannel channel = null;
        try {
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            long deadline = System.nanoTime() + lockTimeout.toNanos();
            while (true) {
                FileLock lock = tryLock(channel);
                if (lock != null) {
                    return lock;
                }
                if (System.nanoTime() >= deadline) {
                    System.err.println("Could not lock " + lockFile + " within " + lockTimeout.toMillis()
                        + " ms; writing the report without the lock");
                    closeQuietly(channel);
                    return null;
                }
                Thread.sleep(LOCK_POLL_INTERVAL.toMillis());
            }
        } catch (IOException | UnsupportedOperationException e) {
            System.err.println("Could not lock " + lockFile + "; writing the report without the lock - " + e.getMessage());
            closeQuietly(channel);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for " + lockFile + "; writing the report without the lock");
            closeQuietly(channel);
            return null;
        }
    }

    // A lock held by this JVM through another channel makes tryLock throw instead of returning null
    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException e) {
            return null;
        }
    }

    // Closing the channel releases its lock
    private static void release(FileLock lock) {
        if (lock != null) {
            closeQuietly(lock.channel());
        }
    }

    private CtrfJson readExisting(Path report) {
        if (!Files.exists(report)) {
            return null;
        }
        try {
            return objectMapper.readValue(report.toFile(), CtrfJson.class);
        } catch (IOException e) {
            System.err.println("Ignoring the existing report, which can't be read: " + report + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Writes to a hidden temp file next to the report, then moves it over the report. Not
     * {@code Files.createTempFile}, which would make the report readable by its owner only.
     */
    private void writeAtomically(Path report, CtrfJson ctrfJson) {
        Path temp = report.resolveSibling("." + report.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            objectMapper.writeValue(temp.toFile(), ctrfJson);
            moveOver(temp, report);
        } catch (IOException e) {
            printWriteFailure(e);
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void moveOver(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            System.err.println("Can't replace " + target + " atomically; replacing it in place");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void printWriteFailure(IOException e) {
        if (e instanceof AccessDeniedException) {
            System.err.println("Access denied: " + configReader.getReportPath() + " - " + e.getMessage());
        } else {
            System.err.println("Failed to write results to file: " + configReader.getReportPath() + " - " + e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // A leftover temp file is harmless
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // Nothing left to release
        }
    }
```

The new import block no longer has `import lombok.RequiredArgsConstructor;`, which is no longer used. Leave `writeResultsToFile`, `getExistingTests`, `getExistingStartTime`, `getExistingEnvironmentHealth`, `getExistingReportCached` and `readExistingReport` alone; Task 4 removes them.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.CtrfReportFileServiceTest'`

Expected: PASS, 9 tests. `reportInAReadOnlyDirectoryIsReportedNotThrown` is skipped, not failed, only when the tests run as root.

- [ ] **Step 5: Prove the file lock and the atomic write matter**

Each check takes the code back to a naive version, runs the test that guards it, and restores the file.

The JVM lock has no check of its own. Without it, threads still take turns, because `tryLock` treats a lock held by this JVM as taken and keeps polling. It exists so that a thread waiting for the timeout can't close its channel while another thread holds the lock, which on POSIX releases that lock. No test observes that.

```bash
F=src/main/java/io/github/alexshamrai/CtrfReportFileService.java
cp $F $F.orig
# 1. never take the file lock
sed -i '' 's/FileLock lock = lockAcrossJvms(report);/FileLock lock = null;/' $F
./gradlew :test --tests 'io.github.alexshamrai.CtrfReportFileServiceTest.waitsForTheLockAndThenWritesWithoutIt' 2>&1 | grep -E 'FAILED|BUILD'
cp $F.orig $F
# 2. write in place instead of temp file + move
sed -i '' 's/objectMapper.writeValue(temp.toFile(), ctrfJson);/objectMapper.writeValue(report.toFile(), ctrfJson);/' $F
./gradlew :test --tests 'io.github.alexshamrai.CtrfReportFileServiceTest.writeThatFailsHalfwayLeavesThePreviousReport' 2>&1 | grep -E 'FAILED|BUILD'
cp $F.orig $F
rm $F.orig
git diff --stat -- $F
```

Expected:
- Check 1 FAILS: `update` doesn't wait, and nothing is printed about the lock.
- Check 2 FAILS: the report can't be parsed.
- After restoring, `git diff --stat` shows only this task's changes. Compare it with the file you wrote in Step 3: the two `sed` edits must be gone.

- [ ] **Step 6: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL. `CtrfReportManager` still uses the old read methods.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/alexshamrai/CtrfReportFileService.java src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java
git commit -m "feat: update the report under a file lock with an atomic write (CB-6)"
```

---

### Task 3: `ReportMerger` and `ContainerSuccess`

**Files:**
- Create: `src/main/java/io/github/alexshamrai/ContainerSuccess.java`
- Create: `src/main/java/io/github/alexshamrai/ReportMerger.java`
- Create: `src/test/java/io/github/alexshamrai/ReportMergerTest.java`

**Interfaces:**
- Consumes: `CtrfJson.getRunId()` (Task 1); `AttemptFolder.fold(List<Test>)` and `AttemptFolder.unfold(List<Test>)` (on `master` since HP-6).
- Produces, package-private:
  - `record ContainerSuccess(String uniqueId, long start, long stop)`;
  - `static ReportMerger.MergedRun ReportMerger.merge(CtrfJson existing, String runId, List<Test> attempts, List<ContainerSuccess> successes, long start, long stop, boolean healthy)`;
  - `record ReportMerger.MergedRun(List<Test> tests, long start, long stop, boolean healthy)`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/io/github/alexshamrai/ReportMergerTest.java`:

```java
package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Results;
import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ReportMergerTest {

    private static final String RUN = "run-1";
    private static final String CLASS_ID = "[engine:junit-jupiter]/[class:com.example.DbTest]";

    @org.junit.jupiter.api.Test
    @DisplayName("Without a report on disk, the run is this JVM's results")
    void noExistingReportGivesThisJvmsResults() {
        var run = ReportMerger.merge(null, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("id-1");
        assertThat(run.start()).isEqualTo(100L);
        assertThat(run.stop()).isEqualTo(200L);
        assertThat(run.healthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report of the same run is unfolded, and this JVM's attempts extend its tests' history")
    void sameRunAppendsThisJvmsAttemptsToTheReport() {
        var existing = report(RUN, 50L, 90L, true, attempt("id-1", FAILED, 60L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(PASSED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isTrue();
            assertThat(test.getRetryAttempts()).extracting(RetryAttempt::getStart).containsExactly(60L);
        });
        assertThat(run.start()).isEqualTo(50L);
        assertThat(run.stop()).isEqualTo(200L);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Tests written by another fork of the same run are kept, before this JVM's")
    void sameRunKeepsTheTestsOfOtherForks() {
        var existing = report(RUN, 50L, 90L, true, attempt("from-fork-1", PASSED, 60L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("from-fork-2", FAILED, 70L)), List.of(), 55L, 95L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("from-fork-1", "from-fork-2");
        assertThat(run.start()).isEqualTo(50L);
        assertThat(run.stop()).isEqualTo(95L);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("The run is unhealthy if the report on disk or this JVM is")
    void sameRunIsUnhealthyIfEitherReportIs() {
        assertThat(ReportMerger.merge(report(RUN, 50L, 90L, false), RUN, List.of(), List.of(), 100L, 200L, true).healthy()).isFalse();
        assertThat(ReportMerger.merge(report(RUN, 50L, 90L, true), RUN, List.of(), List.of(), 100L, 200L, false).healthy()).isFalse();
        assertThat(ReportMerger.merge(report(RUN, 50L, 90L, true), RUN, List.of(), List.of(), 100L, 200L, true).healthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report of another run is replaced, and passes on neither its start time nor its health")
    void differentRunIsReplaced() {
        var existing = report("run-0", 10L, 20L, false, attempt("old", FAILED, 15L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("new", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("new");
        assertThat(run.tests().get(0).getRetries()).isNull();
        assertThat(run.start()).isEqualTo(100L);
        assertThat(run.healthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report without a runId, as 0.4.x and other tools write it, is never merged")
    void reportWithoutRunIdIsNeverMerged() {
        var existing = report(null, 10L, 20L, true, attempt("id-1", FAILED, 15L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> assertThat(test.getRetries()).isNull());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report of the same run without summary or environment still merges")
    void sameRunReportWithoutSummaryOrEnvironmentStillMerges() {
        var existing = CtrfJson.builder()
            .runId(RUN)
            .results(Results.builder().tests(List.of(attempt("id-1", FAILED, 60L))).build())
            .build();

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> assertThat(test.getFlaky()).isTrue());
        assertThat(run.start()).isEqualTo(100L);
        assertThat(run.stop()).isEqualTo(200L);
        assertThat(run.healthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A class failure followed by the class's success is passed and flaky, under the failure's name")
    void recoveredContainerIsPassedAndFlaky() {
        var failure = attempt(CLASS_ID, FAILED, 60L).toBuilder().name("initializationError").message("Connection refused").build();
        var existing = report(RUN, 50L, 90L, true, failure);

        var run = ReportMerger.merge(existing, RUN, List.of(), List.of(new ContainerSuccess(CLASS_ID, 300L, 310L)), 100L, 400L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> {
            assertThat(test.getName()).isEqualTo("initializationError");
            assertThat(test.getStatus()).isEqualTo(PASSED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isTrue();
            assertThat(test.getStart()).isEqualTo(300L);
            assertThat(test.getDuration()).isEqualTo(10L);
            assertThat(test.getRetryAttempts())
                .extracting(RetryAttempt::getStatus, RetryAttempt::getMessage)
                .containsExactly(tuple(FAILED, "Connection refused"));
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A success of a container that never failed adds nothing")
    void successWithoutAnEarlierFailureAddsNothing() {
        var run = ReportMerger.merge(null, RUN, List.of(attempt("id-1", PASSED, 150L)),
            List.of(new ContainerSuccess(CLASS_ID, 100L, 200L)), 100L, 200L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("id-1");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A container that fails in every round stays failed, with its history")
    void containerFailingEveryRoundStaysFailed() {
        var existing = report(RUN, 50L, 90L, true, attempt(CLASS_ID, FAILED, 60L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt(CLASS_ID, FAILED, 160L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(FAILED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A success can't recover a failure written by another run")
    void successDoesNotRecoverAnotherRunsFailure() {
        var existing = report("run-0", 50L, 90L, true, attempt(CLASS_ID, FAILED, 60L));

        var run = ReportMerger.merge(existing, RUN, List.of(), List.of(new ContainerSuccess(CLASS_ID, 300L, 310L)), 100L, 400L, true);

        assertThat(run.tests()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report that can't be merged is ignored, and this JVM's results are written")
    void mergeThatThrowsFallsBackToThisJvm() {
        var existing = report(RUN, 50L, 90L, true);
        existing.getResults().setTests(new ArrayList<>(Arrays.asList((Test) null)));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("id-1");
        assertThat(run.start()).isEqualTo(100L);
    }

    private static Test attempt(String testId, Test.TestStatus status, long start) {
        return Test.builder()
            .testId(testId)
            .name(testId)
            .filepath("com.example.DbTest")
            .status(status)
            .start(start)
            .stop(start + 5)
            .duration(5)
            .build();
    }

    private static CtrfJson report(String runId, long start, long stop, boolean healthy, Test... tests) {
        return CtrfJson.builder()
            .runId(runId)
            .results(Results.builder()
                .tests(new ArrayList<>(List.of(tests)))
                .summary(Summary.builder().start(start).stop(stop).build())
                .environment(Environment.builder().healthy(healthy).build())
                .build())
            .build();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :test --tests 'io.github.alexshamrai.ReportMergerTest'`

Expected: FAIL at `:compileTestJava` with "cannot find symbol" for `ReportMerger` and `ContainerSuccess`.

- [ ] **Step 3: Implement**

Create `src/main/java/io/github/alexshamrai/ContainerSuccess.java`:

```java
package io.github.alexshamrai;

/**
 * A container, such as a test class, that finished without failing. It is kept only so that a failure of the
 * same container in an earlier retry round can be reported as recovered (HP-11).
 *
 * @param uniqueId the container's JUnit unique ID
 * @param start    when the container started, in milliseconds since the epoch
 * @param stop     when it finished
 */
record ContainerSuccess(String uniqueId, long start, long stop) {
}
```

Create `src/main/java/io/github/alexshamrai/ReportMerger.java`:

```java
package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;

/**
 * Decides what one JVM's execution makes of the report on disk. It is pure: no files and no clock.
 * <p>
 * A report written by the same run ({@code runId}), for example by a parallel fork or an earlier retry round,
 * is merged: its tests are unfolded into attempts, this execution's attempts are appended, and the result is
 * folded again, so each test appears once with its whole history. A report of another run, or one without a
 * {@code runId}, is replaced.
 * <p>
 * A container that failed in an earlier attempt and finished without failing in this execution gets a final
 * passed attempt, so its failure is reported as recovered: passed and flaky (HP-11).
 */
final class ReportMerger {

    private ReportMerger() {
        // Utility class - prevent instantiation
    }

    /**
     * Merges this execution into the report on disk.
     *
     * @param existing  the report on disk, or {@code null}
     * @param runId     this JVM's run id
     * @param attempts  this execution's attempts, in the order they were recorded
     * @param successes the containers that finished without failing in this execution
     * @param start     when this execution started
     * @param stop      when it finished
     * @param healthy   whether the environment stayed healthy in this execution
     * @return the tests and run-level values to write
     */
    static MergedRun merge(CtrfJson existing, String runId, List<Test> attempts, List<ContainerSuccess> successes,
                           long start, long stop, boolean healthy) {
        if (existing != null && runId.equals(existing.getRunId())) {
            try {
                return mergeWithSameRun(existing, attempts, successes, start, stop, healthy);
            } catch (RuntimeException e) {
                System.err.println("Ignoring the existing report, which can't be merged: " + e);
            }
        }
        return new MergedRun(AttemptFolder.fold(withRecoveries(attempts, successes)), start, stop, healthy);
    }

    private static MergedRun mergeWithSameRun(CtrfJson existing, List<Test> attempts, List<ContainerSuccess> successes,
                                              long start, long stop, boolean healthy) {
        var results = existing.getResults();
        List<Test> allAttempts = new ArrayList<>();
        if (results != null && results.getTests() != null) {
            allAttempts.addAll(AttemptFolder.unfold(results.getTests()));
        }
        allAttempts.addAll(attempts);

        var summary = results != null ? results.getSummary() : null;
        long mergedStart = summary != null && summary.getStart() > 0 ? Math.min(summary.getStart(), start) : start;
        long mergedStop = summary != null ? Math.max(summary.getStop(), stop) : stop;
        var environment = results != null ? results.getEnvironment() : null;
        boolean mergedHealthy = healthy && (environment == null || environment.isHealthy());

        return new MergedRun(AttemptFolder.fold(withRecoveries(allAttempts, successes)), mergedStart, mergedStop, mergedHealthy);
    }

    /**
     * Appends a final passed attempt for each container that finished without failing and already has attempts.
     * A container has attempts only because it failed at least once. The new attempt keeps the identity of the
     * container's latest attempt, including its name: "initializationError" or "teardownError".
     */
    private static List<Test> withRecoveries(List<Test> attempts, List<ContainerSuccess> successes) {
        if (successes.isEmpty()) {
            return attempts;
        }
        Map<String, Test> latestAttempts = new HashMap<>();
        for (Test attempt : attempts) {
            if (attempt.getTestId() != null) {
                latestAttempts.put(attempt.getTestId(), attempt);
            }
        }
        List<Test> withRecoveries = new ArrayList<>(attempts);
        for (ContainerSuccess success : successes) {
            Test latest = latestAttempts.get(success.uniqueId());
            if (latest != null) {
                withRecoveries.add(Test.builder()
                    .testId(latest.getTestId())
                    .name(latest.getName())
                    .filepath(latest.getFilepath())
                    .tags(latest.getTags())
                    .status(PASSED)
                    .start(success.start())
                    .stop(success.stop())
                    .duration(success.stop() - success.start())
                    .build());
            }
        }
        return withRecoveries;
    }

    /**
     * The tests and run-level values to write.
     *
     * @param tests   one test object per test
     * @param start   when the run started
     * @param stop    when it finished
     * @param healthy whether the environment stayed healthy
     */
    record MergedRun(List<Test> tests, long start, long stop, boolean healthy) {
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.ReportMergerTest'`

Expected: PASS (12 tests).

- [ ] **Step 5: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL. Nothing calls the merger yet.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/alexshamrai/ContainerSuccess.java src/main/java/io/github/alexshamrai/ReportMerger.java \
        src/test/java/io/github/alexshamrai/ReportMergerTest.java
git commit -m "feat: add ReportMerger to merge an execution into its run's report (CB-6, HP-11)"
```

---

### Task 4: Merge into the report at the end of each execution

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/CtrfReportManager.java` (whole file)
- Modify: `src/main/java/io/github/alexshamrai/TestStateTracker.java` (class Javadoc, a new field, two new methods, `clear()`)
- Modify: `src/main/java/io/github/alexshamrai/CtrfJsonComposer.java` (`generateCtrfJson` gains `runId`)
- Modify: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java` (remove the read-at-start API and `writeResultsToFile`)
- Delete: `src/main/java/io/github/alexshamrai/ReportOrchestrator.java`, `src/main/java/io/github/alexshamrai/TestRerunHandler.java`, `src/test/java/io/github/alexshamrai/ReportOrchestratorTest.java`, `src/test/java/io/github/alexshamrai/TestRerunHandlerTest.java`
- Modify: `src/test/java/io/github/alexshamrai/CtrfReportManagerTest.java` (whole file), `src/test/java/io/github/alexshamrai/CtrfJsonComposerTest.java`, `src/test/java/io/github/alexshamrai/TestStateTrackerTest.java`

**Interfaces:**
- Consumes:
  - `RunIdResolver.resolve(ConfigReader)` (Task 1);
  - `CtrfReportFileService.update(UnaryOperator<CtrfJson>)` (Task 2);
  - `ReportMerger.merge(...)`, `ReportMerger.MergedRun` and `ContainerSuccess` (Task 3).
- Produces:
  - `public void CtrfReportManager.onContainerSuccess(String uniqueId, Long startTime)`, used by Task 5;
  - the package-private `CtrfReportManager(ConfigReader, CtrfReportFileService, TestProcessor, CtrfJsonComposer, String runId)`;
  - `public CtrfJson CtrfJsonComposer.generateCtrfJson(Summary summary, List<Test> tests, boolean isEnvironmentHealthy, String runId)`;
  - on `TestStateTracker`: `void addContainerSuccess(ContainerSuccess)` and `List<ContainerSuccess> getAllContainerSuccesses()`.

- [ ] **Step 1: Write the failing tests**

Replace the whole content of `src/test/java/io/github/alexshamrai/CtrfReportManagerTest.java` with:

```java
package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Results;
import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CtrfReportManagerTest {

    private static final String RUN_ID = "run-1";

    @Mock
    private ConfigReader configReader;
    @Mock
    private CtrfReportFileService ctrfReportFileService;
    @Mock
    private TestProcessor testProcessor;

    private CtrfReportManager reportManager;

    /**
     * Stands in for the report file: each update hands it to the merge function and keeps what comes back.
     */
    private CtrfJson reportOnDisk;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        var composer = new CtrfJsonComposer(configReader, new StartupDurationProcessor(), "Listener");
        reportManager = new CtrfReportManager(configReader, ctrfReportFileService, testProcessor, composer, RUN_ID);
        doAnswer(invocation -> {
            UnaryOperator<CtrfJson> merge = invocation.getArgument(0);
            reportOnDisk = merge.apply(reportOnDisk);
            return null;
        }).when(ctrfReportFileService).update(any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestStart should store test details in the map")
    void onTestStart_storesDetails() {
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Test Details");
        var testResult = new Test();

        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestStart(details);
        reportManager.onTestSuccess("id-1");

        verify(testProcessor).createTest(anyString(), any(TestDetails.class), anyLong());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestSuccess should process a successful test result")
    void onTestSuccess_processesResult() {
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Success Test");
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestStart(details);
        reportManager.onTestSuccess("id-1");

        verify(testProcessor).createTest(eq("Success Test"), any(TestDetails.class), anyLong());
        assertEquals(PASSED, testResult.getStatus());
        assertNull(testResult.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A result without a recorded start keeps the identity it was given, with start equal to stop")
    void onTestSuccess_withoutRecordedStart_usesGivenIdentity() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(new Test());
        var given = new TestDetails(5_000L, Set.of("payments"), "com.example.PaymentTest", "id-1", "importantBusinessTest()");

        reportManager.onTestSuccess(given);

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        var stopCaptor = ArgumentCaptor.forClass(Long.class);
        verify(testProcessor).createTest(eq("importantBusinessTest()"), detailsCaptor.capture(), stopCaptor.capture());
        assertEquals("com.example.PaymentTest", detailsCaptor.getValue().filePath());
        assertEquals(Set.of("payments"), detailsCaptor.getValue().tags());
        assertEquals(stopCaptor.getValue(), detailsCaptor.getValue().startTime());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A result with a recorded start keeps the recorded start time")
    void onTestSuccess_withRecordedStart_usesRecordedStartTime() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(new Test());
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), "com.example.PaymentTest", "id-1", "importantBusinessTest()"));

        reportManager.onTestSuccess(new TestDetails(5_000L, Set.of(), "com.example.PaymentTest", "id-1", "importantBusinessTest()"));

        var detailsCaptor = ArgumentCaptor.forClass(TestDetails.class);
        verify(testProcessor).createTest(eq("importantBusinessTest()"), detailsCaptor.capture(), anyLong());
        assertEquals(1_000L, detailsCaptor.getValue().startTime());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestFailure should process a failed test result")
    void onTestFailure_processesResult() {
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Failure Test");
        var testResult = new Test();
        var cause = new RuntimeException("error");
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestStart(details);
        reportManager.onTestFailure("id-1", cause);

        assertEquals(FAILED, testResult.getStatus());
        verify(testProcessor).setFailureDetails(testResult, cause);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestSkipped should create a skipped test")
    void onTestSkipped_createsSkippedTest() {
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Skipped Test");
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestSkipped(details, Optional.of("reason"));

        assertEquals(Test.TestStatus.SKIPPED, testResult.getStatus());
        assertEquals("reason", testResult.getMessage());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("onTestAborted should record the test as skipped with the assumption message")
    void onTestAborted_recordsSkippedTestWithAssumptionMessage() {
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Aborted Test");
        var testResult = new Test();
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(testResult);

        reportManager.onTestStart(details);
        reportManager.onTestAborted("id-1", new RuntimeException("Assumption failed: Docker not available"));

        assertEquals(Test.TestStatus.SKIPPED, testResult.getStatus());
        assertEquals("aborted", testResult.getRawStatus());
        assertEquals("Assumption failed: Docker not available", testResult.getMessage());
        verify(testProcessor, never()).setFailureDetails(any(), any());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("startTestRun reads no report: it is read only at the end, under the lock")
    void startTestRun_readsNoFile() {
        reportManager.startTestRun("Listener");
        reportManager.startTestRun("Listener");

        verifyNoInteractions(ctrfReportFileService);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun writes this execution's tests, with the run id at the root")
    void finishTestRun_writesTheReportWithTheRunId() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenReturn(Test.builder().testId("id-1").name("test").build());
        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "test"));
        reportManager.onTestSuccess("id-1");

        reportManager.finishTestRun();

        verify(ctrfReportFileService).update(any());
        assertEquals(RUN_ID, reportOnDisk.getRunId());
        assertEquals(List.of("id-1"), testIdsOnDisk());
        assertEquals(1, reportOnDisk.getResults().getSummary().getPassed());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun writes nothing when no run was started")
    void finishTestRun_withoutStart_writesNothing() {
        reportManager.finishTestRun();

        verifyNoInteractions(ctrfReportFileService);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("An unhealthy report of the same run keeps the merged report unhealthy")
    void finishTestRun_isUnhealthyWhenTheSameRunsReportIs() {
        reportOnDisk = reportOfRun(RUN_ID, false);
        reportManager.startTestRun("Listener");

        finishWithEnvironmentVariableHealthy();

        assertFalse(reportOnDisk.getResults().getEnvironment().isHealthy());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("The health of another run's report is not passed on")
    void finishTestRun_ignoresTheHealthOfAnotherRunsReport() {
        reportOnDisk = reportOfRun("run-0", false);
        reportManager.startTestRun("Listener");

        finishWithEnvironmentVariableHealthy();

        assertTrue(reportOnDisk.getResults().getEnvironment().isHealthy());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun should re-read ENV_HEALTHY and mark unhealthy if set to false")
    void finishTestRun_reReadsEnvHealthy() {
        reportManager.startTestRun("Listener");

        try (MockedStatic<EnvironmentHealthTracker> healthTracker = Mockito.mockStatic(EnvironmentHealthTracker.class)) {
            healthTracker.when(EnvironmentHealthTracker::isEnvironmentVariableUnhealthy).thenReturn(true);

            reportManager.finishTestRun();

            healthTracker.verify(EnvironmentHealthTracker::isEnvironmentVariableUnhealthy);
        }
        assertFalse(reportOnDisk.getResults().getEnvironment().isHealthy());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun should respect programmatic markEnvironmentUnhealthy call")
    void finishTestRun_respectsProgrammaticHealthChange() {
        reportManager.startTestRun("Listener");
        reportManager.markEnvironmentUnhealthyInternal();

        finishWithEnvironmentVariableHealthy();

        assertFalse(reportOnDisk.getResults().getEnvironment().isHealthy());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun reports the attempts of one test as one test with its earlier attempts")
    void finishTestRun_foldsAttemptsOfTheSameTest() {
        reportManager.startTestRun("Listener");
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("id-1").name("Rerun Test").start(1_000L).build(),
            Test.builder().testId("id-1").name("Rerun Test").start(2_000L).build());

        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestFailure("id-1", new RuntimeException("boom"));
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestSuccess("id-1");
        reportManager.finishTestRun();

        var test = testsOnDisk().get(0);
        assertEquals(1, testsOnDisk().size());
        assertEquals(PASSED, test.getStatus());
        assertEquals(1, test.getRetries());
        assertTrue(test.getFlaky());
        assertEquals(List.of(FAILED), test.getRetryAttempts().stream().map(RetryAttempt::getStatus).toList());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test in the same run's report gets this execution's attempt appended to its history")
    void finishTestRun_extendsTheHistoryInTheSameRunsReport() {
        reportOnDisk = reportOfRun(RUN_ID, true, Test.builder().testId("id-1").name("Rerun Test").status(FAILED).start(2_000L)
            .retries(1)
            .retryAttempts(List.of(RetryAttempt.builder().attempt(1).status(FAILED).start(1_000L).build()))
            .build());
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenReturn(Test.builder().testId("id-1").name("Rerun Test").start(3_000L).build());
        reportManager.startTestRun("Listener");

        reportManager.onTestStart(new TestDetails(3_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestSuccess("id-1");
        reportManager.finishTestRun();

        var test = testsOnDisk().get(0);
        assertEquals(1, testsOnDisk().size());
        assertEquals(PASSED, test.getStatus());
        assertEquals(2, test.getRetries());
        assertEquals(List.of(1_000L, 2_000L), test.getRetryAttempts().stream().map(RetryAttempt::getStart).toList());
        assertTrue(test.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Another run's report is replaced, not merged")
    void finishTestRun_replacesAnotherRunsReport() {
        reportOnDisk = reportOfRun("run-0", true, Test.builder().testId("old").name("old").status(FAILED).build());
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenReturn(Test.builder().testId("id-1").name("test").build());
        reportManager.startTestRun("Listener");

        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "test"));
        reportManager.onTestSuccess("id-1");
        reportManager.finishTestRun();

        assertEquals(List.of("id-1"), testIdsOnDisk());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A second test plan execution in the same JVM does not record the first one's attempts again")
    void finishTestRun_doesNotRepeatAttemptsOfAnEarlierExecution() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("id-1").name("First").build(),
            Test.builder().testId("id-2").name("Second").build());
        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "First"));
        reportManager.onTestSuccess("id-1");
        reportManager.finishTestRun();

        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "id-2", "Second"));
        reportManager.onTestSuccess("id-2");
        reportManager.finishTestRun();

        assertEquals(List.of("id-1", "id-2"), testIdsOnDisk());
        assertNull(testsOnDisk().get(0).getRetries());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A second execution in the same JVM, as Surefire's reruns are, merges into what the first one wrote")
    void secondExecutionInTheSameJvmKeepsTheFirstExecutionsResults() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("stable").name("stable()").build(),
            Test.builder().testId("flaky").name("flakyOnce()").build(),
            Test.builder().testId("flaky").name("flakyOnce()").build());
        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "stable", "stable()"));
        reportManager.onTestSuccess("stable");
        reportManager.onTestStart(new TestDetails(1_100L, Set.of(), null, "flaky", "flakyOnce()"));
        reportManager.onTestFailure("flaky", new AssertionError("fails on its first call"));
        reportManager.finishTestRun();

        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "flaky", "flakyOnce()"));
        reportManager.onTestSuccess("flaky");
        reportManager.finishTestRun();

        assertEquals(List.of("stable", "flaky"), testIdsOnDisk());
        var flaky = testsOnDisk().get(1);
        assertEquals(1, flaky.getRetries());
        assertTrue(flaky.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A container success without a start time uses its stop time")
    void onContainerSuccess_withoutStartUsesTheStopTime() {
        reportOnDisk = reportOfRun(RUN_ID, true, Test.builder().testId("class-1").name("initializationError").status(FAILED).build());
        reportManager.startTestRun("Listener");

        reportManager.onContainerSuccess("class-1", null);
        reportManager.finishTestRun();

        var recovered = testsOnDisk().get(0);
        assertEquals(PASSED, recovered.getStatus());
        assertTrue(recovered.getFlaky());
        assertEquals(recovered.getStart(), recovered.getStop());
        assertEquals(0L, recovered.getDuration());
    }

    private void finishWithEnvironmentVariableHealthy() {
        try (MockedStatic<EnvironmentHealthTracker> healthTracker = Mockito.mockStatic(EnvironmentHealthTracker.class)) {
            healthTracker.when(EnvironmentHealthTracker::isEnvironmentVariableUnhealthy).thenReturn(false);
            reportManager.finishTestRun();
        }
    }

    private static CtrfJson reportOfRun(String runId, boolean healthy, Test... tests) {
        return CtrfJson.builder()
            .runId(runId)
            .results(Results.builder()
                .tests(List.of(tests))
                .summary(Summary.builder().start(500L).stop(600L).build())
                .environment(Environment.builder().healthy(healthy).build())
                .build())
            .build();
    }

    private List<Test> testsOnDisk() {
        return reportOnDisk.getResults().getTests();
    }

    private List<String> testIdsOnDisk() {
        return testsOnDisk().stream().map(Test::getTestId).toList();
    }
}
```

In `src/test/java/io/github/alexshamrai/CtrfJsonComposerTest.java`, pass a run id to every `generateCtrfJson` call, and add a test for it:

```bash
python3 - <<'EOF'
import re
p='src/test/java/io/github/alexshamrai/CtrfJsonComposerTest.java'
s=open(p).read()
s, n = re.subn(r'composer\.generateCtrfJson\(([^;]*?), (true|false)\)', r'composer.generateCtrfJson(\1, \2, "run-1")', s)
assert n == 5, n
if 'import static org.junit.jupiter.api.Assertions.assertNotEquals;' not in s:
    s = s.replace('import static org.junit.jupiter.api.Assertions.assertEquals;\n',
                  'import static org.junit.jupiter.api.Assertions.assertEquals;\n'
                  'import static org.junit.jupiter.api.Assertions.assertNotEquals;\n', 1)
i = s.rstrip().rfind('}')
s = s[:i].rstrip('\n') + '''

    @org.junit.jupiter.api.Test
    void testGenerateCtrfJsonSetsTheRunIdAndANewReportIdEachTime() {
        var first = composer.generateCtrfJson(Summary.builder().build(), List.of(), true, "run-1");
        var second = composer.generateCtrfJson(Summary.builder().build(), List.of(), true, "run-1");

        assertEquals("run-1", first.getRunId());
        assertNotEquals(first.getReportId(), second.getReportId());
    }
}
'''
open(p, 'w').write(s)
EOF
grep -c '"run-1"' src/test/java/io/github/alexshamrai/CtrfJsonComposerTest.java
```

Expected: `8`, the five existing calls plus three lines in the new test.

Append to `src/test/java/io/github/alexshamrai/TestStateTrackerTest.java`, inside the class:

```java
    @org.junit.jupiter.api.Test
    @DisplayName("Container successes are kept in order and cleared with the attempts")
    void shouldKeepContainerSuccessesUntilCleared() {
        var first = new ContainerSuccess("class-1", 1L, 2L);
        var second = new ContainerSuccess("class-2", 3L, 4L);

        tracker.addContainerSuccess(first);
        tracker.addContainerSuccess(second);

        assertThat(tracker.getAllContainerSuccesses()).containsExactly(first, second);
        tracker.clear();
        assertThat(tracker.getAllContainerSuccesses()).isEmpty();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :test --tests 'io.github.alexshamrai.CtrfReportManagerTest' --tests 'io.github.alexshamrai.CtrfJsonComposerTest' --tests 'io.github.alexshamrai.TestStateTrackerTest'`

Expected: FAIL at `:compileTestJava`, with "cannot find symbol" for the 5-argument `CtrfReportManager` constructor, `onContainerSuccess`, the 4-argument `generateCtrfJson`, `addContainerSuccess` and `getAllContainerSuccesses`.

- [ ] **Step 3: Implement**

In `src/main/java/io/github/alexshamrai/TestStateTracker.java`:

Replace the class Javadoc paragraph

```java
 * It collects attempts, one per execution of a test, and the details of tests in progress. Attempts are
```

with

```java
 * It collects attempts, one per execution of a test, the containers that finished without failing, and the
 * details of tests in progress. Attempts are
```

Replace

```java
    private final Queue<Test> attempts = new ConcurrentLinkedQueue<>();
```

with

```java
    private final Queue<Test> attempts = new ConcurrentLinkedQueue<>();
    private final Queue<ContainerSuccess> containerSuccesses = new ConcurrentLinkedQueue<>();
```

Directly before the Javadoc of `putTestDetails`, which starts `     * Stores test details for an in-progress test.`, insert:

```java
    /**
     * Records a container that finished without failing.
     *
     * @param success the container and when it ran
     */
    void addContainerSuccess(ContainerSuccess success) {
        containerSuccesses.add(success);
    }

    /**
     * Returns a snapshot of the recorded container successes, in the order they were recorded.
     *
     * @return a copy of all recorded container successes
     */
    List<ContainerSuccess> getAllContainerSuccesses() {
        return new ArrayList<>(containerSuccesses);
    }

```

In `clear()`, replace

```java
        attempts.clear();
```

with

```java
        attempts.clear();
        containerSuccesses.clear();
```

In `src/main/java/io/github/alexshamrai/CtrfJsonComposer.java`, replace

```java
     * @param isEnvironmentHealthy whether the test environment is considered healthy
     * @return a complete CTRF JSON object ready for serialization
     */
    public CtrfJson generateCtrfJson(Summary summary, List<Test> tests, boolean isEnvironmentHealthy) {
```

with

```java
     * @param isEnvironmentHealthy whether the test environment is considered healthy
     * @param runId the run this report belongs to (CTRF {@code runId})
     * @return a complete CTRF JSON object ready for serialization
     */
    public CtrfJson generateCtrfJson(Summary summary, List<Test> tests, boolean isEnvironmentHealthy, String runId) {
```

and replace

```java
            .reportId(UUID.randomUUID().toString())
```

with

```java
            .reportId(UUID.randomUUID().toString())
            .runId(runId)
```

Replace the whole content of `src/main/java/io/github/alexshamrai/CtrfReportManager.java` with:

```java
package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import io.github.alexshamrai.util.SummaryUtil;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;

/**
 * Coordinates CTRF test report generation and test execution tracking.
 * <p>
 * This class follows the singleton pattern and serves as the main coordinator
 * for the CTRF reporting library. It delegates specific responsibilities to
 * specialized classes:
 * <ul>
 *   <li>{@link TestStateTracker} - collects this execution's attempts and container successes</li>
 *   <li>{@link RunIdResolver} - decides which JVMs share one report</li>
 *   <li>{@link ReportMerger} - merges this execution into the report written by the same run</li>
 *   <li>{@link CtrfReportFileService} - reads and writes the report under a lock</li>
 * </ul>
 */
public final class CtrfReportManager {

    private static final CtrfReportManager INSTANCE = new CtrfReportManager();
    private static final String ABORTED = "aborted";

    private final ConfigReader configReader;
    private final TestStateTracker stateTracker;
    private final CtrfReportFileService fileService;
    private final TestProcessor testProcessor;
    private final CtrfJsonComposer ctrfJsonComposer;
    private final String runId;

    private long testRunStartTime;
    private final AtomicBoolean isTestRunStarted = new AtomicBoolean(false);
    private String generator;
    private final AtomicBoolean isEnvironmentHealthy = new AtomicBoolean(true);

    private CtrfReportManager() {
        this.configReader = new ConfigReader();
        this.stateTracker = new TestStateTracker();
        this.fileService = new CtrfReportFileService(configReader);
        this.testProcessor = new TestProcessor(configReader);
        this.ctrfJsonComposer = null;
        this.runId = RunIdResolver.resolve(configReader);

        if (EnvironmentHealthTracker.isEnvironmentVariableUnhealthy()) {
            isEnvironmentHealthy.set(false);
        }
    }

    /**
     * Package-private constructor for testing purposes, allowing dependency injection.
     */
    CtrfReportManager(ConfigReader configReader,
                      CtrfReportFileService ctrfReportFileService,
                      TestProcessor testProcessor,
                      CtrfJsonComposer ctrfJsonComposer,
                      String runId) {
        this.configReader = configReader;
        this.stateTracker = new TestStateTracker();
        this.fileService = ctrfReportFileService;
        this.testProcessor = testProcessor;
        this.ctrfJsonComposer = ctrfJsonComposer;
        this.runId = runId;
    }

    public static CtrfReportManager getInstance() {
        return INSTANCE;
    }

    void markEnvironmentUnhealthyInternal() {
        isEnvironmentHealthy.set(false);
    }

    boolean isEnvironmentHealthyInternal() {
        return isEnvironmentHealthy.get();
    }

    void resetEnvironmentHealthForTesting() {
        isEnvironmentHealthy.set(true);
    }

    public void onTestStart(TestDetails testDetails) {
        stateTracker.putTestDetails(testDetails.uniqueId(), testDetails);
    }

    public void onTestSkipped(TestDetails testDetails, Optional<String> reason) {
        var test = testProcessor.createTest(testDetails.displayName(), testDetails, testDetails.startTime());
        test.setStatus(SKIPPED);
        reason.ifPresent(test::setMessage);
        stateTracker.addAttempt(test);
    }

    /**
     * Records a finished test. The start recorded by {@link #onTestStart} wins; when there is none, the test
     * keeps the identity it was reported with and its start time is the stop time.
     */
    private void processTestResult(TestDetails reported, Test.TestStatus status, Consumer<Test> outcomeDetails) {
        long stopTime = System.currentTimeMillis();
        TestDetails details = stateTracker.removeTestDetails(reported.uniqueId());
        if (details == null) {
            details = new TestDetails(stopTime, reported.tags(), reported.filePath(), reported.uniqueId(), reported.displayName());
        }

        var newTest = testProcessor.createTest(details.displayName(), details, stopTime);
        newTest.setStatus(status);
        outcomeDetails.accept(newTest);

        stateTracker.addAttempt(newTest);
    }

    public void onTestSuccess(String uniqueId) {
        onTestSuccess(unknownTest(uniqueId));
    }

    /**
     * Records a passed test.
     *
     * @param testDetails the test's identity, used when no start was recorded for it
     */
    public void onTestSuccess(TestDetails testDetails) {
        processTestResult(testDetails, PASSED, test -> { });
    }

    public void onTestFailure(String uniqueId, Throwable cause) {
        onTestFailure(unknownTest(uniqueId), cause);
    }

    /**
     * Records a failed test.
     *
     * @param testDetails the test's identity, used when no start was recorded for it
     * @param cause       the failure, or {@code null}
     */
    public void onTestFailure(TestDetails testDetails, Throwable cause) {
        processTestResult(testDetails, FAILED, test -> {
            if (cause != null) {
                testProcessor.setFailureDetails(test, cause);
            }
        });
    }

    public void onTestAborted(String uniqueId, Throwable cause) {
        onTestAborted(unknownTest(uniqueId), cause);
    }

    /**
     * Records a test that JUnit aborted, typically because an assumption failed. Like Gradle and Surefire,
     * the report counts it as skipped; {@code rawStatus} keeps JUnit's "aborted" and {@code message} the reason.
     *
     * @param testDetails the test's identity, used when no start was recorded for it
     * @param cause       the exception that aborted the test, or {@code null}
     */
    public void onTestAborted(TestDetails testDetails, Throwable cause) {
        processTestResult(testDetails, SKIPPED, test -> {
            test.setRawStatus(ABORTED);
            if (cause != null) {
                test.setMessage(cause.getMessage());
            }
        });
    }

    /**
     * Records a container that finished without failing, such as a test class whose {@code @BeforeAll} and
     * {@code @AfterAll} methods passed. If the same container failed in an earlier retry round, its failure is
     * then reported as recovered.
     *
     * @param uniqueId  the container's JUnit unique ID
     * @param startTime when the container started, or {@code null} if unknown; the stop time is used then
     */
    public void onContainerSuccess(String uniqueId, Long startTime) {
        long stopTime = System.currentTimeMillis();
        stateTracker.addContainerSuccess(new ContainerSuccess(uniqueId, startTime != null ? startTime : stopTime, stopTime));
    }

    /**
     * The identity of a test known only by its unique ID, for callers that recorded its start beforehand.
     */
    private static TestDetails unknownTest(String uniqueId) {
        return new TestDetails(0L, Set.of(), null, uniqueId, "Unknown Test");
    }

    /**
     * Starts recording an execution. Reads no file: the report is read only at the end, under the lock.
     *
     * @param generator the entry point that reports, written as {@code generatedBy}
     */
    public void startTestRun(String generator) {
        if (isTestRunStarted.compareAndSet(false, true)) {
            this.generator = generator;
            this.testRunStartTime = System.currentTimeMillis();
        }
    }

    /**
     * Writes this execution's results. The report on disk is read while it is locked: if it was written by the
     * same run, for example by a parallel fork or an earlier retry round, this execution is merged into it;
     * otherwise it is replaced.
     */
    public void finishTestRun() {
        if (!isTestRunStarted.compareAndSet(true, false)) {
            return;
        }

        long testRunStopTime = System.currentTimeMillis();
        refreshEnvironmentHealthFromEnvVar();

        List<Test> attempts = stateTracker.getAllAttempts();
        List<ContainerSuccess> successes = stateTracker.getAllContainerSuccesses();
        long startTime = testRunStartTime;
        boolean healthy = isEnvironmentHealthy.get();
        CtrfJsonComposer composer = composer();

        fileService.update(existing -> {
            var run = ReportMerger.merge(existing, runId, attempts, successes, startTime, testRunStopTime, healthy);
            var summary = SummaryUtil.createSummary(run.tests(), run.start(), run.stop());
            return composer.generateCtrfJson(summary, run.tests(), run.healthy(), runId);
        });

        stateTracker.clear();
    }

    private CtrfJsonComposer composer() {
        return ctrfJsonComposer != null
            ? ctrfJsonComposer
            : new CtrfJsonComposer(configReader, new StartupDurationProcessor(), generator);
    }

    private void refreshEnvironmentHealthFromEnvVar() {
        if (EnvironmentHealthTracker.isEnvironmentVariableUnhealthy()) {
            isEnvironmentHealthy.set(false);
        }
    }
}
```

Remove the read-at-start API and `writeResultsToFile` from `CtrfReportFileService`. In Task 2's layout, they sit between `update` and the helper `lockAcrossJvms`:

```bash
python3 - <<'EOF'
p='src/main/java/io/github/alexshamrai/CtrfReportFileService.java'
s=open(p).read()
start=s.index('    /**\n     * Writes the provided CTRF JSON object to a file.')
end=s.index('    /**\n     * Takes the exclusive lock on')
assert start < end
s=s[:start]+s[end:]
for old in ['import io.github.alexshamrai.ctrf.model.Test;\n', 'import java.util.Collections;\n', 'import java.util.List;\n',
            '    private CtrfJson cachedReport;\n', '    private boolean reportReadAttempted = false;\n']:
    assert s.count(old)==1, old
    s=s.replace(old,'')
open(p,'w').write(s)
EOF
git rm -q src/main/java/io/github/alexshamrai/ReportOrchestrator.java src/main/java/io/github/alexshamrai/TestRerunHandler.java \
          src/test/java/io/github/alexshamrai/ReportOrchestratorTest.java src/test/java/io/github/alexshamrai/TestRerunHandlerTest.java
grep -rn 'getExisting\|writeResultsToFile\|cachedReport\|ReportOrchestrator\|TestRerunHandler' src/ || echo "no references left"
```

Expected: `no references left`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.CtrfReportManagerTest' --tests 'io.github.alexshamrai.CtrfJsonComposerTest' --tests 'io.github.alexshamrai.TestStateTrackerTest'`

Expected: PASS. `CtrfReportManagerTest` has 20 tests.

- [ ] **Step 5: Prove the manager tests need the merge**

Don't restore with `git checkout`: it would bring back `master`'s version and lose this task's rewrite. Undo the edit with the reverse `sed`:

```bash
F=src/main/java/io/github/alexshamrai/CtrfReportManager.java
sed -i '' 's/ReportMerger.merge(existing, runId,/ReportMerger.merge(null, runId,/' $F
grep -c 'ReportMerger.merge(null' $F
./gradlew :test --tests 'io.github.alexshamrai.CtrfReportManagerTest' 2>&1 | grep -E 'CtrfReportManagerTest > .* FAILED|BUILD'
sed -i '' 's/ReportMerger.merge(null, runId,/ReportMerger.merge(existing, runId,/' $F
grep -c 'ReportMerger.merge(existing, runId,' $F
```

Expected:
- the first `grep` prints `1`;
- these 5 tests FAIL:
  - `finishTestRun_isUnhealthyWhenTheSameRunsReportIs`;
  - `finishTestRun_extendsTheHistoryInTheSameRunsReport`;
  - `finishTestRun_doesNotRepeatAttemptsOfAnEarlierExecution`;
  - `secondExecutionInTheSameJvmKeepsTheFirstExecutionsResults`;
  - `onContainerSuccess_withoutStartUsesTheStopTime`.
- the last `grep` prints `1`.

- [ ] **Step 6: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL, including the existing scenario tests. The HP-6 retry-round scenarios now merge through `update`; their JVMs resolve the same run because the test JVM is their nearest ancestor JVM.

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java/io/github/alexshamrai src/test/java/io/github/alexshamrai
git status --short
git commit -m "feat: merge each execution into its run's report at the end, under the lock (CB-6)"
```

Expected `git status --short` before committing: the modified and deleted files above, plus `?? ctrf-report.json`, which stays untracked.

---

### Task 5: Record container successes in the listener and the extension (HP-11)

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/launcher/CtrfListener.java` (`executionFinished`, class Javadoc)
- Modify: `src/main/java/io/github/alexshamrai/jupiter/CtrfExtension.java` (`afterAll`, new `reportClassSuccess`)
- Modify: `src/test/java/io/github/alexshamrai/launcher/CtrfListenerTest.java`
- Modify: `src/test/java/io/github/alexshamrai/jupiter/CtrfExtensionTest.java`

**Interfaces:**
- Consumes: `CtrfReportManager.onContainerSuccess(String uniqueId, Long startTime)` (Task 4).
- Produces: nothing new for later tasks. Task 6's scenarios depend on these calls.

- [ ] **Step 1: Write the failing tests**

Append to `src/test/java/io/github/alexshamrai/launcher/CtrfListenerTest.java`, inside the class, and add `import static org.mockito.ArgumentMatchers.notNull;` to its static imports:

```java
    @Test
    void executionFinished_shouldRecordSuccessOfAClass() {
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getParentId()).thenReturn(Optional.of("[engine:junit-jupiter]"));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager).onContainerSuccess(eq(TEST_UNIQUE_ID), notNull());
    }

    @Test
    void executionFinished_shouldNotRecordSuccessOfAnEngine() {
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getParentId()).thenReturn(Optional.empty());
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.SUCCESSFUL);

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager, never()).onContainerSuccess(anyString(), any());
    }

    @Test
    void executionFinished_shouldNotRecordSuccessOfAFailedContainer() {
        when(testIdentifier.isTest()).thenReturn(false);
        when(testIdentifier.isContainer()).thenReturn(true);
        when(testIdentifier.getParentId()).thenReturn(Optional.of("[engine:junit-jupiter]"));
        when(testIdentifier.getSource()).thenReturn(Optional.of(ClassSource.from(TEST_CLASS_NAME)));
        when(testExecutionResult.getStatus()).thenReturn(TestExecutionResult.Status.FAILED);
        when(testExecutionResult.getThrowable()).thenReturn(Optional.of(new RuntimeException("setup failed")));

        ctrfListener.executionStarted(testIdentifier);
        ctrfListener.executionFinished(testIdentifier, testExecutionResult);

        verify(reportManager, never()).onContainerSuccess(anyString(), any());
    }
```

In `src/test/java/io/github/alexshamrai/jupiter/CtrfExtensionTest.java`:
- replace the test `afterAll_withoutExecutionException_reportsNothing`, from its `@Test` line through its closing brace, with the two tests below;
- add a never-called check to both failure tests.

```bash
python3 - <<'EOF'
p='src/test/java/io/github/alexshamrai/jupiter/CtrfExtensionTest.java'
s=open(p).read()
start=s.index('    @Test\n    void afterAll_withoutExecutionException_reportsNothing()')
end=s.index('    @Test\n', start+10)
s=s[:start]+'''    @Test
    void afterAll_withoutExecutionException_recordsClassSuccess() {
        when(extensionContext.getExecutionException()).thenReturn(Optional.empty());
        when(store.get(TEST_UNIQUE_ID, ClassState.class)).thenReturn(new ClassState(1_000L));

        ctrfExtension.afterAll(extensionContext);

        verify(reportManager).onContainerSuccess(TEST_UNIQUE_ID, 1_000L);
        verify(reportManager, never()).onTestStart(any());
        verify(reportManager, never()).onTestFailure(anyString(), any());
        verify(reportManager, never()).onTestFailure(any(TestDetails.class), any());
    }

    @Test
    void afterAll_withoutClassState_recordsSuccessWithoutStart() {
        when(extensionContext.getExecutionException()).thenReturn(Optional.empty());

        ctrfExtension.afterAll(extensionContext);

        verify(reportManager).onContainerSuccess(TEST_UNIQUE_ID, null);
    }

'''+s[end:]
old='        verify(reportManager).onTestFailure(TEST_UNIQUE_ID, cause);\n    }\n'
assert s.count(old)==2, s.count(old)
s=s.replace(old, '        verify(reportManager).onTestFailure(TEST_UNIQUE_ID, cause);\n'
                 '        verify(reportManager, never()).onContainerSuccess(anyString(), any());\n    }\n')
open(p,'w').write(s)
EOF
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :test --tests 'io.github.alexshamrai.launcher.CtrfListenerTest' --tests 'io.github.alexshamrai.jupiter.CtrfExtensionTest'`

Expected: FAIL. Nothing records a container success yet, so these 3 tests fail with "Wanted but not invoked: reportManager.onContainerSuccess(...)":
- `executionFinished_shouldRecordSuccessOfAClass`;
- `afterAll_withoutExecutionException_recordsClassSuccess`;
- `afterAll_withoutClassState_recordsSuccessWithoutStart`.

The never-called checks pass.

- [ ] **Step 3: Implement**

In `src/main/java/io/github/alexshamrai/launcher/CtrfListener.java`, replace

```java
            reportTestsNotStartedAsSkipped(testIdentifier, reason);
        }
        // It ran: an enclosing container aborted later must not report it as skipped
```

with

```java
            reportTestsNotStartedAsSkipped(testIdentifier, reason);
        } else if (testIdentifier.isContainer() && testExecutionResult.getStatus() == TestExecutionResult.Status.SUCCESSFUL
            && testIdentifier.getParentId().isPresent()) {
            // Engines are left out: every fork runs them, so one fork's engine success must not look like the
            // recovery of another fork's engine failure
            reportManager.onContainerSuccess(uniqueId, startTime);
        }
        // It ran: an enclosing container aborted later must not report it as skipped
```

and in its class Javadoc, replace

```java
 * <p>
 * The listener can be configured through a {@code ctrf.properties} file placed in the classpath.
```

with

```java
 * <p>
 * A container that finishes successfully is recorded too, so that its failure in an earlier retry round is
 * reported as recovered: passed and flaky.
 * <p>
 * The listener can be configured through a {@code ctrf.properties} file placed in the classpath.
```

In `src/main/java/io/github/alexshamrai/jupiter/CtrfExtension.java`, replace

```java
    /**
     * Reports the class as failed if it failed outside its tests. Runs after the class's {@code @AfterAll}
     * methods, and also when a {@code @BeforeAll} method or a {@code BeforeAllCallback} failed, but not when
     * the class could not be created (see the class documentation).
     *
     * @param context the context of the finished test class
     */
    @Override
    public void afterAll(ExtensionContext context) {
        context.getExecutionException().ifPresent(error -> reportClassFailure(context, error));
    }
```

with

```java
    /**
     * Reports the class as failed if it failed outside its tests, and otherwise records that it succeeded, so
     * that its failure in an earlier retry round is reported as recovered: passed and flaky. Runs after the
     * class's {@code @AfterAll} methods, and also when a {@code @BeforeAll} method or a
     * {@code BeforeAllCallback} failed, but not when the class could not be created (see the class
     * documentation).
     *
     * @param context the context of the finished test class
     */
    @Override
    public void afterAll(ExtensionContext context) {
        context.getExecutionException().ifPresentOrElse(
            error -> reportClassFailure(context, error),
            () -> reportClassSuccess(context));
    }
```

and directly before the Javadoc line `     * Marks the nearest enclosing class, starting at the given context, as having started tests.` (that is, before its `    /**`), insert:

```java
    private void reportClassSuccess(ExtensionContext context) {
        ClassState state = classStateOf(context);
        reportManager.onContainerSuccess(context.getUniqueId(), state != null ? state.startTime() : null);
    }

```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.launcher.CtrfListenerTest' --tests 'io.github.alexshamrai.jupiter.CtrfExtensionTest'`

Expected: PASS.

- [ ] **Step 5: Prove the engine exclusion is tested**

The listener file exists on `master`, so restore it with the reverse `sed`, not `git checkout`:

```bash
F=src/main/java/io/github/alexshamrai/launcher/CtrfListener.java
sed -i '' 's/            && testIdentifier.getParentId().isPresent()) {/            \&\& true) {/' $F
grep -c '&& true) {' $F
./gradlew :test --tests 'io.github.alexshamrai.launcher.CtrfListenerTest' 2>&1 | grep -E 'CtrfListenerTest > .* FAILED|BUILD'
sed -i '' 's/            && true) {/            \&\& testIdentifier.getParentId().isPresent()) {/' $F
grep -c 'getParentId().isPresent()) {' $F
```

Expected:
- the first `grep` prints `1`;
- only `executionFinished_shouldNotRecordSuccessOfAnEngine` FAILS;
- the last `grep` prints `1`.

- [ ] **Step 6: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/alexshamrai/launcher/CtrfListener.java src/main/java/io/github/alexshamrai/jupiter/CtrfExtension.java \
        src/test/java/io/github/alexshamrai/launcher/CtrfListenerTest.java src/test/java/io/github/alexshamrai/jupiter/CtrfExtensionTest.java
git commit -m "feat: record classes that finish without failing, so a recovered failure shows as flaky (HP-11)"
```

---

### Task 6: Scenario tests in forked JVMs

**Files:**
- Modify: `src/test/java/io/github/alexshamrai/scenario/ScenarioRunner.java` (whole file)
- Modify: `src/test/java/io/github/alexshamrai/scenario/ScenarioMain.java` (whole file)
- Create, in `src/test/java/io/github/alexshamrai/scenario/fixtures/`:
  - `ForkBarrier.java`, `ForkOneFixture.java`, `ForkTwoFixture.java`, `ForkThreeFixture.java`;
  - `RerunFixture.java`;
  - `SetupRecoversFixture.java`, `SetupAlwaysFailsFixture.java`, `TeardownRecoversFixture.java`;
  - `ExtSetupRecoversFixture.java`, `ExtSetupAlwaysFailsFixture.java`, `ExtTeardownRecoversFixture.java`.
- Create: `src/test/java/io/github/alexshamrai/scenario/PersistenceScenarioTest.java`, `src/test/java/io/github/alexshamrai/scenario/ContainerRecoveryScenarioTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1–5, through real JVMs.
- Produces, in `ScenarioRunner`:
  - the existing methods, unchanged: `runWithListener`, `runWithExtension`, `runRoundsWithListener(int, Class<?>...)`, `runRoundsWithExtension(int, Class<?>...)`;
  - new: `runListenerRerunningFailures(Class<?>...)`, `runOneAfterAnother(Jvm...)`, `runAtOnce(Jvm...)`;
  - `record ScenarioRunner.Jvm(String mode, List<Class<?>> fixtures, Map<String, String> systemProperties, Map<String, String> environment)`, with `listener(Class<?>...)`, `extension(Class<?>...)`, `withSystemProperty(String, String)` and `withEnvironment(String, String)`;
  - `ScenarioMain` gains the mode `listener-rerun`.

- [ ] **Step 1: Generalise `ScenarioRunner` and `ScenarioMain`**

Replace the whole content of `src/test/java/io/github/alexshamrai/scenario/ScenarioRunner.java` with:

```java
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

    static Result runWithExtension(Class<?>... fixtures) throws IOException, InterruptedException {
        return runOneAfterAnother(Jvm.extension(fixtures));
    }

    /**
     * Runs the fixtures {@code rounds} times through the listener, each round in a new JVM that shares the
     * working directory and the report file with the earlier rounds, as the Gradle test-retry plugin does.
     */
    static Result runRoundsWithListener(int rounds, Class<?>... fixtures) throws IOException, InterruptedException {
        return runOneAfterAnother(repeat(Jvm.listener(fixtures), rounds));
    }

    /**
     * Like {@link #runRoundsWithListener}, reporting only through the fixtures' own extensions.
     */
    static Result runRoundsWithExtension(int rounds, Class<?>... fixtures) throws IOException, InterruptedException {
        return runOneAfterAnother(repeat(Jvm.extension(fixtures), rounds));
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
            log = workspace.await(workspace.start(jvms[i], i), i);
        }
        return workspace.result(junitCounts(log), log);
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
            String log = workspace.await(processes.get(i), i);
            junitCounts(log).forEach((key, value) -> counts.merge(key, value, Long::sum));
            logs.append(log);
        }
        return workspace.result(counts, logs.toString());
    }

    private static Jvm[] repeat(Jvm jvm, int times) {
        Jvm[] jvms = new Jvm[times];
        Arrays.fill(jvms, jvm);
        return jvms;
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
     * One JVM to start: how it reports ("listener", "extension" or "listener-rerun", see {@link ScenarioMain}),
     * the fixtures it runs, and the system properties and environment variables it gets in addition.
     */
    record Jvm(String mode, List<Class<?>> fixtures, Map<String, String> systemProperties, Map<String, String> environment) {

        static Jvm listener(Class<?>... fixtures) {
            return new Jvm("listener", List.of(fixtures), Map.of(), Map.of());
        }

        static Jvm extension(Class<?>... fixtures) {
            return new Jvm("extension", List.of(fixtures), Map.of(), Map.of());
        }

        Jvm withSystemProperty(String key, String value) {
            Map<String, String> properties = new LinkedHashMap<>(systemProperties);
            properties.put(key, value);
            return new Jvm(mode, fixtures, properties, environment);
        }

        Jvm withEnvironment(String key, String value) {
            Map<String, String> variables = new LinkedHashMap<>(environment);
            variables.put(key, value);
            return new Jvm(mode, fixtures, systemProperties, variables);
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

        String await(Process process, int index) throws IOException, InterruptedException {
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("Scenario JVM " + index + " timed out:\n" + Files.readString(output(index)));
            }
            String log = Files.readString(output(index));
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Scenario JVM " + index + " exited with " + process.exitValue() + ":\n" + log);
            }
            return log;
        }

        Result result(Map<String, Long> junitCounts, String log) throws IOException {
            if (!Files.exists(report)) {
                throw new IllegalStateException("The scenario JVMs wrote no report:\n" + log);
            }
            return new Result(new ObjectMapper().readValue(report.toFile(), CtrfJson.class), junitCounts, log);
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
            return tests().stream().filter(test -> fixture.getName().equals(test.getFilepath())).toList();
        }
    }
}
```

Replace the whole content of `src/test/java/io/github/alexshamrai/scenario/ScenarioMain.java` with:

```java
package io.github.alexshamrai.scenario;

import io.github.alexshamrai.launcher.CtrfListener;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.util.Arrays;
import java.util.List;

/**
 * Entry point of the JVM that {@link ScenarioRunner} starts: runs fixture classes on the JUnit Platform,
 * reporting through {@link CtrfListener} ("listener") or only through the fixtures' own extensions ("extension").
 * In "listener-rerun" mode it then runs the failed tests again in the same JVM, through the same launcher, as
 * Surefire's {@code rerunFailingTestsCount} does.
 *
 * <p>Usage: {@code ScenarioMain <listener|extension|listener-rerun> <fixture class name>...}
 */
public final class ScenarioMain {

    static final String SUMMARY_PREFIX = "JUNIT_SUMMARY ";

    private ScenarioMain() {
    }

    public static void main(String[] args) {
        String mode = args[0];
        var config = LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(false);
        if (mode.startsWith("listener")) {
            config.addTestExecutionListeners(new CtrfListener());
        }
        Launcher launcher = LauncherFactory.create(config.build());

        var summary = execute(launcher, Arrays.stream(args, 1, args.length).map(DiscoverySelectors::selectClass).toList());
        if ("listener-rerun".equals(mode) && !summary.getFailures().isEmpty()) {
            summary = execute(launcher, summary.getFailures().stream()
                .map(failure -> DiscoverySelectors.selectUniqueId(failure.getTestIdentifier().getUniqueId()))
                .toList());
        }

        System.out.println(SUMMARY_PREFIX
            + "found=" + summary.getTestsFoundCount()
            + " succeeded=" + summary.getTestsSucceededCount()
            + " failed=" + summary.getTestsFailedCount()
            + " aborted=" + summary.getTestsAbortedCount()
            + " skipped=" + summary.getTestsSkippedCount()
            + " containersFailed=" + summary.getContainersFailedCount()
            + " containersSkipped=" + summary.getContainersSkippedCount());
    }

    private static TestExecutionSummary execute(Launcher launcher, List<? extends DiscoverySelector> selectors) {
        var request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectors)
            .configurationParameter("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer$ClassName")
            .configurationParameter("junit.jupiter.testmethod.order.default", "org.junit.jupiter.api.MethodOrderer$MethodName")
            .build();
        var junitSummary = new SummaryGeneratingListener();
        launcher.execute(request, junitSummary);
        return junitSummary.getSummary();
    }
}
```

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL. The existing scenario tests behave as before on the new runner.

- [ ] **Step 2: Add the fixtures**

All fixtures live in `src/test/java/io/github/alexshamrai/scenario/fixtures/` and carry `@Tag("scenario-fixture")`, so the build's own test task skips them.

`ForkBarrier.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Lets fixtures that run in separate JVMs wait for each other through marker files in the shared working
 * directory, so that the JVMs finish at about the same time and write the report concurrently.
 */
public final class ForkBarrier {

    private ForkBarrier() {
    }

    /**
     * Marks this JVM as ready, then waits until {@code parties} JVMs are.
     *
     * @param name    a name unique to this JVM
     * @param parties how many JVMs to wait for
     */
    public static void await(String name, int parties) throws IOException, InterruptedException {
        Files.createFile(Path.of(name + ".ready"));
        long deadline = System.currentTimeMillis() + 60_000;
        while (ready() < parties) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Only " + ready() + " of " + parties + " JVMs arrived");
            }
            Thread.sleep(5);
        }
    }

    private static long ready() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("."))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".ready")).count();
        }
    }
}
```

`ForkOneFixture.java`. Methods run in name order, so the failing test runs first and the barrier last:

```java
package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * One of three fixtures that run in parallel JVMs of one run. Its last test waits for the other JVMs, so all
 * three finish together.
 */
@Tag("scenario-fixture")
public class ForkOneFixture {

    @Test
    void failsInThisFork() {
        fail("fails in fork one");
    }

    @Test
    void waitsForTheOtherForks() throws Exception {
        ForkBarrier.await("fork-one", 3);
    }
}
```

`ForkTwoFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * One of three fixtures that run in parallel JVMs of one run. Its last test waits for the other JVMs, so all
 * three finish together.
 */
@Tag("scenario-fixture")
public class ForkTwoFixture {

    @Test
    void failsInThisFork() {
        fail("fails in fork two");
    }

    @Test
    void waitsForTheOtherForks() throws Exception {
        ForkBarrier.await("fork-two", 3);
    }
}
```

`ForkThreeFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * One of three fixtures that run in parallel JVMs of one run. Its last test waits for the other JVMs, so all
 * three finish together.
 */
@Tag("scenario-fixture")
public class ForkThreeFixture {

    @Test
    void failsInThisFork() {
        fail("fails in fork three");
    }

    @Test
    void waitsForTheOtherForks() throws Exception {
        ForkBarrier.await("fork-three", 3);
    }
}
```

`RerunFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A test that fails only on its first call in a JVM, for reruns in the same JVM, and a stable one.
 */
@Tag("scenario-fixture")
public class RerunFixture {

    private static final AtomicInteger FLAKY_CALLS = new AtomicInteger();

    @Test
    void flakyOnce() {
        if (FLAKY_CALLS.incrementAndGet() == 1) {
            fail("fails on its first call");
        }
    }

    @Test
    void stable() {
        // passes
    }
}
```

`SetupRecoversFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A class whose {@code @BeforeAll} fails in the first round in a working directory and passes afterwards.
 */
@Tag("scenario-fixture")
public class SetupRecoversFixture {

    @BeforeAll
    static void connect() throws IOException {
        Path marker = Path.of("setup-recovers.marker");
        if (Files.notExists(marker)) {
            Files.createFile(marker);
            throw new IllegalStateException("setup fails in the first round");
        }
    }

    @Test
    void works() {
        // passes once the class is set up
    }
}
```

`SetupAlwaysFailsFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A class whose {@code @BeforeAll} fails in every round.
 */
@Tag("scenario-fixture")
public class SetupAlwaysFailsFixture {

    @BeforeAll
    static void connect() {
        throw new IllegalStateException("setup fails in every round");
    }

    @Test
    void works() {
        // never runs
    }
}
```

`TeardownRecoversFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A class whose {@code @AfterAll} fails in the first round in a working directory and passes afterwards.
 */
@Tag("scenario-fixture")
public class TeardownRecoversFixture {

    @AfterAll
    static void disconnect() throws IOException {
        Path marker = Path.of("teardown-recovers.marker");
        if (Files.notExists(marker)) {
            Files.createFile(marker);
            throw new IllegalStateException("teardown fails in the first round");
        }
    }

    @Test
    void works() {
        // passes in every round
    }
}
```

`ExtSetupRecoversFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The same as {@link SetupRecoversFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtSetupRecoversFixture {

    @BeforeAll
    static void connect() throws IOException {
        Path marker = Path.of("ext-setup-recovers.marker");
        if (Files.notExists(marker)) {
            Files.createFile(marker);
            throw new IllegalStateException("setup fails in the first round");
        }
    }

    @Test
    void works() {
        // passes once the class is set up
    }
}
```

`ExtSetupAlwaysFailsFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The same as {@link SetupAlwaysFailsFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtSetupAlwaysFailsFixture {

    @BeforeAll
    static void connect() {
        throw new IllegalStateException("setup fails in every round");
    }

    @Test
    void works() {
        // never runs
    }
}
```

`ExtTeardownRecoversFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The same as {@link TeardownRecoversFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtTeardownRecoversFixture {

    @AfterAll
    static void disconnect() throws IOException {
        Path marker = Path.of("ext-teardown-recovers.marker");
        if (Files.notExists(marker)) {
            Files.createFile(marker);
            throw new IllegalStateException("teardown fails in the first round");
        }
    }

    @Test
    void works() {
        // passes in every round
    }
}
```

- [ ] **Step 3: Write the scenario tests**

Create `src/test/java/io/github/alexshamrai/scenario/PersistenceScenarioTest.java`:

```java
package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.ScenarioRunner.Jvm;
import io.github.alexshamrai.scenario.fixtures.ForkOneFixture;
import io.github.alexshamrai.scenario.fixtures.ForkThreeFixture;
import io.github.alexshamrai.scenario.fixtures.ForkTwoFixture;
import io.github.alexshamrai.scenario.fixtures.RerunFixture;
import io.github.alexshamrai.scenario.fixtures.RetryRoundsFixture;

import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Several JVMs writing one report (CB-6). Forks of one run merge, a new run replaces the report, and a second
 * execution in the same JVM merges into the first.
 */
class PersistenceScenarioTest {

    @org.junit.jupiter.api.Test
    void forksOfOneRunFinishingTogetherAllLandInOneReport() throws Exception {
        var result = ScenarioRunner.runAtOnce(
            Jvm.listener(ForkOneFixture.class).withSystemProperty("ctrf.run.id", "forks"),
            Jvm.listener(ForkTwoFixture.class).withSystemProperty("ctrf.run.id", "forks"),
            Jvm.listener(ForkThreeFixture.class).withSystemProperty("ctrf.run.id", "forks"));

        assertThat(result.report().getRunId()).isEqualTo("forks");
        for (Class<?> fixture : List.of(ForkOneFixture.class, ForkTwoFixture.class, ForkThreeFixture.class)) {
            assertThat(result.testsOf(fixture)).as("tests of %s", fixture.getSimpleName()).hasSize(2);
        }
        var summary = result.report().getResults().getSummary();
        assertThat(summary.getTests()).isEqualTo(6);
        assertThat(summary.getPassed()).isEqualTo(3);
        assertThat(summary.getFailed()).isEqualTo(3);
    }

    @org.junit.jupiter.api.Test
    void newBuildReplacesTheReportOfAnEarlierBuild() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(
            Jvm.listener(RetryRoundsFixture.class).withSystemProperty("ctrf.run.id", "build-1").withEnvironment("ENV_HEALTHY", "false"),
            Jvm.listener(RerunFixture.class).withSystemProperty("ctrf.run.id", "build-2"));

        assertThat(result.report().getRunId()).isEqualTo("build-2");
        assertThat(result.testsOf(RetryRoundsFixture.class)).isEmpty();
        assertThat(result.testsOf(RerunFixture.class)).hasSize(2);
        assertThat(result.tests()).allSatisfy(test -> assertThat(test.getRetries()).isNull());
        assertThat(result.report().getResults().getEnvironment().isHealthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void newCiRunReplacesTheReportOfAnEarlierCiRun() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(
            Jvm.listener(RetryRoundsFixture.class).withEnvironment("GITHUB_RUN_ID", "100").withEnvironment("GITHUB_RUN_ATTEMPT", "1"),
            Jvm.listener(RerunFixture.class).withEnvironment("GITHUB_RUN_ID", "101").withEnvironment("GITHUB_RUN_ATTEMPT", "1"));

        assertThat(result.report().getRunId()).isEqualTo("github-101-1");
        assertThat(result.testsOf(RetryRoundsFixture.class)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void sameCiRunMergesItsJvms() throws Exception {
        var result = ScenarioRunner.runOneAfterAnother(
            Jvm.listener(RetryRoundsFixture.class).withEnvironment("GITHUB_RUN_ID", "100").withEnvironment("GITHUB_RUN_ATTEMPT", "1"),
            Jvm.listener(RerunFixture.class).withEnvironment("GITHUB_RUN_ID", "100").withEnvironment("GITHUB_RUN_ATTEMPT", "1"));

        assertThat(result.report().getRunId()).isEqualTo("github-100-1");
        assertThat(result.testsOf(RetryRoundsFixture.class)).hasSize(2);
        assertThat(result.testsOf(RerunFixture.class)).hasSize(2);
    }

    @org.junit.jupiter.api.Test
    void secondExecutionInTheSameJvmKeepsTheFirstExecutionsResults() throws Exception {
        var result = ScenarioRunner.runListenerRerunningFailures(RerunFixture.class);

        Test stable = onlyTestNamed(result, "stable()");
        assertThat(stable.getStatus()).isEqualTo(PASSED);
        assertThat(stable.getRetries()).isNull();
        Test flaky = onlyTestNamed(result, "flakyOnce()");
        assertThat(flaky.getStatus()).isEqualTo(PASSED);
        assertThat(flaky.getRetries()).isEqualTo(1);
        assertThat(flaky.getFlaky()).isTrue();
        assertThat(result.report().getResults().getSummary().getFlaky()).isEqualTo(1);
    }

    private static Test onlyTestNamed(ScenarioRunner.Result result, String name) {
        var matches = result.tests().stream().filter(test -> name.equals(test.getName())).toList();
        assertThat(matches).as("entries named %s", name).hasSize(1);
        return matches.get(0);
    }
}
```

Create `src/test/java/io/github/alexshamrai/scenario/ContainerRecoveryScenarioTest.java`:

```java
package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.ExtSetupAlwaysFailsFixture;
import io.github.alexshamrai.scenario.fixtures.ExtSetupRecoversFixture;
import io.github.alexshamrai.scenario.fixtures.ExtTeardownRecoversFixture;
import io.github.alexshamrai.scenario.fixtures.SetupAlwaysFailsFixture;
import io.github.alexshamrai.scenario.fixtures.SetupRecoversFixture;
import io.github.alexshamrai.scenario.fixtures.TeardownRecoversFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A class whose setup or teardown fails in one retry round and succeeds in the next is reported as passed and
 * flaky (HP-11); one that fails in every round stays failed.
 */
class ContainerRecoveryScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerReportsASetupFailureThatRecoversAsPassedAndFlaky() throws Exception {
        assertRecovered(ScenarioRunner.runRoundsWithListener(2, SetupRecoversFixture.class),
            "initializationError", "setup fails in the first round");
    }

    @org.junit.jupiter.api.Test
    void extensionReportsASetupFailureThatRecoversAsPassedAndFlaky() throws Exception {
        assertRecovered(ScenarioRunner.runRoundsWithExtension(2, ExtSetupRecoversFixture.class),
            "initializationError", "setup fails in the first round");
    }

    @org.junit.jupiter.api.Test
    void listenerReportsATeardownFailureThatRecoversAsPassedAndFlaky() throws Exception {
        assertRecovered(ScenarioRunner.runRoundsWithListener(2, TeardownRecoversFixture.class),
            "teardownError", "teardown fails in the first round");
    }

    @org.junit.jupiter.api.Test
    void extensionReportsATeardownFailureThatRecoversAsPassedAndFlaky() throws Exception {
        assertRecovered(ScenarioRunner.runRoundsWithExtension(2, ExtTeardownRecoversFixture.class),
            "teardownError", "teardown fails in the first round");
    }

    @org.junit.jupiter.api.Test
    void listenerReportsASetupFailureInEveryRoundAsFailed() throws Exception {
        assertFailedInEveryRound(ScenarioRunner.runRoundsWithListener(2, SetupAlwaysFailsFixture.class));
    }

    @org.junit.jupiter.api.Test
    void extensionReportsASetupFailureInEveryRoundAsFailed() throws Exception {
        assertFailedInEveryRound(ScenarioRunner.runRoundsWithExtension(2, ExtSetupAlwaysFailsFixture.class));
    }

    private static void assertRecovered(ScenarioRunner.Result result, String name, String firstFailure) {
        Test entry = onlyTestNamed(result, name);
        assertThat(entry.getStatus()).isEqualTo(PASSED);
        assertThat(entry.getRetries()).isEqualTo(1);
        assertThat(entry.getFlaky()).isTrue();
        assertThat(entry.getRetryAttempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getStatus()).isEqualTo(FAILED);
            assertThat(attempt.getMessage()).contains(firstFailure);
        });
        var summary = result.report().getResults().getSummary();
        assertThat(summary.getFailed()).isZero();
        assertThat(summary.getFlaky()).isEqualTo(1);
    }

    private static void assertFailedInEveryRound(ScenarioRunner.Result result) {
        Test entry = onlyTestNamed(result, "initializationError");
        assertThat(entry.getStatus()).isEqualTo(FAILED);
        assertThat(entry.getRetries()).isEqualTo(1);
        assertThat(entry.getFlaky()).isNull();
        assertThat(entry.getRetryAttempts()).extracting(RetryAttempt::getStatus).containsExactly(FAILED);
        var summary = result.report().getResults().getSummary();
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getFlaky()).isZero();
    }

    private static Test onlyTestNamed(ScenarioRunner.Result result, String name) {
        var matches = result.tests().stream().filter(test -> name.equals(test.getName())).toList();
        assertThat(matches).as("entries named %s", name).hasSize(1);
        return matches.get(0);
    }
}
```

- [ ] **Step 4: Run the scenarios to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.scenario.PersistenceScenarioTest' --tests 'io.github.alexshamrai.scenario.ContainerRecoveryScenarioTest'`

Expected: PASS. `PersistenceScenarioTest` has 5 tests and `ContainerRecoveryScenarioTest` has 6.

- [ ] **Step 5: Prove the scenarios fail without the behaviour they check**

`ReportMerger.java` hasn't changed since its Task 3 commit, so `git checkout` restores it safely:

```bash
F=src/main/java/io/github/alexshamrai/ReportMerger.java
SC="--tests io.github.alexshamrai.scenario.PersistenceScenarioTest --tests io.github.alexshamrai.scenario.ContainerRecoveryScenarioTest --tests io.github.alexshamrai.scenario.RetryScenarioTest"
# 1. never merge: every JVM replaces the report
sed -i '' 's/if (existing != null && runId.equals(existing.getRunId())) {/if (false) {/' $F
./gradlew :test ${=SC} 2>&1 | grep -E 'ScenarioTest > .* FAILED|BUILD'
git checkout -- $F
# 2. merge regardless of runId: separate builds accumulate
sed -i '' 's/if (existing != null && runId.equals(existing.getRunId())) {/if (existing != null) {/' $F
./gradlew :test ${=SC} 2>&1 | grep -E 'ScenarioTest > .* FAILED|BUILD'
git checkout -- $F
# 3. no recoveries
sed -i '' 's/if (successes.isEmpty()) {/if (true) {/' $F
./gradlew :test ${=SC} 2>&1 | grep -E 'ScenarioTest > .* FAILED|BUILD'
git checkout -- $F
git diff --stat -- src/main
```

Expected:
1. These FAIL:
   - `forksOfOneRunFinishingTogetherAllLandInOneReport`, `sameCiRunMergesItsJvms` and `secondExecutionInTheSameJvmKeepsTheFirstExecutionsResults`;
   - all 6 `ContainerRecoveryScenarioTest` tests;
   - all 4 `RetryScenarioTest` tests.

   The two "replaces" tests pass.
2. `newBuildReplacesTheReportOfAnEarlierBuild` and `newCiRunReplacesTheReportOfAnEarlierCiRun` FAIL.
3. The 4 `...RecoversAsPassedAndFlaky` tests FAIL; the 2 `...InEveryRoundAsFailed` tests pass.
4. `git diff --stat -- src/main` prints nothing.

Optional; not a gate, because the race depends on timing. Without the file lock, the forks scenario can lose a fork's tests:

```bash
G=src/main/java/io/github/alexshamrai/CtrfReportFileService.java
sed -i '' 's/FileLock lock = lockAcrossJvms(report);/FileLock lock = null;/' $G
for i in 1 2 3 4 5; do ./gradlew :test --tests 'io.github.alexshamrai.scenario.PersistenceScenarioTest.forksOfOneRunFinishingTogetherAllLandInOneReport' --rerun-tasks -q > /dev/null 2>&1 && echo "run ${i}: passed" || echo "run ${i}: FAILED"; done
git checkout -- $G
git diff --stat -- src/main
```

Record how many of the 5 runs failed for the PR description. The final `git diff --stat` prints nothing.

- [ ] **Step 6: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/io/github/alexshamrai/scenario
git commit -m "test: scenario tests for forks, new builds, same-JVM reruns and recovered class failures (CB-6, HP-11)"
```

---

### Task 7: Integration forks, CI, validator and Maven compat

**Files:**
- Modify: `integration-tests-listener/build.gradle`, `integration-tests-extension/build.gradle` (new `configureForks`)
- Modify: `.github/workflows/integration-listener.yml` (`-Dforks=2`)
- Modify: `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java` (new check)
- Modify: `compat/maven/pom.xml`, `compat/maven/pom-ctrf-first.xml` (Surefire forks and reruns)
- Create: `compat/maven/src/test/java/compat/RerunTest.java`
- Modify: `compat/maven/src/test/java/compat/CompatTest.java`, `compat/maven/src/test/java/compat/VersionProbe.java`
- Modify: `compat/check_report.py` (whole file), `compat/run.sh`

**Interfaces:**
- Consumes: the root `runId` and the merged reports (Tasks 1–5).
- Produces: `-Dforks=N` for both integration modules. CI's listener workflow runs with 2 forks.

- [ ] **Step 1: Add the validator check and watch it fail on a report without `runId`**

In `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`, insert directly before the line `    private io.github.alexshamrai.ctrf.model.Test singleTestNamed(String name) {`:

```java
    @Test
    void verifyReportHasARunId() {
        assertThat(report.getRunId()).as("root runId").isNotBlank();
    }

```

Then generate a report, strip its `runId` into a copy, and validate both:

```bash
P='-Dthreads=2 -Dctrf.build.name=system-build -Dctrf.build.number=777 -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678'
export ENV_HEALTHY=false
./gradlew clean :integration-tests-listener:test ${=P} -q 2>&1 | grep -E 'tests completed' | tail -1
python3 - <<'EOF'
import json
p='integration-tests-listener/build/test-results/ctrf-report.json'
r=json.load(open(p)); r.pop('runId', None)
json.dump(r, open('integration-tests-listener/build/test-results/no-run-id.json','w'))
EOF
./gradlew :integration-ctrf-validator:test -Dctrf.report.path=../integration-tests-listener/build/test-results/no-run-id.json ${=P} 2>&1 | grep -F ') FAILED'
./gradlew :integration-ctrf-validator:test -Dctrf.report.path=../integration-tests-listener/build/test-results/ctrf-report.json ${=P} 2>&1 | grep -E 'FAILED|BUILD' | tail -3
unset ENV_HEALTHY
```

Expected:
- only `verifyReportHasARunId()` FAILS on the copy without `runId`;
- the real report gives BUILD SUCCESSFUL.

- [ ] **Step 2: Let the integration modules run several forks**

In both `integration-tests-listener/build.gradle` and `integration-tests-extension/build.gradle`, insert directly before the line `tasks.withType(Test).configureEach {`:

```groovy
def configureForks(Test task) {
    def forks = System.getProperty("forks")
    if (forks?.isInteger() && forks.toInteger() > 1) {
        task.maxParallelForks = forks.toInteger()
        println "${task.name} - Configured number of forks: ${forks}"
    }
}

```

and replace

```groovy
    configureThreads(it)
}
```

with

```groovy
    configureThreads(it)
    configureForks(it)
}
```

Run both modules with CI's flags, the listener with 1, 2 and 4 forks:

```bash
P='-Dthreads=2 -Dctrf.build.name=system-build -Dctrf.build.number=777 -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678'
for f in 1 2 4; do
  export ENV_HEALTHY=false
  ./gradlew clean :integration-tests-listener:test ${=P} -Dforks=${f} -q 2>&1 | grep -E 'tests completed' | tail -1
  printf "listener forks=${f}: "
  python3 -c "import json;s=json.load(open('integration-tests-listener/build/test-results/ctrf-report.json'))['results']['summary'];print({k:s[k] for k in ('tests','passed','failed','skipped','flaky')})"
  ./gradlew :integration-ctrf-validator:test -Dctrf.report.path=../integration-tests-listener/build/test-results/ctrf-report.json ${=P} 2>&1 | grep -E 'FAILED|BUILD' | tail -2
done
unset ENV_HEALTHY
for f in 1 2; do
  ./gradlew clean :integration-tests-extension:test ${=P} -Dforks=${f} -q 2>&1 | grep -E 'tests completed' | tail -1
  printf "extension forks=${f}: "
  python3 -c "import json;s=json.load(open('integration-tests-extension/build/test-results/ctrf-report.json'))['results']['summary'];print({k:s[k] for k in ('tests','passed','failed','skipped','flaky')})"
  ./gradlew :integration-ctrf-validator:test -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json ${=P} 2>&1 | grep -E 'FAILED|BUILD' | tail -2
done
```

Expected:
- Gradle prints the same counts every time: `37 tests completed, 15 failed, 7 skipped` for the listener and `29 tests completed, 11 failed, 3 skipped` for the extension.
- The listener report is `{'tests': 28, 'passed': 14, 'failed': 7, 'skipped': 7, 'flaky': 1}` for 1, 2 and 4 forks. On `master` with 2 forks it was 16 tests, and with 4 forks it was 28 with `flaky` 0.
- The extension report is `{'tests': 22, 'passed': 14, 'failed': 5, 'skipped': 3, 'flaky': 1}` for 1 and 2 forks.
- The validator gives BUILD SUCCESSFUL every time.

If a count differs, don't edit the expected values. Group the report's entries by `testId` and compare them with the 1-fork report first.

- [ ] **Step 3: Run the listener workflow with 2 forks in CI**

In `.github/workflows/integration-listener.yml`, in the step "Generate report with listener", replace

```yaml
          -Dthreads=${{ github.event_name == 'workflow_dispatch' && github.event.inputs.threads || '2' }} \
```

with

```yaml
          -Dthreads=${{ github.event_name == 'workflow_dispatch' && github.event.inputs.threads || '2' }} \
          -Dforks=2 \
```

Check: `grep -n 'forks' .github/workflows/integration-listener.yml`. Expected: one line, `-Dforks=2 \`.

- [ ] **Step 4: Make the Maven compat consumers use forks and reruns**

Create `compat/maven/src/test/java/compat/RerunTest.java`:

```java
package compat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs in its own Surefire fork and fails only on its first call, so Surefire's rerunFailingTestsCount reruns it
 * in the same JVM. compat/check_report.py expects it once, with retries 1 and flaky true.
 */
class RerunTest {

    private static final AtomicInteger CALLS = new AtomicInteger();

    @Test
    void flakyOnce() throws Exception {
        VersionProbe.recordFork("RerunTest");
        if (CALLS.incrementAndGet() == 1) {
            fail("fails on its first call");
        }
    }
}
```

In `compat/maven/src/test/java/compat/CompatTest.java`, replace

```java
    void passes() throws Exception {
        VersionProbe.write();
    }
```

with

```java
    void passes() throws Exception {
        VersionProbe.write();
        VersionProbe.recordFork("CompatTest");
    }
```

In `compat/maven/src/test/java/compat/VersionProbe.java`, replace

```java
    private static String versionOf(String className) {
```

with

```java
    /**
     * Records the pid of the JVM a test class runs in, so that compat/check_report.py can tell the forks apart.
     */
    static void recordFork(String testClass) throws Exception {
        Path directory = Path.of(System.getProperty("compat.out")).getParent();
        Files.writeString(directory.resolve("fork-" + testClass + ".pid"), String.valueOf(ProcessHandle.current().pid()));
    }

    private static String versionOf(String className) {
```

Configure Surefire in both POMs. `reuseForks=false` gives each test class its own JVM, and the two JVMs run at the same time:

```bash
python3 - <<'EOF'
for p in ('compat/maven/pom.xml', 'compat/maven/pom-ctrf-first.xml'):
    s = open(p).read()
    old = '                <configuration>\n                    <systemPropertyVariables>\n'
    assert s.count(old) == 1, p
    s = s.replace(old, '                <configuration>\n'
                       '                    <forkCount>2</forkCount>\n'
                       '                    <reuseForks>false</reuseForks>\n'
                       '                    <rerunFailingTestsCount>1</rerunFailingTestsCount>\n'
                       '                    <systemPropertyVariables>\n')
    open(p, 'w').write(s)
EOF
grep -c 'rerunFailingTestsCount' compat/maven/pom.xml compat/maven/pom-ctrf-first.xml
```

Expected: `1` for each file.

Replace the whole content of `compat/check_report.py` with:

```python
#!/usr/bin/env python3
"""Check one consumer-compatibility run (see compat/run.sh).

Usage: check_report.py OUTPUT_DIR JUNIT_VERSION KIND

OUTPUT_DIR holds the consumer's ctrf-report.json and junit-versions.txt (written by its passing test).
KIND is "jupiter" (Gradle, JUnit Jupiter), "vintage" (Gradle, JUnit 4 tests only, no Jupiter on the classpath)
or "maven" (Maven, JUnit Jupiter, two test classes in separate Surefire forks, failed tests rerun once).
The run passes when the consumer's tests are in the CTRF report as expected, the report has a runId, and the
consumer ran on the JUnit version it asked for.
"""
import json
import sys
from pathlib import Path

# tests, passed, failed, flaky
EXPECTED_SUMMARY = {
    "jupiter": (2, 1, 1, 0),
    "vintage": (2, 1, 1, 0),
    # CompatTest.passes; CompatTest.fails, rerun and failing again; RerunTest.flakyOnce, passing on its rerun
    "maven": (3, 2, 1, 1),
}


def platform_version(junit_version):
    # JUnit 5.x.y ships platform 1.x.y; from JUnit 6 on, all artifacts share one version.
    major, rest = junit_version.split(".", 1)
    return "1." + rest if major == "5" else junit_version


def report_problems(out_dir, kind):
    report = out_dir / "ctrf-report.json"
    if not report.exists():
        return [f"no CTRF report at {report}"]
    document = json.loads(report.read_text())
    summary = document["results"]["summary"]
    problems = []
    counts = (summary["tests"], summary["passed"], summary["failed"], summary.get("flaky", 0))
    if counts != EXPECTED_SUMMARY[kind]:
        problems.append(f"CTRF summary tests/passed/failed/flaky is {counts}, expected {EXPECTED_SUMMARY[kind]}")
    if not document.get("runId"):
        problems.append("the CTRF report has no runId")
    if kind == "maven":
        flaky = [test for test in document["results"]["tests"] if test["name"] == "flakyOnce()"]
        if len(flaky) != 1 or flaky[0].get("retries") != 1 or flaky[0].get("flaky") is not True:
            problems.append(f"flakyOnce() should be one entry with retries 1 and flaky true, got {flaky}")
        forks = sorted(out_dir.glob("fork-*.pid"))
        pids = {fork.read_text().strip() for fork in forks}
        if len(forks) != 2 or len(pids) != 2:
            problems.append(f"expected the two test classes in two JVMs, got {[f.name for f in forks]} with pids {sorted(pids)}")
    return problems


def version_problems(out_dir, junit_version, kind):
    versions_file = out_dir / "junit-versions.txt"
    if not versions_file.exists():
        return [f"no {versions_file}: the consumer's passing test did not run"]
    versions = dict(line.split("=", 1) for line in versions_file.read_text().split())
    expected = {"platform-launcher": platform_version(junit_version)}
    if kind == "vintage":
        expected["jupiter-api"] = "absent"
        expected["vintage-engine"] = junit_version
    else:
        expected["jupiter-api"] = junit_version
    return [f"{artifact} on the test classpath is {versions.get(artifact)}, expected {want}"
            for artifact, want in expected.items() if versions.get(artifact) != want]


def main():
    if len(sys.argv) != 4 or sys.argv[3] not in EXPECTED_SUMMARY:
        sys.exit(__doc__)
    out_dir, junit_version, kind = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
    problems = report_problems(out_dir, kind) + version_problems(out_dir, junit_version, kind)
    for problem in problems:
        print("  - " + problem)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
```

In `compat/run.sh`, make three changes:

```bash
python3 - <<'EOF'
p='compat/run.sh'
s=open(p).read()
def rep(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new)
rep('#   maven/pom-ctrf-first.xml  Maven + CtrfListener, reporter declared before JUnit\n',
    '#   maven/pom-ctrf-first.xml  Maven + CtrfListener, reporter declared before JUnit\n'
    '#   (both Maven projects run their two test classes in separate forks and rerun failed tests once)\n')
rep('  if ! "$@" > "$log" 2>&1; then\n',
    '  # Without CI variables, so that every consumer relies on the run id of its build tool\'s JVM, in CI too\n'
    '  if ! env -u GITHUB_RUN_ID -u GITHUB_RUN_ATTEMPT -u CI_JOB_ID -u BUILD_TAG -u BUILD_BUILDID -u SYSTEM_JOBATTEMPT \\\n'
    '      -u CIRCLE_WORKFLOW_JOB_ID -u BUILDKITE_JOB_ID "$@" > "$log" 2>&1; then\n')
rep('    run_consumer "maven/$pom" "$COMPAT/maven/target" "$junit" jupiter \\\n',
    '    run_consumer "maven/$pom" "$COMPAT/maven/target" "$junit" maven \\\n')
open(p, 'w').write(s)
EOF
bash -n compat/run.sh && echo "run.sh parses"
```

Expected: `run.sh parses`.

- [ ] **Step 5: Prove the compat checks fail on `master`'s library**

```bash
B=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/cb6-compat
mkdir -p $B
git worktree add -q $B/master-wt origin/master
cp -R compat/. $B/master-wt/compat/
(cd $B/master-wt && compat/run.sh 6.1.3 2>&1 | tail -12)
git worktree remove --force $B/master-wt
```

Expected: all four consumers FAIL with "the CTRF report has no runId". The Maven consumers also report a wrong summary or a `flakyOnce()` that isn't flaky, because `master` loses one fork's results and the first execution of the rerun.

- [ ] **Step 6: Run the compat matrix on this branch**

Run: `compat/run.sh 5.10.5 5.14.4 6.1.3 2>&1 | tail -14`

Expected: 12 lines starting with `PASS`, then `all compatibility checks passed`.

If a Maven consumer fails, read its log in `build/compat-logs/` and the `fork-*.pid` files in `compat/maven/target/` before changing anything.

- [ ] **Step 7: Run the whole build and commit**

```bash
./gradlew clean :build 2>&1 | grep -E 'FAILED|BUILD'
git add integration-tests-listener/build.gradle integration-tests-extension/build.gradle .github/workflows/integration-listener.yml \
        integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java compat
git status --short
git commit -m "test: run the listener integration with 2 forks in CI; Maven compat with forks and reruns (CB-6)"
```

Expected: BUILD SUCCESSFUL. `git status --short` before the commit shows only the files above, plus `?? ctrf-report.json`. `compat/maven/target` must not appear; the repo's `.gitignore` already ignores it. If it does appear, unstage it.

---

### Task 8: Documentation

**Files:**
- Modify: `README.md`:
  - the configuration table;
  - the "Behavior" list in "Environment Health Tracking";
  - "Retries and Flaky Tests";
  - a new section before `## Contributing`.
- Modify: `CLAUDE.md`:
  - data flow and component table;
  - concurrency and configuration;
  - file tree;
  - "Retries and Flaky Tests", "Rerun Support" and "Environment Health Tracking".

**Interfaces:** none.

- [ ] **Step 1: Verify the per-build `ctrf.run.id` setting for both Gradle DSLs**

The Groovy version was checked during design; check it again, then the Kotlin version, in a throwaway project:

```bash
B=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/cb6-runid-probe
rm -rf $B && mkdir -p $B/groovy/src/test/java/probe $B/kotlin/src/test/java/probe
for d in groovy kotlin; do
cat > $B/${d}/src/test/java/probe/ProbeTest.java <<'EOF'
package probe;

import org.junit.jupiter.api.Test;

class ProbeTest {
    @Test
    void printsRunId() {
        System.out.println("PROBE run=" + System.getProperty("ctrf.run.id"));
    }
}
EOF
done
echo "rootProject.name = 'probe'" > $B/groovy/settings.gradle
cat > $B/groovy/build.gradle <<'EOF'
plugins { id 'java' }
repositories { mavenCentral() }
dependencies {
    testImplementation platform('org.junit:junit-bom:5.14.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
test { useJUnitPlatform(); testLogging { showStandardStreams = true } }
tasks.withType(Test).configureEach {
    doFirst { systemProperty 'ctrf.run.id', UUID.randomUUID().toString() }
}
EOF
echo 'rootProject.name = "probe"' > $B/kotlin/settings.gradle.kts
cat > $B/kotlin/build.gradle.kts <<'EOF'
plugins { java }
repositories { mavenCentral() }
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform(); testLogging { showStandardStreams = true } }
tasks.withType<Test>().configureEach {
    doFirst { (this as Test).systemProperty("ctrf.run.id", java.util.UUID.randomUUID().toString()) }
}
EOF
G=/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes/gradlew
for d in groovy kotlin; do
  for cc in --no-configuration-cache --configuration-cache --configuration-cache; do
    printf "${d} ${cc}: "; $G -p $B/${d} test --rerun-tasks ${cc} --console=plain 2>&1 | grep -E 'PROBE|FAILED|Configuration cache entry' | sed 's/^ *//' | tr '\n' ' '; echo
  done
  printf "${d} unchanged: "; $G -p $B/${d} test --configuration-cache --console=plain 2>&1 | grep -E ':test '
done
```

Expected, for each DSL:
- three different `PROBE run=<uuid>` values: one without the configuration cache, one that stores a cache entry, and one that reuses it ("Configuration cache entry reused");
- the last build prints `> Task :test UP-TO-DATE`.

If the Kotlin version doesn't compile, fix it here and use the working form in the README.

- [ ] **Step 2: Update `README.md`**

```bash
python3 - <<'PYEOF'
p='README.md'
s=open(p).read()
def rep(old, new):
    global s
    assert s.count(old) == 1, old[:80]
    s = s.replace(old, new)

rep('| `ctrf.calculate.startup.duration` | Whether to calculate and include test suite startup duration | `false`            |\n',
    '| `ctrf.calculate.startup.duration` | Whether to calculate and include test suite startup duration | `false`            |\n'
    '| `ctrf.run.id`                     | Run identifier shared by the JVMs of one build; see [Parallel Forks, Retries and Reruns](#parallel-forks-retries-and-reruns) | detected |\n')

rep('- **Persistence**: Unhealthy state is preserved across test reruns when using the same report file\n',
    '- **Persistence**: When the JVMs of one run merge their results, such as parallel forks and retry rounds, the report is unhealthy if any of them was. A report from an earlier run doesn\'t pass its state on\n')

rep('Retries that JUnit runs as separate tests with their own unique IDs, such as JUnit Pioneer\'s `@RetryingTest`, appear as separate tests.\n',
    'Retries that JUnit runs as separate tests with their own unique IDs, such as JUnit Pioneer\'s `@RetryingTest`, appear as separate tests.\n'
    '\n'
    'A test class whose setup (`@BeforeAll`) or teardown (`@AfterAll`) fails in one round and succeeds in a later one is reported the same way: its `initializationError` or `teardownError` entry becomes `passed`, with `flaky: true` and the failure in `retryAttempts`.\n')

rep('## Contributing\n', '''## Parallel Forks, Retries and Reruns

A build can run tests in several JVMs that write the same report:
- parallel forks: Gradle `maxParallelForks`, Surefire `forkCount`;
- retry rounds: the Gradle test-retry plugin;
- reruns inside one JVM: Surefire `rerunFailingTestsCount`;
- several test tasks or plugin executions in one build.

The report stays one file at `ctrf.report.path`, and each JVM merges its results into it when it finishes.
- **JVMs of the same run merge.** Every test appears once, with earlier attempts in `retryAttempts`.
- **A report from another run is replaced**, for example the one left by the previous build.
- **The report is never half-written.** While a JVM merges, it holds a lock on `<report>.lock` next to the report, and it replaces the report by moving a complete file over it. Add `ctrf-report.json.lock` to `.gitignore`, or set `ctrf.report.path` under `build/` or `target/`.

### How the Run Is Identified

The report's root `runId` decides which JVMs belong together. Each JVM resolves it on its own, and the first match wins:

1. `ctrf.run.id`, a system property or a `ctrf.properties` entry. It must change with every build. A constant makes every build the same run, so reports accumulate again.
2. A CI variable:
   - GitHub Actions: `GITHUB_RUN_ID` and `GITHUB_RUN_ATTEMPT`;
   - GitLab: `CI_JOB_ID`;
   - Jenkins: `BUILD_TAG`;
   - Azure Pipelines: `BUILD_BUILDID` and `SYSTEM_JOBATTEMPT`;
   - CircleCI: `CIRCLE_WORKFLOW_JOB_ID`;
   - Buildkite: `BUILDKITE_JOB_ID`.
3. The build tool's JVM, meaning the nearest ancestor process that is a JVM. This identifies one Maven build, or one `gradle --no-daemon` build.
4. Otherwise each JVM is its own run.

Locally, Gradle runs builds in a long-lived daemon, so consecutive builds share step 3 and their results accumulate in one report. The same goes for other long-lived build servers, such as `mvnd` and Bazel. Give each Gradle build its own id:

```groovy
tasks.withType(Test).configureEach {
    // A new id each time the task runs, shared by its forks and retry rounds.
    // The task stays up to date, and this works with the configuration cache.
    doFirst { systemProperty 'ctrf.run.id', UUID.randomUUID().toString() }
}
```

Or, with the Kotlin DSL:

```kotlin
tasks.withType<Test>().configureEach {
    doFirst { (this as Test).systemProperty("ctrf.run.id", java.util.UUID.randomUUID().toString()) }
}
```

Each test task then gets its own id. If a build runs several test tasks, give each its own `ctrf.report.path`.

### Reports from Several Machines

When tests run on several machines, such as CI matrix jobs, each machine writes its own report. Merge them with [ctrf-cli](https://github.com/ctrf-io/ctrf-cli): `npx ctrf merge <directory>`.

## Contributing
''')
open(p, 'w').write(s)
PYEOF
grep -n 'ctrf.run.id\|## Parallel Forks' README.md | head
```

Expected:
- the new configuration row;
- the new section heading;
- the README mentions `ctrf.run.id` at least four times.

- [ ] **Step 3: Update `CLAUDE.md`**

```bash
python3 - <<'PYEOF'
p='CLAUDE.md'
s=open(p).read()
def rep(old, new):
    global s
    assert s.count(old) == 1, old[:80]
    s = s.replace(old, new)

rep('''    ├─ Delegates to TestRerunHandler for rerun scenarios
    └─ Delegates to ReportOrchestrator for report generation
''', '''    ├─ Delegates to RunIdResolver to decide which JVMs share one report
    └─ At the end, CtrfReportFileService.update → ReportMerger → CtrfJsonComposer
''')
rep('''ReportOrchestrator (generates report)
    ├─ Uses CtrfJsonComposer for JSON structure
    └─ Uses CtrfReportFileService for file I/O
    ↓
CtrfReportFileService (filesystem I/O)
    └─ Writes JSON to configured path (default: ctrf-report.json)
''', '''CtrfReportFileService.update (under a JVM lock and a lock on <report>.lock)
    ├─ Reads the report fresh
    ├─ ReportMerger: same runId → unfold its tests, append this execution's attempts and recoveries, fold;
    │                otherwise this execution alone
    └─ Writes a temp file and moves it over the report (default: ctrf-report.json)
''')
rep('| **TestRerunHandler**            | Loads existing test data from previous runs; manages rerun scenarios                                  |\n'
    '| **ReportOrchestrator**          | Coordinates report generation; delegates to CtrfJsonComposer and CtrfReportFileService                |\n',
    '| **RunIdResolver**               | Resolves the runId: ctrf.run.id, a CI variable, the nearest ancestor JVM, or this JVM                 |\n'
    '| **ReportMerger**                | Pure; merges an execution into the report of the same run, replaces another run\'s report; HP-11 recoveries |\n')
rep('| **CtrfReportFileService**       | Handles file I/O; supports reading existing reports for test reruns                                   |\n',
    '| **CtrfReportFileService**       | `update`: locked fresh read, merge function, atomic write                                             |\n')
rep('- `ConcurrentLinkedQueue<Test>` for attempts (O(1) adds from any thread; folded once when the run finishes)\n',
    '- `ConcurrentLinkedQueue<Test>` for attempts and `ConcurrentLinkedQueue<ContainerSuccess>` for containers that finished without failing (O(1) adds from any thread; merged once when the execution finishes)\n')
rep('**No explicit locks are used.** Thread safety is achieved through concurrent data structures and atomic operations. **AttemptFolder** is stateless and runs once, in finishTestRun, on a snapshot of the attempts.',
    'While tests run, thread safety comes from concurrent data structures and atomic operations. Writing the report is the one locked section: `CtrfReportFileService.update` takes a JVM-wide lock and an exclusive file lock on `<report>.lock`, so the JVMs of one run (forks, retry rounds) merge one after another. **ReportMerger** and **AttemptFolder** are stateless and run inside that section.')
rep('- `ctrf.calculate.startup.duration` - Enable startup duration calculation (default: false)\n',
    '- `ctrf.calculate.startup.duration` - Enable startup duration calculation (default: false)\n'
    '- `ctrf.run.id` - Run identifier shared by the JVMs of one build (default: a CI variable, the nearest ancestor JVM, or this JVM)\n')
rep('├── TestRerunHandler.java          # Handles test rerun scenarios\n'
    '├── ReportOrchestrator.java        # Orchestrates report generation\n',
    '├── RunIdResolver.java             # Resolves the runId\n'
    '├── ReportMerger.java              # Merges an execution into its run\'s report\n'
    '├── ContainerSuccess.java          # A container that finished without failing (HP-11)\n')
rep('`CtrfReportFileService` reads existing reports on startup, allowing incremental report building across multiple test runs. Their tests are unfolded into attempts, so a retry round in a new JVM extends each test\'s `retryAttempts`.',
    'Nothing is read when a run starts. At the end of each execution, `CtrfReportFileService.update` reads the report under a lock. If its root `runId` matches, `ReportMerger` unfolds its tests and appends this execution\'s attempts, so forks, retry rounds in new JVMs and reruns in the same JVM all end up in one report. A report of another run is replaced.')
rep('  - Health state is preserved across test reruns via `CtrfReportFileService`\n',
    '  - Health is merged across the JVMs of one run by `ReportMerger`: unhealthy if any was\n')
rep('- State persists across reruns when using same report file\n',
    '- State is merged within one run and not passed on to a later run\n')
open(p, 'w').write(s)
PYEOF
grep -n 'TestRerunHandler\|ReportOrchestrator' CLAUDE.md README.md || echo "no stale references"
```

Expected: `no stale references`.

In `CLAUDE.md`, find the "Retries and Flaky Tests" section and add this sentence at the end of its paragraph: ` A class failure (initializationError/teardownError) followed by the class's success in a later round becomes a passed, flaky entry (HP-11).`

```bash
python3 - <<'PYEOF'
p='CLAUDE.md'
s=open(p).read()
old='and never joined to entries that have a `testId`.'
assert s.count(old)==1
s=s.replace(old, old + ' A class failure (initializationError/teardownError) followed by the class\'s success in a later round becomes a passed, flaky entry (HP-11).')
open(p,'w').write(s)
PYEOF
```

- [ ] **Step 4: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: describe run identity, the report lock and recovered class failures (CB-6, HP-11)"
```

---

### Task 9: Final verification, performance, squash and PR

**Files:**
- Create (outside the repo, not committed): `$B/MergeBench.java` and `$B/print-cp.gradle`, where `B=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/cb6-bench`.
- Create (not committed): the PR description `$B/../cb6-pr-body.md`.

**Interfaces:** none.

- [ ] **Step 1: Full local verification**

```bash
./gradlew clean :build 2>&1 | grep -E 'FAILED|BUILD'
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t=f=0
for p in glob.glob('build/test-results/test/*.xml'):
    r=ET.parse(p).getroot(); t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors'))
print('tests', t, 'failures', f)
EOF
```

Expected: BUILD SUCCESSFUL and `failures 0`. Write down the test count.

Repeat Task 7, Step 2: the listener with 1, 2 and 4 forks, and the extension with 1 and 2, each followed by the validator. Then run the compat matrix: `compat/run.sh 5.10.5 5.14.4 6.1.3 2>&1 | tail -2`. Expected: `all compatibility checks passed`.

Then show that a new build replaces the report. Run the listener twice without `clean`, with two different explicit ids, as CI variables or the per-build Gradle setting would supply them:

```bash
export ENV_HEALTHY=false
./gradlew clean :integration-tests-listener:test -Dthreads=2 -Dctrf.run.id=local-1 -q > /dev/null 2>&1
./gradlew :integration-tests-listener:test --rerun-tasks -Dthreads=2 -Dctrf.run.id=local-2 -q > /dev/null 2>&1
python3 -c "import json;r=json.load(open('integration-tests-listener/build/test-results/ctrf-report.json'));t=r['results']['tests'];print(r['runId'], len(t), sum(1 for x in t if x.get('retries')))"
unset ENV_HEALTHY
```

Expected: `local-2 28 9`. Only this build's own retry round adds history. On `master`, the same two builds gave 28 tests, all 28 with `retries`.

- [ ] **Step 2: Measure how long one fork holds the lock**

```bash
B=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/cb6-bench
mkdir -p $B
cat > $B/print-cp.gradle <<'EOF'
rootProject {
    afterEvaluate {
        tasks.register('printBenchClasspath') {
            dependsOn 'testClasses'
            doLast { println "CP=" + sourceSets.test.runtimeClasspath.asPath }
        }
    }
}
EOF
cat > $B/MergeBench.java <<'EOF'
import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.model.TestDetails;

import java.util.Set;

public class MergeBench {
    public static void main(String[] args) {
        int count = Integer.parseInt(args[0]);
        var manager = CtrfReportManager.getInstance();
        manager.startTestRun("bench");
        for (int i = 0; i < count; i++) {
            String id = "[engine:junit-jupiter]/[class:Bench]/[method:test" + i + "()]";
            manager.onTestStart(new TestDetails(System.currentTimeMillis(), Set.of(), "Bench", id, "test" + i + "()"));
            manager.onTestSuccess(id);
        }
        long started = System.nanoTime();
        manager.finishTestRun();
        System.out.printf("recorded=%d finish=%.2fs%n", count, (System.nanoTime() - started) / 1e9);
    }
}
EOF
CP=$(./gradlew -q -I $B/print-cp.gradle printBenchClasspath | grep '^CP=' | tail -1 | cut -c4-)
for n in 10000 40000 80000; do
  rm -f $B/bench.json $B/bench.json.lock
  printf "seed ${n}:  "; java -Xmx2g -Dctrf.run.id=bench -Dctrf.report.path=$B/bench.json -cp "$CP" $B/MergeBench.java $n
  printf "merge 1 into ${n}: "; java -Xmx2g -Dctrf.run.id=bench -Dctrf.report.path=$B/bench.json -cp "$CP" $B/MergeBench.java 1
done
```

Expected:
- each "merge 1 into N" line is the time one fork holds the lock in a run that already has N tests;
- it grows about linearly, and stays around a second or less at 80k;
- the seed lines match HP-6's finish times, 0.2–0.4 s at 80k.

Write the numbers down for the PR description.

- [ ] **Step 3: Squash into one commit**

If `origin/master` moved since Task 1, rebase first. Squashing onto a newer `origin/master` without rebasing would silently revert what was merged:

```bash
git fetch origin
git rebase origin/master
```

Expected: "Current branch fix/cb-6-report-persistence is up to date", or a clean rebase. If the rebase brought in new commits, repeat Step 1. If it stops with conflicts, resolve them so that both `master`'s change and this branch's survive, then repeat Step 1.

```bash
git status --short
git reset --soft origin/master
git commit -F - <<'EOF'
fix: one report per run across forks, retries and reruns (CB-6, HP-11)

The report was read when a run started, cached for the life of the JVM,
and rewritten at the end. Parallel forks overwrote each other (2 forks:
16 of 28 tests), a later build was folded into the previous build's
report, a second execution in the same JVM (Surefire's
rerunFailingTestsCount) dropped the first, and a JVM killed mid-write
left a truncated file.

Each JVM now resolves a runId (ctrf.run.id, a CI variable, the nearest
ancestor JVM, or itself) and writes it at the root of the report. At the
end of each execution it locks <report>.lock, reads the report fresh,
merges into it when it has the same runId and replaces it otherwise, and
moves a complete temp file over it. ReportOrchestrator and
TestRerunHandler are gone; ReportMerger holds the merge rule.

A class whose setup or teardown fails in one round and succeeds in a
later one is now reported as passed and flaky instead of failed (HP-11).

Tests: RunIdResolver, ReportMerger and file service unit tests, manager
tests, forked-JVM scenarios for forks, new builds, same-JVM reruns and
recovered class failures through the listener and the extension, the
listener integration run with 2 forks in CI, and Maven compat consumers
with forkCount=2 and rerunFailingTestsCount=1.
EOF
git log --oneline -2
git show --stat --format= HEAD
```

Expected:
- one commit on top of `origin/master`, with no `Co-Authored-By` line;
- `git status --short` before the reset shows only `?? ctrf-report.json`.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin fix/cb-6-report-persistence
```

- [ ] **Step 5: Write the PR description**

Write `$B/../cb6-pr-body.md` with the sections below. Fill them from this plan and from the numbers written down in Steps 1 and 2. End it without any "Generated with Claude Code" line.

- **What:**
  - one report per run;
  - the root `runId`;
  - the report written under a lock and replaced atomically;
  - a recovered class failure reported as passed and flaky.
- **Why:** the four CB-6 failures, with the measured numbers:
  - forks: 16 of 28 tests with 2 forks, and the flaky test not flaky with 4;
  - accumulation: all 28 tests with `retries` after two builds without `clean`;
  - same-JVM reruns;
  - torn writes.

  Plus HP-11's false red.
- **Changes:**
  - `RunIdResolver` and the root `runId`;
  - `CtrfReportFileService.update`;
  - `ReportMerger` and `ContainerSuccess`;
  - the manager reads at the end only;
  - `ReportOrchestrator` and `TestRerunHandler` removed;
  - the listener and the extension record container successes.
- **What changes in the report:** a before/after table:
  - the listener with 1, 2 and 4 forks;
  - two builds without `clean`;
  - Maven compat with forks and reruns;
  - a recovered `initializationError`.
- **Compatibility:**
  - the removed public methods, which go in the 0.5.0 release notes;
  - the `.lock` file next to the report;
  - local Gradle daemon builds accumulate unless `ctrf.run.id` is set per build, as the README shows.
- **Tests:**
  - the unit, scenario, integration and compat tests added;
  - which scenarios fail without the merge, without the `runId` check and without recoveries;
  - the optional no-lock race result.
- **Verified locally:**
  - the build's test count;
  - the integration runs with 1, 2 and 4 forks;
  - compat 12 of 12;
  - the merge-time numbers.
- **Not in this PR:**
  - HP-5 (official schema);
  - MP-5 (lenient reading);
  - CB-2 (nested runs);
  - a merger tool: `npx ctrf merge` is documented instead.

The user opens the PR from the browser as `alexshamrai`, because `gh` is logged in as an account that can't create PRs on this repo:
- URL: https://github.com/alexshamrai/junit-ctrf-reporter/compare/master...fix/cb-6-report-persistence?expand=1
- Title: `fix: one report per run across forks, retries and reruns (CB-6, HP-11)`

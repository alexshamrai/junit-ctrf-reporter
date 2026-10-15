# TI-4: Cross-Check the Integration Reports Against JUnit's Event Log — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The integration validator checks every entry of a CTRF report against JUnit's own event log for the same run, by `testId`, and runs green with no flags locally and with CI's flags in CI.

**Architecture:**
- The two integration modules enable JUnit's Open Test Reporting, one directory per JVM, and give each build its own `ctrf.run.id`.
- In the validator module:
  - `JunitEvents` folds the event files into what the report must contain;
  - `JunitCrossCheckTest` compares the report with it;
  - `IntegrationRun` locates the report, its module and its event log;
  - `ExpectedRun` derives the CI-specific expectations from the run's own inputs.
- `CtrfLogicTest` drops what the cross-check now covers, and looks tests up by `testId`.
- The CI workflows pass the same flags to the report step and the validator step.

**Tech Stack:**
- Gradle 9 with the `org.gradle.test-retry` plugin;
- JUnit 6.1.3 with `junit-platform-reporting` (Open Test Reporting 0.2.0 event XML), parsed with the JDK's DOM parser;
- Jackson 2, AssertJ;
- GitHub Actions.

**Spec:** `IMPROVEMENT_PLAN.md` on branch `feature/improvements-plan`, in the main checkout `/Users/oleksiishamrai/personal_dev/junit-ctrf-extension`, section "TI-4: Validate Integration Output Against the Official Schema and JUnit's Own Counts" (commit `07c8e17`). Read it before starting.

## Global Constraints

- **Paths:**
  - `W=/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes` is the code worktree;
  - `SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad` holds files that are never committed;
  - `MW=$SCRATCH/ti4-old`, a temporary worktree at `58e36f0` used for the RED run in Task 4 and removed at the end of it.
- **Every command runs from `$W`.** The shell's working directory and variables reset between tool calls. Start each command with `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes &&`, and redefine `SCRATCH` or `MW` in any command that uses them.
- **Where:** branch `test/ti-4-junit-cross-check` in `$W`, created from `origin/master` (`291f21d` or later). Ignore the untracked `ctrf-report.json` and `ctrf-report.json.lock` at the root, and never commit them.
- **The event log:**
  - `junit.platform.reporting.open.xml.enabled=true`;
  - `junit.platform.reporting.output.dir=<module>/build/test-results/junit-platform/{uniqueNumber}`;
  - one `open-test-report.xml` per JVM;
  - `testRuntimeOnly 'org.junit.platform:junit-platform-reporting'`, versioned by the JUnit BOM;
  - namespaces: events `https://schemas.opentest4j.org/reporting/events/0.2.0`, core `https://schemas.opentest4j.org/reporting/core/0.2.0`, junit `https://schemas.junit.org/open-test-reporting`.
- **Folding JUnit's events** (`JunitEvents`):
  - attempts per unique ID, ordered by finish time;
  - final status mapped as `SUCCESSFUL` → `passed`, `FAILED` → `failed`, `ABORTED` and `SKIPPED` → `skipped`;
  - `retries` = attempts − 1, and `flaky` = final `passed` after an earlier `FAILED`;
  - test nodes are always entries;
  - containers below the engine are entries only if they failed at least once;
  - other containers whose final status is `SKIPPED` or `ABORTED` are "skipped containers";
  - the engine (a unique ID without `/`) and started-but-never-finished nodes are ignored.
- **Inputs:**
  - the report is `-Dctrf.report.path`, defaulting to `../integration-tests-listener/build/test-results/ctrf-report.json`;
  - the module is the directory that contains the report's `build/`, and must be `integration-tests-listener` or `integration-tests-extension`;
  - the event log is `-Dctrf.junit.events.dir`, defaulting to `junit-platform` next to the report.
- **Expectations:**
  - **threads:** `-Dthreads`. Unset, non-integer or 1 means exactly 1 distinct thread name; N > 1 means between 2 and N;
  - **build name, number and URL:** `-Dctrf.build.*`, else the module's `src/test/resources/ctrf.properties`. A number matching `[0-9]+` after stripping is expected as `buildNumber` (`Long`), with no `buildId`;
  - **health:** unhealthy if `ENV_HEALTHY` equals `false` (ignoring case), or if the module is the extension;
  - **`generatedBy`:** `io.github.alexshamrai.launcher.CtrfListener` for the listener module, `io.github.alexshamrai.jupiter.CtrfExtension` for the extension.
- **No change to the published library** (`src/main` of the root module), so no version bump.
- **Commits:** no `Co-Authored-By` trailer and no "Generated with Claude Code" line, anywhere. The per-task commits are squashed into one in Task 7.
- **Builds:**
  - `./gradlew :build` (with the colon) runs the root module's tests and checkstyle;
  - the root `./gradlew build` also runs the validator, so don't use it;
  - always run the validator task with `--rerun`, because Gradle doesn't treat the report file as an input;
  - checkstyle covers only the root module, but keep its style in the other modules: lines of at most 140 characters, wrapped operators at the start of the new line, no unused imports.
- **zsh:** in loops, use `${m}`.

## Review Focus

1. **A JVM that dies mid-test** leaves an `e:started` with no `e:finished`.
   - Expected: `JunitEvents` ignores the unfinished attempt instead of throwing, and the cross-check reports whatever is then missing.
   - Test: `JunitEventsTest.startedButNeverFinishedIsIgnored` (Task 2).
2. **Event files from an earlier build** in `build/test-results/junit-platform` (the task re-run without `clean`).
   - Expected: the task deletes them before it runs, and the report is replaced too, by the per-build `ctrf.run.id`, so the two logs stay in step.
   - Test: Task 1, Step 5 (two runs with `--rerun`).
3. **A report outside the two module directories,** for example copied elsewhere.
   - Expected: a clear failure that names the expected locations, not a `NullPointerException` or a wrong guess.
   - Test: `IntegrationRunTest.reportOutsideTheModulesFailsClearly` (Task 3).
4. **A run with `-Dthreads=4` where JUnit uses fewer than 4 workers.**
   - Expected: any count from 2 to 4 passes.
   - Test: Task 7, Step 1 (a listener run with `-Dthreads=4`).
5. **A test that is aborted in round 1 and passes in round 2.**
   - Expected: `passed`, `retries` 1, not flaky, because an aborted attempt is not a failure. That is the library's flaky rule.
   - Test: `JunitEventsTest.abortedThenPassedIsNotFlaky` (Task 2).

---

### Task 1: The integration modules write JUnit's event log

**Files:**
- Modify: `integration-tests-listener/build.gradle:15-20` (dependencies), `:51-69` (`tasks.withType(Test).configureEach`)
- Modify: `integration-tests-extension/build.gradle` (the same two places; the two files are identical apart from their names)

**Interfaces:**
- Consumes: nothing.
- Produces: after any `:integration-tests-<module>:test` run, `integration-tests-<module>/build/test-results/junit-platform/<number>/open-test-report.xml`, one per JVM of that run only. The report's root `runId` is new for every run of the task.

- [ ] **Step 1: Create the branch**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git fetch origin
git switch -c test/ti-4-junit-cross-check origin/master
git branch --unset-upstream
git log --oneline -1
```

Expected: HEAD is `origin/master` (`291f21d` or later).

- [ ] **Step 2: Show that no event log exists today**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew clean :integration-tests-listener:test -Dthreads=2 -Dforks=2 -q > /dev/null 2>&1
ls integration-tests-listener/build/test-results/
```

Expected: `ctrf-report.json`, `ctrf-report.json.lock`, `test` and no `junit-platform`.

- [ ] **Step 3: Enable the event log and a per-build run id in both modules**

In `integration-tests-listener/build.gradle` and in `integration-tests-extension/build.gradle`, replace:

```groovy
dependencies {
    testImplementation platform("org.junit:junit-bom:${rootProject.ext.junitVersion}")
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation project(':')
}
```

with:

```groovy
dependencies {
    testImplementation platform("org.junit:junit-bom:${rootProject.ext.junitVersion}")
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    // JUnit's own event log, which integration-ctrf-validator cross-checks the CTRF report against
    testRuntimeOnly 'org.junit.platform:junit-platform-reporting'
    testImplementation project(':')
}
```

In the same two files, replace:

```groovy
    configureThreads(it)
    configureForks(it)
}
```

with:

```groovy
    configureThreads(it)
    configureForks(it)

    // JUnit's event log: one open-test-report.xml per JVM (each fork and each retry round)
    def junitEvents = layout.buildDirectory.dir('test-results/junit-platform').get().asFile
    systemProperty 'junit.platform.reporting.open.xml.enabled', 'true'
    systemProperty 'junit.platform.reporting.output.dir', "${junitEvents}/{uniqueNumber}"
    doFirst {
        // Runs once per task, before the first retry round: the log and the report both cover this run only
        junitEvents.deleteDir()
        systemProperty 'ctrf.run.id', UUID.randomUUID().toString()
    }
}
```

- [ ] **Step 4: Run the listener and check the log**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test -Dthreads=2 -Dforks=2 -q > /dev/null 2>&1
find integration-tests-listener/build/test-results/junit-platform -name open-test-report.xml | wc -l
python3 -c "import json;r=json.load(open('integration-tests-listener/build/test-results/ctrf-report.json'));print(r['runId'], len(r['results']['tests']))"
```

Expected:
- 3 or more files: 2 forks, plus the JVMs of the retry round;
- a UUID-shaped `runId` and `28` tests.

- [ ] **Step 5: Run it again without `clean`** (Review Focus 2)

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
D=integration-tests-listener/build/test-results
FIRST_RUN=$(python3 -c "import json;print(json.load(open('$D/ctrf-report.json'))['runId'])")
FIRST_DIRS=$(ls $D/junit-platform | sort | tr '\n' ' ')
ENV_HEALTHY=false ./gradlew :integration-tests-listener:test --rerun -Dthreads=2 -Dforks=2 -q > /dev/null 2>&1
python3 - "$FIRST_RUN" "$FIRST_DIRS" <<'EOF'
import json, os, sys
d = 'integration-tests-listener/build/test-results'
r = json.load(open(f'{d}/ctrf-report.json'))
dirs = set(os.listdir(f'{d}/junit-platform'))
print('new runId:', r['runId'] != sys.argv[1])
print('old dirs left:', sorted(dirs & set(sys.argv[2].split())))
print('tests:', len(r['results']['tests']), 'flaky retries:', [t.get('retries') for t in r['results']['tests'] if t.get('flaky')])
EOF
```

Expected: `new runId: True`, `old dirs left: []` and `tests: 28 flaky retries: [1]`. The second build's report holds only its own run.

- [ ] **Step 6: Check the extension module**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew clean :integration-tests-extension:test -Dthreads=2 -q > /dev/null 2>&1
find integration-tests-extension/build/test-results/junit-platform -name open-test-report.xml | wc -l
python3 -c "import json;r=json.load(open('integration-tests-extension/build/test-results/ctrf-report.json'));print(len(r['results']['tests']))"
```

Expected: 2 or more files, and `22`.

- [ ] **Step 7: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add integration-tests-listener/build.gradle integration-tests-extension/build.gradle
git status --short
git commit -m "test: the integration modules write JUnit's event log and a per-build run id (TI-4)"
```

Expected: `git status --short` shows only the two untracked report files after the add.

---

### Task 2: `JunitEvents`

**Files:**
- Create: `integration-ctrf-validator/src/main/java/io/github/alexshamrai/integration/JunitEvents.java`
- Test: `integration-ctrf-validator/src/test/java/io/github/alexshamrai/integration/JunitEventsTest.java`

**Interfaces:**
- Consumes: the event-file format from Task 1 (see Global Constraints).
- Produces, in package `io.github.alexshamrai.integration`:
  - `public static JunitEvents read(Path eventsDir) throws IOException`: reads every `open-test-report.xml` below `eventsDir`. It throws `IOException` with a message starting "No JUnit event log" when there are none, or the directory doesn't exist;
  - `public static JunitEvents fromXml(List<String> documents)`;
  - `public Map<String, Expected> expectedEntries()`, keyed by `testId`;
  - `public Set<String> skippedContainers()`;
  - `public record Expected(String testId, String status, int retries, boolean flaky)`, where `status` is `"passed"`, `"failed"` or `"skipped"`.

- [ ] **Step 1: Write the failing tests**

Create `integration-ctrf-validator/src/test/java/io/github/alexshamrai/integration/JunitEventsTest.java`:

```java
package io.github.alexshamrai.integration;

import io.github.alexshamrai.integration.JunitEvents.Expected;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JunitEventsTest {

    private static final String ENGINE = "[engine:junit-jupiter]";
    private static final String CLASS = ENGINE + "/[class:com.example.PaymentTest]";
    private static final String TEST = CLASS + "/[method:pays()]";
    private static final String TEMPLATE = CLASS + "/[test-template:usesData(int)]";

    @TempDir
    Path tempDir;

    @Test
    void passedTestIsExpectedAndPassedContainersAreNot() {
        var events = JunitEvents.fromXml(List.of(document(
            started(1, ENGINE, "CONTAINER"), started(2, CLASS, "CONTAINER"), started(3, TEST, "TEST"),
            finished(3, "2026-10-15T10:00:01Z", "SUCCESSFUL"), finished(2, "2026-10-15T10:00:02Z", "SUCCESSFUL"),
            finished(1, "2026-10-15T10:00:03Z", "SUCCESSFUL"))));

        assertThat(events.expectedEntries()).isEqualTo(Map.of(TEST, new Expected(TEST, "passed", 0, false)));
        assertThat(events.skippedContainers()).isEmpty();
    }

    @Test
    void testThatFailsAndThenPassesInALaterJvmIsFlaky() {
        var firstRound = document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "FAILED"));
        var secondRound = document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:05Z", "SUCCESSFUL"));

        // The files' order must not matter: attempts are ordered by their finish time
        var events = JunitEvents.fromXml(List.of(secondRound, firstRound));

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "passed", 1, true));
    }

    @Test
    void testThatFailsInEveryRoundIsFailedAndNotFlaky() {
        var events = JunitEvents.fromXml(List.of(
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "FAILED")),
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:05Z", "FAILED"))));

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "failed", 1, false));
    }

    @Test
    void abortedTestIsSkipped() {
        var events = JunitEvents.fromXml(List.of(document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "ABORTED"))));

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "skipped", 0, false));
    }

    @Test
    void abortedThenPassedIsNotFlaky() {
        var events = JunitEvents.fromXml(List.of(
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "ABORTED")),
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:05Z", "SUCCESSFUL"))));

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "passed", 1, false));
    }

    @Test
    void failedContainersAreEntriesAndARecoveredOneIsFlaky() {
        var events = JunitEvents.fromXml(List.of(
            document(started(1, CLASS, "CONTAINER"), started(2, TEMPLATE, "CONTAINER"),
                finished(2, "2026-10-15T10:00:01Z", "FAILED"), finished(1, "2026-10-15T10:00:02Z", "FAILED")),
            document(started(1, CLASS, "CONTAINER"), finished(1, "2026-10-15T10:00:05Z", "SUCCESSFUL"))));

        assertThat(events.expectedEntries()).containsOnly(
            Map.entry(CLASS, new Expected(CLASS, "passed", 1, true)),
            Map.entry(TEMPLATE, new Expected(TEMPLATE, "failed", 0, false)));
    }

    @Test
    void skippedAndAbortedContainersAreListedSeparately() {
        var other = ENGINE + "/[class:com.example.RefundTest]";
        var events = JunitEvents.fromXml(List.of(document(
            started(1, CLASS, "CONTAINER"), finished(1, "2026-10-15T10:00:01Z", "SKIPPED"),
            started(2, other, "CONTAINER"), finished(2, "2026-10-15T10:00:02Z", "ABORTED"))));

        assertThat(events.skippedContainers()).containsExactlyInAnyOrder(CLASS, other);
        assertThat(events.expectedEntries()).isEmpty();
    }

    @Test
    void startedButNeverFinishedIsIgnored() {
        var events = JunitEvents.fromXml(List.of(document(started(1, TEST, "TEST"))));

        assertThat(events.expectedEntries()).isEmpty();
        assertThat(events.skippedContainers()).isEmpty();
    }

    @Test
    void readFoldsTheFilesOfEveryJvm() throws IOException {
        write(tempDir.resolve("111/open-test-report.xml"), document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "FAILED")));
        write(tempDir.resolve("222/open-test-report.xml"), document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:05Z", "SUCCESSFUL")));

        var events = JunitEvents.read(tempDir);

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "passed", 1, true));
    }

    @Test
    void readFailsClearlyWithoutEventFiles() {
        var missing = tempDir.resolve("missing");

        assertThatThrownBy(() -> JunitEvents.read(tempDir))
            .isInstanceOf(IOException.class)
            .hasMessageStartingWith("No JUnit event log")
            .hasMessageContaining(tempDir.toString());
        assertThatThrownBy(() -> JunitEvents.read(missing))
            .isInstanceOf(IOException.class)
            .hasMessageStartingWith("No JUnit event log");
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String document(String... events) {
        return "<?xml version=\"1.0\" ?>"
            + "<e:events xmlns=\"https://schemas.opentest4j.org/reporting/core/0.2.0\""
            + " xmlns:e=\"https://schemas.opentest4j.org/reporting/events/0.2.0\""
            + " xmlns:junit=\"https://schemas.junit.org/open-test-reporting\">"
            + String.join("", events)
            + "</e:events>";
    }

    private static String started(int id, String uniqueId, String type) {
        return "<e:started id=\"" + id + "\" name=\"node\" time=\"2026-10-15T10:00:00Z\"><metadata>"
            + "<junit:uniqueId>" + uniqueId + "</junit:uniqueId><junit:type>" + type + "</junit:type>"
            + "</metadata></e:started>";
    }

    private static String finished(int id, String time, String status) {
        return "<e:finished id=\"" + id + "\" time=\"" + time + "\"><result status=\"" + status + "\"></result></e:finished>";
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :integration-ctrf-validator:test --rerun --tests 'io.github.alexshamrai.integration.JunitEventsTest' 2>&1 | grep -E 'error:|FAILED|BUILD'`

Expected: `compileTestJava` fails with `cannot find symbol` for `JunitEvents`.

- [ ] **Step 3: Implement `JunitEvents`**

Create `integration-ctrf-validator/src/main/java/io/github/alexshamrai/integration/JunitEvents.java`:

```java
package io.github.alexshamrai.integration;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What the CTRF report of a run must contain, according to JUnit's own event log for that run.
 *
 * <p>The integration modules enable JUnit's Open Test Reporting, which writes an {@code open-test-report.xml} for
 * each JVM: each fork and each retry round. Each node's unique ID there is the report's {@code testId}.</p>
 */
public final class JunitEvents {

    private static final String EVENTS = "https://schemas.opentest4j.org/reporting/events/0.2.0";
    private static final String CORE = "https://schemas.opentest4j.org/reporting/core/0.2.0";
    private static final String JUNIT = "https://schemas.junit.org/open-test-reporting";
    private static final String FILE_NAME = "open-test-report.xml";

    private final Map<String, Expected> expectedEntries;
    private final Set<String> skippedContainers;

    /**
     * An entry the report must contain.
     *
     * @param testId  the JUnit unique ID
     * @param status  the CTRF status of the final attempt: "passed", "failed" or "skipped"
     * @param retries the attempts before the final one
     * @param flaky   whether the final attempt passed after an earlier failed one
     */
    public record Expected(String testId, String status, int retries, boolean flaky) {
    }

    private record Attempt(Instant finished, String status) {
    }

    private record History(String type, List<Attempt> attempts) {
    }

    private JunitEvents(Map<String, Expected> expectedEntries, Set<String> skippedContainers) {
        this.expectedEntries = Collections.unmodifiableMap(expectedEntries);
        this.skippedContainers = Collections.unmodifiableSet(skippedContainers);
    }

    /**
     * Reads every {@code open-test-report.xml} below {@code eventsDir}.
     *
     * @throws IOException if there is none, or a file can't be read
     */
    public static JunitEvents read(Path eventsDir) throws IOException {
        List<String> documents = new ArrayList<>();
        if (Files.isDirectory(eventsDir)) {
            try (Stream<Path> files = Files.walk(eventsDir)) {
                for (Path file : files.filter(path -> path.getFileName().toString().equals(FILE_NAME)).sorted().toList()) {
                    documents.add(Files.readString(file));
                }
            }
        }
        if (documents.isEmpty()) {
            throw new IOException("No JUnit event log (" + FILE_NAME + ") under " + eventsDir.toAbsolutePath()
                + ". The integration modules write it on every test run: run the integration test task again,"
                + " or pass -Dctrf.junit.events.dir=<directory>.");
        }
        return fromXml(documents);
    }

    /**
     * Folds the event documents of one run, one per JVM, in any order.
     */
    public static JunitEvents fromXml(List<String> documents) {
        Map<String, History> histories = new LinkedHashMap<>();
        for (String document : documents) {
            collect(parse(document), histories);
        }

        Map<String, Expected> expected = new LinkedHashMap<>();
        Set<String> skipped = new LinkedHashSet<>();
        histories.forEach((testId, history) -> {
            // The engine has no '/'; a node that never finished belongs to a JVM that died mid-test
            if (!testId.contains("/") || history.attempts().isEmpty()) {
                return;
            }
            List<String> statuses = history.attempts().stream()
                .sorted(Comparator.comparing(Attempt::finished))
                .map(Attempt::status)
                .toList();
            String last = statuses.get(statuses.size() - 1);
            boolean everFailed = statuses.contains("FAILED");
            if (history.type().equals("CONTAINER") && !everFailed) {
                if (last.equals("SKIPPED") || last.equals("ABORTED")) {
                    skipped.add(testId);
                }
                return;
            }
            String status = ctrfStatus(last);
            boolean flaky = status.equals("passed") && statuses.subList(0, statuses.size() - 1).contains("FAILED");
            expected.put(testId, new Expected(testId, status, statuses.size() - 1, flaky));
        });
        return new JunitEvents(expected, skipped);
    }

    /**
     * The entries the report must contain, by {@code testId}.
     */
    public Map<String, Expected> expectedEntries() {
        return expectedEntries;
    }

    /**
     * Containers JUnit skipped or aborted as a whole. JUnit never starts their tests, so the log can't list them;
     * the report must have at least one entry under each, all skipped.
     */
    public Set<String> skippedContainers() {
        return skippedContainers;
    }

    private static void collect(Document document, Map<String, History> histories) {
        // Event ids are numbered per file, so they map to unique IDs per document
        Map<String, String> uniqueIds = new HashMap<>();
        NodeList started = document.getElementsByTagNameNS(EVENTS, "started");
        for (int i = 0; i < started.getLength(); i++) {
            Element event = (Element) started.item(i);
            String uniqueId = text(event, "uniqueId");
            uniqueIds.put(event.getAttribute("id"), uniqueId);
            histories.computeIfAbsent(uniqueId, id -> new History(text(event, "type"), new ArrayList<>()));
        }
        NodeList finished = document.getElementsByTagNameNS(EVENTS, "finished");
        for (int i = 0; i < finished.getLength(); i++) {
            Element event = (Element) finished.item(i);
            String uniqueId = uniqueIds.get(event.getAttribute("id"));
            if (uniqueId == null) {
                continue;
            }
            Element result = (Element) event.getElementsByTagNameNS(CORE, "result").item(0);
            histories.get(uniqueId).attempts().add(new Attempt(Instant.parse(event.getAttribute("time")), result.getAttribute("status")));
        }
    }

    private static String text(Element event, String junitElement) {
        return event.getElementsByTagNameNS(JUNIT, junitElement).item(0).getTextContent();
    }

    private static String ctrfStatus(String junitStatus) {
        return switch (junitStatus) {
            case "SUCCESSFUL" -> "passed";
            case "FAILED" -> "failed";
            case "ABORTED", "SKIPPED" -> "skipped";
            default -> throw new IllegalStateException("Unknown JUnit result status: " + junitStatus);
        };
    }

    private static Document parse(String document) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(document)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("Can't parse a JUnit event log", e);
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :integration-ctrf-validator:test --rerun --tests 'io.github.alexshamrai.integration.JunitEventsTest' 2>&1 | grep -E 'error:|FAILED|BUILD'`

Expected: `BUILD SUCCESSFUL`, with all 10 tests passing. Task 4's green runs check `JunitEvents` on real event logs.

- [ ] **Step 5: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add integration-ctrf-validator/src/main/java/io/github/alexshamrai/integration/JunitEvents.java \
  integration-ctrf-validator/src/test/java/io/github/alexshamrai/integration/JunitEventsTest.java
git commit -m "test: fold JUnit's event log into the entries a CTRF report must contain (TI-4)"
```

---

### Task 3: Inputs and expectations from the run

**Files:**
- Create: `integration-ctrf-validator/src/test/java/integration/IntegrationRun.java`, `.../integration/ExpectedRun.java`
- Test: `integration-ctrf-validator/src/test/java/integration/IntegrationRunTest.java`, `.../integration/ExpectedRunTest.java`
- Modify: `integration-ctrf-validator/src/test/java/integration/BaseIntegrationTest.java` (whole file)
- Modify: `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`: `verifySummaryIsCorrect`, `verifyTestsRunInMultipleThreads`, `verifyBuildParametersFromSystemProperties`, `verifyGeneratedByFieldIsCorrect`, `verifyEnvironmentHealthIsFalse`, `verifyFailedContainersBelowClassLevelAreReported`, `verifyTestsOfSkippedClassesAreReportedAsSkipped`, `isListenerReport()`, and the `LISTENER` constant
- Modify: `integration-ctrf-validator/build.gradle` (the `test` block)

**Interfaces:**
- Consumes: nothing from Tasks 1–2.
- Produces, in package `integration`:
  - `record IntegrationRun(File reportFile, Path moduleDir, String module, Path eventsDir)`, with `static IntegrationRun locate(String reportPathProperty, String eventsDirProperty)`, `boolean isListener()`, and the constants `LISTENER_MODULE`, `EXTENSION_MODULE` and `DEFAULT_REPORT_PATH`;
  - `record ExpectedRun(int threads, String buildName, Long buildNumber, String buildId, String buildUrl, boolean healthy, String generatedBy)`, with `static ExpectedRun of(IntegrationRun run) throws IOException` and `static ExpectedRun from(String module, Properties moduleProperties, Properties systemProperties, String envHealthy)`;
  - in `BaseIntegrationTest`, the protected fields `run` (`IntegrationRun`), `expected` (`ExpectedRun`), `report` (`CtrfJson`), `reportFile` (`File`) and `objectMapper`.

- [ ] **Step 1: Show that a plain local run fails today**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun > /tmp/claude-ti4-plain.log 2>&1
grep -E 'FAILED|BUILD' /tmp/claude-ti4-plain.log | head -5
./gradlew :integration-ctrf-validator:test --rerun -Dctrf.report.path=../integration-tests-listener/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
rm -f /tmp/claude-ti4-plain.log
```

Expected:
- the first run fails on every validator class, because "Report file not found" at the sentence-shaped default path;
- with an explicit path, `verifyTestsRunInMultipleThreads`, `verifyBuildParametersFromSystemProperties` and `verifyEnvironmentHealthIsFalse` fail. A plain run has one thread, `feature-branch`, and a healthy environment.

- [ ] **Step 2: Write the failing unit tests**

Create `integration-ctrf-validator/src/test/java/integration/IntegrationRunTest.java`:

```java
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
```

Create `integration-ctrf-validator/src/test/java/integration/ExpectedRunTest.java`:

```java
package integration;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectedRunTest {

    private static final String LISTENER = IntegrationRun.LISTENER_MODULE;
    private static final String EXTENSION = IntegrationRun.EXTENSION_MODULE;

    @Test
    void plainLocalRunExpectsTheModulePropertiesOneThreadAndAHealthyListener() {
        var expected = ExpectedRun.from(LISTENER, moduleProperties(), new Properties(), null);

        assertThat(expected).isEqualTo(new ExpectedRun(1, "feature-branch", 123L, null, "http://ci.example.com/build/123", true,
            ExpectedRun.LISTENER_ENTRY_POINT));
    }

    @Test
    void systemPropertiesOverrideTheModuleProperties() {
        var system = properties("threads", "2", "ctrf.build.name", "system-build", "ctrf.build.number", "777",
            "ctrf.build.url", "https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/1");

        var expected = ExpectedRun.from(LISTENER, moduleProperties(), system, null);

        assertThat(expected.threads()).isEqualTo(2);
        assertThat(expected.buildName()).isEqualTo("system-build");
        assertThat(expected.buildNumber()).isEqualTo(777L);
        assertThat(expected.buildUrl()).isEqualTo("https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/1");
    }

    @Test
    void envHealthyFalseInAnyCaseMeansUnhealthy() {
        assertThat(ExpectedRun.from(LISTENER, moduleProperties(), new Properties(), "FALSE").healthy()).isFalse();
        assertThat(ExpectedRun.from(LISTENER, moduleProperties(), new Properties(), "true").healthy()).isTrue();
    }

    @Test
    void extensionIsAlwaysUnhealthyAndGeneratedByTheExtension() {
        var expected = ExpectedRun.from(EXTENSION, moduleProperties(), new Properties(), null);

        assertThat(expected.healthy()).isFalse();
        assertThat(expected.generatedBy()).isEqualTo(ExpectedRun.EXTENSION_ENTRY_POINT);
    }

    @Test
    void nonNumericBuildNumberIsExpectedAsBuildId() {
        var expected = ExpectedRun.from(LISTENER, moduleProperties(), properties("ctrf.build.number", " 2026.10.15-3 "), null);

        assertThat(expected.buildNumber()).isNull();
        assertThat(expected.buildId()).isEqualTo("2026.10.15-3");
    }

    @Test
    void threadsThatArentANumberMeanOne() {
        assertThat(ExpectedRun.from(LISTENER, moduleProperties(), properties("threads", "many"), null).threads()).isEqualTo(1);
    }

    private static Properties moduleProperties() {
        return properties("ctrf.build.name", "feature-branch", "ctrf.build.number", "123",
            "ctrf.build.url", "http://ci.example.com/build/123");
    }

    private static Properties properties(String... keysAndValues) {
        var properties = new Properties();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }
        return properties;
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :integration-ctrf-validator:test --rerun --tests 'integration.IntegrationRunTest' --tests 'integration.ExpectedRunTest' 2>&1 | grep -E 'error:|FAILED|BUILD'`

Expected: `compileTestJava` fails with `cannot find symbol` for `IntegrationRun` and `ExpectedRun`.

- [ ] **Step 4: Implement `IntegrationRun` and `ExpectedRun`**

Create `integration-ctrf-validator/src/test/java/integration/IntegrationRun.java`:

```java
package integration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The report under validation, the integration module that wrote it, and that run's JUnit event log.
 *
 * @param reportFile the CTRF report
 * @param moduleDir  the integration module's directory
 * @param module     the module's name: {@link #LISTENER_MODULE} or {@link #EXTENSION_MODULE}
 * @param eventsDir  the directory holding JUnit's event log of the same run
 */
record IntegrationRun(File reportFile, Path moduleDir, String module, Path eventsDir) {

    static final String LISTENER_MODULE = "integration-tests-listener";
    static final String EXTENSION_MODULE = "integration-tests-extension";
    // Relative to the validator module, which is the working directory of its tests
    static final String DEFAULT_REPORT_PATH = "../" + LISTENER_MODULE + "/build/test-results/ctrf-report.json";

    /**
     * Locates the run from {@code -Dctrf.report.path} and {@code -Dctrf.junit.events.dir}; either may be {@code null}.
     */
    static IntegrationRun locate(String reportPathProperty, String eventsDirProperty) {
        Path report = Path.of(reportPathProperty != null ? reportPathProperty : DEFAULT_REPORT_PATH).toAbsolutePath().normalize();
        if (!Files.isRegularFile(report)) {
            throw new IllegalStateException("No report at " + report + ". Run ./gradlew :" + LISTENER_MODULE
                + ":test first, or pass -Dctrf.report.path=<report>.");
        }
        Path moduleDir = moduleDirOf(report);
        Path eventsDir = eventsDirProperty != null
            ? Path.of(eventsDirProperty).toAbsolutePath().normalize()
            : report.getParent().resolve("junit-platform");
        return new IntegrationRun(report.toFile(), moduleDir, moduleDir.getFileName().toString(), eventsDir);
    }

    boolean isListener() {
        return LISTENER_MODULE.equals(module);
    }

    private static Path moduleDirOf(Path report) {
        for (Path dir = report.getParent(); dir != null && dir.getParent() != null; dir = dir.getParent()) {
            String parentName = dir.getParent().getFileName() == null ? "" : dir.getParent().getFileName().toString();
            if (dir.getFileName().toString().equals("build")
                && (parentName.equals(LISTENER_MODULE) || parentName.equals(EXTENSION_MODULE))) {
                return dir.getParent();
            }
        }
        throw new IllegalStateException("Can't tell which integration module wrote " + report + ": expected it under "
            + LISTENER_MODULE + "/build or " + EXTENSION_MODULE + "/build.");
    }
}
```

Create `integration-ctrf-validator/src/test/java/integration/ExpectedRun.java`:

```java
package integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

/**
 * What the report must say about the run that wrote it, taken from that run's own inputs: the same {@code -Dthreads}
 * and {@code -Dctrf.build.*} flags, the same {@code ENV_HEALTHY} variable, and the module's {@code ctrf.properties}.
 *
 * @param threads     the distinct thread names to expect: exactly 1 when 1, otherwise between 2 and this
 * @param buildName   the expected {@code environment.buildName}
 * @param buildNumber the expected {@code environment.buildNumber}, or {@code null}
 * @param buildId     the expected {@code environment.buildId}, or {@code null}
 * @param buildUrl    the expected {@code environment.buildUrl}
 * @param healthy     the expected {@code environment.healthy}
 * @param generatedBy the expected {@code generatedBy}
 */
record ExpectedRun(int threads, String buildName, Long buildNumber, String buildId, String buildUrl, boolean healthy,
                   String generatedBy) {

    static final String LISTENER_ENTRY_POINT = "io.github.alexshamrai.launcher.CtrfListener";
    static final String EXTENSION_ENTRY_POINT = "io.github.alexshamrai.jupiter.CtrfExtension";

    static ExpectedRun of(IntegrationRun run) throws IOException {
        var moduleProperties = new Properties();
        try (InputStream in = Files.newInputStream(run.moduleDir().resolve("src/test/resources/ctrf.properties"))) {
            moduleProperties.load(in);
        }
        return from(run.module(), moduleProperties, System.getProperties(), System.getenv("ENV_HEALTHY"));
    }

    static ExpectedRun from(String module, Properties moduleProperties, Properties systemProperties, String envHealthy) {
        boolean extension = IntegrationRun.EXTENSION_MODULE.equals(module);
        String number = setting("ctrf.build.number", moduleProperties, systemProperties);
        String stripped = number == null ? "" : number.strip();
        boolean wholeNumber = stripped.matches("[0-9]+");
        return new ExpectedRun(
            threads(systemProperties.getProperty("threads")),
            setting("ctrf.build.name", moduleProperties, systemProperties),
            wholeNumber ? Long.valueOf(stripped) : null,
            wholeNumber || stripped.isEmpty() ? null : stripped,
            setting("ctrf.build.url", moduleProperties, systemProperties),
            // The extension module's FirstLongTest calls markEnvironmentUnhealthy()
            !("false".equalsIgnoreCase(envHealthy) || extension),
            extension ? EXTENSION_ENTRY_POINT : LISTENER_ENTRY_POINT);
    }

    // The library's precedence: a system property wins over ctrf.properties
    private static String setting(String key, Properties moduleProperties, Properties systemProperties) {
        String value = systemProperties.getProperty(key);
        return value != null ? value : moduleProperties.getProperty(key);
    }

    // The integration modules run tests in parallel only for an integer above 1
    private static int threads(String value) {
        try {
            return value == null ? 1 : Math.max(1, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
```

- [ ] **Step 5: Run the unit tests to verify they pass**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :integration-ctrf-validator:test --rerun --tests 'integration.IntegrationRunTest' --tests 'integration.ExpectedRunTest' 2>&1 | grep -E 'error:|FAILED|BUILD'`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Use them in `BaseIntegrationTest`, `CtrfLogicTest` and the build**

Replace the whole content of `integration-ctrf-validator/src/test/java/integration/BaseIntegrationTest.java` with:

```java
package integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected IntegrationRun run;
    protected ExpectedRun expected;
    protected CtrfJson report;
    protected File reportFile;

    @BeforeAll
    void setup() throws IOException {
        run = IntegrationRun.locate(System.getProperty("ctrf.report.path"), System.getProperty("ctrf.junit.events.dir"));
        expected = ExpectedRun.of(run);
        reportFile = run.reportFile();
        report = objectMapper.readValue(reportFile, CtrfJson.class);
    }
}
```

In `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`:

1. Delete the line `    private static final String LISTENER = "io.github.alexshamrai.launcher.CtrfListener";` and the blank line after it.
2. Replace the whole method `verifyTestsRunInMultipleThreads`, including its comment line, with:

```java
    @Test
    void verifyThreadCountMatchesTheRun() {
        var threadIds = report.getResults().getTests().stream()
            .map(io.github.alexshamrai.ctrf.model.Test::getThreadId)
            .distinct()
            .toList();

        assertThat(threadIds).as("thread IDs").doesNotContainNull();
        if (expected.threads() <= 1) {
            assertThat(threadIds).as("thread IDs of a run without -Dthreads").hasSize(1);
        } else {
            assertThat(threadIds.size())
                .as("distinct thread IDs of a run with -Dthreads=%d", expected.threads())
                .isBetween(2, expected.threads());
        }
    }
```

3. Replace the whole method `verifyBuildParametersFromSystemProperties`, including its two comment lines, with:

```java
    // The run's -Dctrf.build.* flags, or the module's ctrf.properties: checks that Gradle forwards -Dctrf.* end to end
    @Test
    void verifyBuildValuesMatchTheRun() {
        var environment = report.getResults().getEnvironment();
        assertThat(environment).isNotNull();

        assertThat(environment.getBuildName()).as("buildName").isEqualTo(expected.buildName());
        assertThat(environment.getBuildNumber()).as("buildNumber").isEqualTo(expected.buildNumber());
        assertThat(environment.getBuildId()).as("buildId").isEqualTo(expected.buildId());
        assertThat(environment.getBuildUrl()).as("buildUrl").isEqualTo(expected.buildUrl());
    }
```

4. Replace the whole method `verifyGeneratedByFieldIsCorrect` with:

```java
    @Test
    void verifyGeneratedByMatchesTheModule() {
        assertThat(report.getGeneratedBy())
            .as("generatedBy of a report written by %s", run.module())
            .isEqualTo(expected.generatedBy());
    }
```

5. Replace the whole method `verifyEnvironmentHealthIsFalse` with:

```java
    @Test
    void verifyEnvironmentHealthMatchesTheRun() {
        var environment = report.getResults().getEnvironment();
        assertThat(environment).isNotNull();

        assertThat(environment.isHealthy())
            .as("healthy: false when ENV_HEALTHY=false or for the extension module, whose FirstLongTest marks it unhealthy")
            .isEqualTo(expected.healthy());
    }
```

6. Replace every `isListenerReport()` call (in `verifySummaryIsCorrect`, `verifyFailedContainersBelowClassLevelAreReported` and `verifyTestsOfSkippedClassesAreReportedAsSkipped`) with `run.isListener()`. Delete the method `isListenerReport()` and the two comment lines above it.

In `integration-ctrf-validator/build.gradle`, replace:

```groovy
test {
    useJUnitPlatform()

    systemProperties += System.properties.findAll { k, v -> k.toString().startsWith("ctrf") }
}
```

with:

```groovy
test {
    useJUnitPlatform()

    // The same -Dctrf.* and -Dthreads flags as the integration run: the expectations come from them (ExpectedRun)
    systemProperties += System.properties.findAll { k, v -> k.toString().startsWith("ctrf") || k == "threads" }

    // One command can run an integration module and then validate its report
    mustRunAfter ':integration-tests-listener:test', ':integration-tests-extension:test'
}
```

- [ ] **Step 7: Run a plain local run and a CI-flag run to verify they pass**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun 2>&1 | grep -E 'FAILED|BUILD'
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun -Dforks=2 -Dthreads=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 2>&1 | grep -E 'FAILED|BUILD'
./gradlew clean :integration-tests-extension:test :integration-ctrf-validator:test --rerun -Dthreads=2 \
  -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
```

Expected: `BUILD SUCCESSFUL` all three times, with no `FAILED` lines.
- In the third run, `-Dctrf.report.path` also reaches the extension's own test JVM. There, `../integration-tests-extension/…`, resolved from the extension module's directory, is the same file.
- `-Dforks` goes only to the integration module. The validator ignores it.

- [ ] **Step 8: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add integration-ctrf-validator
git status --short
git commit -m "test: the validator takes its expectations from the run that wrote the report (TI-4)"
```

---

### Task 4: `JunitCrossCheckTest`

**Files:**
- Create: `integration-ctrf-validator/src/test/java/integration/JunitCrossCheckTest.java`
- Create (not committed): `$SCRATCH/ti4-old.init.gradle`, and the temporary worktree `$MW`

**Interfaces:**
- Consumes:
  - `JunitEvents.read(Path)`, `expectedEntries()`, `skippedContainers()` and `JunitEvents.Expected(testId, status, retries, flaky)` (Task 2);
  - `BaseIntegrationTest`'s `run.eventsDir()` and `report` (Task 3);
  - the library's `Test.TestStatus.toLowerCase()`, which returns `"passed"`, `"failed"` or `"skipped"`.
- Produces: the test class `integration.JunitCrossCheckTest`, with the tests `everyEntryJunitExpectsIsInTheReport`, `reportHasNoEntriesJunitDidNotRun` and `testsOfSkippedContainersAreReportedAsSkipped`.

- [ ] **Step 1: Write the cross-check**

Create `integration-ctrf-validator/src/test/java/integration/JunitCrossCheckTest.java`:

```java
package integration;

import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import io.github.alexshamrai.integration.JunitEvents;
import io.github.alexshamrai.integration.JunitEvents.Expected;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks every report entry against JUnit's own event log of the same run, by testId (see {@link JunitEvents}).
 */
public class JunitCrossCheckTest extends BaseIntegrationTest {

    private JunitEvents junit;
    private Map<String, io.github.alexshamrai.ctrf.model.Test> entries;

    @BeforeAll
    void readJunitEventLog() throws IOException {
        junit = JunitEvents.read(run.eventsDir());
        entries = new LinkedHashMap<>();
        report.getResults().getTests().forEach(test -> entries.putIfAbsent(test.getTestId(), test));
    }

    @Test
    void everyEntryJunitExpectsIsInTheReport() {
        List<String> problems = new ArrayList<>();
        junit.expectedEntries().values().forEach(want -> {
            var entry = entries.get(want.testId());
            if (entry == null) {
                problems.add("missing: " + want);
                return;
            }
            var got = new Expected(entry.getTestId(), entry.getStatus().toLowerCase(),
                entry.getRetries() == null ? 0 : entry.getRetries(), Boolean.TRUE.equals(entry.getFlaky()));
            if (!got.equals(want)) {
                problems.add("JUnit says " + want + ", the report says " + got);
            }
        });

        assertThat(problems)
            .as("entries JUnit ran (%d) that the report misses or gets wrong", junit.expectedEntries().size())
            .isEmpty();
    }

    @Test
    void reportHasNoEntriesJunitDidNotRun() {
        var unknown = entries.keySet().stream()
            .filter(testId -> !junit.expectedEntries().containsKey(testId))
            .filter(testId -> junit.skippedContainers().stream().noneMatch(container -> testId.startsWith(container + "/")))
            .toList();

        assertThat(unknown).as("report entries that aren't in JUnit's event log").isEmpty();
    }

    @Test
    void testsOfSkippedContainersAreReportedAsSkipped() {
        junit.skippedContainers().forEach(container -> {
            var under = entries.values().stream()
                .filter(test -> test.getTestId().startsWith(container + "/"))
                .toList();

            assertThat(under)
                .as("report entries under %s, which JUnit skipped as a whole", container)
                .isNotEmpty()
                .allSatisfy(test -> assertThat(test.getStatus()).as("status of %s", test.getTestId()).isEqualTo(TestStatus.SKIPPED));
        });
    }
}
```

- [ ] **Step 2: Generate a report from before CB-6, with its event log**

`58e36f0` is the last commit before CB-6. There, parallel forks overwrite each other's results. An init script adds the event log and 2 forks without editing that commit:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
MW=$SCRATCH/ti4-old
cat > $SCRATCH/ti4-old.init.gradle <<'EOF'
allprojects {
  afterEvaluate { p ->
    if (p.name == 'integration-tests-listener') {
      p.dependencies { testRuntimeOnly 'org.junit.platform:junit-platform-reporting' }
      p.tasks.withType(Test).configureEach {
        maxParallelForks = 2
        systemProperty 'junit.platform.reporting.open.xml.enabled', 'true'
        systemProperty 'junit.platform.reporting.output.dir',
          p.layout.buildDirectory.dir('test-results/junit-platform/{uniqueNumber}').get().asFile.absolutePath
      }
    }
  }
}
EOF
git worktree add --detach $MW 58e36f0
(cd $MW && ENV_HEALTHY=false ./gradlew -I $SCRATCH/ti4-old.init.gradle clean :integration-tests-listener:test -Dthreads=2 -q > /dev/null 2>&1)
python3 -c "import json;print(len(json.load(open('$MW/integration-tests-listener/build/test-results/ctrf-report.json'))['results']['tests']), 'tests in the old report')"
find $MW/integration-tests-listener/build/test-results/junit-platform -name open-test-report.xml | wc -l
```

Expected: fewer than 28 tests in the old report (CB-6 measured 16), and 3 or more event files.

- [ ] **Step 3: Run the cross-check on it to verify it fails**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
MW=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/ti4-old
./gradlew :integration-ctrf-validator:test --rerun --tests 'integration.JunitCrossCheckTest' \
  -Dctrf.report.path=$MW/integration-tests-listener/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
python3 - <<'EOF'
import xml.etree.ElementTree as ET
root = ET.parse('integration-ctrf-validator/build/test-results/test/TEST-integration.JunitCrossCheckTest.xml').getroot()
for case in root.iter('testcase'):
    failure = case.find('failure')
    print('FAILED' if failure is not None else 'passed', case.get('name'))
    if failure is not None:
        message = failure.get('message') or ''
        print('   ', message.count('missing:'), 'missing;', message[:400])
EOF
```

Expected:
- `BUILD FAILED`;
- `everyEntryJunitExpectsIsInTheReport` FAILED, with one `missing:` line for each test the forks lost (as many as the report is short of 28);
- the other two tests pass or fail independently of this.

- [ ] **Step 4: Remove the temporary worktree**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
git worktree remove --force $SCRATCH/ti4-old
rm -f $SCRATCH/ti4-old.init.gradle
git worktree list
```

Expected: `ti4-old` is no longer listed. It is scratch created in Step 2.

- [ ] **Step 5: Run the cross-check on the branch to verify it passes**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun -Dforks=2 -Dthreads=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 2>&1 | grep -E 'FAILED|BUILD'
./gradlew clean :integration-tests-extension:test :integration-ctrf-validator:test --rerun -Dthreads=2 \
  -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
```

Expected: `BUILD SUCCESSFUL` both times.

- [ ] **Step 6: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add integration-ctrf-validator/src/test/java/integration/JunitCrossCheckTest.java
git status --short
git commit -m "test: cross-check every report entry against JUnit's event log by testId (TI-4)"
```

---

### Task 5: `CtrfLogicTest` keyed by `testId`

**Files:**
- Modify: `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java` (whole file)

**Interfaces:**
- Consumes:
  - `BaseIntegrationTest`'s `report`, `run` and `expected` (Task 3);
  - the methods Task 3 rewrote, which are reproduced verbatim below: `verifyThreadCountMatchesTheRun`, `verifyBuildValuesMatchTheRun`, `verifyGeneratedByMatchesTheModule`, `verifyEnvironmentHealthMatchesTheRun`;
  - the cross-check (Task 4), which now checks every entry's presence, status, `retries` and `flaky`.
- Produces: nothing used by code.

- [ ] **Step 1: Replace the file**

The new file follows these rules:
- **Deleted:** `verifyTestStatuses`, and every status, `retries` and `flaky` assertion that the cross-check now makes.
- **Kept, but looked up by `testId`:** every check of what JUnit can't know. That covers names, messages, `rawStatus`, the contents of `retryAttempts`, durations, threads, `filePath`, and the tests of skipped classes. `testWithId` fails when the entry is missing.
- **Kept as they are:** the summary, `runId`, uniqueness and retry-consistency checks.

Replace the whole content of `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java` with:

```java
package integration;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks what JUnit's event log can't: names, messages, durations, threads and the run's settings. Presence, final
 * status, retries and flaky of every entry are checked by {@link JunitCrossCheckTest}.
 */
public class CtrfLogicTest extends BaseIntegrationTest {

    private static final String FAKE_PACKAGE = "io.github.alexshamrai.integration.fake.";

    @Test
    void verifySummaryIsCorrect() {
        var summary = report.getResults().getSummary();
        assertThat(summary).isNotNull();

        // Each test appears once, as its final attempt: the test-retry plugin's second round is folded into
        // retryAttempts. Both modules: the shared fake tests, AfterAllFailureTest's passing test and its
        // teardownError, and AssumptionTest's aborted test, which counts as skipped. The listener module also
        // reports BrokenSourceTest's two failed containers, and the four tests of DisabledClassTest and
        // ConditionallySkippedClassTest as skipped.
        if (run.isListener()) {
            assertThat(summary.getTests()).isEqualTo(28);
            assertThat(summary.getPassed()).isEqualTo(14);
            assertThat(summary.getFailed()).isEqualTo(7);
            assertThat(summary.getSkipped()).isEqualTo(7);
        } else {
            assertThat(summary.getTests()).isEqualTo(22);
            assertThat(summary.getPassed()).isEqualTo(14);
            assertThat(summary.getFailed()).isEqualTo(5);
            assertThat(summary.getSkipped()).isEqualTo(3);
        }
        assertThat(summary.getFlaky()).isEqualTo(1);
        assertThat(summary.getPending()).isEqualTo(0);
        assertThat(summary.getOther()).isEqualTo(0);
        assertThat(summary.getStart()).isGreaterThan(0);
        assertThat(summary.getStop()).isGreaterThanOrEqualTo(summary.getStart());

        var total = summary.getTests();
        var sum = summary.getPassed() + summary.getFailed() + summary.getSkipped()
            + summary.getPending() + summary.getOther();
        assertThat(total).isEqualTo(sum);
    }

    @Test
    void verifyLongTestDurations() {
        var minimumDurations = Map.of(
            testWithId("FirstLongTest", "[method:firstLongTwoSecondTest()]"), 2000L,
            testWithId("FirstLongTest", "[method:firstLongOneSecondTest()]"), 1000L,
            testWithId("FirstLongTest", "[method:firstLongHalfSecondTest()]"), 500L,
            testWithId("SecondLongTest", "[method:secondLongTwoSecondTest()]"), 2000L,
            testWithId("SecondLongTest", "[method:secondLongOneSecondTest()]"), 1000L,
            testWithId("SecondLongTest", "[method:secondLongHalfSecondTest()]"), 500L);

        minimumDurations.forEach((test, minimum) -> assertThat(test.getDuration())
            .as("duration of %s", test.getTestId())
            .isGreaterThanOrEqualTo(minimum));
    }

    @Test
    void verifyThreadCountMatchesTheRun() {
        var threadIds = report.getResults().getTests().stream()
            .map(io.github.alexshamrai.ctrf.model.Test::getThreadId)
            .distinct()
            .toList();

        assertThat(threadIds).as("thread IDs").doesNotContainNull();
        if (expected.threads() <= 1) {
            assertThat(threadIds).as("thread IDs of a run without -Dthreads").hasSize(1);
        } else {
            assertThat(threadIds.size())
                .as("distinct thread IDs of a run with -Dthreads=%d", expected.threads())
                .isBetween(2, expected.threads());
        }
    }

    // The run's -Dctrf.build.* flags, or the module's ctrf.properties: checks that Gradle forwards -Dctrf.* end to end
    @Test
    void verifyBuildValuesMatchTheRun() {
        var environment = report.getResults().getEnvironment();
        assertThat(environment).isNotNull();

        assertThat(environment.getBuildName()).as("buildName").isEqualTo(expected.buildName());
        assertThat(environment.getBuildNumber()).as("buildNumber").isEqualTo(expected.buildNumber());
        assertThat(environment.getBuildId()).as("buildId").isEqualTo(expected.buildId());
        assertThat(environment.getBuildUrl()).as("buildUrl").isEqualTo(expected.buildUrl());
    }

    @Test
    void verifySummaryDurationSpansTheRun() {
        var summary = report.getResults().getSummary();

        assertThat(summary.getDuration())
            .as("summary.duration should be stop minus start, across every JVM merged into the report")
            .isEqualTo(summary.getStop() - summary.getStart())
            .isPositive();
    }

    @Test
    void verifyFlakyTestKeepsItsFailedAttempt() {
        var flakyTest = testWithId("FlakyTest", "[method:flakyTestPassesOnTheSecondRun()]");

        assertThat(flakyTest.getName()).isEqualTo("Flaky test passed on the second run");
        assertThat(flakyTest.getRetryAttempts())
            .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus)
            .containsExactly(tuple(1, TestStatus.FAILED));
    }

    @Test
    void verifyGeneratedByMatchesTheModule() {
        assertThat(report.getGeneratedBy())
            .as("generatedBy of a report written by %s", run.module())
            .isEqualTo(expected.generatedBy());
    }

    @Test
    void verifyEnvironmentHealthMatchesTheRun() {
        var environment = report.getResults().getEnvironment();
        assertThat(environment).isNotNull();

        assertThat(environment.isHealthy())
            .as("healthy: false when ENV_HEALTHY=false or for the extension module, whose FirstLongTest marks it unhealthy")
            .isEqualTo(expected.healthy());
    }

    @Test
    void verifyInitializationErrorsKeepTheirClassAndMessage() {
        var first = testWithId("InitializationErrorTest", null);
        var second = testWithId("AnotherInitializationErrorTest", null);

        assertThat(first.getName()).isEqualTo("initializationError");
        assertThat(first.getFilePath()).isEqualTo(FAKE_PACKAGE + "InitializationErrorTest");
        assertThat(first.getMessage()).contains("Simulated initialization failure");
        assertThat(second.getName()).isEqualTo("initializationError");
        assertThat(second.getFilePath()).isEqualTo(FAKE_PACKAGE + "AnotherInitializationErrorTest");
        assertThat(second.getMessage()).contains("Another initialization failure");
    }

    @Test
    void verifyAfterAllFailureIsReportedAsTeardownError() {
        var teardown = testWithId("AfterAllFailureTest", null);

        assertThat(teardown.getName()).isEqualTo("teardownError");
        assertThat(teardown.getMessage()).contains("Simulated teardown failure");
        assertThat(testWithId("AfterAllFailureTest", "[method:testBeforeFailingTeardown()]").getName())
            .isEqualTo("testBeforeFailingTeardown()");
    }

    @Test
    void verifyFailedContainersBelowClassLevelAreReported() {
        assumeTrue(run.isListener(), "Only the listener sees containers below class level");

        assertThat(testWithId("BrokenSourceTest", "[test-template:usesData(int)]").getName()).isEqualTo("usesData(int)");
        assertThat(testWithId("BrokenSourceTest", "[test-factory:factory()]").getName()).isEqualTo("factory()");
    }

    @Test
    void verifyEveryTestHasATestId() {
        var tests = report.getResults().getTests();
        assertThat(tests).isNotNull().isNotEmpty();

        assertThat(tests).allSatisfy(test ->
            assertThat(test.getTestId()).as("testId of %s", test.getName()).isNotBlank());
    }

    @Test
    void verifyClassFailuresAreIdentifiedByTheirClass() {
        var classFailures = report.getResults().getTests().stream()
            .filter(test -> "initializationError".equals(test.getName()) || "teardownError".equals(test.getName()))
            .toList();
        assertThat(classFailures)
            .extracting(io.github.alexshamrai.ctrf.model.Test::getName)
            .contains("initializationError", "teardownError");

        assertThat(classFailures).allSatisfy(test ->
            assertThat(test.getTestId())
                .as("testId of the %s of %s", test.getName(), test.getFilePath())
                .isEqualTo("[engine:junit-jupiter]/[class:" + test.getFilePath() + "]"));
    }

    @Test
    void verifyTestsOfSkippedClassesAreReportedWithTheirReason() {
        assumeTrue(run.isListener(), "Only the listener sees tests of classes that JUnit skips as a whole");

        for (String method : List.of("firstTestOfDisabledClass()", "secondTestOfDisabledClass()")) {
            assertThat(testWithId("DisabledClassTest", "[method:" + method + "]").getMessage())
                .as("reason for %s", method)
                .contains("Simulated disabled class");
        }
        for (String method : List.of("firstTestOfSkippedClass()", "secondTestOfSkippedClass()")) {
            assertThat(testWithId("ConditionallySkippedClassTest", "[method:" + method + "]").getMessage())
                .as("reason for %s", method)
                .contains("ctrf.integration.never.set");
        }
    }

    @Test
    void verifyFailedAssumptionKeepsTheAbortedStatus() {
        var abortedTest = testWithId("AssumptionTest", "[method:requiresUnavailableService()]");

        assertThat(abortedTest.getRawStatus()).isEqualTo("aborted");
        assertThat(abortedTest.getMessage()).contains("Simulated assumption failure");
    }

    @Test
    void verifyEachTestAppearsOnce() {
        var testIds = report.getResults().getTests().stream()
            .map(io.github.alexshamrai.ctrf.model.Test::getTestId)
            .toList();

        assertThat(testIds).as("testIds in the report").doesNotHaveDuplicates();
    }

    @Test
    void verifyRetryFieldsAreConsistent() {
        assertThat(report.getResults().getTests()).allSatisfy(test -> {
            if (test.getRetryAttempts() == null) {
                assertThat(test.getRetries()).as("retries of %s without retryAttempts", test.getName()).isNull();
            } else {
                assertThat(test.getRetries()).as("retries of %s", test.getName()).isEqualTo(test.getRetryAttempts().size());
                assertThat(test.getRetryAttempts())
                    .extracting(RetryAttempt::getAttempt)
                    .as("attempt numbers of %s", test.getName())
                    .isEqualTo(IntStream.rangeClosed(1, test.getRetries()).boxed().toList());
            }
        });
    }

    @Test
    void verifyTestsFailingInEveryRoundKeepTheirFailedAttempts() {
        for (String method : List.of("firstFailedTest()", "secondFailedTest()")) {
            assertThat(testWithId("DummyFailedTest", "[method:" + method + "]").getRetryAttempts())
                .extracting(RetryAttempt::getStatus)
                .as("earlier attempts of %s", method)
                .containsExactly(TestStatus.FAILED);
        }
    }

    @Test
    void verifySummaryCountsFlakyTests() {
        long flakyTests = report.getResults().getTests().stream()
            .filter(test -> Boolean.TRUE.equals(test.getFlaky()))
            .count();

        assertThat(report.getResults().getSummary().getFlaky()).isEqualTo((int) flakyTests);
    }

    @Test
    void verifyReportHasARunId() {
        assertThat(report.getRunId()).as("root runId").isNotBlank();
    }

    /**
     * The report entry whose testId is the given fake test class's ID, followed by {@code /segment} unless the segment
     * is {@code null} (a class-level entry). Fails if the report doesn't have exactly one.
     */
    private io.github.alexshamrai.ctrf.model.Test testWithId(String fakeClass, String segment) {
        var testId = "[engine:junit-jupiter]/[class:" + FAKE_PACKAGE + fakeClass + "]" + (segment == null ? "" : "/" + segment);
        var matches = report.getResults().getTests().stream()
            .filter(test -> testId.equals(test.getTestId()))
            .toList();
        assertThat(matches).as("entries with testId %s", testId).hasSize(1);
        return matches.get(0);
    }
}
```

- [ ] **Step 2: Show the helper fails when a test is missing**

Temporarily change `"firstFailedTest()"` in `verifyTestsFailingInEveryRoundKeepTheirFailedAttempts`'s list to `"noSuchTest()"`, run the listener check, and revert:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
sed -i '' 's/List.of("firstFailedTest()", "secondFailedTest()")/List.of("noSuchTest()", "secondFailedTest()")/' \
  integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java
./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun --tests 'integration.CtrfLogicTest' 2>&1 \
  | grep -E 'FAILED|BUILD'
sed -i '' 's/List.of("noSuchTest()", "secondFailedTest()")/List.of("firstFailedTest()", "secondFailedTest()")/' \
  integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java
grep -c 'noSuchTest' integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java
```

Expected:
- `verifyTestsFailingInEveryRoundKeepTheirFailedAttempts FAILED` and `BUILD FAILED`. A missing test now fails instead of passing silently;
- the final `grep -c` prints `0`, because the change is reverted.

- [ ] **Step 3: Run the validator on both modules to verify it passes**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun 2>&1 | grep -E 'FAILED|BUILD'
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun -Dforks=2 -Dthreads=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 2>&1 | grep -E 'FAILED|BUILD'
./gradlew clean :integration-tests-extension:test :integration-ctrf-validator:test --rerun -Dthreads=2 \
  -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
```

Expected: `BUILD SUCCESSFUL` three times. For the extension, the listener-only checks are skipped by their assumption.

- [ ] **Step 4: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java
git status --short
git commit -m "test: CtrfLogicTest looks tests up by testId and leaves statuses to the cross-check (TI-4)"
```

---

### Task 6: CI and docs

**Files:**
- Modify: `.github/workflows/integration-listener.yml` (the job `env` and the two Gradle steps)
- Modify: `.github/workflows/integration-extension.yml` (the same)
- Modify: `INTEGRATION_TESTS.md` (the sections "Test Execution Flow" through "Run tests exactly as in CI")
- Modify: `CLAUDE.md` (the integration-test command block under "Integration Tests")

**Interfaces:**
- Consumes: the validator's inputs from Tasks 3–4 (`-Dctrf.report.path`, `-Dthreads`, `-Dctrf.build.*`, `ENV_HEALTHY`, `-Dctrf.junit.events.dir`).
- Produces: nothing used by code.

- [ ] **Step 1: The listener workflow**

In `.github/workflows/integration-listener.yml`, replace:

```yaml
    env:
      CTRF_REPORT_PATH: 'integration-tests-listener/build/test-results/ctrf-report.json'
      ENV_HEALTHY: 'false' ## required for ctrf-validator test to pass env.healthy check
```

with:

```yaml
    env:
      CTRF_REPORT_PATH: 'integration-tests-listener/build/test-results/ctrf-report.json'
      ENV_HEALTHY: 'false' ## the report must say unhealthy; the validator reads the same variable
      # Passed to both the report step and the validator, which takes its expectations from them
      RUN_FLAGS: >-
        -Dthreads=${{ github.event_name == 'workflow_dispatch' && github.event.inputs.threads || '2' }}
        -Dctrf.build.name=system-build
        -Dctrf.build.number=${{ github.run_number }}
        -Dctrf.build.url=https://github.com/${{ github.repository }}/actions/runs/${{ github.run_id }}
```

Replace:

```yaml
        run: |
          ./gradlew :integration-tests-listener:test \
          -Dthreads=${{ github.event_name == 'workflow_dispatch' && github.event.inputs.threads || '2' }} \
          -Dforks=2 \
          -Dctrf.build.name=system-build \
          -Dctrf.build.number=${{ github.run_number }} \
          -Dctrf.build.url=https://github.com/${{ github.repository }}/actions/runs/${{ github.run_id }} \
        if: always()
```

with:

```yaml
        run: ./gradlew :integration-tests-listener:test -Dforks=2 $RUN_FLAGS
        if: always()
```

Replace:

```yaml
        run: |
          ./gradlew :integration-ctrf-validator:test \
          -Dctrf.report.path=../${{ env.CTRF_REPORT_PATH }}
        if: always()
```

with:

```yaml
        run: ./gradlew :integration-ctrf-validator:test $RUN_FLAGS -Dctrf.report.path=../${{ env.CTRF_REPORT_PATH }}
        if: always()
```

- [ ] **Step 2: The extension workflow**

In `.github/workflows/integration-extension.yml`, replace:

```yaml
    env:
      CTRF_REPORT_PATH: 'integration-tests-extension/build/test-results/ctrf-report.json'
```

with:

```yaml
    env:
      CTRF_REPORT_PATH: 'integration-tests-extension/build/test-results/ctrf-report.json'
      # Passed to both the report step and the validator, which takes its expectations from them
      RUN_FLAGS: >-
        -Dthreads=${{ github.event_name == 'workflow_dispatch' && github.event.inputs.threads || '2' }}
        -Dctrf.build.name=system-build
        -Dctrf.build.number=${{ github.run_number }}
        -Dctrf.build.url=https://github.com/${{ github.repository }}/actions/runs/${{ github.run_id }}
```

Replace:

```yaml
        run: |
          ./gradlew :integration-tests-extension:test \
          -Dthreads=${{ github.event_name == 'workflow_dispatch' && github.event.inputs.threads || '2' }} \
          -Dctrf.build.name=system-build \
          -Dctrf.build.number=${{ github.run_number }} \
          -Dctrf.build.url=https://github.com/${{ github.repository }}/actions/runs/${{ github.run_id }} \
        if: always()
```

with:

```yaml
        run: ./gradlew :integration-tests-extension:test $RUN_FLAGS
        if: always()
```

Replace:

```yaml
        run: |
          ./gradlew :integration-ctrf-validator:test \
          -Dctrf.report.path=../${{ env.CTRF_REPORT_PATH }}
        if: always()
```

with:

```yaml
        run: ./gradlew :integration-ctrf-validator:test $RUN_FLAGS -Dctrf.report.path=../${{ env.CTRF_REPORT_PATH }}
        if: always()
```

- [ ] **Step 3: Check the workflows**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
for f in .github/workflows/integration-listener.yml .github/workflows/integration-extension.yml; do
  ruby -ryaml -e 'y = YAML.load_file(ARGV[0]); env = y["jobs"]["build"]["env"]; puts "#{ARGV[0]}: #{env["RUN_FLAGS"]}"' "${f}"
done
grep -n 'RUN_FLAGS' .github/workflows/integration-*.yml
```

Expected:
- each file parses;
- `RUN_FLAGS` is one line with the four `-D` flags separated by spaces;
- each workflow uses it in two `run` lines.

Then reproduce CI's two steps locally the way the workflow expands them:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
export RUN_FLAGS="-Dthreads=2 -Dctrf.build.name=system-build -Dctrf.build.number=777 -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678"
# bash splits the unquoted $RUN_FLAGS into separate arguments, as the GitHub Actions runner does
ENV_HEALTHY=false bash -c './gradlew clean :integration-tests-listener:test -Dforks=2 $RUN_FLAGS -q > /dev/null 2>&1
  ./gradlew :integration-ctrf-validator:test --rerun $RUN_FLAGS \
    -Dctrf.report.path=../integration-tests-listener/build/test-results/ctrf-report.json 2>&1 | grep -E "FAILED|BUILD"'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: `INTEGRATION_TESTS.md`**

Replace everything from the line `## Test Execution Flow` up to, but not including, the line `## Conclusion` with:

````markdown
## Test Execution Flow

The integration tests follow a two-phase execution process:

1. **Report Generation Phase**:
   - Executes the fake test classes to generate a CTRF report
   - Uses the `integration-tests-listener:test` or `integration-tests-extension:test` Gradle task
   - Ignores test failures (since some tests are designed to fail)
   - Also writes JUnit's own event log (Open Test Reporting) to `build/test-results/junit-platform/<number>/open-test-report.xml`, one file per JVM: each fork and each retry round
   - Gives each run its own `ctrf.run.id`, so a second run without `clean` replaces both the report and the event log

2. **Validation Phase**:
   - Uses the `integration-ctrf-validator:test` Gradle task
   - Runs `CtrfSchemaValidationTest`, `JunitCrossCheckTest` and `CtrfLogicTest`, plus unit tests of its helpers

### Validation Tests

#### CtrfSchemaValidationTest

This test validates that the generated report complies with the CTRF schema:

- Validates the report against the official CTRF schema in draft-07 mode, so unknown properties and wrong types are errors
- Checks that the report format is "CTRF" and that `specVersion` equals `CtrfJson.SPEC_VERSION`
- Checks that the validator rejects a report with the legacy `filepath` key or a string `buildNumber`
- Ensures all test entries have valid names and statuses

#### JunitCrossCheckTest

This test checks every report entry against JUnit's own event log of the same run, by `testId` (the JUnit unique ID):

- Every test JUnit ran, and every container that failed at least once, is in the report with the same final status, `retries` and `flaky`
- The report has no other entries, except tests inside a class that JUnit skipped as a whole, which JUnit never starts and so can't list
- Each class JUnit skipped as a whole has at least one entry under it, all skipped

#### CtrfLogicTest

This test checks what JUnit's event log can't, looking each test up by `testId` and failing if it's missing:

- Summary counts for the fake test set, and that `summary.duration` spans the run
- Names, messages, `rawStatus`, `retryAttempts`, durations and the `testId` of class-level failures
- The run's settings: thread count, build name, number and URL, environment health and `generatedBy`

## Running Integration Tests

One command generates the listener's report and validates it:

```bash
./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test
```

With no flags, the validator expects what that run produces: one thread, the build values from the module's `src/test/resources/ctrf.properties`, and a healthy environment. For the extension module, pass its report:

```bash
./gradlew clean :integration-tests-extension:test :integration-ctrf-validator:test \
  -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json
```

`-Dctrf.report.path` also reaches the extension's own test JVM, where `../integration-tests-extension/…` resolved from that module's directory is the same file, so it's harmless.

### Settings

Pass a setting once to the one command, and both tasks receive it. The validator takes its expectations from the same inputs:

| Setting | Effect on the run | What the validator expects |
|---|---|---|
| `-Dthreads=N` | runs tests on N threads | between 2 and N distinct thread names (exactly 1 without it) |
| `-Dforks=N` | runs N forked JVMs | nothing; the report and JUnit's event log must still agree |
| `-Dctrf.build.name`, `-Dctrf.build.number`, `-Dctrf.build.url` | override `ctrf.properties` | the same values in `environment`; a non-numeric build number as `buildId` |
| `ENV_HEALTHY=false` (environment variable) | marks the environment unhealthy | `healthy: false` (the extension module's `FirstLongTest` always marks it unhealthy) |
| `-Dctrf.report.path` | where the validator reads the report | default `../integration-tests-listener/build/test-results/ctrf-report.json` |
| `-Dctrf.junit.events.dir` | where the validator reads JUnit's event log | default `junit-platform` next to the report |

When the two tasks run in separate commands, pass the same settings to both.

## Run tests exactly as in CI

CI passes the same flags to the report step and the validator step. Locally, in one command:

### For integration-tests-listener

```bash
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test \
  -Dforks=2 -Dthreads=2 \
  -Dctrf.build.name=system-build \
  -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678
```

### For integration-tests-extension

```bash
./gradlew clean :integration-tests-extension:test :integration-ctrf-validator:test -Dthreads=2 \
  -Dctrf.build.name=system-build \
  -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 \
  -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json
```

````

- [ ] **Step 5: `CLAUDE.md`**

In `CLAUDE.md`, replace the code block that starts with `# Generate CTRF report using listener-based tests` and ends with `  -Dctrf.report.path=../integration-tests-listener/build/test-results/ctrf-report.json`, keeping its fences, with:

```bash
# Generate the listener's report and validate it (no flags: the validator expects a plain local run)
./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test

# The same for the extension
./gradlew clean :integration-tests-extension:test :integration-ctrf-validator:test \
  -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json

# Exactly as CI: the same flags reach both tasks, and the validator expects them
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test \
  -Dforks=2 -Dthreads=2 \
  -Dctrf.build.name=system-build \
  -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678
```

Replace the paragraph that follows the block:

```markdown
**Important:** Integration tests use a two-phase process:
1. Generate report by running fake tests that are designed to succeed, fail, or be skipped
2. Validate the generated CTRF JSON against schema and logical correctness
```

with:

```markdown
**Important:** Integration tests use a two-phase process:
1. Generate the report by running fake tests that are designed to succeed, fail, or be skipped. The same run writes JUnit's own event log to `build/test-results/junit-platform/`
2. Validate the report: against the official schema, against JUnit's event log by `testId` (`JunitCrossCheckTest`), and for what JUnit can't know (`CtrfLogicTest`). The validator takes its expectations from the same `-Dthreads`, `-Dctrf.build.*` and `ENV_HEALTHY` as the run; see `INTEGRATION_TESTS.md`
```

- [ ] **Step 6: Check and commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
grep -n 'verifyTestStatuses\|DEFAULT_REPORT_PATH =\|For Local Run' INTEGRATION_TESTS.md CLAUDE.md integration-ctrf-validator/src -r
git diff --stat
git add .github/workflows/integration-listener.yml .github/workflows/integration-extension.yml INTEGRATION_TESTS.md CLAUDE.md
git status --short
git commit -m "ci: the validator gets the same flags as the report step; docs for the cross-check (TI-4)"
```

Expected:
- the `grep` prints only `IntegrationRun.java`'s `DEFAULT_REPORT_PATH =` line;
- the diff touches the four files above.

---

### Task 7: Final verification, squash and PR

**Files:**
- Create (not committed): the PR description `$SCRATCH/ti4-pr-body.md`

**Interfaces:** none.

- [ ] **Step 1: Full local verification**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew clean :build 2>&1 | grep -E 'FAILED|BUILD'
./gradlew :javadoc 2>&1 | grep -E 'error|BUILD'
./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun 2>&1 | grep -E 'FAILED|BUILD'
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun -Dforks=2 -Dthreads=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 2>&1 | grep -E 'FAILED|BUILD'
ENV_HEALTHY=false ./gradlew :integration-tests-listener:test --rerun :integration-ctrf-validator:test --rerun -Dforks=2 -Dthreads=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 2>&1 | grep -E 'FAILED|BUILD'
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun -Dforks=2 -Dthreads=4 2>&1 \
  | grep -E 'FAILED|BUILD'
./gradlew clean :integration-tests-extension:test :integration-ctrf-validator:test --rerun -Dthreads=2 \
  -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
compat/run.sh 5.10.5 5.14.4 6.1.3 2>&1 | tail -1
```

Expected:
- every Gradle line is `BUILD SUCCESSFUL`. The second CI-flag run is the same command without `clean`, and passes because the report and the event log both cover that run only. The `-Dthreads=4` run covers Review Focus 4;
- javadoc has no errors;
- compat prints `all compatibility checks passed`.

Write down the validator's test count from the CI-flag run:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t = f = s = 0
for p in glob.glob('integration-ctrf-validator/build/test-results/test/*.xml'):
    r = ET.parse(p).getroot(); t += int(r.get('tests')); f += int(r.get('failures')) + int(r.get('errors')); s += int(r.get('skipped'))
print('validator tests', t, 'failures', f, 'skipped', s)
EOF
```

- [ ] **Step 2: Squash into one commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git fetch origin
git rebase origin/master
git status --short
git reset --soft origin/master
git commit -F - <<'EOF'
test: cross-check the integration reports against JUnit's event log (TI-4)

The integration validator compared each report only with numbers
written by hand, let some assertions pass when their test was missing
(findFirst().ifPresent with a substring match), and only passed with
CI's exact command line: a sentence as its default path, exactly 2
threads, build name system-build, and ENV_HEALTHY=false.

- The integration modules write JUnit's own event log (Open Test
  Reporting), one file per JVM, and give each build its own
  ctrf.run.id, so the log and the report cover the same run.
- JunitCrossCheckTest checks every report entry against that log by
  testId: presence, final status, retries and flaky; failed containers
  are entries, and tests of classes JUnit skipped must be reported as
  skipped. On 58e36f0 (before CB-6), with 2 forks, it names the tests
  the forks lost.
- The validator takes its expectations from the run that wrote the
  report: the same -Dthreads, -Dctrf.build.* and ENV_HEALTHY, or the
  module's ctrf.properties. Its default report is the listener's, and
  ./gradlew clean :integration-tests-listener:test
  :integration-ctrf-validator:test passes with no flags.
- CtrfLogicTest looks tests up by testId and fails when one is missing;
  statuses are left to the cross-check.
- CI passes the same flags to the report step and the validator.
EOF
git log --oneline -2
git show --stat --format= HEAD
```

Expected:
- if the rebase brought in new commits, repeat Step 1;
- one commit on top of `origin/master`, with no `Co-Authored-By` line;
- `git status --short` before the reset shows only the two untracked report files.

- [ ] **Step 3: Push the branch**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git push -u origin test/ti-4-junit-cross-check
```

- [ ] **Step 4: Write the PR description**

Write `$SCRATCH/ti4-pr-body.md` with the sections below. End it without any "Generated with Claude Code" line.

- **What:**
  - the cross-check against JUnit's event log;
  - expectations from the run's own inputs;
  - `CtrfLogicTest` keyed by `testId`;
  - one-command local runs.
- **Why:**
  - the remaining TI-4 problems, quoted from the spec;
  - why Gradle's JUnit XML can't be the check: 27 vs 28, because `BrokenSourceTest`'s two containers are both named `initializationError`.
- **Changes:** the integration modules' `build.gradle` (event log, per-build run id), `JunitEvents`, `JunitCrossCheckTest`, `IntegrationRun`, `ExpectedRun`, `BaseIntegrationTest`, `CtrfLogicTest`, the validator's `build.gradle`, the CI workflows, `INTEGRATION_TESTS.md` and `CLAUDE.md`.
- **Tests:**
  - the RED runs: the plain local run failing on the old validator, and the cross-check naming the lost tests on `58e36f0` with 2 forks;
  - the unit tests (`JunitEventsTest`, `IntegrationRunTest`, `ExpectedRunTest`);
  - the helper failing on a missing test.
- **Verified locally:**
  - `:build`;
  - the plain run;
  - the CI-flag runs, with and without `clean`;
  - `-Dthreads=4`;
  - the extension;
  - compat;
  - the validator's test count.
- **No library change:** the published artifact is untouched, so there is no version bump.
- **Not in this PR:** TI-5's schema validation of scenario output.

The user opens the PR from the browser as `alexshamrai`:
- URL: https://github.com/alexshamrai/junit-ctrf-reporter/compare/master...test/ti-4-junit-cross-check?expand=1
- Title: `test: cross-check the integration reports against JUnit's event log (TI-4)`

## After the PR merges

On `feature/improvements-plan`, in the main checkout, update `IMPROVEMENT_PLAN.md` in one docs commit:
- mark TI-4 done, with the PR number;
- tick its acceptance criteria;
- update its row in the status table and the Phase 2 roadmap line.

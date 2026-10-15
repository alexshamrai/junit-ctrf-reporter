# HP-5: Conform to the Official CTRF Schema — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The listener and extension reports validate against the official CTRF 0.2.0 schema with 0 errors. A unit test in the main module and the integration validator both check this with a strict draft-07 validator.

**Architecture:**
1. Tasks 1–3 fix the model, each with its own unit tests:
   - `Test.filePath` in Java and JSON, with `@JsonAlias("filepath")` so old reports still read;
   - `Environment.buildNumber` becomes a `Long`, and a new `Environment.buildId` takes any other value. `CtrfJsonComposer` fills them from `ctrf.build.number`;
   - `specVersion` comes from `CtrfJson.SPEC_VERSION = "0.2.0"`, `Summary.duration` is added, and `Test.suite` becomes a list.
2. Task 4 pins the official schema at `schema/ctrf.schema.json`. The main module loads it as a test resource and validates with `com.networknt:json-schema-validator` 3.0.8 in draft-07 mode. Task 5 does the same in the integration validator.
3. Each new schema check is first run against today's `master` in a temporary worktree, where it must fail with the `filepath` and `buildNumber` errors. Then it must pass on the branch.

The schema checks come after the model fixes so that every task ends green. The temporary `master` worktree is how they are still watched failing on today's code.

**Tech Stack:**
- Java 17, built with toolchain 21; Gradle 9; Lombok;
- Jackson 2 in the library;
- `com.networknt:json-schema-validator` 3.0.8, for tests only. It uses Jackson 3, whose package is `tools.jackson`, so it doesn't clash with Jackson 2;
- JUnit 6, AssertJ, Mockito, Checkstyle.

**Spec:** `IMPROVEMENT_PLAN.md` on branch `feature/improvements-plan`, in the main checkout `/Users/oleksiishamrai/personal_dev/junit-ctrf-extension`. The section is "HP-5: Conform to the Official CTRF Schema", commit `6b37cd8`; read it before starting. HP-12 in the same file is out of scope. The schema itself: https://github.com/ctrf-io/ctrf/blob/v0.2.0/schema/ctrf.schema.json.

## Global Constraints

- **Paths used in commands:**
  - `W=/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes`, the code worktree;
  - `SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad`, for files that are never committed;
  - `MW=$SCRATCH/hp5-master`, a temporary worktree of `master` used for the RED runs in Tasks 4 and 5. It is removed at the end of Task 5.
- **Every command runs from `$W`.** The shell's working directory and variables reset between tool calls, back to the main checkout. So start each command with `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes &&`, and a block that uses `$SCRATCH` or `$MW` with this preamble:

  ```bash
  W=/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
  SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
  MW=$SCRATCH/hp5-master
  cd $W
  ```
- **The validator task** is cached by Gradle on its inputs, and the report file isn't one of them. Always run it with `--rerun`, or it may report an earlier result.
- **Where:** in `$W`, on branch `fix/hp-5-ctrf-schema`, created from `origin/master` (`c4093eb` or later). Ignore the untracked `ctrf-report.json` and `ctrf-report.json.lock` at the root, which the module's own tests write, and never commit them.
- **The schema:**
  - CTRF 0.2.0, the file `schema/ctrf.schema.json` from `ctrf-io/ctrf`, tag `v0.2.0`, commit `e325eb33c7662a208bfa4d093a41cc55439d6e6a`;
  - its SHA-256 is `cb9bd075beb0e046076046793f96bb1ba72bd1b1d331b1fbd42ac0ea36776cf3`;
  - it is committed byte for byte, never edited.
- **Spec version:** `CtrfJson.SPEC_VERSION = "0.2.0"`. It changes only in the same commit as the pinned schema.
- **The validator library:**
  - `com.networknt:json-schema-validator` 3.0.8, through `SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)`;
  - `testImplementation` in the main module, and `implementation` in `integration-ctrf-validator`;
  - `com.networknt.schema.Error` clashes with `java.lang.Error`, so never import it. Use `var`, or `Object::toString`.
- **No new runtime dependency:** the main module's `runtimeClasspath` and its generated POM must be the same before and after.
- **The build number rule.** `ctrf.build.number` is stripped of surrounding whitespace, then:
  - a null or blank value sets neither field and prints nothing;
  - a value that matches `[0-9]+` and fits in a `long` goes to `buildNumber`, as a `Long`;
  - any other value goes, stripped, to `buildId`. Exactly one line is printed to `System.err`, and it contains `buildId` and the value.
- **`filePath`:**
  - the Java field is `filePath` (`getFilePath()`), and the JSON property is `filePath`;
  - `@JsonAlias("filepath")` reads old reports;
  - the internal key prefix `"filepath:"` in `AttemptFolder.keyOf` stays as it is.
- **`suite`** is a `List<String>` that the library never sets. Filling it is HP-12.
- **`Summary.duration`** is a `long` equal to stop minus start, set in `SummaryUtil.createSummary`.
- **Out of scope:**
  - HP-12, filling `suite`;
  - the rest of TI-4, such as the cross-check against JUnit's XML;
  - MP-5;
  - a version bump. HP-5 ships in 0.5.0.
- **Commits:** no `Co-Authored-By` trailer and no "Generated with Claude Code" line, anywhere. The per-task commits are squashed into one commit in Task 7.
- **zsh:** in loops, use `${m}`, never `$m:...`.
- **sed:** this is macOS, so in-place edits are `sed -i '' ...`.
- **Builds:**
  - `./gradlew :build`, with the colon, runs the unit tests, the scenario tests and checkstyle;
  - the root `./gradlew build`, without the colon, also runs the validator, which fails without a report. Don't use it.
- **Checkstyle:**
  - lines of at most 140 characters;
  - method names match `^[a-z][a-z0-9][a-zA-Z0-9_]*$`;
  - no more than two capitals in a row in names;
  - no empty catch blocks (a comment counts as content);
  - wrapped operators (`+`, `&&`, `?`, `:`) start the new line;
  - no unused imports.

## Review Focus

1. **Build numbers that look numeric but aren't plain whole numbers:** `+777`, `-1`, `1e3`, `9223372036854775808` (one more than a `long` holds), and Arabic-Indic digits `١٢٣`. `Long.parseLong` accepts some of these.
   - Expected: each goes to `buildId` with one warning; nothing throws, and the report stays valid. `00123` is a whole number, so it gives `buildNumber` 123.
   - Test: `CtrfJsonComposerTest.otherBuildNumberIsWrittenAsBuildIdWithOneWarning` and `wholeBuildNumberIsWrittenAsBuildNumber` (Task 2).
2. **A report already on disk, written by 0.4.x, whose `buildNumber` is a non-numeric string.** It can't be read into the `Long` field.
   - Expected: the update still writes the new report, with the existing "can't be read" warning. Such a report has no `runId`, so it would be replaced anyway.
   - Test: `CtrfReportFileServiceTest.legacyReportWithANonNumericBuildNumberReachesTheMergeAsNull` (Task 2).
3. **A run in which no test ran,** for example a tag filter that matches nothing.
   - Expected: a valid report with `"tests": []`.
   - Test: `CtrfSchemaConformanceTest.reportOfARunWithoutTestsConformsToTheSchema` (Task 4).
4. **The timeline visualization** that the integration modules add to Gradle's HTML test report reads the CTRF JSON.
   - Expected: after the rename, its tooltips show each test's class, not `Unknown`.
   - Code: Task 1. Check: Task 5, Step 8.
5. **A report merged from several JVMs** (2 forks plus the retry round).
   - Expected: `summary.duration` is the merged run's stop minus its start.
   - Test: `CtrfLogicTest.verifySummaryDurationSpansTheRun` (Task 5).

---

### Task 1: `Test.filePath`

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/ctrf/model/Test.java:3-4,39`
- Modify (rename only): `src/main/java/io/github/alexshamrai/TestProcessor.java:43`, `src/main/java/io/github/alexshamrai/AttemptFolder.java:113,132-133`, `src/main/java/io/github/alexshamrai/ReportMerger.java:102`
- Modify (rename only): `src/test/java/io/github/alexshamrai/AttemptFolderTest.java`, `ReportMergerTest.java`, `TestProcessorTest.java`, `scenario/ContainerFailureScenarioTest.java`, `scenario/ExtensionTestIdentityScenarioTest.java`, `scenario/ScenarioRunner.java`, `scenario/TestIdentityScenarioTest.java`
- Modify (rename only): `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`
- Modify: `gradle/timeline-visualization.gradle:113`
- Test: `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Test.getFilePath()`, `Test.setFilePath(String)` and `Test.builder().filePath(String)`. `getFilepath()` and `filepath(...)` no longer exist. The JSON property is `filePath`, and `filepath` is still accepted when reading.

- [ ] **Step 1: Create the branch**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git fetch origin
git switch -c fix/hp-5-ctrf-schema origin/master
git branch --unset-upstream
git log --oneline -1
```

Expected: HEAD is `origin/master` (`c4093eb` or later). `--unset-upstream` keeps the branch from tracking `origin/master`; it gets its own upstream when Task 7 pushes it.

- [ ] **Step 2: Write the failing tests**

Add to `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`, before its closing brace:

```java
    @org.junit.jupiter.api.Test
    void filePathIsWrittenAsFilePath() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
            Test.builder().name("test1()").filePath("com.example.PaymentTest").build()));

        assertThat(json.get("filePath").asText()).isEqualTo("com.example.PaymentTest");
        assertThat(json.has("filepath")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void legacyFilepathIsReadIntoFilePath() throws Exception {
        var test = objectMapper.readValue("{\"name\":\"test1()\",\"filepath\":\"com.example.PaymentTest\"}", Test.class);

        assertThat(test.getFilePath()).isEqualTo("com.example.PaymentTest");
    }
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :test --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest' 2>&1 | grep -E 'error:|FAILED|BUILD'`

Expected: `compileTestJava` fails with `cannot find symbol` for `filePath(java.lang.String)` and `getFilePath()`.

- [ ] **Step 4: Rename the field**

In `src/main/java/io/github/alexshamrai/ctrf/model/Test.java`, replace the two Jackson imports:

```java
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
```

with:

```java
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
```

Replace the line `    private String filepath;` with:

```java
    /**
     * Where the test is defined: its class name. Reports written before 0.5.0 call it {@code filepath}, which is still read.
     */
    @JsonAlias("filepath")
    private String filePath;
```

- [ ] **Step 5: Rename every usage**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git grep -l -e 'getFilepath' -e '\.filepath(' -- src integration-ctrf-validator \
  | xargs sed -i '' -e 's/getFilepath/getFilePath/g' -e 's/\.filepath(/.filePath(/g'
sed -i '' 's/def filepath = test\.filepath ?:/def filepath = test.filePath ?:/' gradle/timeline-visualization.gradle
git grep -n -e 'getFilepath' -e '\.filepath(' -e 'test\.filepath ?:' -- src integration-ctrf-validator gradle
git grep -n '"filepath' -- src/main
git diff --stat
```

Expected:
- the first `git grep` prints nothing;
- the second prints two lines: the `@JsonAlias("filepath")` in `Test.java` and `return "filepath:" + attempt.getFilePath() + ...` in `AttemptFolder.java`. That key prefix is internal and stays;
- `git diff --stat` lists 14 files: the 13 under **Files** above, and `CtrfModelJsonTest.java`.

In `gradle/timeline-visualization.gradle`, only line 113 changes. Lines 128, 183 and 505 read the script's own map key `filepath`, which it fills on line 128, so they stay.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :test --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest' 2>&1 | grep -E 'FAILED|BUILD'`

Expected: `BUILD SUCCESSFUL`.

Then compile the validator module, which uses the model, and run the whole build:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew :integration-ctrf-validator:compileTestJava :build 2>&1 | grep -E 'error:|FAILED|BUILD'
```

Expected: `BUILD SUCCESSFUL`. The scenario tests read reports written by forked JVMs through `getFilePath()`, so they exercise both the writing and the reading.

- [ ] **Step 7: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add -A src integration-ctrf-validator gradle
git status --short
git commit -m "refactor: Test.filepath becomes filePath, as CTRF names it (HP-5)"
```

Expected: `git status --short` shows only `?? ctrf-report.json` and `?? ctrf-report.json.lock` after the add.

---

### Task 2: `buildNumber` as an integer, and `buildId`

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/ctrf/model/Environment.java:19-21`
- Modify: `src/main/java/io/github/alexshamrai/CtrfJsonComposer.java` (imports, constants, `composeEnvironment`, two new private methods)
- Modify: `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java:145-147`
- Test: `src/test/java/io/github/alexshamrai/CtrfJsonComposerTest.java`
- Test: `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`
- Test: `src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `Environment.getBuildNumber()` returns a `Long`, and `Environment.builder().buildNumber(Long)`;
  - `Environment.getBuildId()` returns a `String`, and `Environment.builder().buildId(String)`;
  - `ConfigReader.getBuildNumber()` still returns a `String`.

- [ ] **Step 1: Write the failing file service test** (Review Focus 2)

This test compiles on today's model, so it goes first. Add to `src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java`, right after the test `unparsableReportReachesTheMergeAsNull`:

```java
    @Test
    @DisplayName("A 0.4 report whose buildNumber isn't a number can't be read, so it reaches the merge function as null")
    void legacyReportWithANonNumericBuildNumberReachesTheMergeAsNull() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{\"reportFormat\":\"CTRF\",\"specVersion\":\"0.0.0\","
            + "\"results\":{\"environment\":{\"buildNumber\":\"2026.10.15-3\",\"healthy\":true}}}");
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(readMarker(report)).isEqualTo("fresh");
        assertThat(errContent.toString()).contains("can't be read");
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :test --tests 'io.github.alexshamrai.CtrfReportFileServiceTest' 2>&1 | grep -E 'FAILED|BUILD'`

Expected: `legacyReportWithANonNumericBuildNumberReachesTheMergeAsNull` FAILED. Today the `String` field reads the report, so the merge function receives it instead of `null`.

- [ ] **Step 3: Write the failing composer tests** (Review Focus 1)

In `src/test/java/io/github/alexshamrai/CtrfJsonComposerTest.java`, replace the imports block (from `import io.github.alexshamrai.config.ConfigReader;` to `import static org.junit.jupiter.api.Assertions.assertNull;`) with:

```java
import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

Replace the fields and the start of `setUp()`:

```java
    private ConfigReader configReader;
    private StartupDurationProcessor startupDurationProcessor;
    private CtrfJsonComposer composer;

    @BeforeEach
    void setUp() {
        configReader = Mockito.mock(ConfigReader.class);
```

with:

```java
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalErr = System.err;
    private ConfigReader configReader;
    private StartupDurationProcessor startupDurationProcessor;
    private CtrfJsonComposer composer;

    @BeforeEach
    void setUp() {
        System.setErr(new PrintStream(errContent));
        configReader = Mockito.mock(ConfigReader.class);
```

In `setUp()`, replace:

```java
        Mockito.when(configReader.getBuildNumber()).thenReturn("mockedBuildNumber");
```

with:

```java
        Mockito.when(configReader.getBuildNumber()).thenReturn("777");
```

Add right after `setUp()`:

```java
    @AfterEach
    void restoreStandardError() {
        System.setErr(originalErr);
    }
```

In `testGenerateCtrfJsonWithValidInput`, replace:

```java
        assertEquals("mockedBuildNumber", environment.getBuildNumber());
```

with:

```java
        assertEquals(777L, environment.getBuildNumber());
        assertNull(environment.getBuildId());
```

Add before the class's closing brace:

```java
    @ParameterizedTest(name = "\"{0}\" is buildNumber {1}")
    @CsvSource({"777, 777", "' 777 ', 777", "0, 0", "00123, 123", "9223372036854775807, 9223372036854775807"})
    void wholeBuildNumberIsWrittenAsBuildNumber(String configured, long expected) {
        Mockito.when(configReader.getBuildNumber()).thenReturn(configured);

        var environment = composeEnvironment();

        assertEquals(expected, environment.getBuildNumber());
        assertNull(environment.getBuildId());
        assertEquals("", errContent.toString());
    }

    @ParameterizedTest(name = "\"{0}\" is buildId \"{1}\"")
    @CsvSource({
        "2026.10.15-3, 2026.10.15-3",
        "' 2026.10.15-3 ', 2026.10.15-3",
        "+777, +777",
        "-1, -1",
        "1e3, 1e3",
        "9223372036854775808, 9223372036854775808",
        "١٢٣, ١٢٣"
    })
    void otherBuildNumberIsWrittenAsBuildIdWithOneWarning(String configured, String expected) {
        Mockito.when(configReader.getBuildNumber()).thenReturn(configured);

        var environment = composeEnvironment();

        assertNull(environment.getBuildNumber());
        assertEquals(expected, environment.getBuildId());
        var warnings = errContent.toString().lines().toList();
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("buildId"));
        assertTrue(warnings.get(0).contains(expected));
    }

    @ParameterizedTest(name = "\"{0}\" sets neither field")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankBuildNumberSetsNeitherField(String configured) {
        Mockito.when(configReader.getBuildNumber()).thenReturn(configured);

        var environment = composeEnvironment();

        assertNull(environment.getBuildNumber());
        assertNull(environment.getBuildId());
        assertEquals("", errContent.toString());
    }

    private Environment composeEnvironment() {
        return composer.generateCtrfJson(Summary.builder().build(), List.of(), true, "run-1").getResults().getEnvironment();
    }
```

- [ ] **Step 4: Write the failing model tests**

Add to `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`, before its closing brace:

```java
    @org.junit.jupiter.api.Test
    void buildNumberIsWrittenAsANumberAndBuildIdAsAString() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
            Environment.builder().buildNumber(777L).buildId("2026.10.15-3").build()));

        assertThat(json.get("buildNumber").isIntegralNumber()).isTrue();
        assertThat(json.get("buildNumber").asLong()).isEqualTo(777L);
        assertThat(json.get("buildId").asText()).isEqualTo("2026.10.15-3");
    }

    @org.junit.jupiter.api.Test
    void legacyStringBuildNumberIsReadAsANumber() throws Exception {
        var environment = objectMapper.readValue("{\"buildNumber\":\"777\",\"healthy\":true}", Environment.class);

        assertThat(environment.getBuildNumber()).isEqualTo(777L);
    }
```

- [ ] **Step 5: Run them to verify they fail**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :test --tests 'io.github.alexshamrai.CtrfJsonComposerTest' 2>&1 | grep -E 'error:|FAILED|BUILD'`

Expected: `compileTestJava` fails with `cannot find symbol` for `getBuildId()` and `buildId(java.lang.String)`.

- [ ] **Step 6: Change the model**

In `src/main/java/io/github/alexshamrai/ctrf/model/Environment.java`, replace:

```java
    private String buildName;
    private String buildNumber;
    private String buildUrl;
```

with:

```java
    private String buildName;
    /**
     * {@code ctrf.build.number} when it is a whole number. CTRF types {@code buildNumber} as an integer.
     */
    private Long buildNumber;
    /**
     * {@code ctrf.build.number} when it isn't a whole number, such as {@code 2026.10.15-3}.
     */
    private String buildId;
    private String buildUrl;
```

- [ ] **Step 7: Parse the build number in the composer**

In `src/main/java/io/github/alexshamrai/CtrfJsonComposer.java`, add the import after `import java.util.UUID;`:

```java
import java.util.regex.Pattern;
```

Replace:

```java
    private static final String TOOL_NAME = "JUnit";
```

with:

```java
    private static final String TOOL_NAME = "JUnit";
    private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]+");
```

Replace the whole `composeEnvironment` method with:

```java
    /**
     * Creates the environment section of the CTRF JSON, including details about
     * the application, build, repository, operating system, and test environment.
     *
     * @param isEnvironmentHealthy whether the test environment is considered healthy
     * @return an Environment object containing all available environment information
     */
    private Environment composeEnvironment(boolean isEnvironmentHealthy) {
        var environment = Environment.builder()
            .reportName(configReader.getReportName())
            .appName(configReader.getAppName())
            .appVersion(configReader.getAppVersion())
            .buildName(configReader.getBuildName())
            .buildUrl(configReader.getBuildUrl())
            .repositoryName(configReader.getRepositoryName())
            .repositoryUrl(configReader.getRepositoryUrl())
            .commit(configReader.getCommit())
            .branchName(configReader.getBranchName())
            .osPlatform(configReader.getOsPlatform())
            .osRelease(configReader.getOsRelease())
            .osVersion(configReader.getOsVersion())
            .testEnvironment(configReader.getTestEnvironment())
            .healthy(isEnvironmentHealthy);
        setBuildNumber(environment, configReader.getBuildNumber());
        return environment.build();
    }

    /**
     * CTRF types {@code buildNumber} as an integer. A whole number goes there; any other value goes to
     * {@code buildId}, with a warning, so the report stays valid. A blank value sets neither.
     */
    private static void setBuildNumber(Environment.EnvironmentBuilder environment, String configured) {
        if (configured == null || configured.isBlank()) {
            return;
        }
        var value = configured.strip();
        var number = toWholeNumber(value);
        if (number != null) {
            environment.buildNumber(number);
            return;
        }
        System.err.println("ctrf.build.number is not a whole number, so the report gives it as buildId: " + value);
        environment.buildId(value);
    }

    private static Long toWholeNumber(String value) {
        if (!WHOLE_NUMBER.matcher(value).matches()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // More digits than a long holds
            return null;
        }
    }
```

`[0-9]+` is checked before `Long.parseLong` because `parseLong` also accepts a sign (`+777`, `-1`) and non-ASCII digits (`١٢٣`).

- [ ] **Step 8: Update the integration validator's build number check**

In `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`, in `verifyBuildParametersFromSystemProperties`, replace:

```java
        assertThat(environment.getBuildNumber())
            .as("Build number should be numeric")
            .matches("\\d+");
```

with:

```java
        assertThat(environment.getBuildNumber())
            .as("Build number should be the whole number passed via -Dctrf.build.number")
            .isPositive();
        assertThat(environment.getBuildId())
            .as("A whole build number is never written as buildId")
            .isNull();
```

- [ ] **Step 9: Run the tests to verify they pass**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew :test --tests 'io.github.alexshamrai.CtrfJsonComposerTest' --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest' \
  --tests 'io.github.alexshamrai.CtrfReportFileServiceTest' 2>&1 | grep -E 'FAILED|BUILD'
./gradlew :integration-ctrf-validator:compileTestJava :build 2>&1 | grep -E 'error:|FAILED|BUILD'
```

Expected: `BUILD SUCCESSFUL` both times. `CtrfJsonComposerTest` runs 5 + 7 + 4 parameterized cases on top of its existing tests.

- [ ] **Step 10: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add -A src integration-ctrf-validator
git status --short
git commit -m "fix: buildNumber is an integer; other build numbers go to buildId (HP-5)"
```

Expected: `git status --short` shows only the two untracked report files after the add.

---

### Task 3: `specVersion` 0.2.0, `Summary.duration` and `Test.suite` as a list

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/ctrf/model/CtrfJson.java:33-39`
- Modify: `src/main/java/io/github/alexshamrai/ctrf/model/Summary.java:27-28`
- Modify: `src/main/java/io/github/alexshamrai/ctrf/model/Test.java:30`
- Modify: `src/main/java/io/github/alexshamrai/util/SummaryUtil.java:28,47-48`
- Test: `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`
- Test: `src/test/java/io/github/alexshamrai/util/SummaryUtilTest.java`
- Test: `src/test/java/io/github/alexshamrai/CtrfJsonComposerTest.java:21`

**Interfaces:**
- Consumes: nothing from Tasks 1–2.
- Produces:
  - `public static final String CtrfJson.SPEC_VERSION = "0.2.0"`, also the default of `specVersion`;
  - `Summary.getDuration()` returns a `long`, and `Summary.builder().duration(long)`;
  - `Test.getSuite()` returns a `List<String>`, and `Test.builder().suite(List<String>)`.

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`, before its closing brace:

```java
    @org.junit.jupiter.api.Test
    void specVersionIsTheSupportedCtrfVersion() {
        assertThat(CtrfJson.SPEC_VERSION).isEqualTo("0.2.0");
        assertThat(CtrfJson.builder().build().getSpecVersion()).isEqualTo(CtrfJson.SPEC_VERSION);
    }

    @org.junit.jupiter.api.Test
    void summaryWritesItsDuration() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
            Summary.builder().start(1_000L).stop(3_500L).duration(2_500L).build()));

        assertThat(json.get("duration").asLong()).isEqualTo(2_500L);
    }

    @org.junit.jupiter.api.Test
    void suiteIsWrittenAsAnArray() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
            Test.builder().name("test1()").suite(List.of("PaymentTest", "Refunds")).build()));

        assertThat(json.get("suite").isArray()).isTrue();
        assertThat(json.get("suite").get(1).asText()).isEqualTo("Refunds");
    }
```

Add to `src/test/java/io/github/alexshamrai/util/SummaryUtilTest.java`, before its closing brace:

```java
    @org.junit.jupiter.api.Test
    void durationIsStopMinusStart() {
        var summary = SummaryUtil.createSummary(List.of(), 1_000L, 3_500L);

        assertEquals(2_500L, summary.getDuration());
    }
```

In `src/test/java/io/github/alexshamrai/CtrfJsonComposerTest.java`, replace:

```java
    private static final String DEFAULT_SPEC_VERSION = "0.0.0";
```

with:

```java
    private static final String DEFAULT_SPEC_VERSION = "0.2.0";
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :test --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest' --tests 'io.github.alexshamrai.util.SummaryUtilTest' 2>&1 | grep -E 'error:|FAILED|BUILD'`

Expected: `compileTestJava` fails with `cannot find symbol` for `SPEC_VERSION`, `duration(long)`, `getDuration()`, and `suite(java.util.List<java.lang.String>)`, or with incompatible types for `suite`.

- [ ] **Step 3: Implement**

In `src/main/java/io/github/alexshamrai/ctrf/model/CtrfJson.java`, replace:

```java
public class CtrfJson {
    /**
     * The format of the report.
```

with:

```java
public class CtrfJson {
    /**
     * The CTRF version this library writes: the version of the official schema pinned at {@code schema/ctrf.schema.json}.
     * Change it only together with that file.
     */
    public static final String SPEC_VERSION = "0.2.0";

    /**
     * The format of the report.
```

Then replace:

```java
    /**
     * The version of the specification.
     * This is a required field and must be a valid semver string.
     * Current version is "0.0.0".
     */
    @Builder.Default
    private String specVersion = "0.0.0";
```

with:

```java
    /**
     * The version of the specification.
     * This is a required field and must be a valid semver string: {@link #SPEC_VERSION}.
     */
    @Builder.Default
    private String specVersion = SPEC_VERSION;
```

In `src/main/java/io/github/alexshamrai/ctrf/model/Summary.java`, replace:

```java
    private long start;
    private long stop;
    private Extra extra;
```

with:

```java
    private long start;
    private long stop;
    /**
     * Run duration in milliseconds: {@code stop} minus {@code start}.
     */
    private long duration;
    private Extra extra;
```

In `src/main/java/io/github/alexshamrai/ctrf/model/Test.java`, replace:

```java
    private String suite;
```

with:

```java
    /**
     * Suite hierarchy, from the top level to the immediate parent. When set, it has at least one entry. The library
     * doesn't set it yet.
     */
    private List<String> suite;
```

`java.util.List` is already imported there.

In `src/main/java/io/github/alexshamrai/util/SummaryUtil.java`, replace:

```java
     * @return a Summary object with status counts, the flaky count and timing information
```

with:

```java
     * @return a Summary object with status counts, the flaky count, start, stop and duration
```

and replace:

```java
            .start(startTime)
            .stop(stopTime)
            .build();
```

with:

```java
            .start(startTime)
            .stop(stopTime)
            .duration(stopTime - startTime)
            .build();
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew :test --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest' --tests 'io.github.alexshamrai.util.SummaryUtilTest' \
  --tests 'io.github.alexshamrai.CtrfJsonComposerTest' 2>&1 | grep -E 'FAILED|BUILD'
./gradlew :integration-ctrf-validator:compileTestJava :build 2>&1 | grep -E 'error:|FAILED|BUILD'
```

Expected: `BUILD SUCCESSFUL` both times.

- [ ] **Step 5: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add -A src
git status --short
git commit -m "fix: specVersion 0.2.0, summary.duration, suite as a list (HP-5)"
```

---

### Task 4: Pin the official schema; `CtrfSchemaConformanceTest`

**Files:**
- Create: `schema/ctrf.schema.json` (downloaded, byte for byte)
- Create: `schema/README.md`
- Modify: `build.gradle` (`ext`, `dependencies`, a new `sourceSets` block)
- Create: `src/test/java/io/github/alexshamrai/CtrfSchemaConformanceTest.java`
- Create (not committed): `$SCRATCH/hp5-junit-xml.py`, plus the temporary worktree `$MW`

**Interfaces:**
- Consumes:
  - `Test.filePath` (Task 1);
  - `Environment.buildNumber`/`buildId` and the composer's build number rule (Task 2);
  - `Summary.duration` (Task 3);
  - `TestProcessor.createTest(String, TestDetails, long)`, `TestProcessor.setFailureDetails(Test, Throwable)`, `AttemptFolder.fold(List<Test>)` (package-private), `SummaryUtil.createSummary(List<Test>, long, long)`, `new CtrfJsonComposer(ConfigReader, StartupDurationProcessor, String)` and `generateCtrfJson(Summary, List<Test>, boolean, String)`, all as on `master`.
- Produces:
  - the classpath resource `/ctrf.schema.json` on the test classpath of the root project;
  - the ext property `networkntValidatorVersion = '3.0.8'` in the root `build.gradle`, which Task 5 uses.

- [ ] **Step 1: Record the published dependencies before the change**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
./gradlew -q :dependencies --configuration runtimeClasspath > $SCRATCH/hp5-runtime-before.txt
./gradlew -q :generatePomFileForMavenPublication && cp build/publications/maven/pom-default.xml $SCRATCH/hp5-pom-before.xml
wc -l $SCRATCH/hp5-runtime-before.txt $SCRATCH/hp5-pom-before.xml
```

Expected: both files are non-empty.

- [ ] **Step 2: Add the pinned schema**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
mkdir -p schema
curl -fsSL https://raw.githubusercontent.com/ctrf-io/ctrf/e325eb33c7662a208bfa4d093a41cc55439d6e6a/schema/ctrf.schema.json \
  -o schema/ctrf.schema.json
shasum -a 256 schema/ctrf.schema.json
```

Expected: `cb9bd075beb0e046076046793f96bb1ba72bd1b1d331b1fbd42ac0ea36776cf3  schema/ctrf.schema.json`. If the hash differs, stop: the file is not the pinned one.

Create `schema/README.md`:

```markdown
# Pinned CTRF schema

`ctrf.schema.json` is the official CTRF JSON schema, version 0.2.0, copied byte for byte from
[`ctrf-io/ctrf`](https://github.com/ctrf-io/ctrf):

- file: `schema/ctrf.schema.json`
- tag: `v0.2.0`
- commit: `e325eb33c7662a208bfa4d093a41cc55439d6e6a`
- SHA-256: `cb9bd075beb0e046076046793f96bb1ba72bd1b1d331b1fbd42ac0ea36776cf3`

The tests load it as a test resource: `CtrfSchemaConformanceTest` in the main module, and
`CtrfSchemaValidationTest` in `integration-ctrf-validator`. It is not part of the published library.

## Updating to a new CTRF version

Replace `ctrf.schema.json` with the file from the new tag, and in the same commit:

1. change `CtrfJson.SPEC_VERSION` to the new version;
2. update the tag, commit and SHA-256 above;
3. run `./gradlew :build` and the integration validator, and fix what the new schema rejects.

Never edit the schema by hand.
```

- [ ] **Step 3: Add the validator to the build**

In `build.gradle`, in the `ext` block, replace:

```groovy
    slf4jVersion = '2.0.19'
    logbackVersion = '1.6.3'
```

with:

```groovy
    slf4jVersion = '2.0.19'
    logbackVersion = '1.6.3'
    networkntValidatorVersion = '3.0.8'
```

Leave `jacksonModuleJsonSchemaVersion` and `jsonSchemaValidatorVersion` in place for now. The validator module still uses them until Task 5.

In the root `dependencies` block, replace:

```groovy
    testImplementation "org.assertj:assertj-core:${assertjVersion}"
}
```

with:

```groovy
    testImplementation "org.assertj:assertj-core:${assertjVersion}"
    // Validates reports against the pinned official CTRF schema. It uses Jackson 3 (tools.jackson), apart from the library's Jackson 2
    testImplementation "com.networknt:json-schema-validator:${networkntValidatorVersion}"
    // A logging provider for the validator, so tests don't print SLF4J's "no providers" warning
    testRuntimeOnly "org.slf4j:slf4j-nop:${slf4jVersion}"
}

sourceSets {
    test {
        resources {
            // The pinned official CTRF schema; see schema/README.md
            srcDir 'schema'
            exclude 'README.md'
        }
    }
}
```

- [ ] **Step 4: Write the conformance test**

Create `src/test/java/io/github/alexshamrai/CtrfSchemaConformanceTest.java`:

```java
package io.github.alexshamrai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import io.github.alexshamrai.util.SummaryUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validates the library's reports against the official CTRF schema, pinned at {@code schema/ctrf.schema.json}.
 */
class CtrfSchemaConformanceTest {

    private static final Schema SCHEMA = loadPinnedSchema();
    private static final String GENERATED_BY = "io.github.alexshamrai.launcher.CtrfListener";
    private static final String CLASS_NAME = "com.example.PaymentTest";
    private static final String CLASS_ID = "[engine:junit-jupiter]/[class:" + CLASS_NAME + "]";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigReader configReader = mock(ConfigReader.class);
    private final TestProcessor processor = new TestProcessor(configReader);
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void configureEveryEnvironmentValue() {
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        when(configReader.getJUnitVersion()).thenReturn("6.1.3");
        when(configReader.getReportName()).thenReturn("Nightly");
        when(configReader.getAppName()).thenReturn("payments");
        when(configReader.getAppVersion()).thenReturn("1.2.3");
        when(configReader.getBuildName()).thenReturn("system-build");
        when(configReader.getBuildNumber()).thenReturn("777");
        when(configReader.getBuildUrl()).thenReturn("https://ci.example.com/builds/777");
        when(configReader.getRepositoryName()).thenReturn("payments");
        when(configReader.getRepositoryUrl()).thenReturn("https://github.com/example/payments");
        when(configReader.getCommit()).thenReturn("c4093eb");
        when(configReader.getBranchName()).thenReturn("master");
        when(configReader.getOsPlatform()).thenReturn("linux");
        when(configReader.getOsRelease()).thenReturn("6.8");
        when(configReader.getOsVersion()).thenReturn("Ubuntu 24.04");
        when(configReader.getTestEnvironment()).thenReturn("staging");
        when(configReader.getMaxMessageLength()).thenReturn(500);
        when(configReader.calculateStartupDuration()).thenReturn(true);
    }

    @AfterEach
    void restoreStandardError() {
        System.setErr(originalErr);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report with every field the library writes has no schema errors")
    void fullyPopulatedReportConformsToTheSchema() throws IOException {
        assertThat(schemaErrorsOf(composeReport(fullRun()))).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A non-numeric build number is written as buildId, and the report has no schema errors")
    void reportWithABuildIdConformsToTheSchema() throws IOException {
        when(configReader.getBuildNumber()).thenReturn("2026.10.15-3");

        var report = composeReport(fullRun());

        assertThat(objectMapper.readTree(report).at("/results/environment/buildId").asText()).isEqualTo("2026.10.15-3");
        assertThat(schemaErrorsOf(report)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A run in which no test ran has no schema errors")
    void reportOfARunWithoutTestsConformsToTheSchema() throws IOException {
        assertThat(schemaErrorsOf(composeReport(List.of()))).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("The validator rejects a test with the legacy filepath key")
    void legacyFilepathKeyIsRejected() throws IOException {
        var report = (ObjectNode) objectMapper.readTree(composeReport(fullRun()));
        var test = (ObjectNode) report.at("/results/tests/0");
        test.remove("filePath");
        test.put("filepath", CLASS_NAME);

        assertThat(schemaErrorsOf(objectMapper.writeValueAsString(report)))
            .anySatisfy(error -> assertThat(error).startsWith("/results/tests/0:").contains("'filepath'"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("The validator rejects a buildNumber written as a string")
    void stringBuildNumberIsRejected() throws IOException {
        var report = (ObjectNode) objectMapper.readTree(composeReport(fullRun()));
        ((ObjectNode) report.at("/results/environment")).put("buildNumber", "777");

        assertThat(schemaErrorsOf(objectMapper.writeValueAsString(report)))
            .anySatisfy(error -> assertThat(error).startsWith("/results/environment/buildNumber:"));
    }

    /**
     * Attempts as the listener and the extension record them: a test that failed and then passed on retry, and an
     * aborted test, which the report shows as skipped with JUnit's raw status.
     */
    private List<Test> fullRun() {
        var failed = attempt("pays()", Set.of("smoke"), 1_000L, 1_005L);
        failed.setStatus(FAILED);
        processor.setFailureDetails(failed, new AssertionError("expected 200 but was 500"));

        var passed = attempt("pays()", Set.of("smoke"), 1_010L, 1_020L);
        passed.setStatus(PASSED);

        var aborted = attempt("refunds()", Set.of(), 1_030L, 1_031L);
        aborted.setStatus(SKIPPED);
        aborted.setRawStatus("aborted");
        aborted.setMessage("Assumption failed: the payments sandbox is down");

        return AttemptFolder.fold(List.of(failed, passed, aborted));
    }

    private Test attempt(String method, Set<String> tags, long start, long stop) {
        var details = new TestDetails(start, tags, CLASS_NAME, CLASS_ID + "/[method:" + method + "]", method);
        return processor.createTest(method, details, stop);
    }

    private String composeReport(List<Test> tests) throws IOException {
        var summary = SummaryUtil.createSummary(tests, 900L, 1_100L);
        var composer = new CtrfJsonComposer(configReader, new StartupDurationProcessor(), GENERATED_BY);
        return objectMapper.writeValueAsString(composer.generateCtrfJson(summary, tests, true, "run-1"));
    }

    private static List<String> schemaErrorsOf(String report) {
        return SCHEMA.validate(report, InputFormat.JSON).stream().map(Object::toString).toList();
    }

    private static Schema loadPinnedSchema() {
        try (InputStream schema = CtrfSchemaConformanceTest.class.getResourceAsStream("/ctrf.schema.json")) {
            if (schema == null) {
                throw new IllegalStateException("ctrf.schema.json is not on the test classpath; see schema/README.md");
            }
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schema);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

The test uses only APIs that exist on `master`, and reads the new fields only through JSON. That lets Step 6 run it unchanged against today's model.

- [ ] **Step 5: Write the JUnit XML helper**

Create `$SCRATCH/hp5-junit-xml.py`. Tasks 4 and 5 use it to print each test case's result and failure message. Gradle's console shows only the exception type.

```bash
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
cat > $SCRATCH/hp5-junit-xml.py <<'EOF'
import sys
import xml.etree.ElementTree as ET

for path in sys.argv[1:]:
    for case in ET.parse(path).getroot().iter('testcase'):
        failure = case.find('failure')
        if failure is None:
            print('passed ', case.get('name'))
            continue
        message = failure.get('message') or ''
        print('FAILED ', case.get('name'), '| /results/ entries:', message.count('/results/'))
        print('       ', message[:700])
EOF
```

- [ ] **Step 6: Run the test against today's `master` to verify it fails**

```bash
W=/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
MW=$SCRATCH/hp5-master
git -C $W worktree add --detach $MW $(git -C $W merge-base HEAD origin/master)
cp -R $W/schema $MW/
cp $W/build.gradle $MW/build.gradle
cp $W/src/test/java/io/github/alexshamrai/CtrfSchemaConformanceTest.java $MW/src/test/java/io/github/alexshamrai/
(cd $MW && ./gradlew :test --tests 'io.github.alexshamrai.CtrfSchemaConformanceTest' > $SCRATCH/hp5-conformance-master.log 2>&1)
grep -E 'BUILD' $SCRATCH/hp5-conformance-master.log
python3 $SCRATCH/hp5-junit-xml.py $MW/build/test-results/test/TEST-io.github.alexshamrai.CtrfSchemaConformanceTest.xml
grep -c 'SLF4J' $MW/build/test-results/test/TEST-io.github.alexshamrai.CtrfSchemaConformanceTest.xml
```

Expected:
- `BUILD FAILED`;
- `fullyPopulatedReportConformsToTheSchema` FAILED with 3 `/results/` entries:
  - `/results/tests/0: property 'filepath' is not defined in the schema and the schema does not allow additional properties`;
  - the same for `/results/tests/1`;
  - `/results/environment/buildNumber: string found, integer expected`;
- `reportWithABuildIdConformsToTheSchema` FAILED, because `buildId` is missing;
- `reportOfARunWithoutTestsConformsToTheSchema` FAILED with only the `buildNumber` entry;
- `legacyFilepathKeyIsRejected` and `stringBuildNumberIsRejected` passed. They check that the validator is strict, and need no fix;
- the `SLF4J` count is `0`.

Keep `$MW`; Task 5 uses it.

- [ ] **Step 7: Run it on the branch to verify it passes**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
./gradlew :test --tests 'io.github.alexshamrai.CtrfSchemaConformanceTest' 2>&1 | grep -E 'FAILED|BUILD'
python3 $SCRATCH/hp5-junit-xml.py build/test-results/test/TEST-io.github.alexshamrai.CtrfSchemaConformanceTest.xml
grep -c 'SLF4J' build/test-results/test/TEST-io.github.alexshamrai.CtrfSchemaConformanceTest.xml
```

Expected: `BUILD SUCCESSFUL`, 5 passed, and an `SLF4J` count of `0`.

- [ ] **Step 8: Check that nothing reaches the published library**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
./gradlew -q :dependencies --configuration runtimeClasspath > $SCRATCH/hp5-runtime-after.txt
diff $SCRATCH/hp5-runtime-before.txt $SCRATCH/hp5-runtime-after.txt && echo "runtime classpath unchanged"
./gradlew -q :generatePomFileForMavenPublication
diff $SCRATCH/hp5-pom-before.xml build/publications/maven/pom-default.xml && echo "POM unchanged"
./gradlew -q :jar
for j in build/libs/*.jar; do echo "${j}: $(unzip -l "${j}" | grep -c 'ctrf.schema.json')"; done
```

Expected: `runtime classpath unchanged`, `POM unchanged`, and `0` for every jar, because the schema is not in the library.

- [ ] **Step 9: Run the whole build**

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :build 2>&1 | grep -E 'FAILED|BUILD'`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add schema build.gradle src/test/java/io/github/alexshamrai/CtrfSchemaConformanceTest.java
git status --short
git commit -m "test: validate reports against the pinned official CTRF 0.2.0 schema (HP-5)"
```

Expected: `git status --short` shows only the two untracked report files after the add.

---

### Task 5: The integration validator on the pinned schema

**Files:**
- Modify: `integration-ctrf-validator/build.gradle:12-26` (dependencies, plus a new `sourceSets` block)
- Modify: `build.gradle` (`ext`: remove `jacksonModuleJsonSchemaVersion` and `jsonSchemaValidatorVersion`)
- Rewrite: `integration-ctrf-validator/src/main/java/io/github/alexshamrai/integration/JsonSchemaValidator.java`
- Delete: `integration-ctrf-validator/src/main/java/io/github/alexshamrai/integration/FileUtility.java`
- Delete: `integration-ctrf-validator/src/test/resources/schema/ctrf-schema.json`
- Rewrite: `integration-ctrf-validator/src/test/java/integration/CtrfSchemaValidationTest.java`
- Modify: `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java` (one new test)

**Interfaces:**
- Consumes:
  - `networkntValidatorVersion` and the `schema/` directory (Task 4);
  - `CtrfJson.SPEC_VERSION` and `Summary.getDuration()` (Task 3);
  - `BaseIntegrationTest`'s `objectMapper`, `report` and `reportFile`, which are unchanged.
- Produces: `new JsonSchemaValidator(InputStream schemaJson)` and `List<String> validate(String json)`. Each message starts with the JSON pointer of the value that fails, and the list is empty for a valid report.

- [ ] **Step 1: Switch the validator module's dependencies**

In `integration-ctrf-validator/build.gradle`, replace:

```groovy
    implementation "com.fasterxml.jackson.core:jackson-databind:${rootProject.ext.jaksonVersion}"
    implementation "com.fasterxml.jackson.module:jackson-module-jsonSchema:${rootProject.ext.jacksonModuleJsonSchemaVersion}"
    implementation "com.github.java-json-tools:json-schema-validator:${rootProject.ext.jsonSchemaValidatorVersion}"
```

with:

```groovy
    implementation "com.fasterxml.jackson.core:jackson-databind:${rootProject.ext.jaksonVersion}"
    implementation "com.networknt:json-schema-validator:${rootProject.ext.networkntValidatorVersion}"
```

Add after the `dependencies` block:

```groovy
sourceSets {
    test {
        resources {
            // The pinned official CTRF schema; see schema/README.md
            srcDir rootProject.file('schema')
            exclude 'README.md'
        }
    }
}
```

In the root `build.gradle`, delete these two lines from `ext`:

```groovy
    jacksonModuleJsonSchemaVersion = '2.22.2'
    jsonSchemaValidatorVersion = '2.2.14'
```

- [ ] **Step 2: Rewrite `JsonSchemaValidator` and delete what only it used**

Replace the whole content of `integration-ctrf-validator/src/main/java/io/github/alexshamrai/integration/JsonSchemaValidator.java` with:

```java
package io.github.alexshamrai.integration;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.InputStream;
import java.util.List;

/**
 * Validates JSON documents against a JSON schema in draft-07 mode, the draft of the official CTRF schema.
 */
public class JsonSchemaValidator {

    private final Schema schema;

    public JsonSchemaValidator(InputStream schemaJson) {
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schemaJson);
    }

    /**
     * Returns one message per violation, each starting with the JSON pointer of the value that fails, such as
     * {@code /results/tests/0: property 'filepath' is not defined in the schema ...}. Empty when the document is valid.
     */
    public List<String> validate(String json) {
        return schema.validate(json, InputFormat.JSON).stream().map(Object::toString).toList();
    }
}
```

Then:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git rm -q integration-ctrf-validator/src/main/java/io/github/alexshamrai/integration/FileUtility.java \
  integration-ctrf-validator/src/test/resources/schema/ctrf-schema.json
git grep -n 'FileUtility\|ctrf-schema.json\|java-json-tools\|jsonSchemaValidatorVersion\|jacksonModuleJsonSchema' -- . ':!*.md'
```

Expected: the `git grep` prints nothing. Task 6 updates the `.md` mentions.

- [ ] **Step 3: Rewrite `CtrfSchemaValidationTest`**

Replace the whole content of `integration-ctrf-validator/src/test/java/integration/CtrfSchemaValidationTest.java` with:

```java
package integration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import io.github.alexshamrai.integration.JsonSchemaValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CtrfSchemaValidationTest extends BaseIntegrationTest {

    private JsonSchemaValidator schemaValidator;

    @BeforeAll
    void loadThePinnedSchema() throws IOException {
        try (InputStream schema = getClass().getResourceAsStream("/ctrf.schema.json")) {
            assertThat(schema)
                .as("ctrf.schema.json should be on the test classpath; see schema/README.md")
                .isNotNull();
            schemaValidator = new JsonSchemaValidator(schema);
        }
    }

    @Test
    void validateSchemaCompliance() throws IOException {
        assertThat(schemaValidator.validate(Files.readString(reportFile.toPath())))
            .as("Errors against the official CTRF schema in %s", reportFile)
            .isEmpty();

        assertThat(report.getReportFormat())
            .as("Report format should be 'CTRF'")
            .isEqualTo("CTRF");
        assertThat(report.getSpecVersion())
            .as("Spec version should be the version of the pinned schema")
            .isEqualTo(CtrfJson.SPEC_VERSION);

        assertThat(report.getResults()).isNotNull();

        assertThat(report.getResults().getTool()).isNotNull();
        assertThat(report.getResults().getTool().getName())
            .as("Tool name should not be null or empty")
            .isNotNull()
            .isNotEmpty();

        assertThat(report.getResults().getSummary()).isNotNull();
        assertThat(report.getResults().getSummary().getTests())
            .as("Summary tests count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getPassed())
            .as("Summary passed count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getFailed())
            .as("Summary failed count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getSkipped())
            .as("Summary skipped count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getPending())
            .as("Summary pending count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getOther())
            .as("Summary other count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getStart())
            .as("Summary start timestamp should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getStop())
            .as("Summary stop timestamp should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);

        validateTests(report.getResults().getTests());
    }

    @Test
    void validatorRejectsTheLegacyFilepathKey() throws IOException {
        var tree = (ObjectNode) objectMapper.readTree(reportFile);
        var firstTest = (ObjectNode) tree.at("/results/tests/0");
        firstTest.remove("filePath");
        firstTest.put("filepath", "integration.LegacyTest");

        assertThat(schemaValidator.validate(objectMapper.writeValueAsString(tree)))
            .anySatisfy(error -> assertThat(error).startsWith("/results/tests/0:").contains("'filepath'"));
    }

    @Test
    void validatorRejectsAStringBuildNumber() throws IOException {
        var tree = (ObjectNode) objectMapper.readTree(reportFile);
        ((ObjectNode) tree.at("/results/environment")).put("buildNumber", "777");

        assertThat(schemaValidator.validate(objectMapper.writeValueAsString(tree)))
            .anySatisfy(error -> assertThat(error).startsWith("/results/environment/buildNumber:"));
    }

    private void validateTests(List<io.github.alexshamrai.ctrf.model.Test> tests) {
        assertThat(tests)
            .as("Tests list should not be null or empty")
            .isNotNull()
            .isNotEmpty();

        tests.forEach(test -> {
            assertThat(test.getName())
                .as("Test name should not be null or empty")
                .isNotNull()
                .isNotEmpty();

            assertThat(test.getStatus())
                .as("Test status should be one of the valid enum values")
                .isNotNull()
                .satisfies(status -> {
                    assertThat(status.name())
                        .isIn(TestStatus.PASSED.name(), TestStatus.FAILED.name(),
                            TestStatus.SKIPPED.name(), TestStatus.PENDING.name(),
                            TestStatus.OTHER.name());
                });
        });
    }

}
```

`BaseIntegrationTest` is `PER_CLASS`, so the non-static `@BeforeAll` works. JUnit runs the superclass's `@BeforeAll` (which reads the report) before this one.

- [ ] **Step 4: Add the merged-run duration check to `CtrfLogicTest`**

In `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`, add this test right after `verifyBuildParametersFromSystemProperties`:

```java
    @Test
    void verifySummaryDurationSpansTheRun() {
        var summary = report.getResults().getSummary();

        assertThat(summary.getDuration())
            .as("summary.duration should be stop minus start, across every JVM merged into the report")
            .isEqualTo(summary.getStop() - summary.getStart())
            .isPositive();
    }
```

Run: `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes && ./gradlew :integration-ctrf-validator:compileTestJava 2>&1 | grep -E 'error:|BUILD'`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Generate today's `master` listener report**

The worktree `$MW` from Task 4 has three copied files. Remove them, so `master` is exactly as it was:

```bash
W=/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
MW=$SCRATCH/hp5-master
[ -d $MW ] || git -C $W worktree add --detach $MW $(git -C $W merge-base HEAD origin/master)
rm -rf $MW/schema $MW/src/test/java/io/github/alexshamrai/CtrfSchemaConformanceTest.java
git -C $MW checkout -- build.gradle
git -C $MW status --short
(cd $MW && ./gradlew clean :integration-tests-listener:test -Dthreads=2 -Dforks=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 \
  > $SCRATCH/hp5-master-listener.log 2>&1)
python3 -c "import json;r=json.load(open('$MW/integration-tests-listener/build/test-results/ctrf-report.json'));print(r['specVersion'], len(r['results']['tests']), repr(r['results']['environment']['buildNumber']))"
```

Expected:
- `git -C $MW status --short` shows nothing, or only `?? ctrf-report.json` and `?? ctrf-report.json.lock`;
- the Python line prints `0.0.0 28 '777'`.

- [ ] **Step 6: Run the new validator against `master`'s report to verify it fails**

```bash
W=/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
MW=$SCRATCH/hp5-master
cd $W
./gradlew :integration-ctrf-validator:test --rerun \
  -Dctrf.report.path=$MW/integration-tests-listener/build/test-results/ctrf-report.json > $SCRATCH/hp5-validator-master.log 2>&1
grep -E 'BUILD' $SCRATCH/hp5-validator-master.log
python3 $SCRATCH/hp5-junit-xml.py integration-ctrf-validator/build/test-results/test/TEST-integration.*.xml
```

If `$SCRATCH/hp5-junit-xml.py` is missing, create it first:

```bash
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
cat > $SCRATCH/hp5-junit-xml.py <<'EOF'
import sys
import xml.etree.ElementTree as ET

for path in sys.argv[1:]:
    for case in ET.parse(path).getroot().iter('testcase'):
        failure = case.find('failure')
        if failure is None:
            print('passed ', case.get('name'))
            continue
        message = failure.get('message') or ''
        print('FAILED ', case.get('name'), '| /results/ entries:', message.count('/results/'))
        print('       ', message[:700])
EOF
```

Expected:
- `BUILD FAILED`;
- `validateSchemaCompliance` FAILED with 29 `/results/` entries: `/results/tests/N: property 'filepath' is not defined…` for each of the 28 tests, and `/results/environment/buildNumber: string found, integer expected`;
- `verifySummaryDurationSpansTheRun` FAILED: `master` writes no `duration`, so it reads as 0;
- `validatorRejectsTheLegacyFilepathKey`, `validatorRejectsAStringBuildNumber` and every other `CtrfLogicTest` test passed. The branch's model still reads `master`'s report, through `@JsonAlias` and Jackson's string-to-number conversion.

This is TI-4's acceptance criterion: "the validator fails on today's master output".

- [ ] **Step 7: Run the integration runs on the branch to verify they pass**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
./gradlew clean :integration-tests-listener:test -Dthreads=2 -Dforks=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 > $SCRATCH/hp5-listener.log 2>&1
./gradlew :integration-ctrf-validator:test --rerun \
  -Dctrf.report.path=../integration-tests-listener/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
python3 -c "import json;r=json.load(open('integration-tests-listener/build/test-results/ctrf-report.json'));e=r['results']['environment'];s=r['results']['summary'];print(r['specVersion'], len(r['results']['tests']), e['buildNumber'], 'buildId' in e, s['duration'] == s['stop'] - s['start'])"

./gradlew clean :integration-tests-extension:test -Dthreads=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 > $SCRATCH/hp5-extension.log 2>&1
./gradlew :integration-ctrf-validator:test --rerun \
  -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
python3 -c "import json;r=json.load(open('integration-tests-extension/build/test-results/ctrf-report.json'));print(r['specVersion'], len(r['results']['tests']))"
```

Expected:
- both validator runs end with `BUILD SUCCESSFUL`;
- the listener line prints `0.2.0 28 777 False True`;
- the extension line prints `0.2.0 22`.

- [ ] **Step 8: Check the timeline visualization** (Review Focus 4)

The last run was the extension module. Run the listener again and check its HTML report:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew clean :integration-tests-listener:test -Dthreads=2 -Dforks=2 > /dev/null 2>&1
grep -o 'File:</span> [^<]*' integration-tests-listener/build/reports/tests/test/index.html | sort | uniq -c | head
grep -c 'File:</span> Unknown' integration-tests-listener/build/reports/tests/test/index.html
```

Expected: the first `grep` lists class names such as `File:</span> integration.…Test`, and the count of `Unknown` is `0`.

- [ ] **Step 9: Remove the temporary `master` worktree**

`$MW` is the scratch worktree created in Task 4. It holds nothing but build output and the report generated there.

```bash
W=/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
MW=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/hp5-master
git -C $W worktree remove --force $MW
git -C $W worktree list
```

Expected: `$MW` is no longer listed.

- [ ] **Step 10: Run the whole build and commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
./gradlew :build 2>&1 | grep -E 'FAILED|BUILD'
git add -A build.gradle integration-ctrf-validator
git status --short
git commit -m "test: the integration validator checks reports against the pinned CTRF schema (HP-5, TI-4)"
```

Expected:
- `BUILD SUCCESSFUL`;
- `git status --short` shows only the two untracked report files after the add.

---

### Task 6: Documentation

**Files:**
- Modify: `README.md:24,183,275`
- Modify: `CLAUDE.md:134-136,213-216`
- Modify: `INTEGRATION_TESTS.md:132-145,162-168`

**Interfaces:**
- Consumes: the behaviour from Tasks 1–5.
- Produces: nothing used by code.

- [ ] **Step 1: README**

In `README.md`, replace the first feature line:

```markdown
- Generates CTRF-compliant JSON test reports for JUnit 5 tests
```

with:

```markdown
- Generates JSON test reports for JUnit 5 tests that follow CTRF 0.2.0 and validate against its [official schema](https://github.com/ctrf-io/ctrf/blob/v0.2.0/schema/ctrf.schema.json)
```

In the configuration table, replace the `ctrf.build.number` row:

```markdown
| `ctrf.build.number`               | Build number from your CI/CD system                          |                    |
```

with:

```markdown
| `ctrf.build.number`               | Build number from your CI/CD system. A whole number is written as `buildNumber`; any other value, such as `2026.10.15-3`, as `buildId` |                    |
```

In "Example Report Output", replace:

```json
      "buildNumber": "123"
```

with:

```json
      "buildNumber": 123
```

- [ ] **Step 2: CLAUDE.md**

In `CLAUDE.md`, section "CTRF Model Classes", replace:

```markdown
All model classes in `io.github.alexshamrai.ctrf.model` use:
- **Lombok**: `@Data`, `@Builder` for boilerplate reduction
- **Jackson**: `@JsonInclude(NON_NULL)` to omit null fields from JSON output
```

with:

```markdown
All model classes in `io.github.alexshamrai.ctrf.model` use:
- **Lombok**: `@Data`, `@Builder` for boilerplate reduction
- **Jackson**: `@JsonInclude(NON_NULL)` to omit null fields from JSON output

**Schema:** reports must validate against the official CTRF schema, pinned at `schema/ctrf.schema.json`. It is version 0.2.0, and `schema/README.md` has its source tag and commit. `CtrfSchemaConformanceTest` and the integration validator load it as a test resource; it is not in the published jar. To move to a new CTRF version, replace the file and change `CtrfJson.SPEC_VERSION` in the same commit. Field names follow the spec exactly, for example `filePath`; `buildNumber` is an integer, and a non-numeric `ctrf.build.number` goes to `buildId`.
```

In "Project Structure", replace:

```
integration-tests-extension/       # Integration tests using CtrfExtension
```

with:

```
schema/                            # Pinned official CTRF schema (test resource only)
integration-tests-extension/       # Integration tests using CtrfExtension
```

- [ ] **Step 3: INTEGRATION_TESTS.md**

In `INTEGRATION_TESTS.md`, in the module tree (around line 132), replace these lines:

~~~
        │       ├── CtrfLogicTest.java             # Verifies logical correctness of the report
        └── resources/
            └── schema/
                └── ctrf-schema.json               # CTRF schema definition
~~~

with this one line, so `CtrfLogicTest.java` is now the last entry:

~~~
        │       └── CtrfLogicTest.java             # Verifies logical correctness of the report
~~~

Leave the line before it and the fence that closes the tree as they are. Right after that closing fence, add a blank line and this paragraph:

~~~markdown
The schema is the official CTRF schema pinned at `schema/ctrf.schema.json` in the repository root (see `schema/README.md`). The module's `build.gradle` adds it to the test resources.
~~~

Then replace:

```markdown
- Verifies the report against the JSON schema definition
- Checks that required fields are present and have correct formats
- Validates that the report format is "CTRF" and the spec version follows the pattern x.y.z
- Ensures all test entries have valid names and statuses
```

with:

```markdown
- Validates the report against the official CTRF schema in draft-07 mode, so unknown properties and wrong types are errors
- Checks that the report format is "CTRF" and that `specVersion` equals `CtrfJson.SPEC_VERSION`
- Checks that the validator rejects a report with the legacy `filepath` key or a string `buildNumber`
- Ensures all test entries have valid names and statuses
```

- [ ] **Step 4: Check and commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git grep -n 'ctrf-schema.json\|"buildNumber": "' -- '*.md'
git diff --stat
git add README.md CLAUDE.md INTEGRATION_TESTS.md
git commit -m "docs: CTRF 0.2.0, the pinned schema, buildNumber and buildId (HP-5)"
```

Expected: the `git grep` prints nothing, and the diff touches only the three files.

---

### Task 7: Final verification, squash and PR

**Files:**
- Create (not committed): `$SCRATCH/hp5-pr-body.md`

**Interfaces:** none.

- [ ] **Step 1: Full local verification**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
./gradlew clean :build 2>&1 | grep -E 'FAILED|BUILD'
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t=f=0
for p in glob.glob('build/test-results/test/*.xml'):
    r=ET.parse(p).getroot(); t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors'))
print('tests', t, 'failures', f)
EOF
```

Expected: `BUILD SUCCESSFUL` and `failures 0`. Write down the test count.

Then both integration runs with CI's flags, each followed by the validator:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
./gradlew clean :integration-tests-listener:test -Dthreads=2 -Dforks=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 > $SCRATCH/hp5-listener.log 2>&1
./gradlew :integration-ctrf-validator:test --rerun \
  -Dctrf.report.path=../integration-tests-listener/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
./gradlew clean :integration-tests-extension:test -Dthreads=2 \
  -Dctrf.build.name=system-build -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678 > $SCRATCH/hp5-extension.log 2>&1
./gradlew :integration-ctrf-validator:test --rerun \
  -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json 2>&1 | grep -E 'FAILED|BUILD'
```

Expected: both validator runs end with `BUILD SUCCESSFUL`.

Then the compat matrix:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
compat/run.sh 5.10.5 5.14.4 6.1.3 2>&1 | tail -2
```

Expected: `all compatibility checks passed`. `compat/check_report.py` reads only the summary and `runId`.

- [ ] **Step 2: Squash into one commit**

If `origin/master` moved since Task 1, rebase first. Squashing onto a newer `origin/master` without rebasing would silently revert what was merged:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git fetch origin
git rebase origin/master
```

Expected: "Current branch fix/hp-5-ctrf-schema is up to date", or a clean rebase. If the rebase brought in new commits, repeat Step 1. If it stops with conflicts, resolve them so that both `master`'s change and this branch's survive, then repeat Step 1.

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git status --short
git reset --soft origin/master
git commit -F - <<'EOF'
fix: conform reports to the official CTRF 0.2.0 schema (HP-5)

Against the official schema (ctrf-io/ctrf v0.2.0), master's listener
report had 29 errors and the extension report 23. Every test wrote
"filepath" where the spec has "filePath", and environment.buildNumber
was a string where the spec has an integer. specVersion said 0.0.0. The
integration validator missed all of it: its bundled schema was a looser
copy, checked by a draft-04 library.

- Test.filepath is now filePath, in Java and JSON. Reports with
  "filepath" still read.
- ctrf.build.number goes to buildNumber when it is a whole number, and
  to the new environment.buildId otherwise, with a warning.
- specVersion is 0.2.0 (CtrfJson.SPEC_VERSION), summary.duration is
  written, and Test.suite is a list (still unset; HP-12 fills it).
- The official schema is pinned at schema/ctrf.schema.json. A unit test
  validates a fully populated report against it, and the integration
  validator checks the listener and extension reports, both with
  networknt json-schema-validator in draft-07 mode, in tests only.
EOF
git log --oneline -2
git show --stat --format= HEAD
```

Expected:
- one commit on top of `origin/master`, with no `Co-Authored-By` line;
- `git status --short` before the reset shows only the two untracked report files.

- [ ] **Step 3: Push the branch**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git push -u origin fix/hp-5-ctrf-schema
```

- [ ] **Step 4: Write the PR description**

Write `$SCRATCH/hp5-pr-body.md` with the sections below. Fill them from this plan and from the numbers written down in Step 1. End it without any "Generated with Claude Code" line.

- **What:** reports validate against the official CTRF 0.2.0 schema. The schema is pinned in the repository, and both the unit tests and the integration validator check against it.
- **Why:**
  - the measured errors on `master`: 29 in the listener report and 23 in the extension report, all `filepath` plus `buildNumber`;
  - `specVersion` "0.0.0";
  - the validator's blind spots: a looser bundled copy of the schema, and a draft-04 library.
- **Changes:**
  - `filePath`, with `@JsonAlias`;
  - `buildNumber` as a `Long`, the new `buildId`, and the rule with its warning;
  - `SPEC_VERSION`, `summary.duration`, and `suite` as a list;
  - `schema/` and its README;
  - networknt in tests only, plus `slf4j-nop`;
  - `CtrfSchemaConformanceTest`;
  - the validator rewrite, with `FileUtility`, the bundled schema and the old libraries removed;
  - the docs.
- **What changes in the report:** a before/after table with `filepath` → `filePath`, `"buildNumber": "777"` → `777`, a non-numeric build number → `buildId`, `specVersion`, and `summary.duration`.
- **Release notes for 0.5.0:**
  - the JSON field is now `filePath`, so consumers that read `filepath` must switch;
  - `buildNumber` is an integer, and non-numeric values move to `buildId`;
  - the Java getter `getFilepath()` is now `getFilePath()`, and `Environment.getBuildNumber()` returns a `Long`.
- **Tests:**
  - the new unit tests;
  - the RED runs against `master`: the conformance test, and the validator with 29 errors on `master`'s listener report;
  - the Review Focus cases.
- **Verified locally:**
  - the build's test count;
  - both integration runs with the validator;
  - compat 12 of 12;
  - the runtime classpath and POM unchanged.
- **Not in this PR:**
  - HP-12, filling `suite`;
  - the rest of TI-4, the cross-check against JUnit's XML.

The user opens the PR from the browser as `alexshamrai`, because `gh` is logged in as an account that can't create PRs on this repo:
- URL: https://github.com/alexshamrai/junit-ctrf-reporter/compare/master...fix/hp-5-ctrf-schema?expand=1
- Title: `fix: conform reports to the official CTRF 0.2.0 schema (HP-5)`

## After the PR merges

On `feature/improvements-plan`, in the main checkout, update `IMPROVEMENT_PLAN.md` in one docs commit:
- mark HP-5 done, with the PR number, and tick its acceptance criteria;
- add a line to TI-4's status: its first requirement, validation against the official schema, shipped with HP-5.

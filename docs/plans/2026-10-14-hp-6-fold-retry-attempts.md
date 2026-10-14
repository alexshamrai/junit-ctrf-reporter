# HP-6: Fold Retry Attempts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Report each logical test once, as its final attempt, with earlier attempts in `retryAttempts`, `retries`/`flaky` set by the CTRF rules, and `summary.flaky`. Recording a result becomes O(1).

**Architecture:** While tests run, every finished attempt is appended to a `ConcurrentLinkedQueue` (no scan, no copy). At the start of a run, the existing report's tests are *unfolded* back into attempts. At the end, one pass *folds* the attempts by `testId` into test objects. Folding and unfolding live in one stateless class, `AttemptFolder`, which replaces `FlakyTestDetector`. CB-6 later reuses it to merge shards.

**Tech Stack:** Java 17 (toolchain 21), Gradle, Lombok, Jackson, JUnit 5/6, AssertJ, Mockito, Checkstyle.

**Spec:** `IMPROVEMENT_PLAN.md`, section "HP-6: One Test Object per Logical Test (Fold Retry Attempts)", on branch `feature/improvements-plan` (commit `a6ea35e`). Read it before starting. CTRF spec 0.1.0: https://github.com/ctrf-io/ctrf (`spec/ctrf.md` sections 9.20–9.22 and 11).

## Global Constraints

- Work in the worktree `/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes`, on branch `fix/hp-6-fold-retry-attempts` created from `origin/master`. Ignore the untracked `ctrf-report.json` at its root; never commit it.
- CTRF rules, verbatim from the spec:
  - "If `retryAttempts` is present, `retries` MUST be present and MUST equal the count of entries in `retryAttempts`";
  - "If `retries` is 0, `retryAttempts` MUST NOT be present";
  - attempts numbered from 1 with no gaps;
  - "A test is considered flaky only if its final status is `passed` and it experienced one or more failed attempts before passing".
- Attempt entries may only contain `attempt`, `status`, `duration`, `start`, `stop`, `message` and `trace`; the spec forbids unknown fields there. `threadId` and `rawStatus` stay on the test object.
- A test that ran once must serialize exactly as today: no `retries`, `retryAttempts` or `flaky` fields.
- `summary.flaky` is always present (an `int`, 0 when none).
- Keep the JSON field name `filepath`. Renaming it to `filePath` is HP-5.
- Out of scope:
  - CB-6 (shards, `runId`, same-JVM reruns, atomic writes);
  - HP-5 (schema fields, official schema);
  - issues that only occur with the listener and the extension registered together.
- No version bump and no release notes. HP-6 ships with Phase 2 as 0.5.0.
- Commits:
  - no `Co-Authored-By` trailer and no "Generated with Claude Code" line, anywhere;
  - per-task commits are squashed into one commit in Task 7.
- zsh: in loops use `${m}`, never `$m:...`, because `$m:t` and similar trigger zsh modifiers.
- `./gradlew :build` runs unit tests, scenario tests and checkstyle. Root `./gradlew build` without `:` also runs the validator and fails without a report; don't use it.

## Review Focus

1. **A report written by 0.4.x** has one entry per attempt, the later copies carry `retries: 1` and `flaky: true`, and there's no `retryAttempts`. It must fold to one test with `retries: 1`, `flaky: true` and a single history entry. Test: `AttemptFolderTest.entriesOfAnOlderReportFoldIntoOneTest` (Task 2).
2. **Attempts added from many threads at once** (parallel execution) must all be kept. Test: `TestStateTrackerTest.shouldKeepAttemptsAddedConcurrently` (Task 3).
3. **A report entry without a `status`** (hand-edited or truncated file) must not crash folding and must never make a test flaky. Test: `AttemptFolderTest.attemptWithoutStatusIsKept` (Task 2).
4. **A test that failed and then was skipped** (aborted by an assumption) in the next round is reported skipped with `retries: 1`, not flaky. Test: `AttemptFolderTest.failedThenSkippedIsSkippedAndNotFlaky` (Task 2).
5. **A second test plan execution in the same JVM** must not report the first execution's attempts again; folding must not make CB-6c worse. Test: `CtrfReportManagerTest.finishTestRun_doesNotRepeatAttemptsOfAnEarlierExecution` (Task 3).

---

### Task 1: Report model: `RetryAttempt`, `Test.retryAttempts`, `Summary.flaky`

**Files:**
- Create: `src/main/java/io/github/alexshamrai/ctrf/model/RetryAttempt.java`
- Modify: `src/main/java/io/github/alexshamrai/ctrf/model/Test.java` (the `@Builder` annotation and the fields after `flaky`)
- Modify: `src/main/java/io/github/alexshamrai/ctrf/model/Summary.java` (fields)
- Modify: `src/main/java/io/github/alexshamrai/util/SummaryUtil.java` (whole file)
- Create: `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`
- Modify: `src/test/java/io/github/alexshamrai/util/SummaryUtilTest.java`

**Interfaces:**
- Produces:
  - `RetryAttempt` with Lombok builder/getters: `int attempt`, `Test.TestStatus status`, `long duration`, `Long start`, `Long stop`, `String message`, `String trace`;
  - `Test.getRetryAttempts()`/`setRetryAttempts(List<RetryAttempt>)`, `Test.builder().retryAttempts(...)`, and `Test#toBuilder()`;
  - `Summary.getFlaky()` (`int`);
  - `SummaryUtil.createSummary(List<Test>, long, long)` (unchanged signature), which now also sets `flaky`.

- [ ] **Step 1: Create the branch**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git fetch origin --prune
git checkout -b fix/hp-6-fold-retry-attempts origin/master
git log --oneline -1
```

Expected: the branch starts at the current `origin/master`.

- [ ] **Step 2: Write the failing tests**

Create `src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java`:

```java
package io.github.alexshamrai.ctrf.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CtrfModelJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.Test
    void testThatRanOnceHasNoRetryFields() throws Exception {
        var test = Test.builder().name("test1()").status(Test.TestStatus.PASSED).build();

        var json = objectMapper.readTree(objectMapper.writeValueAsString(test));

        assertThat(json.has("retries")).isFalse();
        assertThat(json.has("retryAttempts")).isFalse();
        assertThat(json.has("flaky")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void retryAttemptsAreWrittenAsSpecEntriesAndReadBack() throws Exception {
        var test = Test.builder()
            .name("test1()")
            .status(Test.TestStatus.PASSED)
            .retries(1)
            .flaky(true)
            .retryAttempts(List.of(RetryAttempt.builder()
                .attempt(1).status(Test.TestStatus.FAILED).duration(5).start(100L).stop(105L)
                .message("boom").trace("trace of boom")
                .build()))
            .build();

        var written = objectMapper.writeValueAsString(test);
        var entry = objectMapper.readTree(written).get("retryAttempts").get(0);

        assertThat(entry.get("attempt").asInt()).isEqualTo(1);
        assertThat(entry.get("status").asText()).isEqualTo("failed");
        assertThat(entry.get("duration").asLong()).isEqualTo(5);
        assertThat(entry.get("message").asText()).isEqualTo("boom");
        assertThat(objectMapper.readValue(written, Test.class)).isEqualTo(test);
    }

    @org.junit.jupiter.api.Test
    void summaryAlwaysHasFlakyAndOlderReportsReadAsZero() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(Summary.builder().build()));
        assertThat(json.get("flaky").asInt()).isZero();

        var older = objectMapper.readValue(
            "{\"tests\":1,\"passed\":1,\"failed\":0,\"pending\":0,\"skipped\":0,\"other\":0,\"start\":1,\"stop\":2}",
            Summary.class);
        assertThat(older.getFlaky()).isZero();
    }

    @org.junit.jupiter.api.Test
    void toBuilderCopiesATest() {
        var test = Test.builder().testId("id-1").name("test1()").status(Test.TestStatus.FAILED).build();

        var copy = test.toBuilder().status(Test.TestStatus.PASSED).build();

        assertThat(copy.getTestId()).isEqualTo("id-1");
        assertThat(copy.getStatus()).isEqualTo(Test.TestStatus.PASSED);
        assertThat(test.getStatus()).isEqualTo(Test.TestStatus.FAILED);
    }
}
```

Append to `src/test/java/io/github/alexshamrai/util/SummaryUtilTest.java`, inside the class:

```java
    @org.junit.jupiter.api.Test
    void shouldCountFlakyTests() {
        var tests = List.of(
            Test.builder().status(TestStatus.PASSED).flaky(true).build(),
            Test.builder().status(TestStatus.PASSED).build(),
            Test.builder().status(TestStatus.FAILED).build()
        );

        var summary = SummaryUtil.createSummary(tests, 1000L, 2000L);

        assertEquals(3, summary.getTests());
        assertEquals(1, summary.getFlaky());
    }
```

In the same file, in `shouldHandleEmptyTestList`, after `assertEquals(0, summary.getOther());`, add:

```java
        assertEquals(0, summary.getFlaky());
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :test --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest' --tests 'io.github.alexshamrai.util.SummaryUtilTest'`

Expected: FAIL at `:compileTestJava`, with "cannot find symbol" for `RetryAttempt`, `retryAttempts`, `getFlaky` and `toBuilder`.

- [ ] **Step 4: Implement the model**

Create `src/main/java/io/github/alexshamrai/ctrf/model/RetryAttempt.java`:

```java
package io.github.alexshamrai.ctrf.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An attempt of a test that completed before its final attempt (CTRF attempt history entry, spec section 11).
 * The test object itself represents the final attempt. The spec allows no other fields here, so attempt
 * details such as the thread stay on the test object.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetryAttempt {

    /**
     * Sequence number of this attempt: 1 is the initial execution.
     */
    private int attempt;
    private Test.TestStatus status;
    private long duration;
    private Long start;
    private Long stop;
    private String message;
    private String trace;
}
```

In `src/main/java/io/github/alexshamrai/ctrf/model/Test.java`, replace the line `@Builder` (the one directly above `@JsonInclude`) with:

```java
@Builder(toBuilder = true)
```

and replace

```java
    private Integer retries;
    private Boolean flaky;
```

with

```java
    private Integer retries;
    private Boolean flaky;
    /**
     * Attempts completed before the final attempt, which this object represents. Present only when
     * {@code retries} is greater than 0, with exactly {@code retries} entries numbered from 1.
     */
    private List<RetryAttempt> retryAttempts;
```

In `src/main/java/io/github/alexshamrai/ctrf/model/Summary.java`, replace

```java
    private int other;
```

with

```java
    private int other;
    /**
     * Number of tests whose final attempt passed after at least one failed attempt.
     */
    private int flaky;
```

Replace the whole content of `src/main/java/io/github/alexshamrai/util/SummaryUtil.java` with:

```java
package io.github.alexshamrai.util;

import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.OTHER;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PENDING;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;

public class SummaryUtil {

    /**
     * Creates a summary from test results: how many tests ended with each status, and how many are flaky.
     * <p>
     * Each test object is one test, represented by its final attempt, so earlier attempts of a retried test
     * are not counted. Both counts take one pass over the tests.
     *
     * @param tests the test results to summarize, one per test
     * @param startTime the test run start timestamp
     * @param stopTime the test run stop timestamp
     * @return a Summary object with status counts, the flaky count and timing information
     */
    public static Summary createSummary(List<Test> tests, long startTime, long stopTime) {
        Map<Test.TestStatus, Long> statusCounts = tests.stream()
            .map(Test::getStatus)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(status -> status, Collectors.counting()));
        long flaky = tests.stream()
            .filter(test -> Boolean.TRUE.equals(test.getFlaky()))
            .count();

        return Summary.builder()
            .tests(tests.size())
            .passed(getCount(statusCounts, PASSED))
            .failed(getCount(statusCounts, FAILED))
            .pending(getCount(statusCounts, PENDING))
            .skipped(getCount(statusCounts, SKIPPED))
            .other(getCount(statusCounts, OTHER))
            .flaky((int) flaky)
            .start(startTime)
            .stop(stopTime)
            .build();
    }

    /**
     * Safely retrieves the count for a given test status from the status counts map.
     *
     * @param statusCounts the map of status counts
     * @param status the status to retrieve count for
     * @return the count for the status, or 0 if not present
     */
    private static int getCount(Map<Test.TestStatus, Long> statusCounts, Test.TestStatus status) {
        return statusCounts.getOrDefault(status, 0L).intValue();
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest' --tests 'io.github.alexshamrai.util.SummaryUtilTest'`

Expected: PASS.

- [ ] **Step 6: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL. Nothing else changes behaviour yet.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/alexshamrai/ctrf/model src/main/java/io/github/alexshamrai/util/SummaryUtil.java \
        src/test/java/io/github/alexshamrai/ctrf/model/CtrfModelJsonTest.java src/test/java/io/github/alexshamrai/util/SummaryUtilTest.java
git commit -m "feat: add retryAttempts and summary.flaky to the report model (HP-6)"
```

---

### Task 2: `AttemptFolder`: fold attempts into tests, unfold tests into attempts

**Files:**
- Create: `src/main/java/io/github/alexshamrai/AttemptFolder.java`
- Create: `src/test/java/io/github/alexshamrai/AttemptFolderTest.java`

**Interfaces:**
- Consumes: `RetryAttempt`, `Test.retryAttempts`, `Test#toBuilder()` (Task 1).
- Produces (package `io.github.alexshamrai`, package-private):
  - `static List<Test> AttemptFolder.fold(List<Test> attempts)`: attempts in the order they ran, to one test object per test, in first-attempt order. It never modifies its input.
  - `static List<Test> AttemptFolder.unfold(List<Test> tests)`: test objects to attempts, each test's earlier attempts first, then the test itself without `retries`/`retryAttempts`/`flaky`. It never modifies its input.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/io/github/alexshamrai/AttemptFolderTest.java`:

```java
package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AttemptFolderTest {

    private static final String CLASS_A = "com.example.ATest";
    private static final String CLASS_B = "com.example.BTest";
    private static final String TEST_1 = "[engine:junit-jupiter]/[class:com.example.ATest]/[method:test1()]";
    private static final String TEST_2 = "[engine:junit-jupiter]/[class:com.example.ATest]/[method:test2()]";

    @org.junit.jupiter.api.Test
    @DisplayName("A test that ran once is returned as it is, without retry fields")
    void singleAttemptIsUntouched() {
        var only = attempt(TEST_1, "test1()", PASSED, 100L);

        assertThat(AttemptFolder.fold(List.of(only))).containsExactly(only);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test that passed after a failed attempt is one flaky test with the failed attempt in its history")
    void failedThenPassedIsFlaky() {
        var failed = attempt(TEST_1, "test1()", FAILED, 100L).toBuilder().message("boom").trace("trace of boom").build();
        var passed = attempt(TEST_1, "test1()", PASSED, 200L);

        var tests = AttemptFolder.fold(List.of(failed, passed));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(PASSED);
            assertThat(test.getStart()).isEqualTo(200L);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isTrue();
            assertThat(test.getRetryAttempts()).containsExactly(RetryAttempt.builder()
                .attempt(1).status(FAILED).duration(5).start(100L).stop(105L)
                .message("boom").trace("trace of boom")
                .build());
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test that failed in every attempt is not flaky")
    void failedThenFailedIsNotFlaky() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_1, "test1()", FAILED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(FAILED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A passing test that ran again, because its whole class was rerun, is not flaky")
    void passedThenPassedIsNotFlaky() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", PASSED, 100L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(PASSED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test that passed after a skipped (aborted) attempt is not flaky")
    void skippedThenPassedIsNotFlaky() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", SKIPPED, 100L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test that failed and then was skipped is skipped, with the failure in its history")
    void failedThenSkippedIsSkippedAndNotFlaky() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_1, "test1()", SKIPPED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(SKIPPED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getRetryAttempts()).extracting(RetryAttempt::getStatus).containsExactly(FAILED);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Earlier attempts are numbered from 1 in the order they ran")
    void earlierAttemptsAreNumberedFromOne() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_1, "test1()", FAILED, 200L),
            attempt(TEST_1, "test1()", PASSED, 300L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(2);
            assertThat(test.getRetryAttempts())
                .extracting(RetryAttempt::getAttempt, RetryAttempt::getStart)
                .containsExactly(tuple(1, 100L), tuple(2, 200L));
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Tests keep the order of their first attempt")
    void testsKeepTheOrderOfTheirFirstAttempt() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_2, "test2()", PASSED, 150L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(tests).extracting(Test::getTestId).containsExactly(TEST_1, TEST_2);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A class failure in another phase is an earlier attempt of the same class")
    void classFailuresFoldAcrossPhases() {
        var classId = "[engine:junit-jupiter]/[class:com.example.ATest]";

        var tests = AttemptFolder.fold(List.of(
            attempt(classId, "initializationError", FAILED, 100L),
            attempt(classId, "teardownError", FAILED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getName()).isEqualTo("teardownError");
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Tests with different testIds are never folded, even with the same name")
    void differentTestIdsAreNotFolded() {
        var templates = "[engine:junit-jupiter]/[class:com.example.ATest]/[test-template:";
        var tests = AttemptFolder.fold(List.of(
            Test.builder().testId("[engine:junit-jupiter]/[class:com.example.BTest]/[method:shouldWork()]")
                .filepath(CLASS_B).name("shouldWork()").status(FAILED).build(),
            Test.builder().testId("[engine:junit-jupiter]/[class:com.example.ATest]/[method:shouldWork()]")
                .filepath(CLASS_A).name("shouldWork()").status(PASSED).build(),
            Test.builder().testId(templates + "first(int)]/[test-template-invocation:#1]")
                .filepath(CLASS_A).name("[1] 1").status(FAILED).build(),
            Test.builder().testId(templates + "second(int)]/[test-template-invocation:#1]")
                .filepath(CLASS_A).name("[1] 1").status(PASSED).build()));

        assertThat(tests).hasSize(4).allSatisfy(test -> assertThat(test.getRetries()).isNull());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Entries without a testId fold by file path and name, apart from entries with a testId")
    void entriesWithoutTestIdFoldByFilePathAndNameOnly() {
        var legacyFailed = Test.builder().filepath(CLASS_A).name("test1()").status(FAILED).build();
        var legacyPassed = Test.builder().filepath(CLASS_A).name("test1()").status(PASSED).build();
        var current = attempt(TEST_1, "test1()", PASSED, 300L);

        var tests = AttemptFolder.fold(List.of(legacyFailed, legacyPassed, current));

        assertThat(tests).extracting(Test::getTestId, Test::getRetries, Test::getFlaky)
            .containsExactly(tuple(null, 1, true), tuple(TEST_1, null, null));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Entries without a testId or a file path are never folded by name alone")
    void entriesWithOnlyANameAreNeverFolded() {
        var tests = AttemptFolder.fold(List.of(
            Test.builder().name("test1()").status(FAILED).build(),
            Test.builder().name("test1()").status(PASSED).build()));

        assertThat(tests).hasSize(2).allSatisfy(test -> assertThat(test.getRetries()).isNull());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("An attempt without a status is kept and never makes a test flaky")
    void attemptWithoutStatusIsKept() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", null, 100L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Folding does not change the attempts it is given")
    void foldDoesNotChangeItsInput() {
        var failed = attempt(TEST_1, "test1()", FAILED, 100L);
        var passed = attempt(TEST_1, "test1()", PASSED, 200L);

        AttemptFolder.fold(List.of(failed, passed));

        assertThat(passed.getRetries()).isNull();
        assertThat(passed.getRetryAttempts()).isNull();
        assertThat(passed.getFlaky()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Unfolding gives the earlier attempts with the test's identity, then the final attempt")
    void unfoldGivesEarlierAttemptsThenTheFinalAttempt() {
        var test = Test.builder().testId(TEST_1).filepath(CLASS_A).name("test1()").tags(List.of("smoke"))
            .status(PASSED).start(300L).stop(305L).duration(5).threadId("worker-1")
            .retries(1).flaky(true)
            .retryAttempts(List.of(RetryAttempt.builder().attempt(1).status(FAILED).duration(7)
                .start(100L).stop(107L).message("boom").trace("trace of boom").build()))
            .build();

        var attempts = AttemptFolder.unfold(List.of(test));

        assertThat(attempts).containsExactly(
            Test.builder().testId(TEST_1).filepath(CLASS_A).name("test1()").tags(List.of("smoke"))
                .status(FAILED).duration(7).start(100L).stop(107L).message("boom").trace("trace of boom").build(),
            test.toBuilder().retries(null).flaky(null).retryAttempts(null).build());
        assertThat(test.getRetryAttempts()).as("the input is not changed").hasSize(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Unfolding keeps a test without retryAttempts as one attempt")
    void unfoldKeepsATestThatRanOnce() {
        var test = attempt(TEST_1, "test1()", PASSED, 100L);

        assertThat(AttemptFolder.unfold(List.of(test))).containsExactly(test);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Unfolding keeps the order of retryAttempts, and folding renumbers them from 1")
    void attemptsAreRenumberedInArrayOrder() {
        var test = attempt(TEST_1, "test1()", PASSED, 300L).toBuilder()
            .retries(2)
            .retryAttempts(List.of(
                RetryAttempt.builder().attempt(4).status(FAILED).start(100L).build(),
                RetryAttempt.builder().attempt(9).status(SKIPPED).start(200L).build()))
            .build();

        var refolded = AttemptFolder.fold(AttemptFolder.unfold(List.of(test)));

        assertThat(refolded).singleElement().satisfies(folded ->
            assertThat(folded.getRetryAttempts())
                .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus, RetryAttempt::getStart)
                .containsExactly(tuple(1, FAILED, 100L), tuple(2, SKIPPED, 200L)));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Folding what was unfolded gives the same tests")
    void foldUnfoldFoldIsStable() {
        var once = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_2, "test2()", PASSED, 150L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(AttemptFolder.fold(AttemptFolder.unfold(once))).isEqualTo(once);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A further retry round extends the history instead of nesting it")
    void aThirdRoundExtendsTheHistory() {
        var afterTwoRounds = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_1, "test1()", FAILED, 200L)));
        var attempts = new ArrayList<>(AttemptFolder.unfold(afterTwoRounds));
        attempts.add(attempt(TEST_1, "test1()", PASSED, 300L));

        var tests = AttemptFolder.fold(attempts);

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(2);
            assertThat(test.getRetryAttempts())
                .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus)
                .containsExactly(tuple(1, FAILED), tuple(2, FAILED));
            assertThat(test.getFlaky()).isTrue();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Entries of a 0.4.x report, one per attempt, fold into one test")
    void entriesOfAnOlderReportFoldIntoOneTest() {
        var first = attempt(TEST_1, "test1()", FAILED, 100L);
        var second = attempt(TEST_1, "test1()", PASSED, 200L).toBuilder().retries(1).flaky(true).build();

        var tests = AttemptFolder.fold(AttemptFolder.unfold(List.of(first, second)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isTrue();
            assertThat(test.getRetryAttempts()).extracting(RetryAttempt::getStatus).containsExactly(FAILED);
        });
    }

    private static Test attempt(String testId, String name, Test.TestStatus status, long start) {
        return Test.builder()
            .testId(testId)
            .filepath(CLASS_A)
            .name(name)
            .status(status)
            .start(start)
            .stop(start + 5)
            .duration(5)
            .build();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :test --tests 'io.github.alexshamrai.AttemptFolderTest'`

Expected: FAIL at `:compileTestJava` with "cannot find symbol: variable AttemptFolder".

- [ ] **Step 3: Implement `AttemptFolder`**

Create `src/main/java/io/github/alexshamrai/AttemptFolder.java`:

```java
package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;

/**
 * Turns attempts into CTRF test objects and back.
 * <p>
 * An attempt is one execution of a test, recorded as a {@link Test}. Attempts of the same test share its
 * {@code testId} (the JUnit unique ID). Entries from reports written before {@code testId} existed are
 * grouped by file path and name among themselves, never by name alone, and never joined to attempts that
 * have a {@code testId}.
 * <p>
 * Folding follows the CTRF specification (sections 9.20 to 9.22 and 11): the final attempt is the test
 * object, the earlier ones go into {@code retryAttempts}, numbered from 1 in the order they ran, and the
 * test is flaky only if its final attempt passed after a failed one.
 * <p>
 * This class is stateless and never modifies the objects it is given.
 */
final class AttemptFolder {

    private AttemptFolder() {
        // Utility class - prevent instantiation
    }

    /**
     * Folds attempts into one test object per test. Tests keep the order of their first attempt.
     *
     * @param attempts the attempts, in the order they ran
     * @return one test object per test: its final attempt, with {@code retries}, {@code retryAttempts}
     *         and {@code flaky} set from the earlier attempts
     */
    static List<Test> fold(List<Test> attempts) {
        Map<String, List<Test>> attemptsByTest = new LinkedHashMap<>();
        for (int i = 0; i < attempts.size(); i++) {
            Test attempt = attempts.get(i);
            String key = keyOf(attempt);
            attemptsByTest.computeIfAbsent(key != null ? key : "unmatched:" + i, k -> new ArrayList<>()).add(attempt);
        }

        List<Test> tests = new ArrayList<>(attemptsByTest.size());
        for (List<Test> testAttempts : attemptsByTest.values()) {
            tests.add(foldAttemptsOfOneTest(testAttempts));
        }
        return tests;
    }

    /**
     * Splits test objects back into their attempts, so that new attempts can be folded together with them,
     * for example in a retry round that reads the report of the earlier rounds.
     *
     * @param tests test objects, for example from an existing report
     * @return the attempts: for each test, its earlier attempts in the order of {@code retryAttempts}, then
     *         the test itself without {@code retries}, {@code retryAttempts} and {@code flaky}
     */
    static List<Test> unfold(List<Test> tests) {
        List<Test> attempts = new ArrayList<>(tests.size());
        for (Test test : tests) {
            if (test.getRetryAttempts() != null) {
                for (RetryAttempt earlier : test.getRetryAttempts()) {
                    attempts.add(toAttempt(test, earlier));
                }
            }
            attempts.add(test.toBuilder().retries(null).retryAttempts(null).flaky(null).build());
        }
        return attempts;
    }

    private static Test foldAttemptsOfOneTest(List<Test> testAttempts) {
        Test last = testAttempts.get(testAttempts.size() - 1);
        if (testAttempts.size() == 1) {
            return last.toBuilder().retries(null).retryAttempts(null).flaky(null).build();
        }

        List<Test> earlier = testAttempts.subList(0, testAttempts.size() - 1);
        List<RetryAttempt> retryAttempts = new ArrayList<>(earlier.size());
        for (int i = 0; i < earlier.size(); i++) {
            retryAttempts.add(toRetryAttempt(i + 1, earlier.get(i)));
        }
        boolean failedBefore = earlier.stream().anyMatch(attempt -> attempt.getStatus() == FAILED);

        return last.toBuilder()
            .retries(retryAttempts.size())
            .retryAttempts(retryAttempts)
            .flaky(last.getStatus() == PASSED && failedBefore ? Boolean.TRUE : null)
            .build();
    }

    private static RetryAttempt toRetryAttempt(int number, Test attempt) {
        return RetryAttempt.builder()
            .attempt(number)
            .status(attempt.getStatus())
            .duration(attempt.getDuration())
            .start(attempt.getStart())
            .stop(attempt.getStop())
            .message(attempt.getMessage())
            .trace(attempt.getTrace())
            .build();
    }

    private static Test toAttempt(Test test, RetryAttempt earlier) {
        return Test.builder()
            .testId(test.getTestId())
            .name(test.getName())
            .filepath(test.getFilepath())
            .tags(test.getTags())
            .status(earlier.getStatus())
            .duration(earlier.getDuration())
            .start(earlier.getStart())
            .stop(earlier.getStop())
            .message(earlier.getMessage())
            .trace(earlier.getTrace())
            .build();
    }

    /**
     * The attempts of one test share this key: its {@code testId}, or, for entries from reports written
     * before {@code testId} existed, its file path and name. An entry with neither is never folded.
     */
    private static String keyOf(Test attempt) {
        if (attempt.getTestId() != null) {
            return "testId:" + attempt.getTestId();
        }
        if (attempt.getFilepath() != null && attempt.getName() != null) {
            return "filepath:" + attempt.getFilepath() + "#" + attempt.getName();
        }
        return null;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.AttemptFolderTest'`

Expected: PASS (20 tests).

- [ ] **Step 5: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL. `AttemptFolder` isn't used yet; `FlakyTestDetector` still is.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/alexshamrai/AttemptFolder.java src/test/java/io/github/alexshamrai/AttemptFolderTest.java
git commit -m "feat: add AttemptFolder to fold attempts into CTRF tests and back (HP-6)"
```

---

### Task 3: Record attempts in O(1) and fold them when the run finishes

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/TestStateTracker.java` (whole file)
- Modify: `src/main/java/io/github/alexshamrai/CtrfReportManager.java` (class Javadoc, `onTestSkipped`, `processTestResult`, `startTestRun`, `finishTestRun`)
- Modify: `src/main/java/io/github/alexshamrai/TestRerunHandler.java` (class Javadoc list)
- Delete: `src/main/java/io/github/alexshamrai/FlakyTestDetector.java`, `src/test/java/io/github/alexshamrai/FlakyTestDetectorTest.java`
- Modify: `src/test/java/io/github/alexshamrai/TestStateTrackerTest.java`
- Modify: `src/test/java/io/github/alexshamrai/CtrfReportManagerTest.java`

**Interfaces:**
- Consumes: `AttemptFolder.fold(List<Test>)`, `AttemptFolder.unfold(List<Test>)` (Task 2); `RetryAttempt` (Task 1).
- Produces, on `TestStateTracker` (package-private):
  - `void addAttempt(Test)`;
  - `void addAllAttempts(List<Test>)`;
  - `List<Test> getAllAttempts()`, which returns a snapshot in insertion order;
  - `putTestDetails`, `removeTestDetails` and `clear()`, all unchanged.

  This replaces `addTest`, `addAllTests` and `getAllTests`. On `CtrfReportManager`, `finishTestRun()` now passes `AttemptFolder.fold(...)` to the report. Task 4 relies on this exact call text: `AttemptFolder.fold(stateTracker.getAllAttempts())`.

- [ ] **Step 1: Write the failing tests**

In `src/test/java/io/github/alexshamrai/TestStateTrackerTest.java`, make these changes:
- replace every `tracker.addTest(` with `tracker.addAttempt(`;
- replace every `tracker.addAllTests(` with `tracker.addAllAttempts(`;
- replace every `tracker.getAllTests()` with `tracker.getAllAttempts()`.

Then add these imports:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
```

and these tests inside the class:

```java
    @org.junit.jupiter.api.Test
    @DisplayName("Attempts added from many threads are all kept")
    void shouldKeepAttemptsAddedConcurrently() throws InterruptedException {
        int threads = 8;
        int perThread = 1_000;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    tracker.addAttempt(Test.builder().name("test").build());
                }
                return null;
            });
        }

        start.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(tracker.getAllAttempts()).hasSize(threads * perThread);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("getAllAttempts returns a copy that later attempts do not change")
    void getAllAttemptsReturnsASnapshot() {
        tracker.addAttempt(Test.builder().name("test1").build());

        var snapshot = tracker.getAllAttempts();
        tracker.addAttempt(Test.builder().name("test2").build());

        assertThat(snapshot).hasSize(1);
    }
```

In `src/test/java/io/github/alexshamrai/CtrfReportManagerTest.java`, delete the whole test method `processTestResult_setsRetryCount` (from its `@org.junit.jupiter.api.Test` line through its closing brace). Add these imports:

```java
import io.github.alexshamrai.ctrf.model.RetryAttempt;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
```

and add these tests and helpers inside the class:

```java
    @org.junit.jupiter.api.Test
    @DisplayName("finishTestRun reports the attempts of one test as one test with its earlier attempts")
    void finishTestRun_foldsAttemptsOfTheSameTest() {
        startRunWithExistingTests(List.of());
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("id-1").name("Rerun Test").start(1_000L).build(),
            Test.builder().testId("id-1").name("Rerun Test").start(2_000L).build());

        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestFailure("id-1", new RuntimeException("boom"));
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestSuccess("id-1");

        var tests = finishAndCaptureReportedTests();
        assertEquals(1, tests.size());
        var test = tests.get(0);
        assertEquals(PASSED, test.getStatus());
        assertEquals(1, test.getRetries());
        assertTrue(test.getFlaky());
        assertEquals(List.of(FAILED), test.getRetryAttempts().stream().map(RetryAttempt::getStatus).toList());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test from the existing report is unfolded, so a new attempt extends its history")
    void startTestRun_unfoldsTheExistingReportSoANewAttemptExtendsIt() {
        var fromEarlierRounds = Test.builder().testId("id-1").name("Rerun Test").status(FAILED).start(2_000L)
            .retries(1)
            .retryAttempts(List.of(RetryAttempt.builder().attempt(1).status(FAILED).start(1_000L).build()))
            .build();
        startRunWithExistingTests(List.of(fromEarlierRounds));
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong()))
            .thenReturn(Test.builder().testId("id-1").name("Rerun Test").start(3_000L).build());

        reportManager.onTestStart(new TestDetails(3_000L, Set.of(), null, "id-1", "Rerun Test"));
        reportManager.onTestSuccess("id-1");

        var tests = finishAndCaptureReportedTests();
        assertEquals(1, tests.size());
        var test = tests.get(0);
        assertEquals(PASSED, test.getStatus());
        assertEquals(2, test.getRetries());
        assertEquals(List.of(1_000L, 2_000L), test.getRetryAttempts().stream().map(RetryAttempt::getStart).toList());
        assertTrue(test.getFlaky());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A second test plan execution in the same JVM does not report the first one's attempts again")
    void finishTestRun_doesNotRepeatAttemptsOfAnEarlierExecution() {
        when(testProcessor.createTest(anyString(), any(TestDetails.class), anyLong())).thenReturn(
            Test.builder().testId("id-1").name("First").build(),
            Test.builder().testId("id-2").name("Second").build());
        startRunWithExistingTests(List.of());
        reportManager.onTestStart(new TestDetails(1_000L, Set.of(), null, "id-1", "First"));
        reportManager.onTestSuccess("id-1");
        finishAndCaptureReportedTests();

        reportManager.startTestRun("Listener");
        reportManager.onTestStart(new TestDetails(2_000L, Set.of(), null, "id-2", "Second"));
        reportManager.onTestSuccess("id-2");

        var tests = finishAndCaptureReportedTests();
        assertEquals(List.of("id-2"), tests.stream().map(Test::getTestId).toList());
    }

    private void startRunWithExistingTests(List<Test> existingTests) {
        when(ctrfReportFileService.getExistingTests()).thenReturn(existingTests);
        when(ctrfReportFileService.getExistingEnvironmentHealth()).thenReturn(true);
        reportManager.startTestRun("Listener");
    }

    @SuppressWarnings("unchecked")
    private List<Test> finishAndCaptureReportedTests() {
        when(ctrfJsonComposer.generateCtrfJson(any(Summary.class), anyList(), anyBoolean()))
            .thenReturn(CtrfJson.builder().build());
        reportManager.finishTestRun();

        ArgumentCaptor<List<Test>> testsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ctrfJsonComposer, atLeastOnce()).generateCtrfJson(any(Summary.class), testsCaptor.capture(), anyBoolean());
        return testsCaptor.getValue();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :test --tests 'io.github.alexshamrai.TestStateTrackerTest' --tests 'io.github.alexshamrai.CtrfReportManagerTest'`

Expected: FAIL at `:compileTestJava` with "cannot find symbol: method addAttempt" (and `getAllAttempts`, `addAllAttempts`).

- [ ] **Step 3: Rewrite `TestStateTracker`**

Replace the whole content of `src/main/java/io/github/alexshamrai/TestStateTracker.java` with:

```java
package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages test execution state during a test run.
 * <p>
 * It collects attempts, one per execution of a test, and the details of tests in progress. Attempts are
 * folded into one test object per test only when the report is written (see {@link AttemptFolder}), so
 * recording one costs O(1):
 * <ul>
 *   <li>{@link ConcurrentLinkedQueue} for attempts, in the order they finished</li>
 *   <li>{@link ConcurrentHashMap} for in-progress test details</li>
 * </ul>
 * <p>
 * Thread-safe: All operations use concurrent collections without additional synchronization.
 */
final class TestStateTracker {

    private final Queue<Test> attempts = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, TestDetails> testDetailsMap = new ConcurrentHashMap<>();

    /**
     * Records a finished attempt of a test.
     *
     * @param attempt the attempt's result
     */
    void addAttempt(Test attempt) {
        attempts.add(attempt);
    }

    /**
     * Records several attempts, for example those of earlier retry rounds read from an existing report.
     *
     * @param attemptsToAdd the attempts, in the order they ran
     */
    void addAllAttempts(List<Test> attemptsToAdd) {
        attempts.addAll(attemptsToAdd);
    }

    /**
     * Returns a snapshot of the recorded attempts, in the order they were recorded.
     *
     * @return a copy of all recorded attempts
     */
    List<Test> getAllAttempts() {
        return new ArrayList<>(attempts);
    }

    /**
     * Stores test details for an in-progress test.
     *
     * @param uniqueId the unique identifier for the test
     * @param details  the test details to store
     */
    void putTestDetails(String uniqueId, TestDetails details) {
        testDetailsMap.put(uniqueId, details);
    }

    /**
     * Retrieves and removes test details for a completed test.
     *
     * @param uniqueId the unique identifier for the test
     * @return the test details, or null if not found
     */
    TestDetails removeTestDetails(String uniqueId) {
        return testDetailsMap.remove(uniqueId);
    }

    /**
     * Clears all test state.
     * <p>
     * This is called after report generation to prepare for the next test run.
     */
    void clear() {
        attempts.clear();
        testDetailsMap.clear();
    }
}
```

- [ ] **Step 4: Wire folding into `CtrfReportManager`**

In `src/main/java/io/github/alexshamrai/CtrfReportManager.java`:

In the class Javadoc, replace

```java
 *   <li>{@link FlakyTestDetector} - detects flaky tests based on retry patterns</li>
```

with

```java
 *   <li>{@link AttemptFolder} - folds the attempts of each test into one test object</li>
```

In `onTestSkipped`, replace `        stateTracker.addTest(test);` with `        stateTracker.addAttempt(test);`.

In `processTestResult`, replace

```java
        FlakyTestDetector.detectAndMarkFlaky(newTest, stateTracker.getAllTests());
        stateTracker.addTest(newTest);
```

with

```java
        stateTracker.addAttempt(newTest);
```

In `startTestRun`, replace

```java
            // Load existing tests from previous run (if any)
            stateTracker.addAllTests(rerunHandler.getFileService().getExistingTests());
```

with

```java
            // Attempts of earlier retry rounds, from the existing report: unfolded, so that this run's
            // attempts extend each test's history when the report is folded again
            stateTracker.addAllAttempts(AttemptFolder.unfold(rerunHandler.getFileService().getExistingTests()));
```

In `finishTestRun`, replace

```java
        reportOrchestrator.generateAndWriteReport(
            stateTracker.getAllTests(),
```

with

```java
        reportOrchestrator.generateAndWriteReport(
            AttemptFolder.fold(stateTracker.getAllAttempts()),
```

- [ ] **Step 5: Update the `TestRerunHandler` Javadoc**

In `src/main/java/io/github/alexshamrai/TestRerunHandler.java`, replace

```java
 *   <li>Detect flaky tests based on retry patterns</li>
```

with

```java
 *   <li>Fold attempts of earlier retry rounds into the same tests (see {@link AttemptFolder})</li>
```

- [ ] **Step 6: Delete `FlakyTestDetector` and its test**

```bash
git rm src/main/java/io/github/alexshamrai/FlakyTestDetector.java src/test/java/io/github/alexshamrai/FlakyTestDetectorTest.java
grep -rn 'FlakyTestDetector\|getAllTests\|addAllTests\|stateTracker.addTest' src/ || echo "no references left"
```

Expected: `no references left`. Its cases now live in `AttemptFolderTest`, from Task 2: matching by `testId`, never by name alone, and class failures across phases.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.TestStateTrackerTest' --tests 'io.github.alexshamrai.CtrfReportManagerTest'`

Expected: PASS.

- [ ] **Step 8: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL, including checkstyle and the existing scenario tests. If a scenario test fails, read its assertion before changing anything. Scenarios run each fixture once, so they shouldn't change.

- [ ] **Step 9: Commit**

```bash
git add -A src/main/java/io/github/alexshamrai src/test/java/io/github/alexshamrai/TestStateTrackerTest.java src/test/java/io/github/alexshamrai/CtrfReportManagerTest.java
git status --short
git commit -m "feat: record attempts in O(1) and fold retries when the run finishes (HP-6)"
```

Expected `git status --short` before committing: the modified and deleted files above, and only `?? ctrf-report.json` untracked.

---

### Task 4: Scenario tests for retry rounds in new JVMs

**Files:**
- Modify: `src/test/java/io/github/alexshamrai/scenario/ScenarioRunner.java` (`runWithListener`, `runWithExtension`, `run`; add two methods)
- Create: `src/test/java/io/github/alexshamrai/scenario/fixtures/RetryRoundsFixture.java`
- Create: `src/test/java/io/github/alexshamrai/scenario/fixtures/ExtRetryRoundsFixture.java`
- Create: `src/test/java/io/github/alexshamrai/scenario/RetryScenarioTest.java`

**Interfaces:**
- Consumes: the folding wired in Task 3, through the line `AttemptFolder.fold(stateTracker.getAllAttempts())` in `CtrfReportManager.finishTestRun`.
- Produces:
  - `ScenarioRunner.runRoundsWithListener(int rounds, Class<?>... fixtures)`;
  - `ScenarioRunner.runRoundsWithExtension(int rounds, Class<?>... fixtures)`.

  Each runs `rounds` JVMs one after another, sharing the working directory and report file, and returns the last round's `Result`.

- [ ] **Step 1: Let `ScenarioRunner` run several rounds**

In `src/test/java/io/github/alexshamrai/scenario/ScenarioRunner.java`, replace the methods `runWithListener`, `runWithExtension` and `run` (everything from `static Result runWithListener` up to, not including, `private static Map<String, Long> junitCounts`) with:

```java
    static Result runWithListener(Class<?>... fixtures) throws IOException, InterruptedException {
        return run("listener", 1, fixtures);
    }

    static Result runWithExtension(Class<?>... fixtures) throws IOException, InterruptedException {
        return run("extension", 1, fixtures);
    }

    /**
     * Runs the fixtures {@code rounds} times through the listener, each round in a new JVM that shares the
     * working directory and the report file with the earlier rounds, as the Gradle test-retry plugin does.
     */
    static Result runRoundsWithListener(int rounds, Class<?>... fixtures) throws IOException, InterruptedException {
        return run("listener", rounds, fixtures);
    }

    /**
     * Like {@link #runRoundsWithListener}, reporting only through the fixtures' own extensions.
     */
    static Result runRoundsWithExtension(int rounds, Class<?>... fixtures) throws IOException, InterruptedException {
        return run("extension", rounds, fixtures);
    }

    private static Result run(String mode, int rounds, Class<?>... fixtures) throws IOException, InterruptedException {
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

        String log = "";
        for (int round = 1; round <= rounds; round++) {
            var processBuilder = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile());
            processBuilder.environment().remove("ENV_HEALTHY");
            Process process = processBuilder.start();
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("Scenario JVM timed out in round " + round + ":\n" + Files.readString(output));
            }
            log = Files.readString(output);
            if (process.exitValue() != 0 || !Files.exists(report)) {
                throw new IllegalStateException("Scenario JVM exited with " + process.exitValue()
                    + " in round " + round + " and wrote no report:\n" + log);
            }
        }
        return new Result(new ObjectMapper().readValue(report.toFile(), CtrfJson.class), junitCounts(log), log);
    }
```

- [ ] **Step 2: Add the fixtures**

Create `src/test/java/io/github/alexshamrai/scenario/fixtures/RetryRoundsFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for retry rounds: one fails only in the first round in a working directory, one fails in every round.
 */
@Tag("scenario-fixture")
public class RetryRoundsFixture {

    @Test
    void passesFromTheSecondRound() throws IOException {
        Path marker = Path.of("first-round-done.marker");
        if (Files.notExists(marker)) {
            Files.createFile(marker);
            fail("fails in the first round");
        }
    }

    @Test
    void alwaysFails() {
        fail("fails in every round");
    }
}
```

Create `src/test/java/io/github/alexshamrai/scenario/fixtures/ExtRetryRoundsFixture.java`:

```java
package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The same as {@link RetryRoundsFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtRetryRoundsFixture {

    @Test
    void passesFromTheSecondRound() throws IOException {
        Path marker = Path.of("first-round-done.marker");
        if (Files.notExists(marker)) {
            Files.createFile(marker);
            fail("fails in the first round");
        }
    }

    @Test
    void alwaysFails() {
        fail("fails in every round");
    }
}
```

- [ ] **Step 3: Write the scenario tests**

Create `src/test/java/io/github/alexshamrai/scenario/RetryScenarioTest.java`:

```java
package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.ExtRetryRoundsFixture;
import io.github.alexshamrai.scenario.fixtures.RetryRoundsFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Retry rounds in new JVMs, as the Gradle test-retry plugin runs them, fold into one entry per test (HP-6).
 */
class RetryScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerFoldsRetryRoundsIntoOneEntryPerTest() throws Exception {
        assertFoldedAfterTwoRounds(ScenarioRunner.runRoundsWithListener(2, RetryRoundsFixture.class), RetryRoundsFixture.class);
    }

    @org.junit.jupiter.api.Test
    void extensionFoldsRetryRoundsIntoOneEntryPerTest() throws Exception {
        assertFoldedAfterTwoRounds(ScenarioRunner.runRoundsWithExtension(2, ExtRetryRoundsFixture.class), ExtRetryRoundsFixture.class);
    }

    @org.junit.jupiter.api.Test
    void listenerExtendsTheHistoryInAThirdRound() throws Exception {
        var result = ScenarioRunner.runRoundsWithListener(3, RetryRoundsFixture.class);

        assertThat(result.testsOf(RetryRoundsFixture.class)).hasSize(2);
        assertThat(onlyTestNamed(result, "alwaysFails()").getRetryAttempts())
            .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus)
            .containsExactly(tuple(1, FAILED), tuple(2, FAILED));
        Test flaky = onlyTestNamed(result, "passesFromTheSecondRound()");
        assertThat(flaky.getRetries()).isEqualTo(2);
        assertThat(flaky.getRetryAttempts())
            .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus)
            .containsExactly(tuple(1, FAILED), tuple(2, PASSED));
        assertThat(flaky.getFlaky()).isTrue();
    }

    private static void assertFoldedAfterTwoRounds(ScenarioRunner.Result result, Class<?> fixture) {
        assertThat(result.testsOf(fixture))
            .extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("passesFromTheSecondRound()", PASSED), tuple("alwaysFails()", FAILED));

        Test flaky = onlyTestNamed(result, "passesFromTheSecondRound()");
        assertThat(flaky.getRetries()).isEqualTo(1);
        assertThat(flaky.getFlaky()).isTrue();
        assertThat(flaky.getRetryAttempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getAttempt()).isEqualTo(1);
            assertThat(attempt.getStatus()).isEqualTo(FAILED);
            assertThat(attempt.getMessage()).contains("fails in the first round");
        });

        Test failing = onlyTestNamed(result, "alwaysFails()");
        assertThat(failing.getRetries()).isEqualTo(1);
        assertThat(failing.getFlaky()).isNull();
        assertThat(failing.getRetryAttempts()).extracting(RetryAttempt::getStatus).containsExactly(FAILED);

        var summary = result.report().getResults().getSummary();
        assertThat(summary.getTests()).isEqualTo(2);
        assertThat(summary.getPassed()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getFlaky()).isEqualTo(1);
    }

    private static Test onlyTestNamed(ScenarioRunner.Result result, String name) {
        var matches = result.tests().stream().filter(test -> name.equals(test.getName())).toList();
        assertThat(matches).as("entries named %s", name).hasSize(1);
        return matches.get(0);
    }
}
```

- [ ] **Step 4: Run the scenarios to verify they pass**

Run: `./gradlew :test --tests 'io.github.alexshamrai.scenario.RetryScenarioTest'`

Expected: PASS (3 tests).

- [ ] **Step 5: Prove the scenarios fail without folding**

Temporarily report the raw attempts, then restore the file:

```bash
sed -i '' 's/AttemptFolder.fold(stateTracker.getAllAttempts())/stateTracker.getAllAttempts()/' src/main/java/io/github/alexshamrai/CtrfReportManager.java
grep -n 'stateTracker.getAllAttempts()' src/main/java/io/github/alexshamrai/CtrfReportManager.java
./gradlew :test --tests 'io.github.alexshamrai.scenario.RetryScenarioTest' 2>&1 | grep -E '\) FAILED|BUILD'
git checkout -- src/main/java/io/github/alexshamrai/CtrfReportManager.java
git diff --stat -- src/main
```

Expected:
- `grep` shows the call without `AttemptFolder.fold`;
- all 3 scenarios FAIL, with 4 entries for 2 tests in the two-round runs;
- after the checkout, `git diff --stat -- src/main` prints nothing.

- [ ] **Step 6: Run the whole build**

Run: `./gradlew clean :build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/io/github/alexshamrai/scenario
git commit -m "test: scenario tests for retry rounds in new JVMs (HP-6)"
```

---

### Task 5: Integration validator

**Files:**
- Modify: `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java` (imports, `verifySummaryIsCorrect`, `verifyFlakyTestContainsValidData`, `verifyMultipleInitializationErrorsAreCaptured`; add 4 checks and a helper)

**Interfaces:**
- Consumes: `Test.getRetryAttempts()`, `RetryAttempt`, `Summary.getFlaky()` (Task 1); the folded reports (Task 3).
- Produces: nothing used by later tasks.

The bundled schema (`integration-ctrf-validator/src/test/resources/schema/ctrf-schema.json`) has no `additionalProperties: false` on the test or summary objects. So it already accepts `retryAttempts` and `summary.flaky`; leave it to HP-5/TI-4.

- [ ] **Step 1: Update the validator**

In `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`, replace the imports block

```java
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
```

with

```java
import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
```

In `verifySummaryIsCorrect`, replace everything from the line `        // Both modules: 24 entries from the shared fake tests, plus AfterAllFailureTest's passing test` through the closing `        }` of the `if (isListenerReport()) { ... } else { ... }` block with:

```java
        // Each test appears once, as its final attempt: the test-retry plugin's second round is folded into
        // retryAttempts. Both modules: the shared fake tests, AfterAllFailureTest's passing test and its
        // teardownError, and AssumptionTest's aborted test, which counts as skipped. The listener module also
        // reports BrokenSourceTest's two failed containers, and the four tests of DisabledClassTest and
        // ConditionallySkippedClassTest as skipped.
        if (isListenerReport()) {
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
```

Replace the whole method `verifyFlakyTestContainsValidData` with:

```java
    @Test
    void verifyFlakyTestContainsValidData() {
        var flakyTest = singleTestNamed("Flaky test passed on the second run");

        assertThat(flakyTest.getStatus()).isEqualTo(TestStatus.PASSED);
        assertThat(flakyTest.getFlaky()).isTrue();
        assertThat(flakyTest.getRetries()).isEqualTo(1);
        assertThat(flakyTest.getRetryAttempts())
            .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus)
            .containsExactly(tuple(1, TestStatus.FAILED));
    }
```

In `verifyMultipleInitializationErrorsAreCaptured`, replace

```java
        // Should have entries from both test classes (with retry, 4 total)
        assertThat(initErrorTests)
            .as("Should have multiple initializationError entries from different test classes")
            .hasSizeGreaterThanOrEqualTo(2);
```

with

```java
        // One entry per class: the retry round is folded into retryAttempts
        assertThat(initErrorTests)
            .as("Should have one initializationError entry for each of the two test classes")
            .hasSize(2);
```

Add these checks and the helper before the `isListenerReport()` helper, at the end of the class:

```java
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
    void verifyTestsFailingInEveryRoundAreNotFlaky() {
        for (String name : List.of("firstFailedTest()", "Second failed test")) {
            var test = singleTestNamed(name);
            assertThat(test.getStatus()).as("status of %s", name).isEqualTo(TestStatus.FAILED);
            assertThat(test.getFlaky()).as("flaky of %s", name).isNull();
            assertThat(test.getRetryAttempts())
                .extracting(RetryAttempt::getStatus)
                .as("earlier attempts of %s", name)
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

    private io.github.alexshamrai.ctrf.model.Test singleTestNamed(String name) {
        var matches = report.getResults().getTests().stream()
            .filter(test -> name.equals(test.getName()))
            .toList();
        assertThat(matches).as("entries named %s", name).hasSize(1);
        return matches.get(0);
    }
```

- [ ] **Step 2: Run both integration modules and the validator**

```bash
P='-Dthreads=2 -Dctrf.build.name=system-build -Dctrf.build.number=777 -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678'
for m in listener extension; do
  echo "=== ${m}"
  if [ "${m}" = listener ]; then export ENV_HEALTHY=false; else unset ENV_HEALTHY; fi
  ./gradlew clean :integration-tests-${m}:test ${=P} -q 2>&1 | grep -E 'tests completed' | tail -1
  python3 -c "import json;s=json.load(open('integration-tests-${m}/build/test-results/ctrf-report.json'))['results']['summary'];print({k:s[k] for k in ('tests','passed','failed','skipped','flaky')})"
  ./gradlew :integration-ctrf-validator:test -Dctrf.report.path=../integration-tests-${m}/build/test-results/ctrf-report.json ${=P} 2>&1 | grep -E 'FAILED|BUILD' | tail -5
done
unset ENV_HEALTHY
```

Expected:
- **Listener:** Gradle still prints `37 tests completed, 15 failed, 7 skipped`, because it counts attempts. The report summary is `{'tests': 28, 'passed': 14, 'failed': 7, 'skipped': 7, 'flaky': 1}`, and the validator says BUILD SUCCESSFUL.
- **Extension:** Gradle prints `29 tests completed, 11 failed, 3 skipped`. The report summary is `{'tests': 22, 'passed': 14, 'failed': 5, 'skipped': 3, 'flaky': 1}`, and the validator says BUILD SUCCESSFUL, with the two listener-only checks skipped.

If the numbers differ, don't edit the expected values. Group the report's entries by `testId` and compare each group with the fake tests to find out why first.

- [ ] **Step 3: Prove the new checks fail on an unfolded report**

```bash
sed -i '' 's/AttemptFolder.fold(stateTracker.getAllAttempts())/stateTracker.getAllAttempts()/' src/main/java/io/github/alexshamrai/CtrfReportManager.java
export ENV_HEALTHY=false
./gradlew clean :integration-tests-listener:test -Dthreads=2 -q 2>&1 | grep -E 'tests completed' | tail -1
./gradlew :integration-ctrf-validator:test -Dctrf.report.path=../integration-tests-listener/build/test-results/ctrf-report.json 2>&1 | grep -E '\) FAILED' 
unset ENV_HEALTHY
git checkout -- src/main/java/io/github/alexshamrai/CtrfReportManager.java
git diff --stat -- src/main
```

Expected:
- **These fail:** `verifySummaryIsCorrect`, `verifyFlakyTestContainsValidData`, `verifyMultipleInitializationErrorsAreCaptured`, `verifyEachTestAppearsOnce` and `verifyTestsFailingInEveryRoundAreNotFlaky`.
- **These still pass:** `verifySummaryCountsFlakyTests` (0 flaky tests and `summary.flaky` 0) and `verifyRetryFieldsAreConsistent` (no test has retry fields).
- **This also fails:** `verifyBuildParametersFromSystemProperties`, because the build flags weren't passed. That one is unrelated to HP-6.
- **After the checkout:** `git diff --stat -- src/main` prints nothing.

- [ ] **Step 4: Commit**

```bash
git add integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java
git commit -m "test: validate folded retries in integration reports (HP-6)"
```

---

### Task 6: Documentation

**Files:**
- Modify: `README.md` (new section before `## Contributing`)
- Modify: `CLAUDE.md` (data flow, component table, concurrency, file tree, "Flaky Test Detection", "Rerun Support")

**Interfaces:** none.

- [ ] **Step 1: Add the README section**

In `README.md`, insert this directly before the line `## Contributing`:

````markdown
## Retries and Flaky Tests

When a failed test is retried, for example by the Gradle [test-retry plugin](https://github.com/gradle/test-retry-gradle-plugin), the report contains the test once. The test object is its final attempt, and earlier attempts are listed in `retryAttempts`, as the CTRF specification defines:

```json
{
  "name": "shouldProcessPayment()",
  "status": "passed",
  "retries": 1,
  "flaky": true,
  "retryAttempts": [
    { "attempt": 1, "status": "failed", "duration": 5, "message": "org.opentest4j.AssertionFailedError: ..." }
  ]
}
```

- `retries` is the number of earlier attempts. A test that ran once has no `retries` or `retryAttempts`.
- A test is `flaky` only if its final attempt passed after at least one failed attempt.
- The summary counts each test once, by its final attempt, and `summary.flaky` counts flaky tests. A test that failed and then passed on retry counts as passed.

Gradle's console output counts every attempt, so its numbers are higher than the report's when tests are retried. The report's `tests` matches the number of `<testcase>` elements in Gradle's JUnit XML with `reports.junitXml.mergeReruns = true`.

Retries that JUnit runs as separate tests with their own unique IDs, such as JUnit Pioneer's `@RetryingTest`, appear as separate tests.

````

- [ ] **Step 2: Update `CLAUDE.md`**

Make these replacements in `CLAUDE.md`, each one exact:

1. `    ├─ Delegates to FlakyTestDetector for flaky test detection` becomes `    ├─ Delegates to AttemptFolder to fold retry attempts into one test`.
2. In the component table, the `**TestStateTracker**` row's description `Manages test state collections (CopyOnWriteArrayList, ConcurrentHashMap); thread-safe test storage` becomes `Collects attempts (ConcurrentLinkedQueue) and in-progress test details (ConcurrentHashMap)`.
3. The row `| **FlakyTestDetector**           | Stateless utility for detecting flaky tests based on retry patterns and previous failures             |` becomes:
   `| **AttemptFolder**               | Stateless; folds attempts into one test per testId (retries, retryAttempts, flaky) and unfolds a report's tests back into attempts |`
4. ``- `CopyOnWriteArrayList<Test>` for test results (read-heavy workload, optimized for concurrent reads)`` becomes ``- `ConcurrentLinkedQueue<Test>` for attempts (O(1) adds from any thread; folded once when the run finishes)``.
5. `**FlakyTestDetector** is stateless with pure functions, making it inherently thread-safe.` becomes `**AttemptFolder** is stateless and runs once, in finishTestRun, on a snapshot of the attempts.`
6. `├── FlakyTestDetector.java         # Detects flaky tests` becomes `├── AttemptFolder.java             # Folds retry attempts into one test`.
7. Replace the section

```markdown
### Flaky Test Detection

A test is marked as `flaky: true` only if it passed after at least one failed attempt. Earlier attempts are matched by `testId` (the JUnit unique ID); entries from older reports without a `testId` fall back to file path and name, never to the name alone.
```

with

```markdown
### Retries and Flaky Tests

Each test appears once in the report, as its final attempt. Earlier attempts with the same `testId` (the JUnit unique ID) go into `retryAttempts`, numbered from 1, and `retries` is their count. A test is marked as `flaky: true` only if its final attempt passed after at least one failed attempt; `summary.flaky` counts such tests. Entries from older reports without a `testId` are grouped by file path and name among themselves, never by name alone, and never joined to entries that have a `testId`.
```

8. In "Rerun Support", after the sentence ``CtrfReportFileService` reads existing reports on startup, allowing incremental report building across multiple test runs.``, append: ` Their tests are unfolded into attempts, so a retry round in a new JVM extends each test's `retryAttempts`.`

Then check:

```bash
grep -n 'FlakyTestDetector\|CopyOnWriteArrayList' CLAUDE.md README.md || echo "no stale references"
```

Expected: `no stale references`.

- [ ] **Step 3: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: describe folded retries and flaky tests (HP-6)"
```

---

### Task 7: Final verification, performance, squash and PR

**Files:**
- Create (outside the repo, not committed): `$B/Mp3Bench.java`, `$B/print-cp.gradle`, where `B=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/hp6-bench`, or any scratch directory outside the repo.
- Create (not committed): the PR description file `$B/../hp6-pr-body.md`.

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

Expected: BUILD SUCCESSFUL and `failures 0`. Write down the test count for the PR description.

Then repeat Task 5 Step 2 (both integration modules plus the validator), and run the compatibility matrix:

```bash
compat/run.sh 5.10.5 5.14.4 6.1.3 2>&1 | tail -2
```

Expected: `all compatibility checks passed` (12 of 12).

- [ ] **Step 2: Measure event processing before and after (MP-3)**

```bash
B=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/hp6-bench
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
cat > $B/Mp3Bench.java <<'EOF'
import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.model.TestDetails;

import java.util.Set;

public class Mp3Bench {
    public static void main(String[] args) {
        int count = Integer.parseInt(args[0]);
        var manager = CtrfReportManager.getInstance();
        manager.startTestRun("bench");
        long started = System.nanoTime();
        for (int i = 0; i < count; i++) {
            String id = "[engine:junit-jupiter]/[class:Bench]/[method:test" + i + "()]";
            manager.onTestStart(new TestDetails(System.currentTimeMillis(), Set.of(), "Bench", id, "test" + i + "()"));
            manager.onTestSuccess(id);
        }
        long recorded = System.nanoTime();
        manager.finishTestRun();
        long finished = System.nanoTime();
        System.out.printf("tests=%d events=%.2fs finish=%.2fs%n", count, (recorded - started) / 1e9, (finished - recorded) / 1e9);
    }
}
EOF
bench() {  # $1 = checkout to measure, $2 = label
  local cp=$(cd "$1" && ./gradlew -q -I $B/print-cp.gradle printBenchClasspath | grep '^CP=' | tail -1 | cut -c4-)
  for n in 10000 20000 40000 80000; do
    rm -f $B/bench-$2-$n.json
    echo -n "$2 "; java -Xmx2g -Dctrf.report.path=$B/bench-$2-$n.json -cp "$cp" $B/Mp3Bench.java $n
  done
}
git worktree add -q $B/master-wt origin/master
bench $B/master-wt master
bench . hp6
git worktree remove --force $B/master-wt
```

Expected: on `master`, `events` grows faster than linearly; MP-3 measured 0.4 s, 1.1 s, 5.9 s and 47.9 s for 10k to 80k on a different machine. On `hp6`, `events` and `finish` grow about linearly, and 80k takes a small fraction of master's time. Write down both tables for the PR description. If `hp6` isn't roughly linear, stop and investigate before continuing.

- [ ] **Step 3: Squash into one commit**

If `origin/master` moved since Task 1 (for example the naming follow-up merged), rebase first. Squashing onto a newer `origin/master` without rebasing would silently revert what was merged:

```bash
git fetch origin
git rebase origin/master
```

Expected: "Current branch fix/hp-6-fold-retry-attempts is up to date", or a clean rebase. If the rebase brought in new commits, repeat Step 1 before squashing. If it stops with conflicts, resolve them so that both master's change and HP-6 survive, then repeat Step 1.

```bash
git status --short
git reset --soft origin/master
git commit -F - <<'EOF'
fix: report each test once, folding retries into retryAttempts (HP-6)

Every attempt of a retried test was a separate entry. A build that is
green after retries still had summary.failed > 0, so CTRF consumers showed
the run as failed, and the second copy carried retries: 1 without
retryAttempts, which the CTRF spec forbids. Every result also scanned all
earlier results and copied the whole result list (MP-3).

Each test now appears once, as its final attempt. Earlier attempts go into
retryAttempts, numbered from 1; retries is their count; flaky is set only
when the final attempt passed after a failed one; summary.flaky counts
flaky tests. Attempts are recorded in O(1) and folded once when the run
finishes, by AttemptFolder, which replaces FlakyTestDetector. Tests read
from an existing report are unfolded first, so a retry round in a new JVM
extends each test's history.

Tests: AttemptFolder unit tests, manager and state tracker tests,
forked-JVM scenarios with two and three retry rounds through the listener
and the extension, and integration validator checks.
EOF
git log --oneline -2
git show --stat --format= HEAD
```

Expected: one commit on top of `origin/master`, with no `Co-Authored-By` line. `git status --short` before the reset shows only `?? ctrf-report.json`.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin fix/hp-6-fold-retry-attempts
```

- [ ] **Step 5: Write the PR description**

Write `$B/../hp6-pr-body.md` with these sections, filled from this plan and the numbers written down in Steps 1 and 2. End it without any "Generated with Claude Code" line.

- **What:** each test appears once, as its final attempt, with `retryAttempts`, and the summary gains `flaky`.
- **Why:**
  - the false red, where `summary.failed > 0` on a build that is green after retries;
  - the CTRF rule "`retries` > 0 requires `retryAttempts`", which the current output breaks;
  - Gradle's `mergeReruns` and Surefire both fold retries the same way;
  - MP-3.
- **Changes:** `RetryAttempt`, `Test.retryAttempts`, `Summary.flaky`, `AttemptFolder` (fold/unfold), the queue in `TestStateTracker`, and `FlakyTestDetector` removed.
- **What changes in the report:** a table of entries before and after.

  | Report | Before | After |
  |---|---|---|
  | Listener | 37 | 28 tests: 14 passed, 7 failed, 7 skipped, flaky 1 |
  | Extension | 29 | 22 tests: 14 passed, 5 failed, 3 skipped, flaky 1 |

  Add a note that this ships with Phase 2 as 0.5.0.
- **Tests:** the unit, scenario and validator tests added, and that the scenarios and new validator checks fail without folding.
- **Verified locally:** the build test count, both integration runs, compat 12 of 12, and both benchmark tables.
- **Not in this PR:**
  - shard merging and same-JVM reruns (CB-6);
  - `filePath` and the official schema (HP-5);
  - Pioneer-style retries with new unique IDs, which stay separate tests.

The user opens the PR from the browser as `alexshamrai`, because `gh` is logged in as an account that can't create PRs on this repo:
- URL: https://github.com/alexshamrai/junit-ctrf-reporter/compare/master...fix/hp-6-fold-retry-attempts?expand=1
- Title: `fix: report each test once, folding retries into retryAttempts (HP-6)`

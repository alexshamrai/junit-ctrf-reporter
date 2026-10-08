# JUnit CTRF Reporter - Comprehensive Improvement Plan

This document provides a detailed, actionable plan for addressing code quality issues, bugs, and architectural improvements identified in the codebase review.

> **Updated 2026-10-08** after a second, behaviour-focused review of `master` @ `69fa713` (0.4.5 plus dependency updates). Read [Review Update — 2026-10-08](#review-update--2026-10-08) first. It records which original items are already done on `master`, and adds 18 new items for defects that were reproduced by running them, not just inferred from the code.

## How to Use This Plan

Each improvement item includes:
- **Priority**: Critical/High/Medium/Low
- **Complexity**: Simple/Moderate/Complex
- **Prompt**: Detailed instructions for implementation
- **Acceptance Criteria**: How to verify the fix is complete
- **Files Affected**: Which files need changes
- **Status** (added 2026-10-08): Done / Partially done / Open / Re-evaluated / Superseded / Stale, relative to `master`

---

## Review Update — 2026-10-08

Scope: `master` @ `69fa713`. This branch is based on v0.4.3, so file paths and line numbers in items added or updated on this date refer to `master`, not to the code on this branch.

### How the findings were verified

Each new item was reproduced, not inferred from reading:
- A launcher-based harness (`LauncherFactory` with an explicitly registered `CtrfListener` and deterministic class order) ran small fake test classes on JUnit 6.1.3. Each CTRF report was compared with JUnit's own `SummaryGeneratingListener` counts.
- A real Gradle run of `:integration-tests-listener:test` on a copy of the repository, with `maxParallelForks` 1 and 2.
- A minimal Maven consumer (JUnit 5.11.4 + `junit-ctrf-reporter:0.4.5` from Maven Central), and a Gradle consumer resolving `testRuntimeClasspath`.
- Generated reports validated against the official CTRF schema (`ctrf-io/ctrf`, `schema/ctrf.schema.json`, spec 0.1.0 released 2026-10-03) with a draft-07 validator.
- Event throughput measured by driving `CtrfReportManager` through its public API.
- Parallel stress runs (1,000 tests on 8–9 threads) with only the listener and with only the extension (CB-2).

All 131 unit tests pass on `master`, and none of them cover these defects. TI-5 turns the scenarios into regression tests.

### Status of the original items on `master`

| Item | Status | Note |
|---|---|---|
| CB-1 NPE in `getExistingTests()` | Done | `cbd61a4` |
| CB-2 Nested run ends the outer run (was: race in `finishTestRun()`) | Re-scoped, lowered to Medium | The thread race isn't reachable; a nested JUnit run ending the outer run is (verified) |
| CB-3 Multiple `ConfigReader`s | Done | `030842d` |
| HP-1 Singleton | Open | Simplified by HP-10 |
| HP-2 God class | Mostly done | `c6b2650`; `CtrfReportManager` is still ~190 lines and kept the name-based flaky logic (CB-7) |
| HP-3 Logging | Open, approach changed | Use `java.util.logging`, not SLF4J (MP-9) |
| HP-4 `TestDetailsUtil` duplication | Done | `b49d2c1` (adapters), `16e61f3` (record) |
| MP-1 Single-pass `SummaryUtil` | Done | `64f5bdd` |
| MP-2 Cache existing report | Done, caused a regression | `c7772e5`: the cache is never invalidated, so same-JVM reruns lose results (CB-6c) |
| MP-3 `CopyOnWriteArrayList` | Open, raised to High | Measured: 80k tests take 48 s; HP-6 resolves it |
| MP-4 Input validation | Open, constraint added | Must never throw into JUnit callbacks (MP-7) |
| CQ-1 Feature envy | Superseded | Extracted as `FlakyTestDetector`; the name-based design shown there is the CB-7 bug |
| CQ-2 Value objects | Open (Low) | Don't call the value object `TestIdentifier` (clashes with JUnit's class) |
| CQ-3 Long parameter lists | Stale | The referenced path and method don't exist on `master` |
| CQ-4 Magic constants | Open | `"initializationError"` is defined in four places |
| TI-1 Concurrency tests | Open, needs update | Sample code uses removed APIs; real data loss is across JVMs (TI-5) |
| TI-2 File-system error tests | Open | Add the MP-5 cases |
| TI-3 Health-test isolation | Partially done | Reset hook in `@BeforeEach`/`@AfterEach`; still a shared singleton |
| DI-1 Javadoc | Open | Sample Javadoc repeats the wrong flaky rule |
| DI-2 Architecture doc | Open | Should describe the target architecture below |
| SP-1 Path validation | Re-evaluated: not recommended | Not a trust boundary; rejecting `..` breaks multi-module setups |
| SP-2 Benchmarks | Open | Baseline numbers are in MP-3 |

### New items (verified unless marked otherwise)

| Item | Priority | Summary |
|---|---|---|
| [CB-4](#cb-4-stop-forcing-junit-runtime-dependencies-on-consumers) | Critical | Published JUnit dependencies break JUnit 5 Maven builds and silently upgrade Gradle users to JUnit 6 |
| [CB-5](#cb-5-report-every-failed-container-not-only-class-level-ones) | Critical | Failed non-class containers vanish, so the report can be green while the build is red |
| [CB-6](#cb-6-rework-report-persistence-forks-accumulation-same-jvm-reruns) | Critical | One shared report file: forks overwrite each other, separate runs pile up, same-JVM reruns lose results |
| [CB-7](#cb-7-identify-tests-by-uniqueid-not-display-name) | Critical | Retries/flaky matched by display name, giving false flaky flags in a single run without retries |
| [HP-5](#hp-5-conform-to-the-official-ctrf-schema) | High | Output fails the official CTRF schema (`filepath`, `buildNumber` type) |
| [HP-6](#hp-6-one-test-object-per-logical-test-fold-retry-attempts) | High | Each attempt is reported as a separate test; summary inflated; `retryAttempts` missing |
| [HP-7](#hp-7-report-aborted-tests-failed-assumptions-as-skipped) | High | Failed assumptions are reported as failed |
| [HP-8](#hp-8-report-tests-inside-skipped-containers) | High | Tests inside skipped containers (e.g. a `@Disabled` class) vanish |
| [HP-9](#hp-9-extension-must-not-lose-test-identity-unknown-test) | High | The extension reports "Unknown Test" when an earlier extension's `beforeEach` fails |
| [HP-10](#hp-10-make-the-testexecutionlistener-the-single-core-integration-point) | High | Make the listener the single core integration point |
| [MP-5](#mp-5-read-previous-reports-tolerantly) | Medium | Reading a previous report is brittle: unknown fields drop history, missing `healthy` means unhealthy |
| [MP-6](#mp-6-replace-deprecated-extensioncontextstorecloseableresource) | Medium | Deprecated `CloseableResource`; JUnit 6 already logs a warning for every extension user |
| [MP-7](#mp-7-never-let-reporter-errors-fail-or-mask-user-tests) | Medium | Reporter errors can fail or mask user tests (by inspection) |
| [MP-8](#mp-8-separate-message-from-trace-bound-the-trace-size) | Medium | `message` holds the stack trace; `trace` is unbounded |
| [MP-9](#mp-9-dependency-and-configuration-hygiene-owner-env-vars-jackson) | Medium | `owner` is unmaintained, env-var configuration is unusable, Jackson lands on users' classpaths |
| [TI-4](#ti-4-validate-integration-output-against-the-official-schema-and-junits-own-counts) | High | Validator uses a loosened schema and a draft-04 validator; some assertions pass silently |
| [TI-5](#ti-5-regression-tests-for-every-verified-scenario) | High | Regression tests for every verified scenario |
| [DI-3](#di-3-fix-incorrect-documentation) | Medium | Incorrect or missing docs (README scopes, one entry point at a time, `CtrfListener` Javadoc, CLAUDE.md) |

### Target architecture

Most new items come from three design choices. Fixing those once is cheaper than fixing each symptom separately:

1. **One integration point: the `TestExecutionListener` (HP-10).** It sees every engine, the `TestPlan` (suite hierarchy, and the descendants of skipped containers), and container failures of every source type. Gradle, Surefire and JUnit's own `LegacyXmlReportGeneratingListener` all work this way. The Jupiter extension structurally cannot see skipped containers or most container failures.
2. **Identity is JUnit's `uniqueId`; analysis happens once at the end (CB-7, HP-6, MP-3).** Record attempts in a `ConcurrentHashMap<testId, List<Attempt>>`, which costs O(1) per event. One O(n) pass at the end folds them into spec-shaped test objects (`testId`, `suite`, `retries`, `retryAttempts`, `flaky`).
3. **No read-modify-write of one shared file (CB-6).** Each JVM writes its own shard atomically. A merge step builds the final report, either a small merger shipped with the library or `ctrf merge <dir>` from `ctrf-io/ctrf-cli`. The spec models this directly with `runId` and `shardId`.

On top of that: force no JUnit or Jackson versions on users (CB-4, MP-9), and validate against the pinned official schema in CI (HP-5, TI-4).

Context: `ctrf-io/junit-to-ctrf` already converts the JUnit XML that Gradle and Surefire write. Those files are written per class, so forks can't clobber each other, and skipped classes are expanded. This library's advantages are tags, thread IDs, exact timings and the environment-health flag, so it should first be at least as correct as that conversion.

---

## Table of Contents

1. [Review Update — 2026-10-08](#review-update--2026-10-08)
2. [Critical Bugs](#critical-bugs)
3. [High Priority Design Issues](#high-priority-design-issues)
4. [Medium Priority Improvements](#medium-priority-improvements)
5. [Code Smells & Quality Issues](#code-smells--quality-issues)
6. [Testing Improvements](#testing-improvements)
7. [Documentation Improvements](#documentation-improvements)
8. [Security & Performance](#security--performance)

---

## Critical Bugs

### CB-1: Fix NullPointerException in CtrfReportFileService.getExistingTests()

**Priority**: Critical
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java`
**Status**: Done on `master` (`cbd61a4`)

#### Prompt
```
Fix the potential NullPointerException in CtrfReportFileService.getExistingTests() method at line 60.

Current code:
```java
public List<Test> getExistingTests() {
    CtrfJson existingReport = readExistingReport();
    return existingReport != null ? existingReport.getResults().getTests() : Collections.emptyList();
}
```

Problem: While existingReport is null-checked, getResults() could return null, causing NPE when calling getTests().

Requirements:
1. Add null-checks for both getResults() and getTests()
2. Follow the same pattern used in getExistingStartTime() and getExistingEnvironmentHealth() methods
3. Return Collections.emptyList() if any intermediate value is null
4. Add unit tests to verify behavior with:
   - Null report
   - Report with null results
   - Report with null tests list
   - Valid report with tests

Expected result:
```java
public List<Test> getExistingTests() {
    CtrfJson existingReport = readExistingReport();
    return existingReport != null
        && existingReport.getResults() != null
        && existingReport.getResults().getTests() != null
        ? existingReport.getResults().getTests()
        : Collections.emptyList();
}
```
```

#### Acceptance Criteria
- [ ] Method returns empty list when report is null
- [ ] Method returns empty list when results is null
- [ ] Method returns empty list when tests is null
- [ ] Method returns actual tests list when all values are present
- [ ] Unit tests cover all four scenarios
- [ ] No NullPointerExceptions in any scenario

---

### CB-2: A Nested JUnit Run Ends the Outer Run (re-scoped from "Race Condition in finishTestRun()")

**Priority**: Medium (lowered from Critical on 2026-10-08)
**Complexity**: Moderate
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java`, `launcher/CtrfListener.java`, `jupiter/TestRunExtension.java`
**Status**: Re-scoped 2026-10-08 (verified)

#### Review Note (2026-10-08)
The original item assumed that test callbacks can run while `finishTestRun()` executes, so results added between the CAS and `clear()` would be lost. With a single entry point that can't happen. JUnit calls `testPlanExecutionFinished` (listener), and closes the root store (extension), only after every test on every thread has finished. Verified: 10 runs of 1,000 tests on 8–9 parallel threads (5 with only the listener, 5 with only the extension) each reported 1,000 of 1,000 tests.

Results are lost when a test starts its own JUnit run in the same JVM. That's common in projects that test JUnit extensions or tooling through `LauncherFactory`. The nested launcher registers `CtrfListener` again via `META-INF/services`, and all state lives in one JVM-wide singleton, so the nested run's `testPlanExecutionFinished` ends the outer run.

Verified with the listener only, using three classes run in order:
- A: two tests;
- B: one test that runs `FastTest` through `LauncherFactory.create()`;
- C: two tests.

JUnit counted 5 tests. The CTRF report contains `outerA1()`, `outerA2()`, and the nested run's `fastOne()` and `fastTwo()`. `runsNestedLauncher()`, `outerC1()` and `outerC2()` are missing. The loss doesn't depend on timing.

The original fix, taking a snapshot right after the CAS, doesn't help: the run is ended by the wrong caller, not by a race.

#### Prompt
````
Make sure only the run that started reporting can end it.

Problem: CtrfReportManager keeps one JVM-wide run (the isTestRunStarted flag), and every CtrfListener
instance calls startTestRun/finishTestRun. When a test runs JUnit itself
(LauncherFactory.create().execute(...)), the nested launcher gets its own CtrfListener via ServiceLoader:
- nested testPlanExecutionStarted -> startTestRun is ignored, because a run is already active;
- the nested tests are recorded into the outer run;
- nested testPlanExecutionFinished -> finishTestRun ends the OUTER run (writes the report, clears state);
- the outer tests that follow are recorded but never written, because the outer finishTestRun returns
  early.

Requirements:
1. Remember who started the active run: the TestPlan for the listener, the root ExtensionContext for
   the extension. Only that owner may end the run. A nested run's start and finish are no-ops.
2. Decide what happens to tests from nested runs: ignore them (they are usually fixtures of tooling
   tests) or attribute them to the outer run. Ignoring is the safer default. Document the choice.
3. Stay compatible with sequential runs in one JVM (CB-6c): once the owning run finishes, the next one
   may start a new run.
4. Add the nested-run scenario as a regression test (TI-5).
````

#### Acceptance Criteria
- [ ] A nested JUnit run in the same JVM does not end the outer run
- [ ] Every outer test appears in the report (`runsNestedLauncher()`, `outerC1()`, `outerC2()` in the scenario above)
- [ ] Tests from nested runs are handled as documented
- [ ] Sequential runs in one JVM still produce correct reports (CB-6)

---

### CB-3: Fix Multiple ConfigReader Instantiations

**Priority**: Critical
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java`
**Status**: Done on `master` (`030842d`)

#### Prompt
```
Eliminate multiple instantiations of ConfigReader in CtrfReportManager.

Current issue:
ConfigReader is created twice:
1. In constructor at line 37
2. In finishTestRun() at line 154

This causes unnecessary object creation and potential configuration inconsistency if system properties
change during execution.

Requirements:
1. Create ConfigReader once as a final field in the constructor
2. Reuse the same instance in finishTestRun()
3. Update CtrfJsonComposer to use the stored configReader
4. Ensure the package-private test constructor also initializes configReader properly
5. Update all tests to verify configuration is read only once

Changes needed:
1. Add final field: `private final ConfigReader configReader;`
2. Initialize in constructor (line 37)
3. Remove instantiation from finishTestRun() (line 154)
4. Pass stored configReader to CtrfJsonComposer constructor
5. Update test constructor to accept ConfigReader parameter if needed

Verify that:
- Configuration is consistent throughout test execution
- No performance regression from Owner library caching
- Tests still pass, including configuration-related tests
```

#### Acceptance Criteria
- [ ] ConfigReader created only once per CtrfReportManager instance
- [ ] Stored as final field
- [ ] Reused in all methods that need configuration
- [ ] All tests pass
- [ ] No configuration inconsistencies possible
- [ ] Code is cleaner and more maintainable

---

### CB-4: Stop Forcing JUnit Runtime Dependencies on Consumers

**Priority**: Critical
**Complexity**: Moderate
**Files**: `build.gradle`, `README.md`, `CtrfReportManager.java`, `SuiteExecutionErrorHandler.java`, new consumer-compatibility CI job
**Status**: Open (verified 2026-10-08)

#### Prompt
````
Remove JUnit from the library's published runtime dependencies, so adding the reporter never changes
the consumer's JUnit versions.

Problem (build.gradle:47-50 on master):
    implementation platform("org.junit:junit-bom:${junitVersion}")
    implementation "org.junit.jupiter:junit-jupiter-api"
    implementation "org.junit.jupiter:junit-jupiter-engine"
    implementation "org.junit.platform:junit-platform-launcher"

These end up in the published metadata:
- POM: junit-jupiter-api, junit-jupiter-engine and junit-platform-launcher at runtime scope, with
  versions taken from an imported junit-bom.
- Gradle module metadata: junit-bom as a platform dependency of runtimeElements.

Verified with the released 0.4.5 (which pins JUnit 6.0.1):
1. Maven project on JUnit 5.11.4, junit-jupiter declared first and the reporter second (both test
   scope): `NoClassDefFoundError: org/junit/platform/engine/OutputDirectoryCreator`, "There was an
   error in the forked process", 0 tests run, BUILD FAILURE. The dependency tree has
   junit-platform-launcher 6.0.1 next to junit-platform-engine 1.11.4.
2. Same project with the reporter declared first: the build passes, but it runs on JUnit 6.0.1 with
   junit-jupiter-params 5.11.4.
3. Gradle project on JUnit 5.11.4: every JUnit module resolves to 6.0.1 (BOM constraint).
4. The README tells users to add the reporter with `implementation` (Gradle) and without
   <scope>test</scope> (Maven). That puts the reporter, the JUnit engine, Jackson and owner on the
   production runtime classpath.

Requirements:
1. Declare junit-jupiter-api and junit-platform-launcher as `compileOnly`; both are always present when
   tests run on the JUnit Platform. Remove junit-jupiter-engine from the main configuration (keep it in
   testImplementation). Keep the BOM out of the published metadata (`compileOnly platform(...)` and
   `testImplementation platform(...)`).
2. Code reachable from CtrfListener must not touch Jupiter types. Otherwise projects that run only
   non-Jupiter engines (Vintage, Spock, Cucumber) break once junit-jupiter-api leaves the classpath.
   Today CtrfReportManager.finishTestRun(Optional<ExtensionContext>) evaluates
   `ExtensionContext::getExecutionException`. Linking that method reference needs the Jupiter class
   even when the Optional is empty (expected NoClassDefFoundError; verify with a Vintage-only consumer).
   Move Jupiter-specific handling into CtrfExtension.
3. Fix the README snippets: `testImplementation` and `<scope>test</scope>`.
4. Add a CI job with minimal consumer projects (Maven and Gradle) for JUnit 5.10.x, the latest 5.x and
   6.x, consuming the library via publishToMavenLocal. Each runs one passing and one failing test and
   checks that the CTRF file exists with the right counts. The job also catches accidental use of
   JUnit 6-only APIs.
5. Check the generated metadata with `./gradlew generatePomFileForMavenPublication
   generateMetadataFileForMavenPublication`, then inspect build/publications/maven/pom-default.xml and
   module.json.

Repro (Maven consumer, maven-surefire-plugin 3.5.2, one trivial @Test, run `mvn test`):
```xml
<dependencies>
  <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId>
    <version>5.11.4</version><scope>test</scope></dependency>
  <dependency><groupId>io.github.alexshamrai</groupId><artifactId>junit-ctrf-reporter</artifactId>
    <version>0.4.5</version><scope>test</scope></dependency>
</dependencies>
```
````

#### Acceptance Criteria
- [ ] The published POM contains no `org.junit` dependencies; `module.json` runtimeElements contains no `junit-bom` platform
- [ ] Maven and Gradle consumers on JUnit 5.10, the latest 5.x and 6.x run their tests and produce a report, in either declaration order
- [ ] A Vintage-only consumer using `CtrfListener` works without `junit-jupiter-api` on the classpath
- [ ] README uses test-scoped coordinates
- [ ] The consumer-compatibility job runs in CI on every PR

---

### CB-5: Report Every Failed Container, Not Only Class-Level Ones

**Priority**: Critical
**Complexity**: Moderate
**Files**: `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java`, `CtrfReportManager.java`, `SuiteExecutionErrorHandler.java`, integration fake tests and validator
**Status**: Open (verified 2026-10-08)

#### Prompt
````
Make sure every container failure ends up in the report as a failed entry. Today the report can show
zero failures while the build fails.

Problem:
- CtrfListener.isContainerFailure (CtrfListener.java:107-111) only accepts containers whose source is a
  ClassSource. Containers with a MethodSource are dropped: a @ParameterizedTest whose @MethodSource
  throws or provides no arguments, a @TestFactory that throws, @TestTemplate providers. Engine-level
  failures are dropped too.
- CtrfExtension only records @BeforeAll failures (handleBeforeAllMethodExecutionException). At the end
  of the run, CtrfReportManager.captureUncaughtInitializationError (CtrfReportManager.java:172-189)
  looks only at the ExtensionContext of the first class that executed (captured by
  TestRunExtension.beforeAll). So @AfterAll failures in any other class are lost.

Verified (JUnit 6.1.3):
- Listener: a class with `static Stream<Integer> data() { throw ... }` plus
  `@ParameterizedTest @MethodSource("data")`, a @TestFactory that throws, and one passing class.
  JUnit's SummaryGeneratingListener: containersFailed=2. CTRF: tests=2, passed=2, failed=0.
- Extension: an @AfterAll failure in a class that is not the first to run, plus a throwing
  @MethodSource. JUnit: containersFailed=2. Neither appears in the CTRF report.

Requirements:
1. Listener: in executionFinished, for every container with status FAILED (any source type, including
   the engine), add a failed entry with the container's identity:
   - testId = the container's uniqueId (CB-7);
   - name = the container's display name (keeping "initializationError" for class containers for
     backward compatibility is fine, but document it);
   - suite = its ancestors; filePath = the source class; message/trace from the throwable.
2. If the container failed after its children ran (e.g. @AfterAll, AfterAllCallback), still add the
   entry, named so the phase is clear. Document the naming.
3. For a container that finishes ABORTED, report its test descendants as skipped (shared with HP-8).
4. Extension: implement handleAfterAllMethodExecutionException as the counterpart of the @BeforeAll
   handler. Remove the first-class-only logic in captureUncaughtInitializationError, or track every
   class context instead. Document what the extension cannot see; HP-10 makes the listener the
   recommended path.
5. Add fake tests to both integration modules: a broken @MethodSource, a throwing @TestFactory and an
   @AfterAll failure. In the validator, assert that CTRF failed == JUnit failed tests + failed
   containers (TI-4).
````

#### Acceptance Criteria
- [ ] Every FAILED container in the test plan produces exactly one failed CTRF entry
- [ ] CTRF `summary.failed > 0` whenever JUnit reports a failed test or container
- [ ] `@AfterAll` failures are captured for every class, not only the first one
- [ ] The integration validator cross-checks counts with JUnit's own results

---

### CB-6: Rework Report Persistence (Forks, Accumulation, Same-JVM Reruns)

**Priority**: Critical
**Complexity**: Complex
**Files**: `CtrfReportFileService.java`, `CtrfReportManager.java`, `TestRerunHandler.java`, `ReportOrchestrator.java`, `config/CtrfConfig.java`, `README.md`
**Status**: Open (verified 2026-10-08)

#### Prompt
````
Replace the current model: read the existing report at start, then rewrite the whole file at the end.
A single shared file gives wrong results in every situation where more than one run touches it.

Root cause (master):
- startTestRun loads the existing file and appends its tests to the current run
  (CtrfReportManager.java:136), and reuses the file's start time and health flag.
- The parsed file is cached for the life of the JVM and never invalidated
  (CtrfReportFileService.java:101-107, introduced by MP-2).
- finishTestRun writes the whole report and then clears in-memory state (CtrfReportManager.java:163).
- The default path `ctrf-report.json` is relative to the test JVM's working directory, which is the
  project directory, not build/. So `clean` does not remove it.

Verified symptoms:
a) Forked JVMs (Gradle maxParallelForks, Surefire forkCount): each fork rewrites the whole file and the
   last writer wins. Real Gradle run of :integration-tests-listener:test, retries disabled:
     maxParallelForks=1 -> CTRF has 19 of 19 tests (passed=12 failed=5 skipped=2)
     maxParallelForks=2 -> CTRF has 9 of 19 tests (passed=6 failed=3 skipped=0)
b) Independent runs accumulate (local re-runs, persistent CI workspaces such as Jenkins). The same
   2-test class run twice in separate JVMs gives tests=4, every passing test retries=1 flaky=true, and
   summary.start from the first run.
c) Re-execution in the same JVM (Surefire rerunFailingTestsCount, programmatic launchers): after two
   launcher.execute(...) calls on one launcher, the report contains only the second execution. The
   first execution's results, the failure history and the flaky flag are all lost: the cached pre-run
   read is reused, and state was cleared after the first write.
d) The write is not atomic (objectMapper.writeValue(path.toFile(), ...)) and happens only at the very
   end. A JVM killed mid-write leaves a truncated file. A crash before the end leaves either no report
   or the previous run's report, which CI then publishes as current.

Target design:
1. Shards: each JVM writes its own file, `<dir>/ctrf-<runId>-<shardId>.json`. shardId = pid plus a
   random suffix, or Gradle's `org.gradle.test.worker` system property when present. Write atomically:
   a temp file in the same directory, then Files.move(ATOMIC_MOVE, REPLACE_EXISTING). Never
   read-modify-write a shard. Fill the spec fields root `runId` and `environment.shardId`.
2. Merge: build the final report from all shards with the same runId.
   - Fold attempts across shards by testId (CB-7/HP-6).
   - start = min(start), stop = max(stop), healthy = AND of all shards.
   - Provide a small merger in the library (main class + API) that can run from a Gradle `finalizedBy`
     task or via Maven exec at `post-integration-test`.
   - Document `npx ctrf merge <dir>` (ctrf-io/ctrf-cli) as an alternative when no retry attempts need
     folding across shards.
3. Run identity: `ctrf.run.id`. Default it from CI variables (GITHUB_RUN_ID + GITHUB_RUN_ATTEMPT,
   BUILD_TAG, CI_PIPELINE_ID), otherwise generate one. Ignore shards from other runs. This alone fixes (b).
4. Stopgap if a single file has to stay the default for now:
   - On finish, take an exclusive FileChannel.lock() on `<report>.lock` and re-read the current file
     there (no cache).
   - Merge only if its runId equals the current runId, otherwise overwrite. Merge by testId.
   - Write atomically, then release the lock.
   - Within one JVM, keep the accumulated state across executions of the same run instead of clearing it.
   This fixes (a), (b) and (c), at the cost of serializing writers.
5. Optionally default the output location to the build directory, but correctness must not depend on
   the location; that is what runId is for.
6. Keep the Gradle test-retry plugin working: retry rounds run in new JVMs of the same build, so they
   must share the runId and fold into one test object (HP-6).
7. Build the report from a snapshot of the collected attempts.
````

#### Acceptance Criteria
- [ ] With `maxParallelForks=2` and `4`, the CTRF test count equals Gradle's JUnit XML count (19 of 19 for the listener module)
- [ ] Re-running without `clean` produces a report for the latest run only
- [ ] Two executions in one JVM: `stable()` is present, and `flakyOnce()` has `retries=1` and `flaky=true`
- [ ] Killing the JVM during a write never leaves an unparsable report
- [ ] Gradle test-retry rounds still fold into one test per logical test
- [ ] README documents shards, the merge step, and `ctrf.run.id`

---

### CB-7: Identify Tests by uniqueId, Not Display Name

**Priority**: Critical
**Complexity**: Simple
**Files**: `FlakyTestDetector.java`, `CtrfReportManager.java`, `TestProcessor.java`, `ctrf/model/Test.java`
**Status**: Open (verified 2026-10-08)

#### Prompt
````
Fix false "flaky" flags and retry counts by matching attempts on JUnit's uniqueId instead of the display
name.

Problem:
- FlakyTestDetector.detectAndMarkFlaky (FlakyTestDetector.java:42) finds "previous attempts" with
  findTestsByName, i.e. by display name. Display names collide all the time:
    `[1] x = 1`            every parameterized test
    `repetition 1 of 2`    every @RepeatedTest
    `shouldWork()`         common method names
    `initializationError`  every failing class
- Line 51 also marks a passed test as flaky whenever retries > 0, even when no earlier attempt failed.
  The spec (section 9.22) defines flaky as: final status passed AND one or more failed attempts before it.

Verified in a single run, with no retries configured:
- CollisionATest (shouldWork(), param(int) x2, @RepeatedTest(2), all failing) and CollisionBTest (same
  names, all passing): all 5 CollisionBTest tests got retries=1 and flaky=true.
- DupPass1Test.common() and DupPass2Test.common(), both passing: the second got retries=1, flaky=true.

Requirements:
1. Store the JUnit uniqueId on each CTRF test as `testId` (spec section 9.2). It is stable across runs and
   retry rounds: `[engine:junit-jupiter]/[class:...]/[method:...]`, `[test-template-invocation:#1]`, ...
2. Match earlier attempts by testId only. For tests loaded from older reports without a testId, fall back
   to filePath/suite + name, never to the name alone.
3. retries = number of earlier attempts with the same testId; flaky = final status passed AND at least
   one earlier attempt failed. Remove the `|| retries > 0` condition.
4. Unit tests:
   - parameterized and repeated name collisions across classes;
   - the same method name in two classes;
   - passed then passed: not flaky, retries=1;
   - failed then passed: flaky;
   - failed then failed: not flaky, retries=1.
5. Ship this as a small fix that keeps today's one-entry-per-attempt output; HP-6 then folds attempts
   into a single test object.
````

#### Acceptance Criteria
- [ ] The collision scenarios produce no `retries` or `flaky` values
- [ ] The integration `FlakyTest` is still flaky with `retries=1`
- [ ] Every test in the report has a `testId`
- [ ] No code looks tests up by display name

---

## High Priority Design Issues

### HP-1: Refactor Singleton Pattern in CtrfReportManager

**Priority**: High
**Complexity**: Complex
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java`, `CtrfExtension.java`, `CtrfListener.java`, test files
**Status**: Open; simplified by HP-10

#### Review Note (2026-10-08)
Once the listener owns the run state (HP-10), the singleton is only needed behind `EnvironmentHealthTracker`'s static API. Until each scenario can get a fresh manager, the regression tests in TI-5 have to run scenarios in forked JVMs.

#### Prompt
```
Refactor the inconsistent singleton pattern in CtrfReportManager to use proper dependency injection.

Current issues:
1. Static singleton instance but package-private constructor breaks singleton guarantees
2. ctrfJsonComposer set to null in constructor, then recreated in finishTestRun()
3. Dependencies directly instantiated instead of injected
4. Hard to test due to global state

Recommended approach: Remove singleton pattern entirely and use dependency injection

Steps:
1. Remove static INSTANCE field and getInstance() method
2. Make constructor public and require all dependencies as parameters
3. Update CtrfExtension and CtrfListener to create their own CtrfReportManager instances
4. For shared state (when both Extension and Listener are used), implement a proper registry pattern:
   ```java
   public class CtrfReportRegistry {
       private static final ConcurrentHashMap<String, CtrfReportManager> managers = new ConcurrentHashMap<>();

       public static CtrfReportManager getOrCreate(String key, Supplier<CtrfReportManager> factory) {
           return managers.computeIfAbsent(key, k -> factory.get());
       }
   }
   ```
5. Update all tests to use dependency injection
6. Document the new instantiation model in javadoc and CLAUDE.md

Benefits:
- Proper testability with constructor injection
- No global mutable state
- Clear dependency graph
- Can create multiple instances for testing
- Follows SOLID principles

Alternative (if singleton is absolutely required):
Implement proper singleton with:
- Private constructor
- Lazy initialization with double-checked locking
- All dependencies injected via factory methods
- Remove package-private constructor
```

#### Acceptance Criteria
- [ ] Singleton pattern removed or properly implemented
- [ ] All dependencies injected through constructor
- [ ] Tests use dependency injection
- [ ] No global mutable state accessed from constructors
- [ ] CtrfExtension and CtrfListener both work correctly
- [ ] All existing tests pass
- [ ] New unit tests verify isolation between instances
- [ ] Documentation updated

---

### HP-2: Break Up CtrfReportManager God Class

**Priority**: High
**Complexity**: Complex
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java` + new files
**Status**: Mostly done on `master` (`c6b2650`)

#### Review Note (2026-10-08)
`TestStateTracker`, `FlakyTestDetector`, `TestRerunHandler` and `ReportOrchestrator` exist on `master`. `CtrfReportManager` is still ~190 lines (the criterion was under 100). The extraction kept the name-based flaky detection, which is the CB-7 defect.

#### Prompt
```
Refactor CtrfReportManager into smaller, focused classes following Single Responsibility Principle.

Current issues:
- 193 lines with multiple responsibilities
- 13 public/package methods
- 6 instance fields
- Handles test lifecycle, state tracking, flaky detection, orchestration, and environment health

Proposed new structure:

1. **TestStateTracker** (new class)
   - Manages CopyOnWriteArrayList<Test> tests
   - Manages ConcurrentHashMap<String, TestDetails> testDetailsMap
   - Methods: addTest(), getTest(), getAllTests(), clear()
   - Thread-safe operations on test state

2. **FlakyTestDetector** (new class)
   - Logic from handleRerunsAndFlaky() and findTestsByName()
   - Methods: detectFlakyTests(List<Test> tests), markTestAsFlaky(Test test)
   - Pure logic, no state

3. **TestRerunHandler** (new class)
   - Handles test rerun logic
   - Merges existing tests with new test results
   - Methods: mergeTestResults(List<Test> existing, List<Test> current)

4. **ReportOrchestrator** (new class)
   - Coordinates report generation
   - Delegates to CtrfJsonComposer and CtrfReportFileService
   - Methods: generateReport(TestStateTracker state, CtrfConfig config)

5. **CtrfReportManager** (simplified)
   - Lifecycle coordinator only
   - Delegates to above classes
   - Much simpler, ~80 lines

Implementation steps:
1. Create TestStateTracker with test collection management
2. Extract FlakyTestDetector with pure functions
3. Extract TestRerunHandler with merge logic
4. Create ReportOrchestrator for composition
5. Update CtrfReportManager to delegate to new classes
6. Update all tests to work with new structure
7. Ensure thread safety is maintained
8. Update documentation

Each new class should be:
- Focused on single responsibility
- Independently testable
- Well-documented with javadoc
- Thread-safe where needed
```

#### Acceptance Criteria
- [ ] CtrfReportManager reduced to <100 lines
- [ ] Each new class has single, clear responsibility
- [ ] All existing functionality preserved
- [ ] Thread safety maintained or improved
- [ ] Each class has comprehensive unit tests
- [ ] Integration tests pass
- [ ] Code coverage maintained or improved
- [ ] Documentation updated in CLAUDE.md

---

### HP-3: Implement Proper Logging Framework

**Priority**: High
**Complexity**: Moderate
**Files**: `CtrfReportFileService.java`, `build.gradle`, and classes with System.out/err
**Status**: Open; approach changed 2026-10-08

#### Review Note (2026-10-08)
On `master`, SLF4J and Logback are used only by the `integration-ctrf-validator` module, not by the library. Adding `slf4j-api` to a library that sits on every user's test classpath adds a dependency, and without a provider SLF4J 2 prints a "No SLF4J providers were found" warning. Use `java.util.logging` instead: no dependency, and the JUnit Platform itself logs through JUL. See MP-9. The rest of the prompt (log levels, no `System.out`/`System.err`) still applies.

#### Prompt
```
Replace System.out/err with proper SLF4J logging throughout the codebase.

Current issues:
1. SLF4J and Logback in dependencies but never used
2. Errors written to System.err.println() (CtrfReportFileService lines 47, 49, 113)
3. Info messages to System.out.println() (CtrfReportFileService line 110)
4. No way for users to control log levels or output
5. Cannot integrate with existing logging infrastructure

Requirements:
1. Add SLF4J Logger to each class that needs logging
2. Replace all System.out with logger.info() or logger.debug()
3. Replace all System.err with appropriate log levels (error/warn)
4. Add proper exception logging with logger.error("message", exception)
5. Use parameterized logging: logger.info("Writing report to {}", path)
6. Add logback-test.xml for test logging configuration
7. Make logging optional for library users (don't force logback implementation)
8. Document logging configuration in README

Example transformation:
```java
// Before
System.err.println("Error reading existing report: " + e.getMessage());

// After
private static final Logger logger = LoggerFactory.getLogger(CtrfReportFileService.class);
logger.error("Error reading existing report from {}", filePath, e);
```

Log level guidelines:
- ERROR: Exceptions, failures that prevent core functionality
- WARN: Issues that don't prevent operation (file exists, etc.)
- INFO: Important events (report written, tests completed)
- DEBUG: Detailed information for troubleshooting
- TRACE: Very detailed information (not needed initially)

Files to update:
1. CtrfReportFileService.java - primary candidate
2. Any other classes with System.out/err
3. build.gradle - ensure SLF4J is compile scope, Logback is test scope
4. Add logback-test.xml configuration
5. Update CLAUDE.md with logging information
```

#### Acceptance Criteria
- [ ] No System.out.println() or System.err.println() in production code
- [ ] SLF4J Logger added to relevant classes
- [ ] Appropriate log levels used
- [ ] Exceptions logged with full stack traces
- [ ] Parameterized logging used for performance
- [ ] logback-test.xml provides reasonable defaults for tests
- [ ] Documentation explains logging configuration
- [ ] Library users can provide their own SLF4J implementation
- [ ] All tests pass with logging enabled

---

### HP-4: Remove or Fix TestDetailsUtil Duplication

**Priority**: High
**Complexity**: Moderate
**Files**: `TestDetailsUtil.java`, `CtrfExtension.java`, `CtrfListener.java`
**Status**: Done on `master` (`b49d2c1` adapters, `16e61f3` record)

#### Prompt
```
Eliminate code duplication by consolidating the three implementations of createTestDetails() logic.

Current situation:
Three nearly identical implementations exist:
1. TestDetailsUtil.createTestDetails() - never used
2. CtrfExtension.createTestDetails() - lines 70-77
3. CtrfListener.createTestDetails() - lines 93-100

Differences:
- Extension uses ExtensionContext directly
- Listener converts tags to strings and has custom extractFilePathFromSource logic
- TestDetailsUtil is unused

Recommended approach: Create unified utility with strategy pattern

1. Create TestContextAdapter interface:
   ```java
   public interface TestContextAdapter {
       String getUniqueId();
       String getDisplayName();
       Set<String> getTags();
       Optional<String> getSourceLocation();
       String getThreadId();
   }
   ```

2. Implement adapters:
   ```java
   public class ExtensionContextAdapter implements TestContextAdapter {
       private final ExtensionContext context;
       // ... implement methods using ExtensionContext
   }

   public class TestIdentifierAdapter implements TestContextAdapter {
       private final TestIdentifier identifier;
       // ... implement methods using TestIdentifier
   }
   ```

3. Update TestDetailsUtil to accept TestContextAdapter:
   ```java
   public static TestDetails createTestDetails(TestContextAdapter context) {
       return TestDetails.builder()
           .uniqueId(context.getUniqueId())
           .displayName(context.getDisplayName())
           .tags(context.getTags())
           .filePath(context.getSourceLocation().orElse(null))
           .threadId(context.getThreadId())
           .build();
   }
   ```

4. Update CtrfExtension to use:
   ```java
   private TestDetails createTestDetails(ExtensionContext context) {
       return TestDetailsUtil.createTestDetails(new ExtensionContextAdapter(context));
   }
   ```

5. Update CtrfListener similarly

6. Add tests for all adapters and TestDetailsUtil

Benefits:
- Single source of truth for TestDetails creation
- Easy to add new context sources
- Better testability
- Clear separation of concerns
```

#### Acceptance Criteria
- [ ] Only one implementation of createTestDetails logic exists
- [ ] Both CtrfExtension and CtrfListener use shared utility
- [ ] No code duplication between entry points
- [ ] All tests pass
- [ ] New tests cover adapter pattern
- [ ] Behavior identical to previous implementation
- [ ] Code is cleaner and more maintainable

---

### HP-5: Conform to the Official CTRF Schema

**Priority**: High
**Complexity**: Moderate
**Files**: `ctrf/model/*.java`, `CtrfJsonComposer.java`, `config/CtrfConfig.java`, `config/ConfigReader.java`, `integration-ctrf-validator` (schema and validator)
**Status**: Open (verified 2026-10-08)

#### Prompt
````
Make every generated report valid against the official CTRF JSON schema, and validate against that
schema in CI.

Reference: https://github.com/ctrf-io/ctrf, files schema/ctrf.schema.json and spec/ctrf.md (spec 0.1.0,
released 2026-10-03). The root, results, summary, environment and test objects all have
additionalProperties: false. Unknown fields are only allowed inside `extra`.

Verified: a typical report (2 classes, 10 tests, -Dctrf.build.number=123) has 11 validation errors:
- 10 x "Additional properties are not allowed ('filepath' was unexpected)". The spec field is
  `filePath` (ctrf/model/Test.java:34).
- "'123' is not of type 'integer'" at /results/environment/buildNumber (Environment.java:20 is a String).
The schema bundled in integration-ctrf-validator is a looser copy (no additionalProperties: false,
buildNumber typed as string), so the same file passes it with 0 errors.

Requirements:
1. Serialize `filePath` (@JsonProperty("filePath")), and still accept the legacy `filepath` when reading
   (@JsonAlias).
2. Make buildNumber an Integer by parsing the configured value. If it isn't numeric, log a warning and
   put the raw value into environment.extra instead of emitting invalid JSON.
3. Make `suite` a List<String>, ordered top-level to immediate parent, filled from the TestPlan
   ancestors (listener) or the ExtensionContext parents (extension).
4. Set `specVersion` to the version actually targeted ("0.1.0"), as a constant updated deliberately.
5. Add the spec fields other items need: testId (CB-7), retryAttempts (HP-6), summary.flaky and
   summary.duration, root runId and environment.shardId (CB-6), rawStatus (HP-7).
6. Replace the bundled schema with a pinned copy of the official one (record the commit), and validate
   with a draft-07 capable validator such as com.networknt:json-schema-validator (TI-4).
7. Add a fast unit test in the main module that serializes a fully populated CtrfJson and validates it
   against the pinned schema.
````

#### Acceptance Criteria
- [ ] Listener and extension integration reports validate against the pinned official schema with 0 errors
- [ ] Reports that use the legacy `filepath` field can still be read
- [ ] Non-numeric build numbers no longer produce invalid reports
- [ ] `specVersion` matches the pinned schema version

---

### HP-6: One Test Object per Logical Test (Fold Retry Attempts)

**Priority**: High
**Complexity**: Moderate
**Files**: `CtrfReportManager.java`, `FlakyTestDetector.java`, `TestStateTracker.java`, `util/SummaryUtil.java`, `ctrf/model/Test.java` and a new attempt model, `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`
**Status**: Open (verified 2026-10-08)

#### Prompt
````
Emit one test object per logical test, representing its final attempt, with earlier attempts in
`retryAttempts`. Count only final attempts in the summary.

Spec (sections 9.1, 9.20, 9.21): "The test object represents the final attempt in that execution. When
retries occur, attempts completed before the final attempt are represented in retryAttempts." Also:
"If retries is greater than 0, retryAttempts MUST be present." Today's output violates that MUST.

Effect today, in this repository's integration run with retries: tests=24 and failed=9, although only
4 tests actually fail. The spec-correct summary is tests=19, passed=13, failed=4, skipped=2, flaky=1.
CtrfLogicTest.verifySummaryIsCorrect (lines 48-50) currently asserts the inflated numbers.

Requirements:
1. During execution, record attempts in a ConcurrentHashMap<String testId, List<Attempt>>: O(1) per
   event, with no scan over earlier results. This replaces the per-event FlakyTestDetector scan and
   resolves MP-3.
2. At report time, in a single O(n) pass:
   - the final attempt becomes the test object;
   - earlier attempts go into retryAttempts, numbered contiguously from 1, each with status,
     duration, start/stop and message/trace;
   - retries = retryAttempts.size(); flaky as defined by the spec;
   - the summary counts final attempts only and sets summary.flaky.
3. Before folding, merge attempts from other shards and runs with the same runId (CB-6) by testId.
4. Update CtrfLogicTest to the spec numbers, and assert retryAttempts for the flaky test and for
   DummyFailedTest.
5. Report consumers will see different test counts. Note this in the changelog and README, and bump the
   minor version.
````

#### Acceptance Criteria
- [ ] Each logical test appears exactly once in `results.tests`
- [ ] Integration reports show tests=19, passed=13, failed=4, skipped=2, flaky=1
- [ ] `retries` always equals `retryAttempts.size()`, and `retryAttempts` is absent when `retries` is 0
- [ ] Reports validate against the official schema (HP-5)

---

### HP-7: Report Aborted Tests (Failed Assumptions) as Skipped

**Priority**: High
**Complexity**: Simple
**Files**: `CtrfReportManager.java`, `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java`, unit tests, integration fake tests
**Status**: Open (verified 2026-10-08)

#### Prompt
````
Report JUnit's ABORTED status (failed assumptions) as `skipped`, not `failed`.

Problem: CtrfReportManager.onTestAborted (CtrfReportManager.java:126-128) records the test as FAILED.
Verified: a test calling `Assumptions.assumeTrue(false, "Docker not available on this agent")` gives
JUnit aborted=1, but the CTRF report says failed=1, with a TestAbortedException trace. Gradle and
Surefire report such tests as skipped, and CLAUDE.md documents ABORTED -> skipped.

Requirements:
1. Map ABORTED to `skipped`, set `rawStatus: "aborted"`, and put the assumption message in `message`.
2. For an aborted container (an assumption in @BeforeAll), report its test descendants as skipped
   (shared with HP-8).
3. Add unit tests for both entry points, add an integration fake test with an assumption, and update
   the validator counts.
````

#### Acceptance Criteria
- [ ] Failed assumptions appear as skipped, with `rawStatus` "aborted" and the assumption message
- [ ] `summary.failed` does not count aborted tests
- [ ] The listener and the extension behave the same way

---

### HP-8: Report Tests Inside Skipped Containers

**Priority**: High
**Complexity**: Simple
**Files**: `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java` (documented limitation), tests
**Status**: Open (verified 2026-10-08)

#### Prompt
````
When a whole container is skipped, report its tests as skipped instead of dropping them.

Problem: CtrfListener.executionSkipped (CtrfListener.java:144) only handles identifiers where isTest()
is true. When a container is skipped, JUnit reports only the container, and its children get no events
at all.

Verified: a @Disabled class and a class-level @EnabledOnOs(OS.WINDOWS) running on macOS (2 tests each),
plus one passing class. JUnit: found=6, skipped=4. CTRF: tests=2, skipped=0. CtrfExtension has the same
problem, because TestWatcher is never called for tests inside a disabled class.

Requirements:
1. Keep the TestPlan from testPlanExecutionStarted. In executionSkipped for a container, add every test
   in testPlan.getDescendants(container) as skipped, with the container's reason as the message. This
   is what JUnit's own SummaryGeneratingListener and Gradle do.
2. Do the same for containers that finish ABORTED (HP-7).
3. Extension: TestWatcher cannot see this case. Document the limitation (see HP-10).
4. Add integration fake tests for a @Disabled class and a class with a class-level condition, and have
   the validator compare skipped counts with JUnit's results.
````

#### Acceptance Criteria
- [ ] Skipped counts match JUnit's `SummaryGeneratingListener` for disabled classes, class-level conditions and aborted containers
- [ ] Each skipped test carries the container's reason

---

### HP-9: Extension Must Not Lose Test Identity ("Unknown Test")

**Priority**: High
**Complexity**: Simple
**Files**: `jupiter/CtrfExtension.java`, `CtrfReportManager.java`
**Status**: Open (verified 2026-10-08)

#### Prompt
````
Stop reporting real tests as "Unknown Test".

Problem: start details are recorded only in CtrfExtension.beforeEach. TestWatcher callbacks pass only
the uniqueId, and CtrfReportManager.processTestResult (CtrfReportManager.java:105-108) invents
`TestDetails(..., "Unknown Test")` when no start was recorded. That happens whenever
CtrfExtension.beforeEach didn't run, for example when an extension registered earlier failed in its
own beforeEach.

Verified: `@ExtendWith({FailingBeforeEachExtension.class, CtrfExtension.class})` on a class with
importantBusinessTest() produces an entry named 'Unknown Test', with duration 0 and no filePath.

Requirements:
1. TestWatcher callbacks pass full details built from the ExtensionContext (display name, tags, class,
   uniqueId). Use the recorded start time if there is one, otherwise the stop time.
2. Never invent a name when the context provides one.
3. Add a unit test and a regression scenario (TI-5).
````

#### Acceptance Criteria
- [ ] The scenario reports `importantBusinessTest()` with its class and tags
- [ ] No "Unknown Test" entries appear when an `ExtensionContext` is available

---

### HP-10: Make the TestExecutionListener the Single Core Integration Point

**Priority**: High
**Complexity**: Complex
**Files**: `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java`, `jupiter/TestRunExtension.java`, `CtrfReportManager.java`, `EnvironmentHealthTracker.java`, `README.md`
**Status**: Open (design item; motivated by the verified defects in CB-5, HP-8 and HP-9)

#### Prompt
````
Move all reporting logic into the TestExecutionListener, and reduce CtrfExtension to a thin
compatibility adapter (or deprecate it).

Why: the listener sees everything a reporter needs:
- all engines, not only Jupiter;
- the TestPlan: the suite hierarchy for `suite`, and the descendants of skipped containers (HP-8);
- container failures of every source type (CB-5);
- dynamic tests.
Gradle, Surefire and JUnit's own LegacyXmlReportGeneratingListener all work this way. The extension
structurally cannot see skipped containers or most container failures. It also depends on its own
beforeEach having run (HP-9), and ends the run through a deprecated store hook (MP-6).

Requirements:
1. Implement the full model in the listener: TestPlan-aware suite, testId, container failures, skipped
   descendants, and the attempts map (HP-6).
2. Keep CtrfExtension for backward compatibility, delegating with full context. Document the listener as
   the recommended setup.
3. Decide explicitly whether the jar ships META-INF/services/org.junit.platform.launcher.TestExecutionListener
   (automatic activation behind a `ctrf.enabled` switch) or keeps opt-in registration. Shipping it
   activates the listener for everyone with the jar on the test classpath, including CtrfExtension
   users. Document the choice.
4. With the listener owning the run state (CB-2 defines who may end a run), revisit HP-1: the singleton
   is then only needed behind EnvironmentHealthTracker's static API.
````

#### Acceptance Criteria
- [ ] All CB-5 and HP-8 scenarios pass with the listener
- [ ] The listener reports non-Jupiter engines (e.g. Vintage)
- [ ] README recommends one setup and explains the trade-offs

---

## Medium Priority Improvements

### MP-1: Optimize SummaryUtil with Single-Pass Counting

**Priority**: Medium
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/util/SummaryUtil.java`
**Status**: Done on `master` (`64f5bdd`); `summary.flaky` is added by HP-6

#### Prompt
```
Optimize SummaryUtil.createSummary() to count test statuses in a single pass instead of five separate streams.

Current implementation (lines 10-20):
```java
.passed((int) tests.stream().filter(t -> t.getStatus() == PASSED).count())
.failed((int) tests.stream().filter(t -> t.getStatus() == FAILED).count())
.skipped((int) tests.stream().filter(t -> t.getStatus() == SKIPPED).count())
.pending((int) tests.stream().filter(t -> t.getStatus() == PENDING).count())
.other((int) tests.stream().filter(t -> t.getStatus() == OTHER).count())
```

Problem: O(5n) complexity - list is iterated 5 times

Solution: Use single stream with grouping collector

Implementation:
```java
public static Summary createSummary(List<Test> tests, long start, long stop) {
    // Single pass to count all statuses
    Map<TestStatus, Long> statusCounts = tests.stream()
        .collect(Collectors.groupingBy(Test::getStatus, Collectors.counting()));

    // Helper to get count with default 0
    java.util.function.ToIntFunction<TestStatus> getCount =
        status -> statusCounts.getOrDefault(status, 0L).intValue();

    // Count flaky tests in same pass or separate if needed
    long flakyCount = tests.stream().filter(Test::isFlaky).count();

    return Summary.builder()
        .tests(tests.size())
        .passed(getCount.applyAsInt(PASSED))
        .failed(getCount.applyAsInt(FAILED))
        .skipped(getCount.applyAsInt(SKIPPED))
        .pending(getCount.applyAsInt(PENDING))
        .other(getCount.applyAsInt(OTHER))
        .flaky((int) flakyCount)
        .start(start)
        .stop(stop)
        .build();
}
```

Performance improvement:
- Before: O(5n) - five full iterations
- After: O(n) - single iteration
- For 1000 tests: ~5x faster
- For 10000 tests: ~5x faster

Requirements:
1. Maintain identical behavior
2. Add performance benchmark test comparing old vs new approach
3. Verify all test statuses are counted correctly
4. Handle empty list edge case
5. Update unit tests to verify grouping logic
```

#### Acceptance Criteria
- [ ] Single stream operation counts all statuses
- [ ] Behavior identical to previous implementation
- [ ] All unit tests pass
- [ ] Performance benchmark shows improvement
- [ ] Edge cases handled (empty list, all same status, etc.)
- [ ] Code is more readable and maintainable

---

### MP-2: Cache readExistingReport() Results

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java`
**Status**: Done on `master` (`c7772e5`), but it caused a regression (CB-6c)

#### Review Note (2026-10-08)
`invalidateCache()` was never added, and the cache lives as long as the JVM. The criterion "No stale data issues" is not met. A second test plan executed in the same JVM (Surefire `rerunFailingTestsCount`, programmatic launchers) reuses the read made before the first run, and loses the first execution's results (verified). CB-6 replaces this mechanism.

#### Prompt
```
Optimize CtrfReportFileService by caching the result of readExistingReport() to avoid multiple file reads.

Current issue:
readExistingReport() is called three times on startup:
1. getExistingTests()
2. getExistingStartTime()
3. getExistingEnvironmentHealth()

Each call reads and parses the same JSON file, which is wasteful for large reports.

Solution: Implement lazy initialization with caching

Implementation approach:
```java
public class CtrfReportFileService {
    private final ConfigReader configReader;
    private CtrfJson cachedReport; // Cache the parsed report
    private boolean reportRead = false; // Flag to track if we've attempted to read

    // Update existing methods to use cache
    private CtrfJson readExistingReportCached() {
        if (!reportRead) {
            cachedReport = readExistingReport();
            reportRead = true;
        }
        return cachedReport;
    }

    public List<Test> getExistingTests() {
        CtrfJson existingReport = readExistingReportCached();
        // ... rest of implementation
    }

    public Long getExistingStartTime() {
        CtrfJson existingReport = readExistingReportCached();
        // ... rest of implementation
    }

    public Boolean getExistingEnvironmentHealth() {
        CtrfJson existingReport = readExistingReportCached();
        // ... rest of implementation
    }

    // Add method to invalidate cache if needed
    public void invalidateCache() {
        cachedReport = null;
        reportRead = false;
    }
}
```

Thread safety considerations:
- If this service is shared across threads, add synchronization
- Or make the cache volatile with double-checked locking
- Document threading requirements

Requirements:
1. Cache the parsed CtrfJson after first read
2. Reuse cached value for subsequent calls
3. Handle thread safety if service is shared
4. Add method to invalidate cache if needed
5. Update tests to verify caching behavior
6. Measure performance improvement for large reports

Alternative simpler approach:
Read the report once in constructor or in a single initialization method called by CtrfReportManager.
```

#### Acceptance Criteria
- [ ] Report file read only once per service instance
- [ ] All three getter methods use cached value
- [ ] Behavior identical to previous implementation
- [ ] Thread safety maintained if needed
- [ ] Tests verify caching works correctly
- [ ] Performance improvement measurable for large reports
- [ ] No stale data issues

---

### MP-3: Replace CopyOnWriteArrayList with Better Alternative

**Priority**: High (raised from Medium on 2026-10-08)
**Complexity**: Moderate
**Files**: `src/main/java/io/github/alexshamrai/TestStateTracker.java`, `FlakyTestDetector.java` (on `master`; the list moved out of `CtrfReportManager.java` in `c6b2650`)
**Status**: Open; resolved by HP-6

#### Review Note (2026-10-08)
The analysis below assumes a single read at the end, which is wrong. Every test completion runs two O(n) operations:
- `FlakyTestDetector.detectAndMarkFlaky` (`FlakyTestDetector.java:41-55`) streams over all completed tests.
- `TestStateTracker.addTest` (`TestStateTracker.java:24, 32-34`) copies the whole array.

`CopyOnWriteArrayList.add` copies under a lock, so parallel test threads also queue on it.

Measured on `master` (single thread, unique names, no retries, `-Xmx2g`, JDK 21):

| Tests | Event processing |
|---|---|
| 10,000 | 0.4 s |
| 20,000 | 1.1 s |
| 40,000 | 5.9 s |
| 80,000 | 47.9 s |

Swapping only the collection would keep the O(n) scan on every event. HP-6 removes both costs: an attempts map keyed by `testId`, folded once at the end. Implement HP-6 rather than benchmarking list types.

#### Prompt
```
Evaluate and potentially replace CopyOnWriteArrayList<Test> with a more appropriate concurrent collection.

Current implementation (line 24):
```java
private final List<Test> tests = new CopyOnWriteArrayList<>();
```

Analysis:
CopyOnWriteArrayList is optimized for:
- Many reads, few writes
- Read operations don't block

Current usage pattern:
- Many writes during test execution (one per test)
- Single read at end for report generation
- For 1000 tests, the array is copied 1000 times

Performance issue:
- Each add() copies entire array: O(n) per insertion
- Total cost for n tests: O(n²)
- For 1000 tests: ~500,000 operations
- For 10,000 tests: ~50,000,000 operations

Task requirements:
1. Benchmark current implementation with 1000, 5000, 10000 tests
2. Compare alternatives:
   - Collections.synchronizedList(new ArrayList<>())
   - ConcurrentLinkedQueue (if order doesn't matter)
   - Custom solution with ReentrantLock
3. Consider read/write patterns:
   - Many concurrent writes during execution
   - Single read at end
   - Clear at end
4. Run performance benchmarks
5. Evaluate thread safety guarantees needed
6. Choose best option based on data
7. Update implementation
8. Verify all integration tests pass with parallel execution

Benchmark code template:
```java
@Test
void benchmarkTestCollection() {
    int numTests = 10000;

    // Test CopyOnWriteArrayList
    long cowStart = System.nanoTime();
    List<Test> cowList = new CopyOnWriteArrayList<>();
    for (int i = 0; i < numTests; i++) {
        cowList.add(createTestResult());
    }
    long cowTime = System.nanoTime() - cowStart;

    // Test synchronized ArrayList
    long syncStart = System.nanoTime();
    List<Test> syncList = Collections.synchronizedList(new ArrayList<>());
    for (int i = 0; i < numTests; i++) {
        syncList.add(createTestResult());
    }
    long syncTime = System.nanoTime() - syncStart;

    System.out.printf("CopyOnWrite: %d ms, Synchronized: %d ms%n",
        cowTime / 1_000_000, syncTime / 1_000_000);
}
```

Recommendation (pending benchmarks):
If order matters: `Collections.synchronizedList(new ArrayList<>())`
If order doesn't matter: `ConcurrentLinkedQueue` (fastest for concurrent writes)
```

#### Acceptance Criteria
- [ ] Performance benchmarks completed for multiple collection types
- [ ] Data-driven decision made based on benchmarks
- [ ] Implementation replaced with better alternative
- [ ] Thread safety maintained or improved
- [ ] All tests pass, including parallel integration tests
- [ ] Performance improvement measurable
- [ ] Documentation updated with rationale

---

### MP-4: Add Comprehensive Input Validation

**Priority**: Medium
**Complexity**: Moderate
**Files**: Multiple (ConfigReader, CtrfReportManager, model classes)
**Status**: Open; constraint added 2026-10-08

#### Review Note (2026-10-08)
Validation must never throw into JUnit callbacks (`beforeEach`, `TestWatcher`, the store `close()`). An exception there fails or masks the user's tests (MP-7). For invalid configuration, log a warning and fall back to the default instead of throwing `IllegalArgumentException` as sketched below. Do not reject `..` in report paths (see SP-1).

#### Prompt
```
Add comprehensive input validation throughout the codebase to prevent invalid states and improve error messages.

Current issues:
1. No validation of configuration values (e.g., negative maxMessageLength)
2. No validation that report path is writable before execution
3. No validation that test names are non-null
4. No validation of time values (start > stop, negative durations)
5. Model objects can be created in invalid states

Implementation strategy:

1. **Create validation utilities**:
```java
public class Validators {
    public static <T> T requireNonNull(T obj, String paramName) {
        if (obj == null) {
            throw new IllegalArgumentException(paramName + " must not be null");
        }
        return obj;
    }

    public static int requirePositive(int value, String paramName) {
        if (value <= 0) {
            throw new IllegalArgumentException(paramName + " must be positive, got: " + value);
        }
        return value;
    }

    public static String requireNonBlank(String str, String paramName) {
        if (str == null || str.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be blank");
        }
        return str;
    }

    public static Path requireWritablePath(String pathStr, String paramName) {
        Path path = Paths.get(pathStr);
        Path parent = path.getParent();
        if (parent != null && !Files.isWritable(parent)) {
            throw new IllegalArgumentException(
                paramName + " parent directory is not writable: " + parent);
        }
        return path;
    }
}
```

2. **Add ConfigValidator**:
```java
public class ConfigValidator {
    public static void validate(CtrfConfig config) {
        requireNonBlank(config.getReportPath(), "ctrf.report.path");
        requirePositive(config.getMaxMessageLength(), "ctrf.max.message.length");
        requireWritablePath(config.getReportPath(), "ctrf.report.path");
        // ... other validations
    }
}
```

3. **Validate at entry points**:
- CtrfReportManager constructor
- CtrfReportFileService before write
- Model builders (add validation in build() methods)

4. **Add validation to critical methods**:
```java
public void onTestSuccess(String uniqueId) {
    requireNonBlank(uniqueId, "uniqueId");
    // ... rest of implementation
}
```

5. **Validate time values**:
```java
public static void validateTimeRange(long start, long stop) {
    if (start < 0) throw new IllegalArgumentException("start time must be non-negative");
    if (stop < 0) throw new IllegalArgumentException("stop time must be non-negative");
    if (stop < start) throw new IllegalArgumentException("stop time must be >= start time");
}
```

6. **Add validation tests** for:
- Each validator utility
- Configuration validation
- Model object validation
- Edge cases (null, negative, empty)

Files to update:
1. Create Validators utility class
2. Create ConfigValidator
3. Update CtrfReportManager to validate inputs
4. Update CtrfReportFileService to validate paths
5. Add validation to model builders if using Lombok @Builder
6. Update all relevant tests
7. Document validation behavior in javadoc
```

#### Acceptance Criteria
- [ ] Validators utility class created with comprehensive methods
- [ ] Configuration validated on startup
- [ ] Report path validated before writing
- [ ] All public method parameters validated
- [ ] Model objects cannot be created in invalid states
- [ ] Clear error messages for validation failures
- [ ] Tests cover all validation scenarios
- [ ] Documentation explains validation behavior
- [ ] No invalid states possible at runtime

---

### MP-5: Read Previous Reports Tolerantly

**Priority**: Medium
**Complexity**: Simple
**Files**: `CtrfReportFileService.java`, `ctrf/model/Environment.java`, `ctrf/model/*.java`, `CtrfReportManager.java`
**Status**: Open (verified 2026-10-08, except item 3)

#### Prompt
````
Wherever a previously written report is still read (the CB-6 merge or the stopgap), reading must be
tolerant.

Findings:
1. Unknown field (verified): an existing report with a root `runId`, which is valid per spec, fails to
   parse with "Unrecognized field "runId" (class CtrfJson), not marked as ignorable". The earlier
   results are dropped silently and the file is overwritten. The ObjectMapper keeps
   FAIL_ON_UNKNOWN_PROPERTIES on (CtrfReportFileService.java:26).
2. Missing `healthy` (verified): a previous report without environment.healthy (written by version
   0.4.1 or earlier, or by another tool) makes the new run unhealthy. Environment.healthy is a primitive
   boolean, which defaults to false (Environment.java:30).
3. Null stop time (by inspection, not reproduced): CtrfReportManager.java:186 unboxes Test.getStop(),
   which is a Long. A loaded test without `stop` causes a NullPointerException there.

Requirements:
1. Disable FAIL_ON_UNKNOWN_PROPERTIES, or use @JsonIgnoreProperties(ignoreUnknown = true). When unknown
   fields must be written back, keep them in `extra` or via @JsonAnySetter/@JsonAnyGetter.
2. Make `healthy` a Boolean. An absent value means unknown and must not make the run unhealthy.
3. Make start/stop handling null-safe.
4. If parsing fails, don't overwrite the unreadable file. Move it aside
   (`<name>.corrupt-<timestamp>`) and log a warning.
5. Add unit tests for each case (extends TI-2).
````

#### Acceptance Criteria
- [ ] Reports with spec-valid but unknown fields are read without losing data
- [ ] A missing `healthy` field does not mark the run unhealthy
- [ ] Unparsable files are preserved, never silently overwritten

---

### MP-6: Replace Deprecated ExtensionContext.Store.CloseableResource

**Priority**: Medium
**Complexity**: Simple
**Files**: `jupiter/TestRunExtension.java`
**Status**: Open (verified 2026-10-08)

#### Prompt
````
TestRunExtension.beforeAll (TestRunExtension.java:23) stores an anonymous
ExtensionContext.Store.CloseableResource, and that resource's close() writes the report.

Verified:
- Compilation prints a deprecation note.
- At runtime, JUnit 6 logs this for every user of CtrfExtension:
  "WARNING: Type implements CloseableResource but not AutoCloseable: io.github.alexshamrai.jupiter.TestRunExtension$1"
If a future JUnit release drops CloseableResource support, afterAllTests is never called and the
extension silently stops writing reports.

Requirements:
1. Make the stored value implement AutoCloseable. JUnit 5.13 and later close AutoCloseable store values
   by default.
2. While JUnit versions older than 5.13 are still supported, implement both interfaces. Drop
   CloseableResource once the minimum supported JUnit is 5.13 or later. The CB-4 consumer matrix checks
   both.
3. Assert in a test that no warning is logged and that the report is written.
````

#### Acceptance Criteria
- [ ] No `CloseableResource` warning on JUnit 6
- [ ] The report is still written on the oldest supported JUnit 5.x

---

### MP-7: Never Let Reporter Errors Fail or Mask User Tests

**Priority**: Medium
**Complexity**: Simple
**Files**: `jupiter/CtrfExtension.java`, `jupiter/TestRunExtension.java`, `CtrfReportManager.java`
**Status**: Open (by inspection, 2026-10-08)

#### Prompt
````
A reporting library must never change a test's outcome.

JUnit already guards listener callbacks and TestWatcher callbacks. Reading the code shows three places
where an exception from reporter code still reaches JUnit:
- CtrfExtension.beforeEach is a BeforeEachCallback, so an exception there fails the user's test.
- CtrfExtension.handleBeforeAllMethodExecutionException calls reportManager.onTestFailure(...) before
  `throw throwable`. If reporting throws, the user's original exception is replaced. Example:
  setFailureDetails with a negative ctrf.max.message.length throws StringIndexOutOfBoundsException.
- The store close() calls finishTestRun; a runtime exception there becomes an engine-level failure.

Requirements:
1. Wrap reporter work in these callbacks in try/catch and log failures. Don't swallow
   VirtualMachineError.
2. In handleBeforeAllMethodExecutionException, always rethrow the original throwable (try/finally).
3. MP-4 validation: invalid configuration logs a warning and falls back to defaults; it never throws
   into JUnit callbacks.
4. Add unit tests with a throwing TestProcessor or file service: the user test's outcome and exception
   must stay unchanged.
````

#### Acceptance Criteria
- [ ] No reporter exception can change a test's status or exception
- [ ] Reporter failures are logged with context

---

### MP-8: Separate `message` from `trace`; Bound the Trace Size

**Priority**: Medium
**Complexity**: Simple
**Files**: `TestProcessor.java`, `config/CtrfConfig.java`, `config/ConfigReader.java`
**Status**: Open (observed 2026-10-08)

#### Prompt
````
Observed: TestProcessor.setFailureDetails puts the first ctrf.max.message.length characters of the
printed stack trace into `message` (e.g. "org.opentest4j.AssertionFailedError: ...\n\tat ..."). `trace`
has no size limit, so deep or recursive stack traces can make the report very large.

Requirements:
1. message = throwable.toString() (class and message), truncated to ctrf.max.message.length.
2. trace = the stack trace truncated to a new `ctrf.max.trace.length` with a documented default. Keep
   the head and the "Caused by" chain.
3. Optional (spec fields): fill `line`, and possibly `snippet`, from the first stack frame inside the
   test class.
4. Update the unit tests and the README configuration table.
````

#### Acceptance Criteria
- [ ] `message` contains the exception summary, not stack frames
- [ ] `trace` respects the configured maximum

---

### MP-9: Dependency and Configuration Hygiene (owner, Env Vars, Jackson)

**Priority**: Medium
**Complexity**: Moderate
**Files**: `config/CtrfConfig.java`, `config/ConfigReader.java`, `CtrfReportFileService.java`, `build.gradle`, `README.md`
**Status**: Open (verified 2026-10-08, except item 3)

#### Prompt
````
Reduce what the reporter brings onto users' test classpaths, and make configuration work the way it is
documented.

Findings:
1. Environment variables (verified): the CtrfConfig Javadoc and CLAUDE.md document configuration via
   environment variables, but only names containing dots work.
   - `CTRF_REPORT_PATH=...` is ignored; the report went to ./ctrf-report.json instead.
   - `env 'ctrf.report.path=...'` works, but POSIX shells can't export such a name:
     "export: `ctrf.report.path=x': not a valid identifier".
   So in practice environment-variable configuration is unusable.
2. owner (verified): 1.0.12 is the last release (June 2020); the library is unmaintained.
3. Jackson (a risk, not reproduced against a specific application): jackson-databind 2.22.x is a runtime
   dependency, so on Gradle it raises the Jackson version on users' test runtime classpath.

Requirements:
1. Replace owner with a small loader: system property, then environment variable, then classpath
   ctrf.properties, then the default. The environment lookup accepts both `ctrf.report.path` and
   `CTRF_REPORT_PATH`. Keep every existing key working.
2. Either drop Jackson, or shade and relocate it. The CTRF model is small: a ~150-line writer, plus a
   reader only where CB-6 still needs one. At minimum, don't force a newer Jackson than needed.
3. For logging (HP-3), use java.util.logging rather than adding SLF4J: no new dependency, and JUnit
   itself logs through JUL.
4. Document configuration precedence and environment variable names in the README.
````

#### Acceptance Criteria
- [ ] `CTRF_REPORT_PATH` and the other `CTRF_*` variables work
- [ ] No owner dependency; Jackson is removed or shaded
- [ ] Published runtime dependencies are listed and justified in README or DEVELOPMENT.md

---

## Code Smells & Quality Issues

### CQ-1: Extract Feature Envy from CtrfReportManager

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java` + new files
**Status**: Superseded by CB-7 and HP-6. Do not implement the design below as written.

#### Review Note (2026-10-08)
This logic was extracted on `master` as `FlakyTestDetector`. The design below matches tests by display name (`findTestsByName`, and `mergeRerunResults` comparing names), which is exactly the CB-7 defect. Its `isFlaky` rule ("retries > 0 and passed", "different outcomes") also contradicts the spec definition: final status passed, and at least one earlier failed attempt. Identity and folding are specified in CB-7 and HP-6.

#### Prompt
```
Extract the handleRerunsAndFlaky() and findTestsByName() methods into a dedicated FlakyTestAnalyzer class.

Current issue:
These methods in CtrfReportManager (lines 166-193) operate primarily on the tests list data,
suggesting this logic belongs in a separate class. This is the "Feature Envy" code smell.

Create FlakyTestAnalyzer class:
```java
package io.github.alexshamrai.analysis;

/**
 * Analyzes test results to detect flaky tests and handle test reruns.
 *
 * A test is considered flaky if:
 * - It passed but has retries > 0 (indicating previous failures)
 * - It exists in previous runs with different outcome
 */
public class FlakyTestAnalyzer {

    /**
     * Processes a list of tests to identify and mark flaky tests based on rerun history.
     *
     * @param tests The list of tests to analyze (will be modified in place)
     */
    public void detectAndMarkFlakyTests(List<Test> tests) {
        for (Test test : tests) {
            if (isFlaky(test, tests)) {
                test.setFlaky(true);
            }
        }
    }

    /**
     * Determines if a test is flaky based on retry count and previous executions.
     */
    private boolean isFlaky(Test test, List<Test> allTests) {
        if (test.getRetries() > 0 && "passed".equals(test.getStatus())) {
            return true;
        }

        // Check if there are multiple executions with different outcomes
        List<Test> sameTests = findTestsByName(test.getName(), allTests);
        if (sameTests.size() > 1) {
            Set<String> statuses = sameTests.stream()
                .map(Test::getStatus)
                .collect(Collectors.toSet());
            return statuses.size() > 1;
        }

        return false;
    }

    /**
     * Finds all tests with the given name.
     */
    private List<Test> findTestsByName(String name, List<Test> tests) {
        return tests.stream()
            .filter(t -> name.equals(t.getName()))
            .collect(Collectors.toList());
    }

    /**
     * Merges test results from reruns, keeping the latest result but preserving history.
     *
     * @param existingTests Tests from previous runs
     * @param newTests Tests from current run
     * @return Merged list with flaky tests marked
     */
    public List<Test> mergeRerunResults(List<Test> existingTests, List<Test> newTests) {
        // Implementation of merge logic
        List<Test> merged = new ArrayList<>(newTests);

        // Add tests from existing that aren't in new
        for (Test existing : existingTests) {
            boolean foundInNew = newTests.stream()
                .anyMatch(t -> t.getName().equals(existing.getName()));
            if (!foundInNew) {
                merged.add(existing);
            }
        }

        detectAndMarkFlakyTests(merged);
        return merged;
    }
}
```

Update CtrfReportManager:
1. Add FlakyTestAnalyzer as a dependency
2. Replace handleRerunsAndFlaky() with call to analyzer
3. Remove findTestsByName() method
4. Simplify finishTestRun() method

Benefits:
- Single responsibility for each class
- Easier to test flaky detection logic in isolation
- Can add more sophisticated flaky detection algorithms
- Cleaner CtrfReportManager

Requirements:
1. Create FlakyTestAnalyzer class with comprehensive javadoc
2. Move logic from CtrfReportManager
3. Add extensive unit tests for analyzer
4. Update CtrfReportManager to use analyzer
5. Verify all integration tests pass
6. Update documentation
```

#### Acceptance Criteria
- [ ] FlakyTestAnalyzer class created with clear responsibility
- [ ] All flaky detection logic moved to analyzer
- [ ] CtrfReportManager simplified
- [ ] Comprehensive unit tests for analyzer
- [ ] All integration tests pass
- [ ] Behavior identical to previous implementation
- [ ] Documentation updated

---

### CQ-2: Replace Primitive Obsession with Value Objects

**Priority**: Low
**Complexity**: Moderate
**Files**: Multiple (model package, CtrfReportManager, TestDetails)
**Status**: Open (Low)

#### Review Note (2026-10-08)
Don't name the value object `TestIdentifier`: it clashes with `org.junit.platform.launcher.TestIdentifier`, which `CtrfListener` and `TestIdentifierAdapter` already use. The identity concept itself is defined in CB-7 (`testId` = JUnit `uniqueId`). A name like `TestId` fits.

#### Prompt
```
Replace primitive type parameters with meaningful value objects to improve type safety and clarity.

Current primitive obsession examples:
1. String uniqueId - could be TestIdentifier type
2. long startTime, stopTime - could be TestDuration value object
3. boolean isEnvironmentHealthy - could be EnvironmentHealth enum

Implementation:

1. **Create TestIdentifier value object**:
```java
package io.github.alexshamrai.model;

/**
 * Type-safe wrapper for test unique identifiers.
 * Ensures test IDs are non-null and properly formatted.
 */
public final class TestIdentifier {
    private final String value;

    private TestIdentifier(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Test identifier must not be blank");
        }
        this.value = value;
    }

    public static TestIdentifier of(String value) {
        return new TestIdentifier(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestIdentifier)) return false;
        TestIdentifier that = (TestIdentifier) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
```

2. **Create TestDuration value object**:
```java
package io.github.alexshamrai.model;

/**
 * Represents the duration of a test execution with start and stop times.
 * Ensures temporal consistency (stop >= start).
 */
public final class TestDuration {
    private final long startTimeMillis;
    private final long stopTimeMillis;

    private TestDuration(long startTimeMillis, long stopTimeMillis) {
        if (startTimeMillis < 0) {
            throw new IllegalArgumentException("Start time must be non-negative");
        }
        if (stopTimeMillis < startTimeMillis) {
            throw new IllegalArgumentException("Stop time must be >= start time");
        }
        this.startTimeMillis = startTimeMillis;
        this.stopTimeMillis = stopTimeMillis;
    }

    public static TestDuration of(long startTimeMillis, long stopTimeMillis) {
        return new TestDuration(startTimeMillis, stopTimeMillis);
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getStopTimeMillis() {
        return stopTimeMillis;
    }

    public long getDurationMillis() {
        return stopTimeMillis - startTimeMillis;
    }

    // equals, hashCode, toString
}
```

3. **Create EnvironmentHealth enum**:
```java
package io.github.alexshamrai.model;

/**
 * Represents the health status of the test environment.
 */
public enum EnvironmentHealth {
    HEALTHY(true),
    UNHEALTHY(false);

    private final boolean healthy;

    EnvironmentHealth(boolean healthy) {
        this.healthy = healthy;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public static EnvironmentHealth fromBoolean(boolean healthy) {
        return healthy ? HEALTHY : UNHEALTHY;
    }
}
```

4. **Update TestDetails to use value objects**:
```java
@Data
@Builder
public class TestDetails {
    private final TestIdentifier identifier;
    private final String displayName;
    private final Set<String> tags;
    private final String filePath;
    private final String threadId;
    private TestDuration duration;
}
```

5. **Update CtrfReportManager method signatures**:
```java
// Before
public void onTestStart(String uniqueId)
public void onTestSuccess(String uniqueId)

// After
public void onTestStart(TestIdentifier identifier)
public void onTestSuccess(TestIdentifier identifier)
```

6. **Update internal maps**:
```java
// Before
private final ConcurrentHashMap<String, TestDetails> testDetailsMap = new ConcurrentHashMap<>();

// After
private final ConcurrentHashMap<TestIdentifier, TestDetails> testDetailsMap = new ConcurrentHashMap<>();
```

Migration strategy:
1. Create value objects with comprehensive tests
2. Add overloaded methods accepting both old and new types
3. Deprecate old methods
4. Update internal usage to new types
5. Update entry points (Extension/Listener) to use new types
6. Remove deprecated methods in next major version

This is a significant refactoring - consider if the benefits outweigh the changes required.
```

#### Acceptance Criteria
- [ ] Value objects created with validation
- [ ] Type safety improved throughout codebase
- [ ] Invalid states prevented at compile time
- [ ] All tests updated and passing
- [ ] Backwards compatibility maintained if needed
- [ ] Documentation updated
- [ ] Clear migration path for users

---

### CQ-3: Extract Long Parameter Lists to Context Objects

**Priority**: Low
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/suite/SuiteExecutionErrorHandler.java`
**Status**: Stale

#### Review Note (2026-10-08)
On `master` the class is `src/main/java/io/github/alexshamrai/SuiteExecutionErrorHandler.java` and has only `handleInitializationError(...)`; `handleExecutionError(...)` doesn't exist. If CB-5 and HP-10 move container-failure handling into the listener, this class will probably be removed. Revisit after those items.

#### Prompt
```
Refactor long parameter lists in SuiteExecutionErrorHandler to use context objects.

Current issue (lines 34, 49):
```java
public Optional<Test> handleInitializationError(
    ExtensionContext context, long testRunStartTime, long testRunStopTime)

public Optional<Test> handleExecutionError(
    ExtensionContext context, long testRunStartTime, long testRunStopTime)
```

Both methods take the same three parameters, which should be encapsulated.

Create ErrorContext parameter object:
```java
package io.github.alexshamrai.suite;

/**
 * Context information for handling suite execution errors.
 * Contains the test execution context and timing information.
 */
public final class ErrorContext {
    private final ExtensionContext extensionContext;
    private final long testRunStartTime;
    private final long testRunStopTime;

    private ErrorContext(ExtensionContext extensionContext,
                        long testRunStartTime,
                        long testRunStopTime) {
        this.extensionContext = Objects.requireNonNull(extensionContext, "extensionContext");
        if (testRunStartTime < 0) {
            throw new IllegalArgumentException("testRunStartTime must be non-negative");
        }
        if (testRunStopTime < testRunStartTime) {
            throw new IllegalArgumentException("testRunStopTime must be >= testRunStartTime");
        }
        this.testRunStartTime = testRunStartTime;
        this.testRunStopTime = testRunStopTime;
    }

    public static ErrorContext of(ExtensionContext extensionContext,
                                 long testRunStartTime,
                                 long testRunStopTime) {
        return new ErrorContext(extensionContext, testRunStartTime, testRunStopTime);
    }

    public ExtensionContext getExtensionContext() {
        return extensionContext;
    }

    public long getTestRunStartTime() {
        return testRunStartTime;
    }

    public long getTestRunStopTime() {
        return testRunStopTime;
    }

    public long getDuration() {
        return testRunStopTime - testRunStartTime;
    }
}
```

Update SuiteExecutionErrorHandler:
```java
public Optional<Test> handleInitializationError(ErrorContext context) {
    return handleError(
        context.getExtensionContext(),
        context.getTestRunStartTime(),
        context.getTestRunStopTime(),
        "INITIALIZATION_ERROR"
    );
}

public Optional<Test> handleExecutionError(ErrorContext context) {
    return handleError(
        context.getExtensionContext(),
        context.getTestRunStartTime(),
        context.getTestRunStopTime(),
        "EXECUTION_ERROR"
    );
}
```

Update call sites in CtrfExtension:
```java
// Before
handler.handleInitializationError(context, testRunStartTime, testRunStopTime);

// After
ErrorContext errorContext = ErrorContext.of(context, testRunStartTime, testRunStopTime);
handler.handleInitializationError(errorContext);
```

Benefits:
- Fewer parameters to pass around
- Encapsulated validation in one place
- Easier to add new context fields in the future
- More readable method signatures
```

#### Acceptance Criteria
- [ ] ErrorContext class created with validation
- [ ] Method signatures simplified to single parameter
- [ ] All call sites updated
- [ ] Tests updated and passing
- [ ] Validation ensures valid time ranges
- [ ] Documentation updated

---

### CQ-4: Extract Magic Numbers and Strings to Constants

**Priority**: Low
**Complexity**: Simple
**Files**: `TestProcessor.java`, configuration files, multiple classes
**Status**: Open

#### Review Note (2026-10-08)
On `master`, `"initializationError"` is defined as a constant in `CtrfExtension`, `CtrfListener` and `SuiteExecutionErrorHandler`, and used as a literal in `CtrfReportManager`. The CTRF status strings are already centralised by the `Test.TestStatus` enum and its `@JsonValue`, so no separate status constants are needed.

#### Prompt
```
Extract magic numbers and strings throughout the codebase to named constants for better maintainability.

Current magic values:

1. **TestProcessor.java:22** - Truncation indicator
```java
? trace.substring(0, maxMessageLength) + "..."  // "..." is magic string
```

2. **Default configuration values** (scattered):
- 500 for maxMessageLength
- "ctrf-report.json" for default report path
- Test status strings: "passed", "failed", "skipped", etc.

3. **Environment variable names**:
- "ENV_HEALTHY"

Create constants class:
```java
package io.github.alexshamrai.constants;

/**
 * Application-wide constants for the CTRF reporter.
 */
public final class CtrfConstants {

    private CtrfConstants() {
        throw new UnsupportedOperationException("Constants class");
    }

    // File names and paths
    public static final String DEFAULT_REPORT_FILENAME = "ctrf-report.json";

    // Configuration defaults
    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 500;
    public static final boolean DEFAULT_CALCULATE_STARTUP_DURATION = false;

    // Test status values (matching CTRF spec)
    public static final String STATUS_PASSED = "passed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SKIPPED = "skipped";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_OTHER = "other";

    // Error type identifiers
    public static final String ERROR_TYPE_INITIALIZATION = "INITIALIZATION_ERROR";
    public static final String ERROR_TYPE_EXECUTION = "EXECUTION_ERROR";

    // Environment variable names
    public static final String ENV_VAR_ENVIRONMENT_HEALTHY = "ENV_HEALTHY";

    // Message formatting
    public static final String MESSAGE_TRUNCATION_INDICATOR = "...";
    public static final String MESSAGE_NO_ERROR_MESSAGE = "No error message available";

    // File operation messages
    public static final String MSG_FILE_ALREADY_EXISTS = "File already exists, will be overwritten: %s";
    public static final String MSG_ERROR_READING_REPORT = "Error reading existing report: %s";
    public static final String MSG_ERROR_WRITING_REPORT = "Error writing report to file: %s";
}
```

Update usages:

1. **TestProcessor.java**:
```java
import static io.github.alexshamrai.constants.CtrfConstants.MESSAGE_TRUNCATION_INDICATOR;

// Before
? trace.substring(0, maxMessageLength) + "..."

// After
? trace.substring(0, maxMessageLength) + MESSAGE_TRUNCATION_INDICATOR
```

2. **Configuration interfaces**:
```java
@DefaultValue("${ctrf.report.path:ctrf-report.json}")

// After
import static io.github.alexshamrai.constants.CtrfConstants.DEFAULT_REPORT_FILENAME;

@DefaultValue("${ctrf.report.path:" + DEFAULT_REPORT_FILENAME + "}")
```

3. **EnvironmentHealthTracker**:
```java
// Before
System.getenv("ENV_HEALTHY")

// After
import static io.github.alexshamrai.constants.CtrfConstants.ENV_VAR_ENVIRONMENT_HEALTHY;
System.getenv(ENV_VAR_ENVIRONMENT_HEALTHY)
```

Requirements:
1. Create CtrfConstants class with all magic values
2. Update all usages to reference constants
3. Use static imports where it improves readability
4. Add javadoc explaining each constant
5. Group related constants together
6. Verify all tests still pass
7. Update documentation
```

#### Acceptance Criteria
- [ ] CtrfConstants class created with comprehensive constants
- [ ] No magic numbers in code (except 0, 1, -1 in obvious contexts)
- [ ] No magic strings in code
- [ ] All usages updated to reference constants
- [ ] Constants are well-documented
- [ ] All tests pass
- [ ] Code is more maintainable

---

## Testing Improvements

### TI-1: Add Comprehensive Concurrency Tests

**Priority**: High
**Complexity**: Complex
**Files**: New test file `src/test/java/io/github/alexshamrai/CtrfReportManagerConcurrencyTest.java`
**Status**: Open; update the sample before implementing

#### Review Note (2026-10-08)
The sample code uses APIs that no longer exist: `onTestStart(String)`, `finishTestRun()` with no arguments, and a public no-argument constructor. On `master` the signatures are `onTestStart(TestDetails)` and `finishTestRun(Optional<ExtensionContext>)`. The data loss seen in practice happens across JVMs, across repeated executions (CB-6) and through nested JUnit runs (CB-2), not across threads. The CB-2 race that `testRaceConditionBetweenAddAndFinish` targets isn't reachable; it was checked under parallel load. TI-5 covers the real scenarios and is more valuable. Keep this item for the thread-level guarantees.

#### Prompt
```
Create comprehensive tests to verify thread safety of CtrfReportManager under concurrent access.

Current gap:
No tests verify behavior when multiple threads simultaneously:
- Add test results
- Finish test runs
- Access shared state

Create comprehensive concurrency test suite:

```java
package io.github.alexshamrai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for thread safety and concurrent access to CtrfReportManager.
 */
class CtrfReportManagerConcurrencyTest {

    @RepeatedTest(10) // Repeat to catch intermittent race conditions
    void testConcurrentTestResultAddition() throws Exception {
        // Arrange
        CtrfReportManager manager = createManager();
        int numThreads = 10;
        int testsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - All threads add test results simultaneously
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < testsPerThread; j++) {
                        String uniqueId = "test-" + threadId + "-" + j;
                        manager.onTestStart(uniqueId);
                        manager.onTestSuccess(uniqueId);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);

        // Assert
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(numThreads * testsPerThread);

        manager.finishTestRun();
        // Verify report contains all tests

        executor.shutdown();
    }

    @Test
    void testConcurrentFinishTestRun() throws Exception {
        // Test that only one finishTestRun actually executes
        CtrfReportManager manager = createManager();

        // Add some test results
        manager.onTestStart("test-1");
        manager.onTestSuccess("test-1");

        // Try to finish from multiple threads
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    manager.finishTestRun();
                    executionCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Verify finishTestRun was called by all threads but only executed once
        assertThat(executionCount.get()).isEqualTo(numThreads);
        // Verify report was written only once (check file system)
    }

    @Test
    void testRaceConditionBetweenAddAndFinish() throws Exception {
        // Test the race condition identified in CB-2
        CtrfReportManager manager = createManager();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger testsAdded = new AtomicInteger(0);

        // Thread 1: Keep adding tests
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 100; i++) {
                    String uniqueId = "test-" + i;
                    manager.onTestStart(uniqueId);
                    manager.onTestSuccess(uniqueId);
                    testsAdded.incrementAndGet();
                    Thread.sleep(1); // Small delay
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Thread 2: Try to finish in the middle
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(50); // Let some tests accumulate
                manager.finishTestRun();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Verify all tests that were added before finishTestRun are in the report
        // This test may fail if CB-2 is not fixed
    }

    @Test
    void testConcurrentEnvironmentHealthAccess() throws Exception {
        // Test concurrent access to environment health tracking
        CtrfReportManager manager = createManager();
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        // Half threads mark unhealthy, half check status
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (threadId % 2 == 0) {
                        EnvironmentHealthTracker.markEnvironmentUnhealthy();
                    } else {
                        boolean healthy = EnvironmentHealthTracker.isEnvironmentHealthy();
                        // Just read, don't assert (will be false after first mark)
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(EnvironmentHealthTracker.isEnvironmentHealthy()).isFalse();

        executor.shutdown();
    }

    @Test
    void testHighVolumeParallelExecution() {
        // Simulate real-world scenario with thousands of tests in parallel
        CtrfReportManager manager = createManager();
        int numTests = 10000;

        // Use parallel stream to simulate parallel test execution
        long start = System.currentTimeMillis();

        IntStream.range(0, numTests).parallel().forEach(i -> {
            String uniqueId = "test-" + i;
            manager.onTestStart(uniqueId);

            // Simulate test execution
            if (i % 10 == 0) {
                manager.onTestFailure(uniqueId, new AssertionError("Test failed"));
            } else if (i % 5 == 0) {
                manager.onTestAborted(uniqueId, new RuntimeException("Test aborted"));
            } else {
                manager.onTestSuccess(uniqueId);
            }
        });

        manager.finishTestRun();
        long duration = System.currentTimeMillis() - start;

        System.out.println("Processed " + numTests + " tests in " + duration + "ms");

        // Verify report correctness
        // All 10000 tests should be in report
        // Correct counts for passed/failed/skipped
    }

    private CtrfReportManager createManager() {
        // Create manager with test configuration
        return new CtrfReportManager();
    }
}
```

Additional requirements:
1. Use @RepeatedTest to catch intermittent issues
2. Test with different thread pool sizes
3. Test under high load (1000+ tests)
4. Verify no data loss
5. Verify no duplicate results
6. Verify atomic operations work correctly
7. Use Thread.sleep() strategically to expose race conditions
8. Check for deadlocks with timeout assertions
9. Profile performance under concurrent load

Tools to use:
- ExecutorService for thread pools
- CountDownLatch for synchronization
- AtomicInteger for counting
- @RepeatedTest for flakiness detection
- AssertJ for fluent assertions
```

#### Acceptance Criteria
- [ ] Comprehensive concurrency test suite created
- [ ] Tests cover all concurrent access patterns
- [ ] Tests reliably detect race conditions
- [ ] Tests pass consistently (run 100 times)
- [ ] High-volume test verifies performance
- [ ] Tests document expected thread-safe behavior
- [ ] Any race conditions discovered are documented

---

### TI-2: Add File System Error Tests

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java`
**Status**: Open

#### Review Note (2026-10-08)
Add the MP-5 cases: spec-valid unknown fields such as a root `runId`, a missing `environment.healthy`, and an unparsable file that must be kept rather than overwritten. Also add an atomic-write case (CB-6d).

#### Prompt
```
Add comprehensive tests for file system errors and edge cases in CtrfReportFileService.

Current gap:
Tests don't cover error scenarios like:
- Unwritable directories
- Disk full
- File locked by another process
- Corrupted JSON files
- Permission errors

Add test cases:

```java
@Test
void testWriteToUnwritableDirectory() {
    // Arrange
    Path readOnlyDir = Files.createTempDirectory("readonly");
    readOnlyDir.toFile().setWritable(false);
    Path reportPath = readOnlyDir.resolve("report.json");

    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(reportPath.toString())
    );

    // Act & Assert
    CtrfJson report = createValidReport();

    // Should log error but not throw exception
    service.writeReportToFile(report);

    // Cleanup
    readOnlyDir.toFile().setWritable(true);
}

@Test
void testReadCorruptedJsonFile() throws IOException {
    // Arrange
    Path reportPath = tempDir.resolve("corrupted.json");
    Files.writeString(reportPath, "{invalid json}]}");

    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(reportPath.toString())
    );

    // Act
    List<Test> tests = service.getExistingTests();

    // Assert - Should return empty list, not throw
    assertThat(tests).isEmpty();
}

@Test
void testReadTruncatedJsonFile() throws IOException {
    // Arrange - Simulate file truncated mid-write
    Path reportPath = tempDir.resolve("truncated.json");
    String validJson = "{\"results\":{\"tests\":[{\"name\":\"test1\"}";
    Files.writeString(reportPath, validJson);

    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(reportPath.toString())
    );

    // Act
    CtrfJson report = service.readExistingReport();

    // Assert
    assertThat(report).isNull();
}

@Test
void testWriteLargeReport() {
    // Test with report containing 10,000 tests
    CtrfJson largeReport = createReportWithTests(10_000);

    CtrfReportFileService service = new CtrfReportFileService(configReader);

    // Should complete without error
    assertThatCode(() -> service.writeReportToFile(largeReport))
        .doesNotThrowAnyException();

    // Verify file can be read back
    CtrfJson readBack = service.readExistingReport();
    assertThat(readBack.getResults().getTests()).hasSize(10_000);
}

@Test
void testConcurrentWriteAttempts() throws Exception {
    // Test what happens if two processes try to write simultaneously
    // (Difficult to test reliably, but document behavior)
}

@Test
void testWriteWithSpecialCharactersInPath() {
    // Test paths with spaces, unicode, etc.
    String pathWithSpaces = tempDir.resolve("report with spaces.json").toString();
    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(pathWithSpaces)
    );

    CtrfJson report = createValidReport();
    service.writeReportToFile(report);

    assertThat(Paths.get(pathWithSpaces)).exists();
}

@Test
void testReadFileWithBOM() throws IOException {
    // Test reading file with UTF-8 BOM (byte order mark)
    Path reportPath = tempDir.resolve("with-bom.json");
    byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
    byte[] jsonBytes = validJsonString.getBytes(StandardCharsets.UTF_8);
    byte[] withBom = new byte[bom.length + jsonBytes.length];
    System.arraycopy(bom, 0, withBom, 0, bom.length);
    System.arraycopy(jsonBytes, 0, withBom, bom.length, jsonBytes.length);
    Files.write(reportPath, withBom);

    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(reportPath.toString())
    );

    CtrfJson report = service.readExistingReport();
    assertThat(report).isNotNull();
}
```

Use mocking for some scenarios:
```java
@Test
void testIOExceptionDuringWrite() {
    // Mock ObjectMapper to throw IOException
    ObjectMapper mockMapper = mock(ObjectMapper.class);
    when(mockMapper.writeValue(any(File.class), any()))
        .thenThrow(new IOException("Simulated IO error"));

    // Inject mock and verify error handling
}
```

Requirements:
1. Test all error paths in CtrfReportFileService
2. Verify graceful error handling
3. Test edge cases (empty files, huge files, special characters)
4. Test concurrent access where possible
5. Use temp directories for all file operations
6. Clean up test files in @AfterEach
7. Document expected behavior in comments
```

#### Acceptance Criteria
- [ ] All file system error scenarios tested
- [ ] Edge cases covered
- [ ] Tests use temp directories
- [ ] No files left after tests
- [ ] Error handling verified
- [ ] Large file performance tested
- [ ] Special characters in paths tested
- [ ] Documentation explains expected behavior

---

### TI-3: Improve Test Isolation in EnvironmentHealthTrackerTest

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/test/java/io/github/alexshamrai/EnvironmentHealthTrackerTest.java`
**Status**: Partially done on `master`

#### Review Note (2026-10-08)
On `master`, `EnvironmentHealthTrackerTest` calls `CtrfReportManager.getInstance().resetEnvironmentHealthForTesting()` in `@BeforeEach` and `@AfterEach` (approach 2 below). The tests still share the singleton, so the isolation goals remain open.

#### Prompt
```
Improve test isolation in EnvironmentHealthTrackerTest to avoid dependency on global singleton state.

Current issue:
Tests depend on global singleton state with cleanup in @BeforeEach and @AfterEach:
- Tests are not truly isolated
- Test order could matter
- Parallel execution problematic
- Fragile cleanup

Current approach:
```java
@BeforeEach
void setUp() {
    CtrfReportManager.getInstance(); // Reset via singleton
}

@AfterEach
void tearDown() {
    // Cleanup somehow
}
```

Recommended approach 1: Use package-private constructor
```java
@Test
void testEnvironmentHealthTracking() {
    // Create isolated instance for this test
    CtrfReportFileService fileService = createMockFileService();
    CtrfReportManager manager = new CtrfReportManager(
        fileService,
        new CtrfJsonComposer(...),
        ...
    );

    // Test with this isolated instance
    assertThat(manager.isEnvironmentHealthyInternal()).isTrue();
    manager.markEnvironmentUnhealthyInternal();
    assertThat(manager.isEnvironmentHealthyInternal()).isFalse();

    // No cleanup needed - instance is garbage collected
}
```

Recommended approach 2: Add reset method for testing
```java
// In EnvironmentHealthTracker
@VisibleForTesting
static void resetForTesting() {
    CtrfReportManager.getInstance().resetEnvironmentHealth();
}

// In test
@BeforeEach
void setUp() {
    EnvironmentHealthTracker.resetForTesting();
}

@Test
void testSomething() {
    // Test with clean state
}
```

Recommended approach 3: Extract interface for testability
```java
public interface EnvironmentHealthChecker {
    boolean isHealthy();
    void markUnhealthy();
}

public class CtrfReportManager implements EnvironmentHealthChecker {
    // Implementation
}

// In tests, use mock implementation
@Test
void testWithMockHealth() {
    EnvironmentHealthChecker mockChecker = mock(EnvironmentHealthChecker.class);
    when(mockChecker.isHealthy()).thenReturn(false);

    // Use mock in test
}
```

Requirements:
1. Choose best approach (recommend #1 with package-private constructor)
2. Update all tests to use isolated instances
3. Remove @BeforeEach and @AfterEach cleanup
4. Verify tests can run in parallel
5. Verify tests can run in any order
6. Add test to verify isolation works
7. Document testing approach in javadoc

Benefits:
- True test isolation
- Can run tests in parallel
- No order dependencies
- No cleanup needed
- More reliable tests
- Follows testing best practices
```

#### Acceptance Criteria
- [ ] Tests use isolated instances
- [ ] No shared state between tests
- [ ] Tests can run in parallel
- [ ] Tests pass in random order
- [ ] No cleanup code needed
- [ ] Tests are simpler and more reliable
- [ ] Documentation explains testing approach

---

### TI-4: Validate Integration Output Against the Official Schema and JUnit's Own Counts

**Priority**: High
**Complexity**: Moderate
**Files**: `integration-ctrf-validator/build.gradle`, `integration-ctrf-validator/src/test/resources/schema/ctrf-schema.json`, `.../integration/BaseIntegrationTest.java`, `.../integration/CtrfLogicTest.java`, `.../integration/JsonSchemaValidator.java`, CI workflows
**Status**: Open (verified 2026-10-08)

#### Prompt
````
As it stands, the integration validator cannot detect CB-5, CB-6, HP-5 or HP-8. Make it able to.

Problems:
1. The bundled schema is a looser copy of the official one, so `filepath` and a string buildNumber pass
   (HP-5).
2. The validator library (com.github.java-json-tools:json-schema-validator 2.2.14) only supports
   draft-04, while both schemas declare draft-07.
3. CtrfLogicTest.verifyTestStatuses (lines 31-40) uses findFirst().ifPresent(...), so a missing test
   passes silently. It also matches with contains(), so "Second failed test" (the display name of
   secondFailedTest()) is never checked.
4. BaseIntegrationTest.DEFAULT_REPORT_PATH (lines 14-15) is a sentence, not a path. Running
   `./gradlew :integration-ctrf-validator:test` without -Dctrf.report.path therefore always fails.
5. Several tests only pass with the exact CI parameters: exactly 2 threads, build name "system-build",
   and ENV_HEALTHY=false for the listener.

Requirements:
1. Use a pinned copy of the official schema and a draft-07 validator (shared with HP-5).
2. Cross-check against JUnit's own results for the same run: compare CTRF tests/failed/skipped with the
   Gradle JUnit XML in build/test-results/test (sum of tests, failures, errors and skipped; folded per
   logical test once HP-6 is done). This would have caught CB-5, CB-6a and HP-8 automatically.
3. Assertions must require the test to be present, and match by testId (CB-7) rather than by a
   display-name substring.
4. Use a real default path, e.g. ../integration-tests-listener/build/test-results/ctrf-report.json, or
   fail with a clear message.
5. Make the CI-specific expectations configurable through system properties.
6. Add a CI variant with maxParallelForks=2 (CB-6).
````

#### Acceptance Criteria
- [ ] The validator fails on today's `master` output (`filepath`, `buildNumber`, data lost across forks)
- [ ] The validator passes once HP-5 and CB-6 are fixed
- [ ] The validator runs locally with documented defaults

---

### TI-5: Regression Tests for Every Verified Scenario

**Priority**: High
**Complexity**: Moderate
**Files**: new tests under `src/test/java/io/github/alexshamrai/scenario/` (or a new integration module), fake test classes
**Status**: Open (2026-10-08)

#### Prompt
````
Turn the scenarios from the 2026-10-08 review into fast, deterministic regression tests.

Harness used in the review. It runs the JUnit Platform in-process with an explicitly registered listener:
```java
Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
    .enableTestExecutionListenerAutoRegistration(false)
    .addTestExecutionListeners(new CtrfListener())
    .build());
LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
    .selectors(selectClass(CollisionATest.class), selectClass(CollisionBTest.class))
    .configurationParameter("junit.jupiter.testclass.order.default",
        "org.junit.jupiter.api.ClassOrderer$ClassName")
    .build();
SummaryGeneratingListener junitCounts = new SummaryGeneratingListener();
launcher.execute(request, junitCounts);
// Point ctrf.report.path at a temp file, then read the CTRF report and compare it
// with junitCounts.getSummary().
```
Many fake classes fail on purpose. Put them in a package the build's own test task does not pick up,
or exclude them.

Scenarios, with the results expected after the fixes:
| Scenario | Classes | Item |
|---|---|---|
| Name collisions across classes | CollisionATest (failing) and CollisionBTest (passing), both with shouldWork(), param(int) x2 and @RepeatedTest(2) | CB-7 |
| Same method name, both passing | DupPass1Test.common(), DupPass2Test.common() | CB-7 |
| Skipped containers | a @Disabled class; an @EnabledOnOs(OS.WINDOWS) class | HP-8 |
| Assumption | assumeTrue(false, ...) | HP-7 |
| Broken sources | an @MethodSource that throws; a @TestFactory that throws | CB-5 |
| Same-JVM rerun | RerunTest: stable(), plus flakyOnce() that fails on its first call; two launcher.execute calls | CB-6c |
| Two JVMs, one report | SlowTest and FastTest in two concurrent JVMs (ProcessBuilder) | CB-6a |
| Accumulation | the same class run twice in separate JVMs | CB-6b |
| Extension identity | @ExtendWith({FailingBeforeEachExtension.class, CtrfExtension.class}) | HP-9 |
| Extension @AfterAll | @AfterAll throws in a class that is not the first to run | CB-5 |
| Nested JUnit run | NestAOuterFirstTest, NestBLauncherTest (runs FastTest through LauncherFactory.create()), NestCOuterLastTest; CtrfListener registered via META-INF/services | CB-2 |
| Tolerant read | previous report with a root runId; previous report without healthy | MP-5 |
| Official schema | validate the output of every scenario | HP-5 |

The CtrfReportManager singleton keeps state across scenarios within one JVM. Either give each scenario
a fresh manager (HP-1), or run each scenario in a forked JVM.
````

#### Acceptance Criteria
- [ ] Every scenario above has an automated test
- [ ] The tests fail on today's `master` and pass after the corresponding fixes
- [ ] The scenario suite runs in CI in under a minute

---

## Documentation Improvements

### DI-1: Add Comprehensive Javadoc

**Priority**: Medium
**Complexity**: Moderate
**Files**: All public classes and methods
**Status**: Open

#### Review Note (2026-10-08)
The sample class Javadoc below repeats the old flaky rule: "pass after previous failures (retries > 0) or if multiple executions have different outcomes". Use the spec definition instead: the final status is passed, and at least one earlier attempt failed (CB-7). The sample method signatures are also outdated (see the TI-1 note).

#### Prompt
```
Add comprehensive Javadoc documentation to all public APIs.

Current state:
- 56 public methods across 14 files
- Only 31 @param, @return, @throws annotations
- Missing documentation for CtrfReportManager public methods
- No package-level documentation

Requirements for each public class:
1. Class-level javadoc with:
   - Brief description (one line)
   - Detailed explanation of purpose
   - Usage examples
   - Thread safety guarantees
   - @since tag
   - @author tag (optional)

2. Method-level javadoc with:
   - Brief description
   - @param for each parameter
   - @return for non-void methods
   - @throws for checked and important unchecked exceptions
   - Usage examples for complex methods
   - Thread safety notes if relevant

3. Package-level documentation:
   - Create package-info.java in each package
   - Explain package purpose
   - List main classes
   - Explain relationships

Example for CtrfReportManager:
```java
package io.github.alexshamrai;

/**
 * Central coordinator for CTRF test report generation.
 *
 * <p>This singleton class manages the lifecycle of test execution reporting,
 * collecting test results from JUnit callbacks and orchestrating the generation
 * of CTRF-compliant JSON reports.
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe and designed for concurrent test execution. Test results
 * can be reported from multiple threads simultaneously. The {@link #finishTestRun()}
 * method uses atomic operations to ensure report generation occurs exactly once.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CtrfReportManager manager = CtrfReportManager.getInstance();
 *
 * // In test callbacks:
 * manager.onTestStart("test-id");
 * manager.onTestSuccess("test-id");
 *
 * // After all tests complete:
 * manager.finishTestRun();
 * }</pre>
 *
 * <h2>Flaky Test Detection</h2>
 * Tests are automatically marked as flaky if they pass after previous failures
 * (retries > 0) or if multiple executions have different outcomes.
 *
 * @since 0.1.0
 * @see CtrfExtension
 * @see CtrfListener
 * @see EnvironmentHealthTracker
 */
public class CtrfReportManager {

    /**
     * Retrieves the singleton instance of the report manager.
     *
     * <p>The instance is eagerly initialized and thread-safe.
     *
     * @return the singleton instance, never {@code null}
     */
    public static CtrfReportManager getInstance() {
        return INSTANCE;
    }

    /**
     * Records the start of test execution.
     *
     * <p>This method should be called when a test begins execution, before
     * any test logic runs. It initializes tracking state for the test.
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be
     * called concurrently from multiple test threads.
     *
     * @param uniqueId the unique identifier for the test, must not be {@code null} or blank
     * @throws IllegalArgumentException if uniqueId is {@code null} or blank
     */
    public void onTestStart(String uniqueId) {
        // Implementation
    }

    /**
     * Records a successful test execution.
     *
     * <p>This method should be called after a test completes successfully.
     * The test result will be included in the final report with status "passed".
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be
     * called concurrently from multiple test threads.
     *
     * @param uniqueId the unique identifier for the test, must match a previous
     *                 {@link #onTestStart(String)} call
     * @throws IllegalArgumentException if uniqueId is {@code null} or blank
     * @throws IllegalStateException if no matching test start was recorded
     */
    public void onTestSuccess(String uniqueId) {
        // Implementation
    }

    /**
     * Finalizes report generation and writes the CTRF JSON file.
     *
     * <p>This method should be called exactly once after all tests complete.
     * It performs the following operations:
     * <ol>
     *   <li>Detects flaky tests based on retry count and execution history</li>
     *   <li>Merges results with previous test runs if applicable</li>
     *   <li>Composes the CTRF JSON structure with metadata</li>
     *   <li>Writes the report to the configured file path</li>
     *   <li>Clears internal test state</li>
     * </ol>
     *
     * <p><strong>Thread Safety:</strong> This method uses atomic operations to
     * ensure it executes exactly once, even if called from multiple threads.
     * Subsequent calls are safely ignored.
     *
     * <p><strong>Important:</strong> This method must be called after all test
     * execution callbacks ({@link #onTestSuccess}, {@link #onTestFailure}, etc.)
     * have completed to ensure all test results are included in the report.
     *
     * @throws IllegalStateException if called before {@link #startTestRun()}
     */
    public void finishTestRun() {
        // Implementation
    }
}
```

Package-info.java example:
```java
/**
 * Core components of the CTRF test report generator.
 *
 * <p>This package contains the main classes responsible for collecting test
 * execution data and generating CTRF-compliant JSON reports.
 *
 * <h2>Main Classes</h2>
 * <ul>
 *   <li>{@link io.github.alexshamrai.CtrfReportManager} - Central coordinator</li>
 *   <li>{@link io.github.alexshamrai.CtrfReportFileService} - File I/O operations</li>
 *   <li>{@link io.github.alexshamrai.CtrfJsonComposer} - JSON structure assembly</li>
 *   <li>{@link io.github.alexshamrai.TestProcessor} - Test result processing</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <p>This package is used by:
 * <ul>
 *   <li>{@link io.github.alexshamrai.jupiter.CtrfExtension} - JUnit Jupiter Extension</li>
 *   <li>{@link io.github.alexshamrai.launcher.CtrfListener} - JUnit Platform Listener</li>
 * </ul>
 *
 * @since 0.1.0
 */
package io.github.alexshamrai;
```

Files requiring documentation:
1. CtrfReportManager - all public methods
2. EnvironmentHealthTracker - all public methods
3. CtrfReportFileService - all public methods
4. CtrfExtension - class and callbacks
5. CtrfListener - class and callbacks
6. All model classes - class level
7. All utility classes
8. Create package-info.java for each package

Guidelines:
- Use present tense ("Returns" not "Will return")
- Be concise but complete
- Include examples for complex functionality
- Document thread safety guarantees
- Document null handling
- Use {@code} for code elements
- Use {@link} for cross-references
- Use <p> for paragraph breaks
- Use <ul>/<ol> for lists
- Use @throws for exceptions
```

#### Acceptance Criteria
- [ ] All public classes have class-level javadoc
- [ ] All public methods have complete javadoc
- [ ] All @param, @return, @throws documented
- [ ] Package-info.java created for each package
- [ ] Thread safety documented where relevant
- [ ] Examples provided for complex APIs
- [ ] Javadoc builds without warnings
- [ ] Documentation is accurate and helpful

---

### DI-2: Add Architecture Documentation

**Priority**: Medium
**Complexity**: Moderate
**Files**: New file `ARCHITECTURE.md`
**Status**: Open

#### Review Note (2026-10-08)
Describe the target architecture from the Review Update rather than the current design: listener first, attempts keyed by `testId`, shards plus a merge step. Drop the "Why CopyOnWriteArrayList?" section (see MP-3 and HP-6). Document the report semantics: one test per logical test, `retries`/`retryAttempts`, and the flaky definition.

#### Prompt
```
Create comprehensive architecture documentation explaining the design and implementation of the library.

Create ARCHITECTURE.md with the following sections:

# Architecture Documentation

## Overview
High-level description of the library's purpose and architecture

## Design Principles
- Single Responsibility Principle
- Thread Safety
- Extensibility
- etc.

## Component Diagram
```
[CtrfExtension] ────┐
                    ├──→ [CtrfReportManager] ──→ [CtrfJsonComposer]
[CtrfListener] ─────┘              │                     │
                                   │                     ↓
                                   ↓              [CtrfReportFileService]
                            [TestProcessor]              │
                                                         ↓
                                                   [JSON File]
```

## Core Components

### CtrfReportManager
- Role: Central coordinator
- Responsibilities:
  - Test lifecycle management
  - State tracking
  - Report orchestration
- Thread Safety: Uses concurrent collections
- Singleton Pattern: Why and how

### Entry Points

#### CtrfExtension
- JUnit Jupiter Extension
- Lifecycle hooks
- When to use

#### CtrfListener
- JUnit Platform Listener
- Registration methods
- When to use

### Data Flow

Detailed sequence diagram showing:
1. Test starts
2. Extension/Listener notified
3. Manager records state
4. Test completes
5. Manager updates result
6. All tests complete
7. Report generated
8. File written

### Concurrency Model

Explain thread safety approach:
- CopyOnWriteArrayList for tests
- ConcurrentHashMap for test details
- AtomicBoolean for flags
- Why no explicit locks
- Race condition considerations

### Configuration

- Configuration loading priority
- Owner library integration
- System properties vs files
- Extension points

### Error Handling Strategy

- Where errors are caught
- How errors are reported
- Graceful degradation
- User notification

## Extension Points

How to extend the library:
- Custom metadata
- Alternative storage
- Custom test status mapping

## Design Decisions

Document key decisions and rationale:

### Why Singleton?
Pros, cons, alternatives considered

### Why Two Entry Points?
Extension vs Listener tradeoffs

### Why Lombok?
Benefits and drawbacks

### Why CopyOnWriteArrayList?
Performance considerations

## Testing Strategy

- Unit test approach
- Integration test approach
- Concurrency testing
- Test isolation techniques

## Performance Considerations

- Memory usage
- CPU usage
- File I/O impact
- Large test suite handling
- Benchmarks

## Future Enhancements

- Planned improvements
- Extension possibilities
- API evolution

## Glossary

Define terms:
- CTRF
- Flaky test
- Test rerun
- Environment health
etc.

Requirements:
1. Include sequence diagrams (can use ASCII art or mermaid.js)
2. Include component diagrams
3. Explain all design patterns used
4. Document threading model clearly
5. Explain all major design decisions
6. Link to relevant code locations
7. Keep diagrams up to date with code
```

#### Acceptance Criteria
- [ ] ARCHITECTURE.md created
- [ ] All major components documented
- [ ] Diagrams explain data flow
- [ ] Threading model explained clearly
- [ ] Design decisions documented with rationale
- [ ] Extension points documented
- [ ] Testing strategy explained
- [ ] Document is kept up to date

---

### DI-3: Fix Incorrect Documentation

**Priority**: Medium
**Complexity**: Simple
**Files**: `README.md`, `launcher/CtrfListener.java`, `CLAUDE.md`, `config/CtrfConfig.java`
**Status**: Open (verified 2026-10-08)

#### Prompt
````
Fix documentation that is wrong today:
1. README dependency snippets: `implementation` (Gradle) and Maven without <scope>test</scope> put the
   reporter on the production classpath. Use testImplementation and test scope (CB-4).
2. The CtrfListener class Javadoc (CtrfListener.java:26-27) suggests
   `-Djunit.platform.launcher.listeners.discovery=io.github.alexshamrai.launcher.CtrfListener`. That is
   not a JUnit configuration key; it is the name of a package inside junit-platform-launcher. The only
   listener-related key is junit.platform.execution.listeners.deactivate. Document ServiceLoader
   registration (META-INF/services) and programmatic registration instead.
3. CLAUDE.md:
   - "JUnit ABORTED -> skipped" doesn't match the code, which says failed. Fix the code (HP-7).
   - The version "0.4.1 / 0.4.2-SNAPSHOT" doesn't match build.gradle (0.4.5 / 0.4.6-SNAPSHOT).
   - "Concurrency Design": "read-heavy workload" and "No explicit locks" are wrong. The workload is
     write-heavy, and CopyOnWriteArrayList.add takes a lock. Update after MP-3/HP-6.
4. CtrfConfig Javadoc and CLAUDE.md: environment-variable configuration only works with dotted names
   (MP-9). Document the real behaviour until it is fixed.
5. README: once implemented, document the report semantics (one test per logical test,
   retries/retryAttempts, the flaky definition) and the multi-fork setup (CB-6).
6. README "Usage options": state that CtrfListener and CtrfExtension are alternatives. Use one or the
   other; registering both in the same run is not supported.
````

#### Acceptance Criteria
- [ ] Every documented setting and command works as written
- [ ] README dependency snippets are test-scoped
- [ ] README says to use either the listener or the extension, not both
- [ ] CLAUDE.md matches the code

---

## Security & Performance

### SP-1: Add Path Validation for Security

**Priority**: Low
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java`
**Status**: Re-evaluated 2026-10-08: not recommended as specified

#### Review Note (2026-10-08)
The report path is not a trust boundary. It comes from the build owner's own configuration (system properties, environment, a classpath file), and whoever controls that configuration can already run arbitrary code in the build. Rejecting `..` would break legitimate multi-module setups: the test JVM's working directory is the module directory, so writing to a parent build directory needs `../`. Keep only clear error messages for paths that can't be written (MP-4), and drop the `SecurityException` checks below.

#### Prompt
```
Add path validation to prevent path traversal vulnerabilities in report file path configuration.

Current issue:
No validation of the report path. Malicious configuration could write to arbitrary locations:
- ../../../../etc/passwd
- /tmp/malicious.json
- C:\Windows\System32\config\sam

While this is configuration-controlled (not user input), it's still a security risk in
shared environments or CI/CD pipelines.

Implementation:

1. **Create PathValidator utility**:
```java
package io.github.alexshamrai.validation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Validates file paths for security and accessibility.
 */
public class PathValidator {

    /**
     * Validates that a path is safe for writing report files.
     *
     * <p>Checks performed:
     * <ul>
     *   <li>Path is not absolute outside current working directory (configurable)</li>
     *   <li>Path does not contain ".." traversal</li>
     *   <li>Parent directory exists or can be created</li>
     *   <li>Parent directory is writable</li>
     *   <li>Filename has valid extension</li>
     * </ul>
     *
     * @param pathString the path to validate
     * @param allowAbsolutePaths whether to allow absolute paths
     * @return validated Path object
     * @throws SecurityException if path is unsafe
     * @throws IllegalArgumentException if path is invalid
     */
    public static Path validateReportPath(String pathString, boolean allowAbsolutePaths) {
        if (pathString == null || pathString.isBlank()) {
            throw new IllegalArgumentException("Report path must not be blank");
        }

        Path path = Paths.get(pathString);
        Path normalizedPath = path.normalize();

        // Check for path traversal
        if (normalizedPath.toString().contains("..")) {
            throw new SecurityException(
                "Report path contains illegal '..' traversal: " + pathString);
        }

        // Check absolute paths if not allowed
        if (!allowAbsolutePaths && path.isAbsolute()) {
            // Allow absolute paths only within project directory
            Path currentDir = Paths.get("").toAbsolutePath();
            try {
                Path resolved = currentDir.resolve(normalizedPath).normalize();
                if (!resolved.startsWith(currentDir)) {
                    throw new SecurityException(
                        "Absolute report path outside project directory: " + pathString);
                }
            } catch (Exception e) {
                throw new SecurityException("Invalid report path: " + pathString, e);
            }
        }

        // Validate parent directory
        Path parentDir = normalizedPath.getParent();
        if (parentDir != null) {
            if (!Files.exists(parentDir)) {
                // Try to create parent directories
                try {
                    Files.createDirectories(parentDir);
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                        "Cannot create parent directory: " + parentDir, e);
                }
            }

            if (!Files.isWritable(parentDir)) {
                throw new IllegalArgumentException(
                    "Parent directory is not writable: " + parentDir);
            }
        }

        // Validate filename
        String filename = normalizedPath.getFileName().toString();
        if (!filename.endsWith(".json")) {
            // Warning, not error (allow other extensions but log)
            System.err.println("Warning: Report file does not have .json extension: " + filename);
        }

        return normalizedPath;
    }

    /**
     * Validates with default settings (allow absolute paths).
     */
    public static Path validateReportPath(String pathString) {
        return validateReportPath(pathString, true);
    }
}
```

2. **Update CtrfReportFileService constructor**:
```java
public CtrfReportFileService(ConfigReader configReader) {
    this.configReader = configReader;

    // Validate path on construction
    String configuredPath = configReader.getReportPath();
    this.validatedPath = PathValidator.validateReportPath(configuredPath);
}
```

3. **Add configuration option**:
```java
// In CtrfConfig interface
@Key("ctrf.validate.report.path")
@DefaultValue("true")
boolean validateReportPath();

@Key("ctrf.allow.absolute.paths")
@DefaultValue("true")
boolean allowAbsolutePaths();
```

4. **Add tests**:
```java
@Test
void testPathTraversalBlocked() {
    assertThatThrownBy(() ->
        PathValidator.validateReportPath("../../../../etc/passwd"))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("path traversal");
}

@Test
void testAbsolutePathOutsideProjectBlocked() {
    assertThatThrownBy(() ->
        PathValidator.validateReportPath("/tmp/report.json", false))
        .isInstanceOf(SecurityException.class);
}

@Test
void testValidRelativePath() {
    Path validated = PathValidator.validateReportPath("build/ctrf-report.json");
    assertThat(validated).isNotNull();
}

@Test
void testValidAbsolutePathInProject() {
    Path projectPath = Paths.get("").toAbsolutePath();
    String absolutePath = projectPath.resolve("report.json").toString();

    Path validated = PathValidator.validateReportPath(absolutePath);
    assertThat(validated).isNotNull();
}
```

5. **Document security considerations**:
- Add section to README about path configuration
- Document in CLAUDE.md
- Add javadoc warnings

Requirements:
1. Implement PathValidator with comprehensive checks
2. Integrate into CtrfReportFileService
3. Add configuration options
4. Add comprehensive tests for all scenarios
5. Document security behavior
6. Consider backward compatibility (don't break existing valid paths)
```

#### Acceptance Criteria
- [ ] PathValidator created with security checks
- [ ] Path traversal attempts blocked
- [ ] Dangerous absolute paths blocked or restricted
- [ ] Valid paths work correctly
- [ ] Configuration options added
- [ ] Comprehensive security tests
- [ ] Documentation explains security model
- [ ] Backward compatibility maintained
- [ ] No false positives on valid paths

---

### SP-2: Performance Benchmarking Suite

**Priority**: Low
**Complexity**: Moderate
**Files**: New file `src/test/java/io/github/alexshamrai/benchmark/PerformanceBenchmarks.java`
**Status**: Open

#### Review Note (2026-10-08)
Baseline numbers measured on `master` are in MP-3 (10k tests 0.4 s, 80k tests 47.9 s for event processing). The sample code uses outdated APIs (see the TI-1 note). The most useful benchmark is event processing against test count, which should be linear after HP-6.

#### Prompt
```
Create a performance benchmarking suite to measure and track performance characteristics.

Goals:
1. Establish performance baselines
2. Detect performance regressions
3. Identify bottlenecks
4. Guide optimization efforts

Create comprehensive benchmarks:

```java
package io.github.alexshamrai.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

/**
 * Performance benchmarks for the CTRF reporter.
 *
 * <p>These tests measure performance characteristics and are disabled by default.
 * Enable with: -DenableBenchmarks=true
 *
 * <p>Results should be tracked over time to detect regressions.
 */
@Disabled("Enable manually for benchmarking")
class PerformanceBenchmarks {

    @Test
    void benchmarkTestResultCollection() {
        // Measure overhead of recording test results
        int[] testCounts = {100, 1000, 5000, 10000};

        for (int numTests : testCounts) {
            CtrfReportManager manager = new CtrfReportManager();

            long startTime = System.nanoTime();
            long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            for (int i = 0; i < numTests; i++) {
                String id = "test-" + i;
                manager.onTestStart(id);
                manager.onTestSuccess(id);
            }

            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;
            long memoryUsedMb = (endMemory - startMemory) / (1024 * 1024);
            double perTestUs = (endTime - startTime) / 1000.0 / numTests;

            System.out.printf("%d tests: %d ms (%.2f μs/test), Memory: %d MB%n",
                numTests, durationMs, perTestUs, memoryUsedMb);
        }
    }

    @Test
    void benchmarkReportGeneration() {
        // Measure time to generate report
        int[] testCounts = {100, 1000, 5000, 10000};

        for (int numTests : testCounts) {
            CtrfReportManager manager = new CtrfReportManager();

            // Populate with test results
            for (int i = 0; i < numTests; i++) {
                String id = "test-" + i;
                manager.onTestStart(id);
                if (i % 10 == 0) {
                    manager.onTestFailure(id, new AssertionError("Failed"));
                } else {
                    manager.onTestSuccess(id);
                }
            }

            // Measure report generation
            long startTime = System.nanoTime();
            manager.finishTestRun();
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;

            System.out.printf("%d tests: Report generation took %d ms%n",
                numTests, durationMs);
        }
    }

    @Test
    void benchmarkFileWritePerformance() {
        // Measure file I/O performance
        int[] testCounts = {100, 1000, 5000, 10000};

        for (int numTests : testCounts) {
            CtrfJson report = createReportWithTests(numTests);
            Path tempFile = Files.createTempFile("benchmark-", ".json");

            CtrfReportFileService service = new CtrfReportFileService(configReader);

            long startTime = System.nanoTime();
            service.writeReportToFile(report);
            long endTime = System.nanoTime();

            long fileSizeKb = Files.size(tempFile) / 1024;
            long durationMs = (endTime - startTime) / 1_000_000;

            System.out.printf("%d tests: Write took %d ms, File size: %d KB%n",
                numTests, durationMs, fileSizeKb);

            Files.delete(tempFile);
        }
    }

    @Test
    void benchmarkConcurrentExecution() {
        // Measure performance under concurrent load
        int numThreads = Runtime.getRuntime().availableProcessors();
        int testsPerThread = 1000;

        CtrfReportManager manager = new CtrfReportManager();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        long startTime = System.nanoTime();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < testsPerThread; i++) {
                        String id = "test-" + threadId + "-" + i;
                        manager.onTestStart(id);
                        manager.onTestSuccess(id);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        long endTime = System.nanoTime();

        executor.shutdown();

        int totalTests = numThreads * testsPerThread;
        long durationMs = (endTime - startTime) / 1_000_000;
        double throughput = totalTests * 1000.0 / durationMs;

        System.out.printf("%d threads × %d tests = %d total: %d ms (%.0f tests/sec)%n",
            numThreads, testsPerThread, totalTests, durationMs, throughput);
    }

    @Test
    void benchmarkFlakyTestDetection() {
        // Measure cost of flaky test detection algorithm
        int numTests = 1000;
        int numReruns = 10;

        List<Test> tests = new ArrayList<>();
        for (int i = 0; i < numTests; i++) {
            for (int r = 0; r < numReruns; r++) {
                Test test = Test.builder()
                    .name("test-" + i)
                    .status(r % 2 == 0 ? "passed" : "failed")
                    .build();
                tests.add(test);
            }
        }

        CtrfReportManager manager = new CtrfReportManager();

        long startTime = System.nanoTime();
        // Call flaky detection method
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;

        System.out.printf("Flaky detection for %d tests × %d reruns: %d ms%n",
            numTests, numReruns, durationMs);
    }

    @Test
    void benchmarkCollectionPerformance() {
        // Compare CopyOnWriteArrayList vs alternatives
        int numTests = 10000;

        // Benchmark CopyOnWriteArrayList
        long cowTime = benchmarkCollection(new CopyOnWriteArrayList<>(), numTests);

        // Benchmark synchronized ArrayList
        long syncTime = benchmarkCollection(
            Collections.synchronizedList(new ArrayList<>()), numTests);

        // Benchmark ConcurrentLinkedQueue
        Queue<Test> queue = new ConcurrentLinkedQueue<>();
        long queueTime = benchmarkQueue(queue, numTests);

        System.out.printf("CopyOnWrite: %d ms%n", cowTime);
        System.out.printf("Synchronized: %d ms%n", syncTime);
        System.out.printf("Queue: %d ms%n", queueTime);
    }

    private long benchmarkCollection(List<Test> list, int numTests) {
        long startTime = System.nanoTime();
        for (int i = 0; i < numTests; i++) {
            list.add(createTestResult());
        }
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1_000_000;
    }
}
```

Additional requirements:
1. Create benchmark runner script
2. Document how to run benchmarks
3. Set up CI job to track performance over time
4. Create performance regression tests (fail if > X% slower)
5. Generate performance reports
6. Track results in CSV/JSON for trend analysis

Output format:
```
=== CTRF Reporter Performance Benchmarks ===
Date: 2025-01-15
JVM: OpenJDK 17.0.2
OS: Linux 5.15.0

Test Result Collection:
  100 tests: 5 ms (50 μs/test), Memory: 1 MB
  1000 tests: 45 ms (45 μs/test), Memory: 8 MB
  5000 tests: 225 ms (45 μs/test), Memory: 35 MB
  10000 tests: 450 ms (45 μs/test), Memory: 70 MB

Report Generation:
  100 tests: 25 ms
  1000 tests: 180 ms
  5000 tests: 850 ms
  10000 tests: 1700 ms

... etc
```
```

#### Acceptance Criteria
- [ ] Comprehensive benchmark suite created
- [ ] Benchmarks cover all major operations
- [ ] Results are reproducible
- [ ] Benchmarks documented
- [ ] CI integration planned
- [ ] Performance baselines established
- [ ] Regression detection implemented
- [ ] Reports are easy to understand

---

## Implementation Order

Updated 2026-10-08. This replaces the original four-phase order, whose Phase 1 and most of Phase 2 are already done on `master`.

### Already done on `master`
CB-1, CB-3, HP-2 (mostly), HP-4, MP-1, MP-2 (its regression is tracked in CB-6), TI-3 (partially)

### Phase 1: Stop wrong results and broken builds (small, independent changes)
1. CB-4: Remove JUnit runtime dependencies, fix the README scopes, add the consumer matrix
2. CB-7: Identify tests by `uniqueId` (`testId`) and fix the flaky rule
3. CB-5: Report every failed container
4. HP-8: Report tests inside skipped containers
5. HP-7: Report aborted tests as skipped
6. HP-9: Keep test identity in the extension
7. TI-5: Write the regression scenario for each of the fixes above alongside it

### Phase 2: Data model and persistence (changes the report shape, so bump the minor version)
8. HP-6: One test object per logical test, with `retryAttempts` (also resolves MP-3)
9. CB-6: Shards plus merge, run identity, atomic writes
10. HP-5: Conform to the official schema
11. TI-4: Validator using the official schema and a cross-check against JUnit's counts
12. MP-5: Read previous reports tolerantly

### Phase 3: Architecture and robustness
13. HP-10: Listener as the single core integration point, then revisit HP-1
14. CB-2: Only the run that started reporting may end it (nested JUnit runs)
15. MP-6: `AutoCloseable` instead of `CloseableResource`
16. MP-7: Reporter errors never affect test outcomes
17. MP-8: Separate `message` from `trace`
18. MP-9: Dependency and configuration hygiene (includes HP-3, using JUL)

### Phase 4: Polish
19. DI-3, DI-1, DI-2: Documentation
20. MP-4, CQ-2, CQ-4, TI-1, TI-2, SP-2

### Not planned
- CQ-1: superseded by CB-7 and HP-6
- CQ-3: stale; revisit after CB-5 and HP-10
- SP-1: not recommended as specified

---

## Notes

- The original items can be implemented independently. The 2026-10-08 items have dependencies, noted in each item: HP-6 builds on CB-7, CB-6's merge needs `testId`, and HP-8 shares code with CB-5 and HP-7.
- Tests should be added/updated for each change
- Document breaking changes in CHANGELOG
- Consider semantic versioning for releases
- Keep CLAUDE.md updated with architectural changes
- File paths and line numbers in items added or updated on 2026-10-08 refer to `master` @ `69fa713`. This branch is based on v0.4.3.

---

Generated: 2025-01-20
Updated: 2026-10-08 (behaviour-focused review of `master` @ `69fa713`)
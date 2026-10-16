# JUnit CTRF Reporter - Comprehensive Improvement Plan

This document provides a detailed, actionable plan for addressing code quality issues, bugs, and architectural improvements identified in the codebase review.

> **Updated 2026-10-08** after a second, behaviour-focused review of `master` @ `69fa713` (0.4.5 plus dependency updates). Read [Review Update — 2026-10-08](#review-update--2026-10-08) first. It records which original items are already done on `master`, and adds 18 new items for defects that were reproduced by running them, not just inferred from the code.

> **Progress 2026-10-16:** Phases 1 and 2 are released as 0.5.0, and Phase 3 as 0.5.1. Since 0.5.1, two build and test changes are merged; they don't change the published jar: the build uses JDK 25 and tests on Java 17, 21 and 25 (#140), and Jackson 2 is gone from the tests (#141). 0.5.2 is on `master` and not released yet: streaming JSON, the fix for 0.5.1's heap use (#142, [0.5.2 Design — streaming JSON](#052-design--streaming-json)), and the five deferred review minors (#143). Phase 4 has started: [HP-12](#hp-12-design--suite), which fills `suite`, is merged (#144) and not released either; it's a feature, so semver says the next release is 0.6.0. Two changes followed that don't change the jar: `CLAUDE.md`'s broken commands and stale class names are fixed (DI-3, #145), and every scenario's report is checked against the official schema (TI-5, #146). [Status on `master` — 2026-10-14](#status-on-master--2026-10-14) lists what is done.

## How to Use This Plan

Each improvement item includes:
- **Priority**: Critical/High/Medium/Low
- **Complexity**: Simple/Moderate/Complex
- **Prompt**: Detailed instructions for implementation
- **Acceptance Criteria**: How to verify the fix is complete
- **Files Affected**: Which files need changes
- **Status** (added 2026-10-08): Done / Partially done / Open / Re-evaluated / Superseded / Stale, relative to `master`

---

## Status on `master` — 2026-10-14

`master` @ `513c03d` (updated 2026-10-16). Streaming JSON (#142) and the review minors (#143) are merged for 0.5.2, which isn't released yet. Phase 3 is released as [0.5.1](https://github.com/alexshamrai/junit-ctrf-reporter/releases/tag/v0.5.1) (2026-10-16, release PR #139). Phases 1 and 2 are released as [0.5.0](https://github.com/alexshamrai/junit-ctrf-reporter/releases/tag/v0.5.0) (2026-10-15, Maven Central `io.github.alexshamrai:junit-ctrf-reporter:0.5.0`, release PR #136).

| Item | Status | PR | Commits |
|---|---|---|---|
| [CB-4](#cb-4-stop-forcing-junit-runtime-dependencies-on-consumers) Stop forcing JUnit runtime dependencies | Done | #124 | `8f88975`, `156ea67` |
| [CB-5](#cb-5-report-every-failed-container-not-only-class-level-ones) Report every failed container | Done; one known gap in the extension | #125, #131 | `151c9c8`, `58e36f0` |
| [CB-7](#cb-7-identify-tests-by-uniqueid-not-display-name) Identify tests by `uniqueId` | Done | #126 | `af822eb` |
| [HP-8](#hp-8-report-tests-inside-skipped-containers) Tests inside skipped containers | Done | #127 | `732bf7e` |
| [HP-9](#hp-9-extension-must-not-lose-test-identity-unknown-test) Extension keeps test identity | Done | #128 | `7235eb5` |
| [HP-7](#hp-7-report-aborted-tests-failed-assumptions-as-skipped) Aborted tests as skipped | Done | #129 | `f6c3c7b` |
| [HP-6](#hp-6-one-test-object-per-logical-test-fold-retry-attempts) Fold retry attempts | Done | #130 | `56518bd` |
| [MP-3](#mp-3-replace-copyonwritearraylist-with-better-alternative) `CopyOnWriteArrayList` | Done by HP-6 | #130 | `56518bd` |
| [CQ-1](#cq-1-extract-feature-envy-from-ctrfreportmanager) Feature envy | Superseded; HP-6 removed `FlakyTestDetector` | #130 | `56518bd` |
| [CB-6](#cb-6-rework-report-persistence-forks-accumulation-same-jvm-reruns) One report per run (forks, accumulation, same-JVM reruns) | Done | #132 | `9084a83` |
| [HP-11](#hp-11-a-class-failure-that-recovers-on-retry-stays-failed) Class failure that recovers on retry | Done, with CB-6 | #132 | `9084a83` |
| [HP-5](#hp-5-conform-to-the-official-ctrf-schema) Conform to the official CTRF schema | Done | #134 | `291f21d` |
| [TI-4](#ti-4-validate-integration-output-against-the-official-schema-and-junits-own-counts) Official schema and JUnit counts in the validator | Done: the JUnit event-log cross-check and expectations from the run (#135); the official schema (HP-5, #134); CI with 2 forks (CB-6, #132) | #132, #134, #135 | `9084a83`, `291f21d`, `059992b` |
| [TI-5](#ti-5-regression-tests-for-every-verified-scenario) Regression scenarios | Partially done: CB-2, CB-5, CB-6, CB-7, HP-6, HP-7, HP-8, HP-9 and HP-11 have scenario tests, and every scenario's report is checked against the official schema (#146); checking them against JUnit's event log is open | #138 (CB-2), #146 | `ecb4fd1`, `77dce2e` |
| [MP-5](#mp-5-read-previous-reports-tolerantly) Read previous reports tolerantly | Superseded by CB-6; unknown fields are ignored since MP-9 (#138) | #132, #138 | `9084a83`, `037e253` |
| [HP-3](#hp-3-implement-proper-logging-framework) Logging through `java.util.logging` | Done | #138 | `47cd66b` |
| [MP-9](#mp-9-dependency-and-configuration-hygiene-owner-env-vars-jackson) Own config loader and JSON code; no runtime dependencies | Done | #138 | `d888754`, `037e253` |
| [MP-8](#mp-8-separate-message-from-trace-bound-the-trace-size) `message` and capped `trace` | Done | #138 | `8f91c54` |
| [MP-6](#mp-6-replace-deprecated-extensioncontextstorecloseableresource) `AutoCloseable` end-of-run hook | Done | #138 | `b52b060` |
| [MP-7](#mp-7-never-let-reporter-errors-fail-or-mask-user-tests) Reporter errors never change a test's outcome | Done | #138 | `639fd06` |
| [CB-2](#cb-2-a-nested-junit-run-ends-the-outer-run-re-scoped-from-race-condition-in-finishtestrun) Nested runs are ignored | Done | #138 | `ecb4fd1` |
| [HP-10](#hp-10-make-the-testexecutionlistener-the-single-core-integration-point) Listener as the recommended setup; run ownership | Done | #138 | `ecb4fd1`, `61df632` |
| [HP-1](#hp-1-refactor-singleton-pattern-in-ctrfreportmanager) Collaborators injected; singleton only as the default instance | Done | #138 | `61df632` |
| [Phase 3](#phase-3-design--051) HP-10, HP-1, CB-2, MP-6, MP-7, MP-8, MP-9, HP-3 | Done; merged 2026-10-15 (rebase, eight commits); released as 0.5.1 on 2026-10-16 (#139) | #138 | `47cd66b`..`61df632` |
| Build with JDK 25, keep Java 17 (not a plan item): `--release 17` for main and test code; the tests and the compat consumers run on Java 17, 21 and 25 | Done | #140 | `6776c2e` |
| [Tests on Jackson 3](#follow-ups-after-051) Jackson 2 removed; the JSON output pinned by golden JSON | Done | #141 | `6ff69d5` |
| [0.5.2 streaming JSON](#052-design--streaming-json) The JSON code streams both ways; a rerun reads only the old report's `runId`; heap guard | Done; merged 2026-10-16 (rebase, five commits); not released | #142 | `67f33d5`..`6083b2f` |
| [Review minors](#follow-ups-after-051) the five the Phase 3 final review deferred | Done; merged 2026-10-16 (rebase, five commits); not released | #143 | `9cdb0df`..`513c03d` |
| [HP-12](#hp-12-design--suite) `suite` holds each test's container hierarchy | Done; merged 2026-10-16 (rebase, four commits); not released | #144 | `04fe3c9`..`c793e51` |
| [DI-3](#di-3-fix-incorrect-documentation) Incorrect documentation | Done: `CLAUDE.md`'s single-test commands and stale class names; its six original points were already fixed | #145 | `3984760` |

Done before 2026-10-08: CB-1, CB-3, HP-2 (mostly), HP-4, MP-1, MP-2 and TI-3 (partially); see the table in the review update below. Each item's **Status** line has the details, and its ticked acceptance criteria are the ones `master` meets.

---

## HP-12 Design — `suite`

**Status**: Done. Designed 2026-10-16 against `master` @ `513c03d`; merged 2026-10-16 as #144 (`04fe3c9`..`c793e51`, four commits, rebase-merged). Not released yet. The version is decided at release: this is a feature, so semver says 0.6.0.
**Item**: [HP-12](#hp-12-fill-suite-with-each-tests-container-hierarchy). CTRF viewers group and filter tests by `suite`, defined as "Suite hierarchy from top-level to immediate parent", an array of strings with at least one item. This library never sets it, so every test appears ungrouped.

### The rule

- **`suite` is the display names of the test's ancestors, outermost first, without the engine.** Every engine is left out, also under a `@Suite` class: the suite engine and the engine it runs.
- If that leaves nothing, the nearest engine's display name is used instead, so every entry with a known engine has a `suite`.
- If not even an engine is known, there's no `suite`. That only happens for a test the manager knows by its ID alone, which it records as "Unknown Test".
- **A class's `initializationError` or `teardownError` entry** gets the suite of the class's own tests: the class's ancestors without the engine, then the class's display name, so it's grouped with the tests it affected. That's never empty, so the engine's name is never used for it: a top-level class's error gets just the class's name. Other container entries keep their own name and get their ancestors, like a test: a failed `@MethodSource` or `@TestFactory`, a failed engine, or the one skipped entry for a parameterized method.

Examples:

| Test | `name` | `suite` |
|---|---|---|
| `@DisplayName("Payments") class PaymentTest` → `@Nested class Refunds` → `@Test refundsTheFee()` | `refundsTheFee()` | `["Payments", "Refunds"]` |
| the same class → `@ParameterizedTest refundsTheFee(int)`, invocation 1 | `[1] 1` | `["Payments", "Refunds", "refundsTheFee(int)"]` |
| `@TestFactory factory()` → `DynamicContainer("group")` → a dynamic test (listener only) | its display name | `["PaymentTest", "factory()", "group"]` |
| `@BeforeAll` of `Refunds` fails | `initializationError` | `["Payments", "Refunds"]` |
| `@BeforeAll` of a top-level `class SetupTest` fails | `initializationError` | `["SetupTest"]` |
| JUnit 4 test `com.example.LegacyTest.test()`, through Vintage, which shows a class by its simple name | `test()` | `["LegacyTest"]` |
| a test directly under a custom engine | its display name | `["My Engine"]` |
| `@Suite class PaymentsSuite` selecting `PaymentTest` → `Refunds` → `refundsTheFee()`, through the listener | `refundsTheFee()` | `["PaymentsSuite", "Payments", "Refunds"]` |
| the same, through the extension, which sees only what Jupiter runs | `refundsTheFee()` | `["Payments", "Refunds"]` |

### Decisions (2026-10-16)

- **Display names,** as the IDE tree and Gradle's report show them, honouring `@DisplayName` and `@DisplayNameGeneration`. `filePath` keeps the exact class name for tooling.
  - Rejected: simple class names, which ignore the names authors chose for readers.
  - Rejected: fully qualified class names, which are long and repeat `filePath`.
- **The engine is left out,** as CTRF's reference example starts at the first group: `suite: ['Math', 'Addition']` with the file in `filePath`.
  - Rejected: always starting with the engine, which adds the same first level to every test in single-engine projects.
- **Class errors are grouped under the class.**
  - Rejected: next to the class, one level above its tests.
- **Each entry point computes `suite` when the test starts.** The listener walks the `TestPlan`, the extension walks the `ExtensionContext` parents.
  - Rejected: deriving it from the `testId`, whose segments hold class and method names, not display names.
  - Rejected: recording a container tree in the manager and building suites at the end. That's more state for no gain, since both entry points know a test's ancestors when it starts.
- **Public API is only added to:**
  - `TestContextAdapter`'s new methods are default methods;
  - `TestDetails` keeps its 5-argument constructor;
  - `TestIdentifierAdapter` keeps its 1-argument constructor.
- **Dynamic tests through the extension:** not reported, as today, because JUnit runs no extension callbacks for them.
- **One branch, `feat/hp-12-suite`, with four commits, each green, rebase-merged, no squash.**
- **Java 17** and the JUnit 5.10 compile floor stay; `TestPlan.getParent` exists in 5.10.
- **Added after the final review:**
  - **Under `@Suite`, every engine is left out,** recognised by its unique ID's last segment, whose type is `engine`. Before, the listener dropped only the topmost ancestor, so the engine a `@Suite` class runs, "JUnit Jupiter", stood in the middle of every suite. The `@Suite` class stays, as the outermost group. The extension can't see above Jupiter's engine, so its suites start at the class; the two entry points agree except for that first item.
  - **A failing walk gives no suite.** `TestPlan.getParent` can throw for an identifier the plan doesn't hold. The listener's walks catch a `RuntimeException` and return nothing, because the alternative is the HP-9 fallback, which loses the test's name and file.
  - **The root module's tests depend on `junit-platform-suite`,** for a `@Suite` fixture that its test discovers itself. The Gradle test tasks exclude the suite engine, so that Gradle doesn't run the fixture.

### Data model

- **`TestContextAdapter`** gets four default methods. The rule lives in the last two, so both adapters apply it the same way:
  - `getContainerNames()`: the display names of the containers above, outermost first, without the engine. It's empty by default, and the adapters override it;
  - `getEngineName()`: the nearest engine's display name, this one's if it is an engine. It's empty by default, and the adapters override it;
  - `getSuite()`: the container names, or the engine's name when there are none, or nothing when neither is known;
  - `getSuiteOfChildren()`: the container names followed by this container's own display name, which is what the tests directly inside it get. A class error uses it.
- **`TestDetails`** gets a sixth component, `List<String> suite`.
  - It's stored as an unmodifiable copy, never null; an empty list means no `suite`.
  - The 5-argument constructor stays and passes an empty list.
  - `fromAdapter` takes the adapter's suite.
- **`TestProcessor.createTest`** copies `suite` into the CTRF test, or leaves it out when it's empty, because the schema needs at least one item.
- **The manager's HP-9 fallback,** a finish whose start wasn't recorded, takes the suite from the reported details.
- **The CTRF model** already has `List<String> suite` on `Test`. `RetryAttempt` has none, and the official schema defines none there.

### The listener

- **New constructor `TestIdentifierAdapter(TestIdentifier, TestPlan)`.** Its `getContainerNames()` walks `plan.getParent(…)` upwards, collecting each parent's display name except the engines', whose unique ID ends with an `engine` segment, and reverses the names. `getEngineName()` is the nearest engine's name, the identifier's own if it is one. Both walks return nothing if the plan throws.
- **`createTestDetails`** passes the listener's `TestPlan`. Finished tests, tests skipped one by one, and the tests HP-8 reports inside a skipped or aborted container all get their `suite` this way.
- **`handleContainerFailure`:**
  - a class error gets `getSuiteOfChildren()` of the class;
  - any other container gets `getSuite()`, like a test.

### The extension

- **`ExtensionContextAdapter.getContainerNames()`** walks `context.getParent()` up to the root context, which is the engine ("JUnit Jupiter"). The root is dropped, and the names are reversed. `getEngineName()` is the root's display name.
- **`reportClassFailure`** uses `getSuiteOfChildren()` of the class context.
- **Why they agree:** an `ExtensionContext`'s and a `TestIdentifier`'s display names both come from the same `TestDescriptor`. A parameterized or repeated invocation's parent context is its method's, as in the plan.

### Retries, forks and merging

- **`AttemptFolder.fold`** is unchanged. One test per `testId`, from its final attempt, so the final attempt's `suite` is written.
- **`AttemptFolder.toAttempt`,** which rebuilds earlier attempts when a report is unfolded, also copies `suite`. The output doesn't depend on it, since only the final attempt's `suite` is written, but every attempt then carries the whole identity.
- **`ReportMerger.withRecoveries`** copies `suite` into the passed entry it builds for a class that failed and then recovered (HP-11). Without it, a recovered class would lose its `suite`, because that entry is the final one.
- **Reports written before this change:** a test from an older report of the same run keeps no `suite` until it runs again. Reading and writing need no change.

### Tests

- **Unit:**
  - `TestDetails`: the old constructor gives an empty suite, `fromAdapter` takes the adapter's, and the suite is a copy;
  - the default `getSuite()` is empty;
  - `TestIdentifierAdapter` on a `TestPlan` discovered from fixture classes: nested display names, the engine fallback, an empty suite from the 1-argument constructor, a `@Suite` class, which keeps its name and drops both engines, and a plan that throws, which gives no suite;
  - `ExtensionContextAdapter` on a mocked chain of contexts: root, class, nested class, method, invocation; and the root alone;
  - `TestProcessor`: an empty suite is left out, a non-empty one is copied;
  - `AttemptFolder` keeps `suite` through unfold and fold;
  - `ReportMerger`'s recovery entry keeps the class's suite;
  - `CtrfReportManager`'s HP-9 fallback keeps it.
- **Scenario, forked JVMs, listener and extension:**
  - **Fixtures:** a `@DisplayName` class with a `@Nested` class holding a `@Test`, a `@ParameterizedTest` with two invocations and a `@RepeatedTest(2)`; a `@TestFactory` with a `DynamicContainer`; a class whose `@BeforeAll` fails; a class whose `@AfterAll` fails; a `@Disabled` class.
  - **Assertions:** each entry's exact `suite`, and the same `suite` from both entry points for every test both report.
  - **Recovery:** the existing HP-11 recovery scenario also checks the recovered class's `suite`.
- **Integration validator:**
  - `JunitEvents` records each `started` event's `name` and `parentId`, and works out each started node's expected suite by the rule above, leaving out every node whose unique ID ends with an `[engine:…]` segment;
  - `JunitCrossCheckTest` checks every report entry that JUnit started against it, in both integration modules;
  - `CtrfLogicTest` checks that every entry has a non-empty `suite`, including HP-8 entries, which JUnit's log doesn't list;
  - both integration modules get `PaymentsTest`: `@DisplayName("Payments")`, with a `@Nested` class `Refunds` holding one `@Test` and a `@ParameterizedTest` of two values, all passing;
  - `CtrfLogicTest`'s pinned summary grows by those 3 passed tests: the listener module from 28/14/7/7 (tests, passed, failed, skipped) to 31/17/7/7, the extension module from 22/14/5/3 to 25/17/5/3.
- **Unchanged:** the golden JSON, whose full report already sets `suite`, and the schema checks.

### Commits

| # | Commit |
|---|---|
| 1 | `TestDetails` carries a `suite`; `TestContextAdapter.getSuite()`; `TestProcessor` writes it |
| 2 | The listener and the extension compute `suite`; scenario tests |
| 3 | Retries, merging and HP-11 recoveries keep `suite` |
| 4 | The integration validator checks `suite` against JUnit's event log; `PaymentsTest` in both integration modules; README and `CLAUDE.md` |

### Verification

As for 0.5.2:
- **Every commit:**
  - tests written first and watched failing;
  - `./gradlew :build`, which runs the tests on Java 25, 17 and 21, and checkstyle;
  - `:javadoc`;
  - all main classes are class-file version 61.
- **Commits 2 to 4 also run** both integration modules with the validator, and compat.

### Changes during implementation (2026-10-16)

- **Vintage shows a class by its simple name,** so a JUnit 4 test's `suite` is `["LegacyTest"]`, not the full class name. The example above is corrected, and compat expects the simple name for every consumer.
- **The final review** by a fresh reviewer found two Important issues, both fixed before the merge with a test that failed first: the engine inside a `@Suite` class stood in every suite, and a `TestPlan` that throws would have lost the test's name. See "Added after the final review" under Decisions.
- **A test that passed without the change,** the check that the listener and the extension agree, compared two maps of empty suites. It now also requires the listener's suites to be there.
- **Follow-ups,** the review's minors, not done:
  - a display name of null would make `TestDetails` throw in `List.copyOf`; JUnit never gives one;
  - the comment at `CtrfLogicTest.java:30` is 150 characters long, and the validator module has no checkstyle;
  - display names with `&`, quotes, `<`, a backslash or an emoji are pinned only at the validator's oracle; an end-to-end fake would pin them through the writer.

### Release notes (user-visible changes)

- **Every test has a `suite`:** the display names of its classes, nested classes and parameterized or dynamic containers, outermost first, without the engine. CTRF viewers can now group tests as the IDE does.
- **A class's `initializationError` or `teardownError`** is grouped under that class.
- **Under a `@Suite` class,** no engine is part of `suite` either. Through `CtrfListener`, the `@Suite` class's name comes first; `CtrfExtension` sees only what Jupiter runs, so its suites start at the test class.

### Acceptance Criteria

- [x] Every test entry has a `suite` that matches the official schema, except a test known only by its ID
- [x] The listener and the extension produce the same `suite` for the same test; under `@Suite`, only the listener has the `@Suite` class first
- [x] Scenario tests cover nested classes, parameterized, repeated and dynamic tests, class-level failures, a skipped class and a recovered class
- [x] The integration validator checks each entry's `suite` against JUnit's own event log
- [x] No public signature changes; existing `TestDetails` and `TestIdentifierAdapter` constructors keep working
- [x] All library classes are class-file version 61 (Java 17)

---

## 0.5.2 Design — streaming JSON

**Status**: Done. Designed 2026-10-16 against `master` @ `6ff69d5`; merged 2026-10-16 as #142 (`67f33d5`..`6083b2f`, five commits, rebase-merged). Ships as **0.5.2**, not released yet.
**Implementation plan**: [`docs/plans/2026-10-16-streaming-json-0.5.2.md`](docs/plans/2026-10-16-streaming-json-0.5.2.md)
**Fixes**: the streaming follow-up in [Follow-ups after 0.5.1](#follow-ups-after-051). 0.5.1's JSON code holds whole-text copies and trees in memory, so it needs about twice the heap of 0.5.0 to write a large report.

### Measurements (2026-10-16)

The smallest `-Xmx`, in 8 MB steps, at which a JVM records 100,000 tests through the manager's public API and ends the run with a complete report. One test in ten fails with a 1.7 KB trace, and the report is about 56 MB. JDK 21, default GC (G1).

| Scenario | 0.5.1 | 0.5.0 (Jackson 2) | Streaming | Streaming + `runId` check |
|---|---|---|---|---|
| Record the tests only, no write | 80 MB | 88 MB | | |
| First write, no report yet | 280 MB | 128 MB | 128 MB | 128 MB |
| Replace another run's report (a local rerun) | 328 MB | 224 MB | 216 MB | 128 MB |
| Merge two forks of one run | 280 MB | 144 MB | 144 MB | 144 MB |

- **The last two columns are a stand-in for this design:** 0.5.1's code with the file service's read and write switched to Jackson 3's streaming `readValue(File)` and `writeValue(File)`. The last column also reads only the `runId` of another run's report.
- **0.5.1's write path** turns the model into a tree of maps, the tree into a `StringBuilder` that grows by doubling, that into a `String`, and the `String` into a `byte[]`: about 200 MB for this report. Its read path holds the whole text, a tree and the model at once.
- **The floor is 80 MB:** the recorded tests themselves. The about 48 MB above it, the same in every version, is `AttemptFolder`'s copies while folding and composing. That isn't JSON code, and stays out of scope.
- The benchmark isn't kept in the repository; the heap guard below keeps the result.

### Changes during implementation (2026-10-16)

- **Results,** measured on the merged code with the same benchmark: the smallest `-Xmx` in 8 MB steps for 100,000 tests.

  | Scenario | 0.5.1 | 0.5.0 | Java 17 | Java 21 | Java 25 |
  |---|---|---|---|---|---|
  | First write | 280 MB | 128 MB | 120 MB | 120 MB | 128 MB |
  | Replace another run's report | 328 MB | 224 MB | 120 MB | 128 MB | 128 MB |
  | Merge two forks | 280 MB | 144 MB | 136 MB | 136 MB | 136 MB |

  At 2 MB steps on Java 21, first needs 122 MB and replace 118 MB: replace needs no more than first, and the 8 MB difference above is the step size.
- **Heap guard:** `-Xmx176m`, the largest number above (136 MB) plus about 25%, rounded up to 8 MB. It fails on 0.5.1's code (3/3 `OutOfMemoryError`) and passes in about 8 seconds per JDK.
- **From the final review,** folded into commits 4 and 5:
  - `update(String, UnaryOperator)` rejects a null `runId` with a `NullPointerException` before touching the report. Without that, the error was caught as a report problem, and the report was replaced;
  - `readRunId` reads a report that is the literal `null` as one without a run id, as the full read reads it as no report, so it's replaced without a warning through either `update`;
  - the heap guard's JVMs use G1, the collector the limit was measured with;
  - a test sends a same-run report saved with a BOM through `update(String, …)`, end to end.
- **Benchmark harness:** compiled with `--release 17` for the Java 17 run; compiled by JDK 21, Java 17 couldn't load it.
- **Decided in the plan, unchanged:**
  - a binding error early in the text is reported before a syntax error later in it;
  - `readRunId` takes the first `runId`.
- **Left out of scope by the review:**
  - the listener's end-of-run call has no `Throwable` guard. That's existing code, MP-7's territory;
  - `AttemptFolder`'s copies, the roughly 48 MB above the 80 MB of recorded tests;
  - the three stacked 8 KB read buffers. `InputStreamReader(InputStream, Charset)` would replace invalid UTF-8 instead of rejecting it.

### Decisions (2026-10-16)

- **Stream both ways, with our own code.**
  - Rejected: bringing Jackson back. Streaming gives the same numbers without it. As a dependency, Jackson brings back the version conflicts 0.5.1 removed: Jackson 2 with users' Jackson 2, Jackson 3 with Spring Boot 4's. Shading it adds about 2 MB to the jar, hides it from dependency scanners, and makes every Jackson CVE a release of ours.
  - Rejected: streaming the text but keeping the trees. Estimated at about 160 MB for a first write and over 200 MB for the others.
  - Rejected: streaming only the writer. Replace and merge would stay above 0.5.0.
- **A report of another run is replaced after reading only its `runId`.**
  - Rejected: one pass that stops at a foreign `runId`. It ties the binding to a stop condition, and a report with `results` before `runId` would still be built.
- **0.5.2 is a patch release:** nothing in the public API, the configuration or the report changes or goes away. `CtrfReportFileService` is public, so its `update(UnaryOperator)` stays as it is, and the new `update(String, UnaryOperator)` is added next to it.
- **Heap only.** The five deferred minors and `AttemptFolder`'s copies are separate.
- **One branch, `fix/streaming-json`, with five commits, each green, rebase-merged, no squash.**
- **Java 17:** `--release 17` already enforces it.

### Reader

- **New `JsonReader`:** a tokenizer that pulls tokens from a `java.io.Reader` through its own 8 KB character buffer. It never holds the whole text.
  - It's as strict as `JsonParser` is today: nesting deeper than 100 levels and numbers longer than 1,000 characters throw, a BOM may start the text, numbers become `Integer`, `Long`, `BigInteger` or `Double` by the same rules, and error messages end in "at character N".
- **`JsonParser.parse(String)` stays,** as a thin wrapper over `JsonReader`. Its tests then run, unchanged, against the tokenizer.
- **`CtrfJsonMapper.read(Reader, Class)` binds tokens straight into the model's fields, by each field's declared type.**
  - Unknown members are skipped token by token, without building them.
  - A tree of `LinkedHashMap` and `ArrayList`, as today, is built only for fields declared `Object` or `Map<String, Object>`: `extra`, `parameters`, `attachments`.
  - Every reading rule of today stays: the `filepath` alias; a whole-number string fills a number field, and a number or a boolean a text field; `null` fills a primitive field with 0 or false; status is read ignoring case; a repeated name keeps its last value; error paths like `$.results.tests[3].status`; only whitespace may follow the root value.
- **`CtrfJsonMapper.read(String, Class)` stays,** as a wrapper over a `StringReader`.
- **The file service reads through `Files.newBufferedReader(path, UTF_8)`.** Like `Files.readString` today, it rejects invalid UTF-8, so such a report is still logged as "Ignoring the existing report" and replaced.
- **Memory:** the model it produces, an 8 KB buffer and short-lived strings.

### Writer

- **`CtrfJsonMapper.write(Object, Writer)` writes the model in one pass, straight to the `Writer`.** Today's two steps, a tree of maps and then its text, become one, with the same rules: fields in declaration order; a null field left out, an empty list kept, a primitive always written; a `null` inside a map or a list written as `null`; status in lower case under `Locale.ROOT`, any other enum as its name, any other object without a JSON form as its `toString()`; map keys through `String.valueOf`.
- **`JsonWriter` becomes an object that wraps a `Writer`,** with today's escaping: `"` and `\`, the short escapes, `\u00XX` with upper-case hex for other control characters, every surrogate escaped, and a `NaN` or an infinity written as a string.
- **`JsonWriter.write(Object)` and `CtrfJsonMapper.write(Object)` stay,** as wrappers over a `StringWriter`, returning the text.
- **The file service writes the temp file through `Files.newBufferedWriter(temp, UTF_8)`** and moves it over the report, as today. A write that fails part-way, on an I/O error, a value without a JSON form or an OOM, deletes the temp file and leaves the report as it was. Today a value without a JSON form throws before the first byte is written; now it can throw in the middle, with the same result.
- **Output:** byte-identical, pinned by `JsonGoldenTest`.
- **Changed edge case:** two keys of one map that turn into the same string are both written, where today the later one replaced the earlier. Only a raw-typed map, with keys such as `1` and `"1"`, can have them. Reading the output back gives the same result as before, since a repeated name keeps its last value.
- **Memory:** an 8 KB buffer.

### File service: the `runId` check

- **New `CtrfJsonMapper.readRunId(Reader)`:** reads the root object's members in order, skipping each value without building it, until `runId`.
  - It returns the `runId` as text, converted as the full read converts it, or `null` when the report has none, as 0.4.x reports don't.
  - It throws, as the full read does, when the root isn't an object or the text before the `runId` isn't valid JSON.
  - Our writer puts `runId` fourth, before `results`, so it reads a few hundred characters. If another tool put it after `results`, it streams through the rest without building it: time, not memory.
- **New `CtrfReportFileService.update(String runId, UnaryOperator<CtrfJson> merge)`.** Under the same two locks as today:
  1. no report: `merge(null)`;
  2. the report's `runId` isn't `runId`, or it has none: `merge(null)`; the report is replaced without being read any further;
  3. the same run: the report is read with the streaming reader, then `merge(existing)`.
  - An error in step 2 or 3 logs "Ignoring the existing report, which can't be read" and calls `merge(null)`, as today.
- **`update(UnaryOperator)` stays,** unchanged: it reads the whole report, whatever its run.
- **The manager calls the new `update` with its `runId`.** `ReportMerger` keeps its own `runId` comparison as a safety net; the file service only skips what is certainly another run.
- **Cost:** for the same run, the file is opened twice; the first read stops after a few hundred characters.
- **Changed log:** another run's report that is damaged after its `runId` is now replaced without the "Ignoring" warning, because it's no longer read that far.

### Tests

- **Unchanged, must pass:** `JsonGoldenTest`, `JsonParserTest`, `JsonWriterTest`, `CtrfJsonMapperTest`, `CtrfModelJsonTest`, `CtrfSchemaConformanceTest`, `CtrfReportFileServiceTest`, the scenario tests, the integration tests with the validator, and compat.
- **New:**
  1. **Buffer boundaries:** the golden report and tricky inputs, read through a `Reader` that hands out 1, 2, 3 or 7 characters at a time, read exactly as from a `String`: escapes, `\u` escapes and surrogate pairs cut across refills, numbers, literals, the BOM. Errors report the same character positions.
  2. **Reader against Jackson 3** on a generated report of about 2,000 tests, with retries, failures and `extra`.
  3. **Writer:** to a `Writer`, the same text as the `String` version, for the whole golden corpus; an `IOException` from the `Writer` propagates; in the file service, a failing write leaves the report as it was and no temp file.
  4. **`readRunId`:** `runId` first and after `results`; missing, as in 0.4.x, and `null`; a number; a root that isn't an object; invalid JSON before the `runId`; a BOM.
  5. **File service:** another run's report that is damaged after its `runId` is replaced without a warning; a report of the same run is merged; a damaged report of the same run is logged as "Ignoring" and replaced, as today.
  6. **Heap guard:** a forked JVM, started as `ScenarioRunner` starts its JVMs, runs a small main class that records 100,000 tests through the manager's public API, as the benchmark did, and ends the run under a fixed `-Xmx`. Three cases: first write, replace, merge.
     - The limit is the most this design needs on Java 17, 21 and 25, measured when the guard is written, plus about 25%. From the stand-in, that is about 176 MB, against 0.5.1's 280 to 328 MB.
     - The guard must fail on 0.5.1's code before the fix.
     - It costs a few seconds per JVM, on each of the three JDKs.

### Commits

| # | Commit |
|---|---|
| 1 | `JsonReader`, a streaming tokenizer; `JsonParser.parse` becomes a wrapper over it |
| 2 | `CtrfJsonMapper` binds a `Reader` straight into the model; the file service reads through a `BufferedReader` |
| 3 | `JsonWriter` and `CtrfJsonMapper` write straight to a `Writer`; the file service writes through a `BufferedWriter` |
| 4 | A report of another run is replaced after reading only its `runId`; a null `runId` is rejected |
| 5 | Heap guard (`-Xmx176m`, G1); `CLAUDE.md`. The release notes and the follow-up are in this file |

### Verification

- **Every commit:**
  - tests written first and watched failing;
  - `./gradlew :build`: unit tests and scenarios on Java 25, 17 and 21, and checkstyle;
  - `./gradlew :javadoc`;
  - every class in `build/classes/java/main` is major version 61.
- **Commits 2 to 5 also run:**
  - the listener and extension integration with CI's flags, plus the validator;
  - `compat/run.sh 5.10.5 5.14.4 6.1.3`.
- **Before the PR:** all of the above on the final branch, and the benchmark on JDK 21 for the PR description.

### 0.5.2 release notes (user-visible changes)

Measured on the merged code, 100,000 tests (a report of about 56 MB), Java 21.

- **Less heap for large reports:** writing them needs about 120 MB, against 280 MB in 0.5.1 and 128 MB in 0.5.0; merging forks about 136 MB, against 280 MB in 0.5.1 and 144 MB in 0.5.0.
- **A rerun no longer loads the previous run's report:** it's replaced after reading its `runId`. At 100,000 tests, about 120 MB, against 328 MB in 0.5.1 and 224 MB in 0.5.0.
- **Logs:** a damaged report of another run is replaced without the "Ignoring the existing report" warning.

Also in 0.5.2, from the Phase 3 review (#143):

- **A failure whose `toString()` throws an `Error`** is reported, by its class name, instead of missing from the report.
- **A `ctrf.properties` saved with a UTF-8 BOM** keeps its first setting.
- **A capped trace with Windows line endings** never ends a line in `\r\r\n`.
- **A warning about a report that can't be read** shows at most 100 characters of the offending value, and names objects and arrays instead of printing them.

### Acceptance Criteria

- [x] The output is byte-identical: `JsonGoldenTest` passes unchanged
- [x] Every existing JSON, file service, scenario, integration and compat test passes; `JsonParserTest` runs unchanged on `JsonReader`
- [x] Reading in chunks of 1, 2, 3 and 7 characters gives the same results and error positions as reading a `String`
- [x] The reader matches Jackson 3 on a generated 2,000-test report
- [x] A report of another run isn't read past its `runId`
- [x] The heap guard passes for first write, replace and merge on Java 17, 21 and 25, and fails on 0.5.1's code
- [x] On the benchmark (JDK 21), no scenario needs more heap than 0.5.0 (128, 224 and 144 MB), and replace needs no more than first write
- [x] All library classes are class-file version 61 (Java 17)

---

## Phase 3 Design — 0.5.1

**Status**: Done. Designed 2026-10-15 against `master` @ `635b4e6`, after the 0.5.0 release; merged 2026-10-15 as #138 (`47cd66b`..`61df632`, eight commits, rebase-merged). Released as **0.5.1** on 2026-10-16 (release PR #139, `de63c00`).
**Implementation plan**: [`docs/plans/2026-10-15-phase-3-0.5.1.md`](docs/plans/2026-10-15-phase-3-0.5.1.md)
**Items**:
- [HP-10](#hp-10-make-the-testexecutionlistener-the-single-core-integration-point) with [HP-1](#hp-1-refactor-singleton-pattern-in-ctrfreportmanager);
- [CB-2](#cb-2-a-nested-junit-run-ends-the-outer-run-re-scoped-from-race-condition-in-finishtestrun);
- [MP-6](#mp-6-replace-deprecated-extensioncontextstorecloseableresource);
- [MP-7](#mp-7-never-let-reporter-errors-fail-or-mask-user-tests);
- [MP-8](#mp-8-separate-message-from-trace-bound-the-trace-size);
- [MP-9](#mp-9-dependency-and-configuration-hygiene-owner-env-vars-jackson) with [HP-3](#hp-3-implement-proper-logging-framework).

### Changes during implementation (2026-10-15)

The text below is corrected where implementation proved it wrong.

- **Surrogates (commit 3):** Jackson 2's byte output, which 0.5.0 used, escapes every surrogate. An emoji is written as the `\uXXXX` escapes of its two surrogates, and a lone surrogate is escaped too: Jackson never threw on it. The writer does the same, so 0.5.1 reports are byte-identical to 0.5.0's.
- **Nesting (commit 3):** the parser caps nesting at 100 levels, not 1,000. The reader and the writer are recursive, and 1,000 levels overflowed the test thread's stack on the Linux CI runner. A test now reads and writes a document nested to the cap inside a 256 KB stack.
- **`CTRF_*` variables (commit 2):** the integration workflows defined `CTRF_REPORT_PATH` for their publish and validator steps, and the reporter started reading it; they now call it `REPORT_FILE`. The README warns users whose CI defines `CTRF_*` variables for other steps.
- **Manager (commit 8):** the constructor doesn't take the configuration; nothing in the manager uses it once its collaborators are built.
- **README:**
  - it notes that Jackson and `owner` no longer reach users' test runtime classpath;
  - it explains the extension's ordering gap with nested runs: a nested run started before the first class with the extension becomes the reported run;
  - it says to register one listener instance per launcher;
  - it no longer claims that registering both entry points reports every test twice. Since CB-2, only the listener reports.
- **Also:**
  - `ctrf.properties` is read as UTF-8, as `owner` did, or as ISO-8859-1 if it isn't valid UTF-8;
  - booleans may have surrounding spaces;
  - `TestStatus` lower-cases with `Locale.ROOT`: under a Turkish default locale, `failed` was written `faıled`.

### Follow-ups after 0.5.1

- **Streaming JSON (0.5.2): done** (#142, `67f33d5`..`6083b2f`, 2026-10-16; not released). The writer and the reader built the whole report in memory, so a 100,000-test report needed 280 to 328 MB of heap. They now stream, and a rerun reads only the old report's `runId`: 120 to 136 MB. See [0.5.2 Design — streaming JSON](#052-design--streaming-json).
- **Tests on Jackson 3: done** (#141, `6ff69d5`, 2026-10-16). Jackson 2 is gone from the build: no module uses `jackson-databind` or `jackson-core` 2.x. The tests and the integration validator read JSON with Jackson 3 only.
  - **Golden JSON:** `JsonGoldenTest` replaces `JacksonDifferentialTest`. It pins the writer's output to the JSON that 0.5.0 wrote with Jackson 2. Before Jackson 2 was removed, this JSON was checked to be byte-identical to Jackson 2's output. It also checks that the reader reads that JSON as Jackson 3 does.
    - The full report is in `src/test/resources/json/golden-report.json`. It holds every field, all kinds of numbers, nested values and tricky text.
    - The short cases, such as `{"duration":0}`, are inline strings.
  - **Why golden JSON:** Jackson 3 can't be the reference, because it writes differently. For example, it sorts properties alphabetically by default.
  - **Annotations:** the model's Jackson annotations stay compile-only. Jackson 2 and 3 share them (`com.fasterxml.jackson.annotation`), and their version comes from the Jackson 3 BOM.
  - **Compat:** the consumers check that neither Jackson 2 nor Jackson 3 is on their test classpath.
- **Deferred review minors: done** (#143, `9cdb0df`..`513c03d`, 2026-10-16; not released). Each has one commit with its own test:
  - an `Error` thrown from a failure's `toString()` lost the entry. Any `Throwable` is now caught, and the failure is reported by its class name; a `VirtualMachineError` still propagates, as everywhere in the reporter;
  - a capped trace could keep a stray `\r` when the cut fell inside `\r\n`. The cut now moves back one character;
  - `ConfigReader(Map, Map, Map)` didn't document that it rejects nulls. Its Javadoc says so now, and a test pins it;
  - mapper error messages included whole offending values. Objects and arrays are now named ("an object", "an array"), and other values cut at 100 characters; the 12 pinned messages are unchanged;
  - a `ctrf.properties` saved with a UTF-8 BOM lost its first key, as with `owner`. The BOM is now dropped after decoding.

### Decisions (2026-10-15)

- **0.5.1 is a patch release, so nothing breaks for existing setups.**
  - `@ExtendWith(CtrfExtension.class)` and explicit `CtrfListener` registration keep working unchanged.
  - Every configuration key keeps resolving exactly as in 0.5.0.
  - Nothing new is switched on automatically.
  - The report keeps its format; only the fixes listed below change its content.
  - Rejected: a 0.6.0 that allows breaking changes, such as auto-registering the listener or removing the model's Jackson annotations.
- **One branch, `fix/phase-3-robustness`, with one commit per task and no squash.**
- **Java 17:**
  - the main code keeps compiling with `--release 17`;
  - every commit checks that all library classes are class-file major version 61;
  - no Java 17 CI job is added.
- **CB-2: the tests of a nested JUnit run are ignored.** Only the run that started reporting is reported.
  - Rejected: adding them to the outer report. Nested tests are usually deliberate fixtures, and their failures would show as the outer build's.
  - Rejected: a separate report for nested runs, which would need new configuration.
- **MP-8: `ctrf.max.trace.length` defaults to 20,000 characters,** and 0 means no limit.
  - Rejected: 50,000, which almost never cuts anything.
  - Rejected: no limit by default, which leaves the problem as it is.
- **MP-9: our own config loader replaces `owner`.**
  - **Facts, checked 2026-10-15:**
    - `org.aeonbits.owner` was last released on Maven Central as 1.0.12, on 2020-06-06.
    - The project is active again, at `github.com/matteobaccan/owner`, formerly `lviggiano/owner`. `master` is `2.0.0-SNAPSHOT`, with 2026 security fixes and new readers. So MP-9's 2026-10-08 finding "unmaintained" was wrong; what holds is "no release in six years".
  - **Reasons:**
    - this library needs about 20 string keys, two numbers and one boolean, and every dependency lands on users' test classpaths;
    - `CTRF_*` environment variables don't work with `owner`;
    - invalid values throw inside JUnit callbacks;
    - `owner` 2.0 shares its package with 1.0.12, so a project using 2.0 could clash with us.
  - **Rejected alternatives:**
    - keeping `owner` with workarounds. `owner`'s `imports` map outranks system properties, so `CTRF_*` support would have to rebuild the precedence around it;
    - JUnit configuration parameters, which don't read `ctrf.properties` or environment variables, and aren't available where the manager is created;
    - another library, which has the same footprint problem or a worse one.
- **MP-9: Jackson leaves the runtime classpath in 0.5.1.**
  - `jackson-databind` is a *runtime*-scoped dependency in the POM (`implementation` in Gradle), so it was never on users' compile classpath. Removing it can't break anyone's compilation.
  - Our own JSON writer and reader replace it. The model keeps its Jackson annotations, compile-only, so users who read our model with their own Jackson see no change.
  - Rejected: shading Jackson, which would relocate the annotations and break exactly those users.
  - Rejected: deferring it to 0.6.0. This was first proposed, then reversed once the runtime scope was confirmed.
- **HP-10: run ownership plus docs.**
  - Rejected: a full shared core that both entry points feed. That rewrites code Phases 1 and 2 just fixed and tested, which is risky for a patch.
  - Rejected: docs only, which would leave the global design that caused CB-2.
- **`CtrfListener` stays opt-in.** The jar doesn't ship `META-INF/services/org.junit.platform.launcher.TestExecutionListener`.
  - Shipping it would switch reporting on for every project with the jar on its test classpath.
  - That includes `CtrfExtension` users, who would then get double reporting.

### Commits

| # | Commit | Item |
|---|---|---|
| 1 | Log through `java.util.logging` instead of `System.err` | HP-3 |
| 2 | Own config loader: `CTRF_*` environment variables, invalid values warn and fall back, `owner` removed | MP-9 |
| 3 | Own JSON writer and reader: Jackson leaves the runtime classpath | MP-9 |
| 4 | `message` is the exception summary; `trace` is capped by `ctrf.max.trace.length` | MP-8 |
| 5 | The extension's end-of-run hook is also `AutoCloseable` | MP-6 |
| 6 | Reporter errors in extension callbacks are caught and logged | MP-7 |
| 7 | A `TestRun` remembers its owner; nested runs are ignored | CB-2 |
| 8 | The manager's collaborators are injected; the README recommends the listener | HP-10, HP-1 |

### `owner` 1.0.12 behaviour the loader keeps (probed 2026-10-15)

| Case | `owner` 1.0.12 | Loader |
|---|---|---|
| A key set as a system property and in `ctrf.properties` | system property wins | same |
| Environment variable with the exact key, e.g. `ctrf.report.path` | used | same |
| Environment variable `CTRF_REPORT_PATH` | ignored | **used** |
| `${user.home}` or `${other.key}` in a value | expanded through the same lookup (system property first) | same |
| Unknown `${no.such.key}` | expands to an empty string | same |
| Cyclic `${a}` → `${b}` → `${a}` | `StackOverflowError` | **warning; the reference expands to an empty string** |
| `value  ` with trailing spaces (strings) | kept as is | same |
| Empty value | `""` | same |
| Unset key | the default, or null | same |
| Number `" 300 "` | throws `UnsupportedOperationException` | **300** |
| Number `abc`, or a negative length | throws | **warning; the default** |
| Boolean `TRUE` | `true` | same |
| Boolean `yes` | throws | **warning; the default** |

### Commit 1 — HP-3: log through `java.util.logging`

- **Every `System.err.println` in the library becomes a WARNING on that class's JUL logger.** That covers `CtrfReportFileService`, `ReportMerger` and the `buildId` warning in `CtrfJsonComposer`.
- **Message texts stay as they are.** Only the format around them changes: JUL's default handler still prints to stderr, but adds a line with the timestamp and the logging class.
- **Why:** users can silence, filter or redirect the warnings with `logging.properties`, or with the JUL bridges of SLF4J and Log4j. No dependency is added.
- **Tests:** the tests that capture `System.err` switch to a test handler attached to the logger.

### Commit 2 — MP-9: own config loader

- **`ConfigReader` keeps every public getter.** The `owner` interface `CtrfConfig` is removed, along with `ConfigReader(CtrfConfig)`. A public constructor takes explicit inputs (system properties, environment, `ctrf.properties`), for tests and embedders.
- **Lookup per key, first value wins:**
  1. system property `ctrf.x.y`;
  2. environment variable `ctrf.x.y`, then `CTRF_X_Y` (upper case, dots become underscores);
  3. `ctrf.properties` on the classpath, through the context class loader first, then the library's own class loader;
  4. the default.

  The sources are read once, when the reader is created, which is also when `owner` read them.
- **`owner`'s behaviour is kept, except for the fixes in the table above.**
- **Typed values are parsed once, when the reader is created,** so a bad value warns once, not on every test.
- **Build:** `owner` is removed from `build.gradle`, and so from the published POM.
- **Tests:** `ConfigReaderTest` becomes a compatibility suite with one test per row of the table.
- **Docs:** the README's configuration section documents the precedence order, the `CTRF_*` names, and what happens to invalid values.

### Commit 3 — MP-9: own JSON writer and reader

- **New internal package `io.github.alexshamrai.json`:**
  - **`JsonWriter`** writes compact JSON, as Jackson does today, with no whitespace. Escaping follows Jackson: `"` and `\`, the short escapes `\b \t \n \f \r`, and `\u00XX` with uppercase hex for other control characters. Other text is written as-is in UTF-8, except surrogates. As in Jackson's byte output, a character outside the Basic Multilingual Plane, such as an emoji, is written as the `\uXXXX` escapes of its two surrogates, and so is a lone surrogate.
  - **`JsonParser`** is a recursive-descent parser. Integers become `Integer`, `Long` or `BigInteger` depending on size, and decimals become `Double`. It accepts any whitespace and a UTF-8 BOM. Malformed input throws, which the file service already treats as "can't be read, replace it". So does nesting deeper than 100 levels, so a corrupt report can't overflow the stack.
  - **`CtrfJsonMapper`** binds the model by reflection over each model class's declared fields. The rules are written explicitly, because the annotation classes aren't present at runtime:
    - **Fields:** in declaration order, named after the field; static fields are skipped.
    - **Nulls:** null fields are omitted; empty lists are kept; primitives are always written; nulls inside `Map<String, Object>` values are written as `null`.
    - **Status:** written lowercase; read ignoring case. An unknown status still fails the read.
    - **Coercions when reading:** numeric strings become numbers, and numbers and booleans become text in string fields; null in a primitive field becomes 0 or false; `filepath` is read as `filePath`.
    - **Unknown fields are ignored.** Before, one unknown field discarded the whole report, which was MP-5's remaining case.
- **`CtrfReportFileService`** reads with `Files.readString(…, UTF_8)` and writes the temp file as UTF-8, through the mapper.
- **Build:**
  - `jackson-databind` becomes `testImplementation`;
  - `jackson-annotations` becomes `compileOnly`;
  - the published POM has **no dependencies**.
- **Tests:**
  - **Differential, against Jackson:** a corpus of model instances must give byte-identical output from our writer and from Jackson. The corpus has every field set, nulls everywhere, Unicode and emoji, every control character, nested `extra` maps, and big and decimal numbers. Our parser must also read Jackson's output and our own back into equal objects.
  - **Hand-written cases:** pretty-printed input, a BOM, `filepath`, a string `buildNumber`, unknown fields, a lone surrogate, malformed JSON.
  - **Unchanged:** a 0.4 report whose `buildNumber` isn't a number still can't be read and is replaced.
  - **Schema:** `CtrfSchemaConformanceTest` validates the real writer's output.
  - **Compat:** the compat consumers run the published jar with no Jackson on the classpath.
- **Docs:** the README says the reporter has no runtime dependencies.

### Commit 4 — MP-8: `message` and `trace`

- **`message` = `throwable.toString()`**, the class plus its message. Over `ctrf.max.message.length` it's cut with `...`, as today.
  - It applies to every failure, including class-level entries and earlier attempts in `retryAttempts`.
  - Aborted tests keep the assumption's message (HP-7), and skipped tests keep their reason.
- **`trace`** is the full printed stack trace, unless it's longer than `ctrf.max.trace.length`.
  - The default is 20,000 and 0 means no limit. A negative value warns and uses the default.
  - When it's longer, the trace is: the head up to the limit, cut back to the last full line; a line `... N characters truncated (ctrf.max.trace.length=L)`; then every `Caused by:` line from the cut part, in order.
  - A single line longer than the limit is cut at the limit.
- **`ConfigReader.getMaxTraceLength()`** is new.
- **Tests:**
  - `message`: an exception with a message, one without, one with a cause, and the cut;
  - `trace`: under the limit, over it, one long line, and 0;
  - existing tests that expected stack frames in `message` are updated.
- **Docs:** a README configuration row, and a note on what `message` and `trace` contain.

### Commit 5 — MP-6: `AutoCloseable`

- **`TestRunExtension` stores a named class** that implements both `ExtensionContext.Store.CloseableResource` and `AutoCloseable`, with one `close()`.
  - JUnit before 5.13, down to the supported 5.10.5, closes it as a `CloseableResource`.
  - JUnit 5.13 and later closes it as an `AutoCloseable`, without the warning.
  - Finishing a run is already one-shot, so a second `close()` would do nothing.
- **Tests:** a forked-JVM scenario with the extension on JUnit 6.1.3 checks that the report is written, and that the output has no "implements CloseableResource but not AutoCloseable" warning. The compat matrix covers JUnit 5.10.5 and 5.14.4.

### Commit 6 — MP-7: reporter errors never change a test's outcome

- **Guarded:** all reporter work in the extension:
  - `beforeAll`: starting the run, and the class's bookkeeping;
  - `beforeEach`;
  - `afterAll`: the class's failure or success;
  - the end-of-run close, which writes the report;
  - the `TestWatcher` callbacks too, so every failure is logged the same way, although JUnit already guards them.
- **An internal guard** catches any `Throwable` except `VirtualMachineError`, which it rethrows. It logs a JUL warning with context (the callback and the unique ID) and the exception.
- **The listener isn't wrapped:** JUnit's launcher catches and logs exceptions from `TestExecutionListener` callbacks. The plan confirms that in JUnit 5.10's code, and adds the guard if it doesn't hold.
- **Test seam:** a package-private `CtrfExtension(CtrfReportManager)` constructor.
- **Tests:** these use `EngineTestKit` (`junit-platform-testkit`, a test dependency) with fixture classes tagged out of the normal run.
  - The reporter throws when a test starts: the test passes.
  - It throws while recording a failure: the test fails with its own exception.
  - It throws while starting the run, or in `afterAll`: the class's tests run.
  - It throws while writing the report: no engine failure.
  - It throws an `OutOfMemoryError`: that propagates.

### Commit 7 — CB-2: only the run that started reporting may end it

- **`TestRun`** holds one run's state: its owner, the generator, the start time, the recorded attempts and container successes, and the test details in progress.
- **The manager** keeps at most one active run, in an `AtomicReference<TestRun>`:
  - `boolean startTestRun(Object owner, String generator)` returns true if this owner owns the active run, and false when another owner's run is active;
  - `finishTestRun(Object owner)` does nothing unless `owner` owns the active run. When it does, it detaches the run and writes the report from it;
  - `startTestRun(String)` and `finishTestRun()` stay, delegating with a fixed owner of their own.
- **The entry points:**
  - **Listener:** the owner is its `TestPlan`. An instance whose `testPlanExecutionStarted` didn't win the run ignores all its events, including the finish.
  - **Extension:** the owner is the root `ExtensionContext`. Whether the root owns the run is stored in its store, and every callback checks it.
- **Unchanged:**
  - a second run in the same JVM (CB-6) starts once the first one has finished;
  - parallel tests in one run record into the same run;
  - environment health stays JVM-wide.
- **Tests:**
  - forked-JVM scenarios, one through the listener and one through the extension. Class A has two tests; class B runs a fixture class through `LauncherFactory.create()`; class C has two tests. The report must contain A's tests, `runsNestedLauncher()` and C's tests, and none of the fixture's;
  - the same-JVM rerun scenario still passes;
  - unit tests cover the owner rules.

### Commit 8 — HP-10 and HP-1: the singleton only where it's needed, and the docs

- **The manager's collaborators are passed in** through one package-private constructor: test processor, file service, a composer factory per generator, the run id, and the initial health. The configuration isn't one of them, because nothing in the manager uses it once those are built. That replaces "the composer is null, then rebuilt at every finish".
- **`getInstance()` stays as the default instance,** used by `EnvironmentHealthTracker` and by the entry points' no-argument constructors.
- **`CtrfListener` gets a package-private test constructor,** like `CtrfExtension`'s.
- **README, "Choosing a setup":**
  - recommend the listener, which sees tests of classes skipped as a whole, failed containers of every kind, other engines such as Vintage, and dynamic tests;
  - the extension stays supported, with its limits listed;
  - never register both;
  - why the jar doesn't register the listener automatically;
  - how to register the listener with Gradle and with Maven.
- **`CLAUDE.md`:** the architecture section covers who owns a run, and what the singleton is still for.
- **HP-10's acceptance criteria:**
  - the CB-5 and HP-8 scenarios already pass through the listener;
  - the compat matrix's JUnit 4 consumer already runs the listener on Vintage;
  - the README criterion is this commit.

### Verification

- **Every commit:**
  - tests written first and watched failing;
  - `./gradlew :build`, which runs unit tests, scenarios and checkstyle;
  - `./gradlew :javadoc`;
  - every class in `build/classes/java/main` is major version 61.
- **Commits 3, 5, 6, 7 and 8 also run:**
  - the listener and extension integration with CI's flags, plus the validator;
  - `compat/run.sh 5.10.5 5.14.4 6.1.3`.
- **Before the PR:** all of the above on the final branch.

### 0.5.1 release notes (user-visible changes)

- **`message`** holds the exception summary, not the start of the stack trace.
- **`trace`** is capped by the new `ctrf.max.trace.length`. The default is 20,000, and 0 means no limit.
- **Configuration:**
  - `CTRF_*` environment variables now work, e.g. `CTRF_REPORT_PATH`. A `CTRF_*` variable that your CI defines for another step, such as a publishing action, now configures the reporter too, so rename it if it isn't meant for the reporter;
  - an invalid number or boolean logs a warning and uses the default, instead of breaking the report;
  - numbers and booleans may have surrounding spaces;
  - `ctrf.properties` is read as UTF-8, or as ISO-8859-1 if it isn't valid UTF-8.
- **Nested runs:** a test that runs JUnit itself no longer ends the outer run, and the nested run's tests aren't reported.
- **Logging:** warnings go through `java.util.logging`, with the same texts.
- **No runtime dependencies:** `owner` and `jackson-databind` are no longer brought in. The model classes keep their Jackson annotations. If your tests used Jackson or `owner` through this library, for example with REST Assured or WireMock, declare it as your own test dependency.
- **Statuses** are written in English lower case under any default locale; under a Turkish locale, `failed` was written `faıled`.
- **Reading an existing report:** unknown fields are ignored instead of discarding the report.
- **Extension:** no "CloseableResource" warning on JUnit 5.13 and later, and reporter errors never fail a test.

### Acceptance Criteria

- [x] Every row of the `owner` behaviour table holds for the new loader
- [x] `CTRF_REPORT_PATH` and the other `CTRF_*` variables work, below system properties and above `ctrf.properties`
- [x] The published POM has no dependencies, and the compat consumers pass without Jackson on the classpath
- [x] Our writer's output is byte-identical to Jackson's for the test corpus; the schema conformance test and the integration validator pass
- [x] `message` has no stack frames; `trace` respects `ctrf.max.trace.length` and keeps every `Caused by:` line
- [x] No "CloseableResource" warning on JUnit 6; the report is still written on JUnit 5.10.5
- [x] No reporter exception changes a test's status or exception; `VirtualMachineError` propagates
- [x] A nested JUnit run neither ends the outer run nor adds its tests, through the listener and through the extension; same-JVM reruns still work
- [x] README recommends the listener and explains the trade-offs, and why it isn't registered automatically
- [x] All library classes are class-file version 61 (Java 17)

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

Snapshot of 2026-10-08. For the current state, see [Status on `master` — 2026-10-14](#status-on-master--2026-10-14).

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
| TI-2 File-system error tests | Open | The MP-5 cases are superseded with MP-5; CB-6 added the atomic-write and unparsable-report cases |
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
| [HP-11](#hp-11-a-class-failure-that-recovers-on-retry-stays-failed) | High | Added 2026-10-14: a class failure that recovers on retry stays failed |
| [HP-12](#hp-12-fill-suite-with-each-tests-container-hierarchy) | Medium | Added 2026-10-15: fill `suite` with each test's container hierarchy (split from HP-5) |
| [MP-5](#mp-5-read-previous-reports-tolerantly) | Medium | Reading a previous report is brittle: unknown fields drop history, missing `healthy` means unhealthy. Superseded by CB-6 (2026-10-15) |
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

1. [HP-12 Design — `suite`](#hp-12-design--suite)
1. [0.5.2 Design — streaming JSON](#052-design--streaming-json)
1. [Phase 3 Design — 0.5.1](#phase-3-design--051)
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
**Status**: Done (#138, `ecb4fd1`), 2026-10-15; released in 0.5.1. Re-scoped 2026-10-08. Nested runs are ignored: a `TestRun` remembers its owner, and only the owner can end it. Covered by `NestedRunScenarioTest`.

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
- [x] A nested JUnit run in the same JVM does not end the outer run
- [x] Every outer test appears in the report (`runsNestedLauncher()`, `outerC1()`, `outerC2()` in the scenario above; `third()` and `fourth()` in `NestedRunScenarioTest`)
- [x] Tests from nested runs are handled as documented
- [x] Sequential runs in one JVM still produce correct reports (CB-6)

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
**Status**: Done on `master` (#124: `8f88975`, `156ea67`)

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
- [x] The published POM contains no `org.junit` dependencies; `module.json` runtimeElements contains no `junit-bom` platform
- [x] Maven and Gradle consumers on JUnit 5.10, the latest 5.x and 6.x run their tests and produce a report, in either declaration order
- [x] A Vintage-only consumer using `CtrfListener` works without `junit-jupiter-api` on the classpath
- [x] README uses test-scoped coordinates
- [x] The consumer-compatibility job runs in CI on every PR

---

### CB-5: Report Every Failed Container, Not Only Class-Level Ones

**Priority**: Critical
**Complexity**: Moderate
**Files**: `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java`, `CtrfReportManager.java`, `SuiteExecutionErrorHandler.java`, integration fake tests and validator
**Status**: Done on `master` (#125: `151c9c8`; follow-up #131: `58e36f0`, which names an `@AfterAll` failure `teardownError` when the class's tests were skipped). Known gap: the extension still names it `initializationError` when every child of the class is a skipped `@Nested` class, because JUnit calls no extension callback for those; the listener gets it right. [HP-11](#hp-11-a-class-failure-that-recovers-on-retry-stays-failed) tracks container failures that recover on retry.

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
- [x] Every FAILED container in the test plan produces exactly one failed CTRF entry (with the listener; the extension's limits are listed in its Javadoc)
- [x] CTRF `summary.failed > 0` whenever JUnit reports a failed test or container (with the listener; see above)
- [x] `@AfterAll` failures are captured for every class, not only the first one
- [ ] The integration validator cross-checks counts with JUnit's own results (open; the scenario tests do this, the validator doesn't yet: TI-4)

---

### CB-6: Rework Report Persistence (Forks, Accumulation, Same-JVM Reruns)

**Priority**: Critical
**Complexity**: Complex
**Files**: `CtrfReportFileService.java`, `CtrfReportManager.java`, `TestStateTracker.java`, `CtrfJsonComposer.java`, `ReportMerger.java` (new), `RunIdResolver.java` (new), `ContainerSuccess.java` (new), `ReportOrchestrator.java` and `TestRerunHandler.java` (deleted), `ctrf/model/CtrfJson.java`, `config/CtrfConfig.java`, `config/ConfigReader.java`, `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java`, `scenario/*`, `integration-tests-*/build.gradle`, `.github/workflows/integration-listener.yml`, `compat/maven/*`, `README.md`, `CLAUDE.md`
**Status**: Done on `master` (#132: `9084a83`), together with HP-11. Designed 2026-10-14 against `master` @ `58e36f0`; replaces the 2026-10-08 prompt. Follow-ups: [Deferred from the review](#deferred-from-the-cb-6-review).
**Implementation plan**: [`docs/plans/2026-10-14-cb-6-report-persistence.md`](docs/plans/2026-10-14-cb-6-report-persistence.md)

#### Why

Each JVM reads the report when its run starts, keeps that read for the rest of the JVM (the MP-2 cache), and rewrites the whole file at the end. Whenever more than one JVM or execution touches the file, the results are wrong:

- **(a) Forks lose results.** Each fork rewrites the file, and the last writer wins. Measured on `master` @ `58e36f0` with `:integration-tests-listener:test` and CI's flags; Gradle counted 37 attempts every time:
  - `maxParallelForks=1`: tests=28, passed=14, failed=7, skipped=7, flaky=1, which is correct;
  - `maxParallelForks=2`: tests=16, passed=8, failed=5, skipped=3, flaky=1;
  - `maxParallelForks=4`: tests=28 but flaky=0, because the flaky test's failed first attempt was lost.
- **(b) Separate builds accumulate.** A report left by an earlier build is folded into the next one: a build without `clean`, or a persistent CI workspace such as Jenkins. Tests inherit `retries` and `flaky`, and the earlier build's `summary.start` and unhealthy flag carry over.
- **(c) A second execution in the same JVM loses the first.** Surefire's `rerunFailingTestsCount` reruns failed tests in the same JVM; a probe on 2026-10-14 showed the same pid. The cached read is reused, so the second write drops the first execution's results, history and flaky flag.
- **(d) Writes are not atomic.** The report is written in place, so a JVM killed mid-write leaves a truncated file.

#### Decisions (2026-10-14)

- One report at `ctrf.report.path` stays the default, and the default path doesn't change. Each JVM merges its results into it at the end, under a file lock.
  - Rejected: shards plus an automatically merged file, which adds a shard directory to manage and clean.
  - Rejected: shards plus an explicit merge step, which breaks every consumer that expects one file.
- JVMs belong to the same run when they resolve the same `runId`. The report is merged within a run and replaced otherwise.
- The report is read only at the end, under the lock. Rejected: also reading it at the start, which means two reads and two code paths that must agree.
- `ReportOrchestrator` and `TestRerunHandler` are deleted. The merge rule is a pure `ReportMerger`, called by `CtrfReportManager`.
- HP-11 is designed with CB-6 and ships in the same PR. A class failure that recovers in a later round becomes a passed, flaky entry.
- No merger tool of our own. For reports from several machines, the README points to `npx ctrf merge` (`ctrf-io/ctrf-cli`).
- Not released on its own. It ships with Phase 2 as 0.5.0, and the PR has no version bump.

#### Run identity

`RunIdResolver` (new, package-private) resolves the `runId` once per JVM. Blank values are skipped, and the first source that gives a value wins:

1. `ctrf.run.id`, read through `ConfigReader`: a system property or `ctrf.properties`. It is used as is. An environment variable would have to be named `ctrf.run.id` literally, which most shells can't set (MP-9).
2. A CI variable:

   | CI | `runId` |
   |---|---|
   | GitHub Actions | `github-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}` |
   | GitLab | `gitlab-${CI_JOB_ID}` |
   | Jenkins | `${BUILD_TAG}` (already starts with `jenkins-`) |
   | Azure Pipelines | `azure-${BUILD_BUILDID}-${SYSTEM_JOBATTEMPT}` |
   | CircleCI | `circleci-${CIRCLE_WORKFLOW_JOB_ID}` |
   | Buildkite | `buildkite-${BUILDKITE_JOB_ID}` |

3. The nearest ancestor process that is a JVM, searching upwards from the direct parent. A JVM here means its command is a `java`, `java.exe` or `javaw.exe` executable. The `runId` is `process-<pid>-<start millis>`, or `process-<pid>` if the platform doesn't report the start time. An ancestor whose command can't be read is skipped.
4. Otherwise this JVM alone: `jvm-<pid>-<start millis>-<random>`.

The value goes into the root `runId` (CTRF §5.4) of every report.

A probe on 2026-10-14 checked fallback 3:
- Gradle 9.7.1 with a daemon, test-retry 1.6.6 and `maxParallelForks=2`: both forks and the retry-round JVM are children of the daemon, and a second build has the same daemon as its parent.
- Maven with Surefire 3.5.2, `forkCount=2` and `rerunFailingTestsCount=1`: each fork's parent is its own `/bin/bash`, whose parent is the `mvn` JVM, so the direct parent would split one build into several runs. The rerun happened in the same fork JVM.

What fallback 3 gives when neither `ctrf.run.id` nor a CI variable is set:

| Launch | Run |
|---|---|
| Gradle forks and retry rounds | One run (the daemon) |
| Gradle builds one after another with a daemon | The same run, so they accumulate unless `ctrf.run.id` is set per build |
| `gradle --no-daemon` | One run per build |
| Maven Surefire forks | One run per build (the `mvn` JVM) |
| JUnit's console launcher started from a terminal | Each JVM is its own run |
| IDE runners | Not probed. If the IDE process itself is a `java` executable, its runs accumulate like a daemon's. |
| `mvnd`, Bazel and other long-lived JVM build servers | Accumulate unless `ctrf.run.id` is set |

#### Writing the report

`CtrfReportFileService` keeps writing to `ctrf.report.path`. It loses `getExistingTests`, `getExistingStartTime`, `getExistingEnvironmentHealth`, `writeResultsToFile` and the MP-2 cache. These are public methods, so the 0.5.0 release notes list them. It gains one operation, `update(UnaryOperator<CtrfJson> merge)`, the only way to write the report, whose function receives the existing report, or `null`:

1. Take a JVM-wide lock, a plain Java lock. A JVM can hold a file lock only once, and a second attempt throws `OverlappingFileLockException`, so threads of one JVM queue on the Java lock first. Then take an exclusive `FileChannel` lock on `<report>.lock`, polling `tryLock` for up to 60 s.
2. Read the report fresh. It is `null` when the file is missing or can't be parsed.
3. Apply `merge`.
4. Write to a hidden temp file in the same directory, `.<report name>.<random>.tmp`, then `Files.move` it over the report with `ATOMIC_MOVE` and `REPLACE_EXISTING`.
5. Release both locks.

The `.lock` file stays next to the report, because deleting it would race with other JVMs. With the default path, that is the project directory, so the README says to ignore `ctrf-report.json.lock` in version control or to point `ctrf.report.path` under `build/`.

#### Merging

`ReportMerger` (new, package-private) is a pure function, with no files and no clock:

```java
MergedRun merge(CtrfJson existingOrNull, String runId, List<Test> attempts, List<ContainerSuccess> successes,
                long start, long stop, boolean healthy)
```

It returns the folded tests, start, stop and health:

- `sameRun` means the existing report is not `null` and its `runId` equals `runId`. A report without a `runId`, written by 0.4.x or another tool, is never the same run.
- The attempts are the existing report's tests unfolded with `AttemptFolder.unfold` if `sameRun`, followed by this JVM's attempts in the order they were recorded.
- Recoveries (HP-11) are appended next. For each container success whose unique ID equals the `testId` of an attempt in that list, add a passed attempt. It copies that test's `testId`, `name`, `filepath` and `tags` from its latest attempt, and takes its start, stop and duration from the success. Every other success is dropped.
- The tests are `AttemptFolder.fold(attempts)`.
- If `sameRun`, start is the earlier of the two, stop the later, and the run is healthy only if both are. Otherwise all three come from this JVM.
- If merging with the existing report throws (for example `"tests": [null]` in a hand-edited file), the merge falls back to this JVM's data alone and prints a warning.

`CtrfReportManager.finishTestRun` then calls `fileService.update(existing -> ...)` with a function that merges, builds the summary with `SummaryUtil` and composes the document with `CtrfJsonComposer`, which now also sets `runId`. The manager now creates the composer, which `ReportOrchestrator` used to do.
- Every write gets a new `reportId` and `timestamp`, because spec §5.3 says a merged document gets a new `reportId`.
- Environment metadata comes from the writing JVM.
- `startTestRun` records the generator, the start time and the `ENV_HEALTHY` flag, and reads no file.
- State is cleared after each write, as today, so a second execution in the same JVM re-reads what the first one wrote.

The existing report's attempts come first, then this JVM's. That is the order they ran in: retry rounds run one after another, and the forks of one round run different tests. It also resolves the first minor deferred from the HP-6 review. An attempt the extension records before `startTestRun` no longer ends up ahead of the report's attempts, because the report is now read at the end.

Attempts and successes are added in O(1) from any thread. The merge runs once per execution, under the locks.

#### HP-11: container successes

- `ContainerSuccess` (new, internal record): the container's unique ID, start and stop.
- `TestStateTracker` collects them in a `ConcurrentLinkedQueue`, and clears them together with the attempts.
- `CtrfListener.executionFinished`: a container below the engine that finishes with SUCCESSFUL records a success, with its start from `containerStartTimes`. Engine containers are left out because every fork runs them. Otherwise one fork's engine success could "recover" another fork's engine failure in the same round. A class runs in exactly one fork per round, so classes and the containers inside them can't meet this problem.
- `CtrfExtension.afterAll`: a class without an execution exception records a success, with its start from `ClassState`.
- A recovered entry:
  - has status `passed`, `retries: n`, `flaky: true` and its failures in `retryAttempts`;
  - keeps the name of its last failure: `initializationError` or `teardownError`, or the container's own name for a container below class level.
- A container that never failed has no attempts, so its success adds nothing. The engine container is one example.

#### Errors and edge cases

The reporter never throws into JUnit and never leaves a report that can't be parsed. Each fallback prints one warning to stderr.

| Situation | Behaviour |
|---|---|
| The file system doesn't support locks (some network mounts) | Write without the file lock, still atomically. Forks on such a mount can overwrite each other. |
| Another JVM holds the lock for more than 60 s | Write without it. A lock is held only for one read, merge and write, and the OS releases a dead holder's lock, so a longer wait would only hang the build. |
| The existing report is missing | Write this JVM's results. |
| The existing report can't be parsed (another tool, a hand edit, or a pre-0.5 JVM killed mid-write) | Treat it as another run and replace it. Lenient reading of unknown fields stays in MP-5. |
| Merging throws | Fall back to this JVM's results alone. |
| The write fails (disk full, permissions) | Delete the temp file. The existing report is left untouched. |
| The platform can't move files atomically | Fall back to a plain replace. |
| A JVM is killed mid-write | Only a temp file is left behind; the report is the previous complete version. Leftover temp files and the `.lock` file are harmless. |
| The JVM dies before `finishTestRun` | Its results are lost, as today. What earlier JVMs wrote stays valid. |
| No ancestor JVM, and no explicit or CI `runId` | This JVM is its own run. |
| Two separate builds (different `runId`s) write the same file at the same moment, such as the same project started in two terminals | The file stays a complete report of the build that finished last. To keep both, give each build its own `ctrf.report.path`. |

One behaviour change is a fix: a report from a different run no longer passes on its start time or its unhealthy flag.

#### Tests

- **Unit tests**, without files or child processes where possible:
  - `RunIdResolverTest`: the order of sources, blank values skipped, each CI variable, a JVM ancestor behind a shell, an ancestor without a start time, an ancestor whose command can't be read, and no JVM ancestor. The resolver takes its environment, configuration and process handle as inputs.
  - `ReportMergerTest`:
    - no existing report;
    - same run: unfold and append, earliest start, latest stop, unhealthy if either is;
    - a different run is replaced and passes on neither its start time nor its health;
    - a report without a `runId` is never merged;
    - recoveries: failure then success is passed and flaky, a success without a failure adds nothing, failing every round stays failed;
    - a merge that throws falls back.
  - `CtrfReportFileServiceTest`:
    - `update` creates directories;
    - an unparsable report reaches the merge function as `null`;
    - a write that fails halfway leaves the previous report parseable and unchanged;
    - concurrent `update` calls from several threads all land;
    - the lock times out, with a short timeout passed in.
  - `CtrfReportManagerTest`: `startTestRun` reads no file; a second execution in the same JVM keeps the first one's results.
  - Listener and extension tests: container successes are recorded.
- **Scenario tests**, in forked JVMs through `ScenarioRunner`:
  - Forks: three JVMs started at once with the same `ctrf.run.id`, each with different fixtures, give one report with every test. The fixtures wait for each other through marker files in the shared working directory, so all three finish together and contend for the lock. Without that barrier, the scenario would pass even without the lock.
  - New build: two runs in a row with different `runId`s, once through `ctrf.run.id` and once through `GITHUB_RUN_ID`. Only the second run remains.
  - Same JVM: `ScenarioMain` runs the launcher twice. `stable()` is present, and `flakyOnce()` has `retries: 1` and `flaky: true`.
  - HP-11, through both the listener and the extension:
    - `@BeforeAll` fails in round 1 and passes in round 2: the class entry is passed and flaky;
    - it fails in every round: one failed entry with its history;
    - the same for `@AfterAll`.
  - The existing retry-round scenarios still pass. They set no `runId`, so they also exercise fallback 3: their JVMs are children of the test JVM.
  - `ScenarioRunner` removes the CI variables above from the child JVMs' environment, as it already removes `ENV_HEALTHY`. Scenarios then behave the same locally and in CI, where `GITHUB_RUN_ID` is set.
- **Integration and compat:**
  - The integration modules accept `-Dforks=N`, and the listener workflow in CI runs with 2 forks, so the validator's fixed numbers check fork merging on every PR. Before the PR, also run 4 forks locally.
  - The validator checks the root `runId`.
  - The Maven compat consumers get a second test class and run with `forkCount=2`, which checks on real Surefire that its forks resolve one run. They also run with `rerunFailingTestsCount=1` and a test that fails only on its first call, which checks (c) on real Surefire: the test must come out flaky. The scenario only models (c) with two launcher executions.
- **Before the PR:**
  - `./gradlew :build`;
  - both integration runs with the validator, using CI's flags, with 1, 2 and 4 forks for the listener;
  - `compat/run.sh 5.10.5 5.14.4 6.1.3`;
  - the time to merge into an existing report with 80k tests, which is how long one fork holds the lock.

#### Docs and delivery

- `README.md`:
  - A new section, "Parallel forks, retries and reruns". It covers one report per run, how the `runId` is chosen, and the `.lock` file. It also covers the Gradle daemon and build-server caveat, with a per-build `ctrf.run.id` setting verified with and without Gradle's configuration cache. It warns that the value must change with every build: a constant, for example one in `ctrf.properties`, makes every build the same run, so reports accumulate again. Finally, it covers reports from several machines with `npx ctrf merge`.
  - "Retries and Flaky Tests" gains class failures that recover.
- `CLAUDE.md`:
  - the data flow and component table: `ReportOrchestrator` and `TestRerunHandler` removed, `ReportMerger` and `RunIdResolver` added;
  - "Rerun Support";
  - the concurrency section: the locks;
  - the `ctrf.run.id` key;
  - the line saying `unfold` runs at the start.
- Branch `fix/cb-6-report-persistence` from `master`, one commit, and a PR description file.

#### Deferred from the CB-6 review

The per-task and whole-branch reviews of #132 left these open. The full list is in the PR's review notes.
- **Windows is untested in CI.** The move is retried on Windows file-sharing errors, but the lock, the move, the `java.exe` ancestor lookup and Surefire's `cmd.exe` fork chain have never run on Windows. A `windows-latest` job running `:build` would cover them. Its move-retry test asserts the "Failed to write results to file" message, which Windows reports as "Access denied" instead.
- **A read error is treated like an unparsable report,** so a same-run report that can't be read for a moment is replaced. A short retry, like the move's, would fix it.
- **A symlinked report is replaced by a regular file.** The rename also resets the file's mode to the default.
- **Under a `SecurityManager` that denies process inspection,** forks fall back to separate `jvm-` run ids and replace each other. Reading the CI variables before inspecting processes would keep them merging. `CtrfReportManager`'s constructor also reads `ENV_HEALTHY` outside any `try`.
- **The CI variable list is written in three places:** `RunIdResolver`, `ScenarioRunner` and `compat/run.sh`.
- **Test hygiene:**
  - scenario JVMs aren't destroyed when another one fails;
  - scenario temp directories are never deleted;
  - one Review Focus test can't tell the stop time from the start time within a millisecond.

#### Acceptance Criteria
- [x] With `maxParallelForks` 2 and 4, the listener integration report equals the 1-fork report: tests=28, passed=14, failed=7, skipped=7, flaky=1
- [x] Maven Surefire with `forkCount=2` reports the tests of both classes (compat)
- [x] A build with a different `runId` replaces the report, including its start time and health
- [x] Two executions in one JVM: `stable()` is present, and `flakyOnce()` has `retries: 1` and `flaky: true`
- [x] Surefire with `rerunFailingTestsCount=1`: a test that fails only once is reported flaky (compat)
- [x] A write that fails halfway leaves the previous report parseable and unchanged
- [x] Gradle test-retry rounds still fold into one test per logical test
- [x] Every report has a root `runId`
- [x] HP-11's acceptance criteria hold
- [x] README documents run identity, the `.lock` file, the per-build `ctrf.run.id` setting (and that it must change with every build) and `npx ctrf merge`

---

### CB-7: Identify Tests by uniqueId, Not Display Name

**Priority**: Critical
**Complexity**: Simple
**Files**: `FlakyTestDetector.java`, `CtrfReportManager.java`, `TestProcessor.java`, `ctrf/model/Test.java`
**Status**: Done on `master` (#126: `af822eb`)

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
- [x] The collision scenarios produce no `retries` or `flaky` values
- [x] The integration `FlakyTest` is still flaky with `retries=1`
- [x] Every test in the report has a `testId`
- [x] No code looks tests up by display name (entries from reports written before CB-7 have no `testId`, so they alone are grouped by file path and name)

---

## High Priority Design Issues

### HP-1: Refactor Singleton Pattern in CtrfReportManager

**Priority**: High
**Complexity**: Complex
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java`, `CtrfExtension.java`, `CtrfListener.java`, test files
**Status**: Done (#138, `61df632`), 2026-10-15; released in 0.5.1. The manager's collaborators are injected through one package-private constructor. The singleton remains only as `getInstance()`, the default instance that the JUnit-created entry points and `EnvironmentHealthTracker` use.

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
- [x] Singleton pattern removed or properly implemented
- [x] All dependencies injected through constructor
- [x] Tests use dependency injection
- [x] No global mutable state accessed from constructors
- [x] CtrfExtension and CtrfListener both work correctly
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
**Status**: Done (#138, `47cd66b`), 2026-10-15; released in 0.5.1. Built with `java.util.logging`, not SLF4J (approach changed 2026-10-08), so the SLF4J-specific criteria below don't apply.

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
- [x] No System.out.println() or System.err.println() in production code
- [ ] SLF4J Logger added to relevant classes (not applicable: `java.util.logging`)
- [x] Appropriate log levels used
- [ ] Exceptions logged with full stack traces
- [ ] Parameterized logging used for performance (not applicable: `java.util.logging`)
- [ ] logback-test.xml provides reasonable defaults for tests (not applicable: `java.util.logging`)
- [x] Documentation explains logging configuration
- [ ] Library users can provide their own SLF4J implementation (not applicable: `java.util.logging`)
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
**Files**:
- model: `ctrf/model/Test.java`, `ctrf/model/Environment.java`, `ctrf/model/Summary.java`, `ctrf/model/CtrfJson.java`;
- production code: `CtrfJsonComposer.java`, `util/SummaryUtil.java`, and every user of `Test.getFilepath()` (`TestProcessor`, `AttemptFolder`, `ReportMerger`, their tests, the scenario helpers and `integration-ctrf-validator`);
- new: `schema/ctrf.schema.json` and `schema/README.md`;
- build: `build.gradle`, `integration-ctrf-validator/build.gradle`;
- validator module: `integration-ctrf-validator/src/main/java/io/github/alexshamrai/integration/JsonSchemaValidator.java`, `.../integration/CtrfSchemaValidationTest.java`, `.../integration/CtrfLogicTest.java`, and its bundled `ctrf-schema.json` (deleted);
- docs: `README.md`, `CLAUDE.md`, `INTEGRATION_TESTS.md`.

**Status**: Done on `master` (#134: `291f21d`). Designed 2026-10-15 against `master` @ `c4093eb`; replaces the 2026-10-08 prompt. Follow-ups: [Deferred from the review](#deferred-from-the-hp-5-review).
**Implementation plan**: [`docs/plans/2026-10-15-hp-5-ctrf-schema.md`](docs/plans/2026-10-15-hp-5-ctrf-schema.md)

#### Why

On 2026-10-15, I validated `master`'s integration reports, generated with CI's flags, against the official schema (`ctrf-io/ctrf` v0.2.0) with a draft-07 validator:
- **Listener report: 29 errors.** Each of its 28 tests has "Additional properties are not allowed ('filepath' was unexpected)", because the spec field is `filePath`. There is also "'777' is not of type 'integer'" at `results/environment/buildNumber`.
- **Extension report: 23 errors.** The same two problems: 22 `filepath` errors and the `buildNumber` error.
- **`specVersion` is `"0.0.0"`.** That matches the schema's pattern but is wrong.
- **A latent mismatch:** `Test.suite` is a `String`, while the spec wants an array of strings. It is always null today, so it never shows.

The integration validator can't detect any of this (TI-4, points 1 and 2):
- its bundled schema is a looser copy, without `additionalProperties: false` and with `buildNumber` typed as a string;
- it uses a draft-04 library, `com.github.java-json-tools:json-schema-validator` 2.2.14, although the schemas are draft-07.

Spec versions: 0.1.0 was tagged on 2026-10-03 and 0.2.0 on 2026-10-10. The only schema change in 0.2.0 is an optional `attemptId` on test objects.

#### Decisions (2026-10-15)

- **Conformance only.** `suite` becomes a list but stays unset. Filling it is [HP-12](#hp-12-fill-suite-with-each-tests-container-hierarchy).
- **Target spec 0.2.0,** pinned to tag v0.2.0, commit `e325eb3`.
- **A non-numeric `ctrf.build.number`** goes to `environment.buildId`, with one warning on stderr.
  - Rejected: putting it under `environment.extra`, which consumers wouldn't show as a build identifier.
  - Rejected: dropping it.
- **The Java field is renamed too:** `filepath` becomes `filePath` (`getFilePath()`), with `@JsonAlias("filepath")` for reading.
  - Rejected: renaming only the JSON property, which would leave the Java getter different from the JSON and from `TestDetails.filePath()`.
- **The validator is `com.networknt:json-schema-validator` 3.0.8** in draft-07 mode.
  - It brings Jackson 3, which lives in its own `tools.jackson` package, so it can share a classpath with the library's Jackson 2.
  - In the main module it is a test dependency only.
  - Rejected: the 1.5.x line, which uses Jackson 2, because Renovate would later propose the API-breaking jump to 3.x.
- **No new runtime dependencies.**
- **Not released on its own.** It ships with Phase 2 as 0.5.0, and the PR has no version bump.

#### Report and model

| Field | Today | After HP-5 |
|---|---|---|
| `Test.filePath` | Java and JSON `filepath` | Java and JSON `filePath` (`getFilePath()`); `@JsonAlias("filepath")` keeps old reports readable. Every usage is renamed. |
| `Test.suite` | `String`, never set | `List<String>`, still never set (HP-12) |
| `Environment.buildNumber` | `String` | `Long`: the configured value, trimmed, if it is digits only and fits in a `long` |
| `Environment.buildId` | not present | `String`: any other non-blank `ctrf.build.number`, with one warning on stderr. A blank value sets neither field. |
| `CtrfJson.specVersion` | `"0.0.0"` | `CtrfJson.SPEC_VERSION = "0.2.0"`, changed only together with the pinned schema |
| `Summary.duration` | not present | `long`, stop minus start, always written (optional in the spec, like `flaky`) |

- `ConfigReader.getBuildNumber()` stays a `String`, since configuration is text. `CtrfJsonComposer` parses it where it builds the environment.
- An old report with `"buildNumber": "777"` still reads, because Jackson converts numeric strings to numbers by default. An old report with a non-numeric string there fails to parse and counts as unreadable. Under CB-6 such a report has no `runId`, so it would be replaced anyway. Lenient reading belongs to MP-5.
- Everything else in today's reports already validates.

#### Validation

**One pinned copy of the official schema**
- `schema/ctrf.schema.json` at the repository root is the exact file from `ctrf-io/ctrf` tag v0.2.0, commit `e325eb3`.
- `schema/README.md` records the source URL, the tag and the commit. It also gives the update rule: replace the file and change `CtrfJson.SPEC_VERSION` in the same commit.
- Both the main module's tests and the integration validator load this file, so there is no second copy to drift.
- The validator's loose copy, `integration-ctrf-validator/src/test/resources/schema/ctrf-schema.json`, is deleted.

**The validator library**
- `com.networknt:json-schema-validator` 3.0.8 in draft-07 mode.
- In the main module it is `testImplementation` only.
- In `integration-ctrf-validator` it replaces `com.github.java-json-tools:json-schema-validator`. The unused `jackson-module-jsonSchema` is removed too, along with the `jsonSchemaValidatorVersion` and `jacksonModuleJsonSchemaVersion` entries in the root `build.gradle`.
- `JsonSchemaValidator` in the validator module is rewritten on top of it and keeps its role: validate a report file against a schema file and return the errors.

**A fast unit test in the main module, `CtrfSchemaConformanceTest`**
- **The library's output must validate.** It builds a report through `CtrfJsonComposer`, the way the library does, with every field the library writes:
  - `testId`, `filePath`, tags, `threadId`, `rawStatus`, message and trace;
  - `retries`, `retryAttempts` and `flaky`;
  - the root `runId`;
  - every environment value, including a numeric `buildNumber`, and separately the non-numeric case that becomes `buildId`;
  - the summary with `flaky`, `duration` and the startup-duration `extra`.

  It serializes the report and requires 0 schema errors.
- **The validator must be strict.** A report with a legacy `filepath` key, and one with a string `buildNumber`, must each be rejected. That proves `additionalProperties: false` and the types are enforced, unlike today's draft-04 setup.

**The integration validator**
- `CtrfSchemaValidationTest` validates the listener and extension reports against the pinned schema, and checks that `specVersion` equals the pinned version.
- This delivers TI-4's first requirement. TI-4 keeps the rest, such as the cross-check against JUnit's XML.

#### Tests

- **Watch the new checks fail first.** `CtrfSchemaConformanceTest` fails on today's model with the `filepath` and string `buildNumber` errors. The rewritten integration validator fails the same way on a report from today's `master` (TI-4's acceptance criterion "the validator fails on today's master output"). Then the fixes make both pass.
- **`CtrfJsonComposerTest`**, for `ctrf.build.number`:
  - `"777"` gives `buildNumber` 777;
  - `" 777 "` gives 777;
  - `"2026.10.15-3"` gives `buildId` and one warning;
  - a blank or missing value gives neither field.
- **`SummaryUtilTest`:** `duration` is stop minus start.
- **`CtrfModelJsonTest`:**
  - `filePath` is written;
  - legacy `filepath` is read;
  - a legacy string `buildNumber` is read;
  - `specVersion` is `"0.2.0"`.
- **Existing tests and scenario helpers** move from `getFilepath()` to `getFilePath()`.
- **Before the PR:**
  - `./gradlew :build`;
  - the listener and extension integration runs with the validator, using CI's flags, which now includes the pinned-schema validation;
  - `compat/run.sh 5.10.5 5.14.4 6.1.3`. `compat/check_report.py` reads only the summary and `runId`, so it isn't affected.

#### Docs and delivery

- **`README.md`:** the `ctrf.build.number` row of the configuration table says that whole numbers go to `buildNumber` and anything else to `buildId`. The README also says the reports follow CTRF 0.2.0.
- **`CLAUDE.md`:** the model notes mention the pinned schema and its update rule (the schema file and `SPEC_VERSION` change together).
- **`IMPROVEMENT_PLAN.md`:** TI-4's status records that its first requirement shipped with HP-5.
- **0.5.0 release notes,** written into the PR description:
  - the JSON field is now `filePath`, so consumers that read `filepath` must switch;
  - `buildNumber` is an integer, and non-numeric values move to `buildId`;
  - `specVersion` is `"0.2.0"`, and `summary.duration` is new;
  - Java API: `getFilepath()`/`setFilepath()`/`builder().filepath()` are now `getFilePath()`/`setFilePath()`/`builder().filePath()`; `Environment.getBuildNumber()` returns a `Long` and `getBuildId()` is new; `Test.getSuite()` returns a `List<String>`; the all-args constructors of `Environment` and `Summary` gain the new fields. (The last three were added by the final review of #134.)
- **Delivery:** branch `fix/hp-5-ctrf-schema` from `master`, one commit, and a PR description file.

#### Deferred from the HP-5 review

The per-task and whole-branch reviews of #134 left these open.
- **A build number that isn't plain digits warns on every run.** Azure Pipelines' default build number (`20261015.1`) and TeamCity's (`1.0.42`) both go to `buildId` with a warning, and such reports have no `buildNumber`. A `ctrf.build.id` setting would let these CI systems set `buildId` directly without the warning, and keep `buildNumber` for a numeric counter (for example Azure's `Build.BuildId`).
- **`./gradlew :build` doesn't run `:javadoc`.** Only publishing does (`compat/run.sh`, CI's compat jobs, the release). A Lombok-generated type in a method signature passed every review and `:build`, and only compat caught it before the PR. Adding `javadoc` to `check` would catch it locally.
- **The validator task must be run with `--rerun`,** because Gradle doesn't treat the report file as an input. Declaring `ctrf.report.path` as an input file would fix it (TI-4, requirements 4 and 5).
- **`CtrfSchemaConformanceTest` asserts only "0 schema errors".** Most fields it covers are optional in the schema, so a field the library silently stopped writing would still pass.
- **The `Test.filePath` Javadoc says reports "before 0.5.0" used `filepath`.** It becomes wrong if a 0.4.x patch release is cut from `master` before 0.5.0.
- **Small leftovers:**
  - `CtrfLogicTest` still says "filepath" in an assertion message and a variable name;
  - `CtrfSchemaValidationTest` keeps hand-written summary checks that the schema now enforces;
  - the rejection tests match networknt's message format (`/pointer: …`), so a validator upgrade that changes it fails them loudly.

#### Acceptance Criteria
- [x] The listener and extension integration reports validate against the pinned official schema with 0 errors
- [x] Reports that use `filepath` or a numeric string `buildNumber` can still be read
- [x] A non-numeric build number never produces an invalid report; it is written as `buildId`
- [x] `specVersion` equals the pinned schema's version
- [x] The integration validator rejects a report with `filepath` or a string `buildNumber`
- [x] No runtime dependency is added

---

### HP-6: One Test Object per Logical Test (Fold Retry Attempts)

**Priority**: High
**Complexity**: Moderate
**Files**: `CtrfReportManager.java`, `TestStateTracker.java`, `FlakyTestDetector.java` (deleted) → `AttemptFolder.java` (new), `TestRerunHandler.java` (Javadoc), `util/SummaryUtil.java`, `ctrf/model/Test.java`, `ctrf/model/Summary.java`, `ctrf/model/RetryAttempt.java` (new), `scenario/ScenarioRunner.java`, `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`, `README.md`, `CLAUDE.md`
**Status**: Done on `master` (#130: `56518bd`). Designed 2026-10-14 against `master` @ `7235eb5`; replaces the 2026-10-08 prompt. Follow-ups: [HP-11](#hp-11-a-class-failure-that-recovers-on-retry-stays-failed) and [Deferred from the review](#deferred-from-the-hp-6-review).
**Implementation plan**: [`docs/plans/2026-10-14-hp-6-fold-retry-attempts.md`](docs/plans/2026-10-14-hp-6-fold-retry-attempts.md)

#### Why

Each attempt of a retried test is a separate entry today. A build that is green after retries (`failOnPassedAfterRetry=false`) still produces `summary.failed > 0`, so every CTRF consumer (GitHub test reporter, Slack reporters, `ctrf-cli`) shows the run as failed.

Gradle's default output counts attempts the same way. Its console says "37 tests completed, 15 failed", and its JUnit XML has the flaky test twice, `tests=2 failures=1`. Gradle offers `reports.junitXml.mergeReruns = true` for exactly this problem. With it, the same run has 28 `<testcase>` elements, and the flaky test is one element with `<flakyFailure>`. Surefire's `rerunFailingTestsCount` always merges, and its summary reports `Flakes: 1`. That's per its docs; not run here.

CTRF defines it explicitly (spec 0.1.0, sections 9.1, 9.20, 9.21). The test object is the final attempt, earlier attempts go into `retryAttempts`, and "if `retries` is greater than 0, `retryAttempts` MUST be present". Today's second entry has `retries: 1` and no `retryAttempts`, which violates that MUST.

Today every result also scans all earlier results (`FlakyTestDetector`) and copies the whole `CopyOnWriteArrayList` (MP-3: 80k tests take 47.9 s).

#### Decisions (2026-10-14)

- Fold retries by default, with no option to keep one entry per attempt.
- Not released on its own. HP-6 merges to `master`, and Phase 2 ships together as 0.5.0. The PR has no version bump; the release notes describe the count change.
- HP-6 lands before CB-6 and works on today's single report file. Merging shards by `runId` (the 2026-10-08 prompt's requirement 3) is CB-6's job, which reuses `AttemptFolder`.
- Same-JVM reruns (Surefire `rerunFailingTestsCount`, CB-6c) stay broken until CB-6. HP-6 doesn't make them worse.
- Retries that JUnit gives new unique IDs (JUnit Pioneer `@RetryingTest`) are different tests by `testId` and are not folded.
- `filePath`, `suite`, `specVersion` and validation against the official schema stay in HP-5/TI-4.
- Approach: record every attempt with an O(1) append and fold once at the end. This was chosen over a `testId` → attempts map with a separate attempt type, which needs more types and conversions and loses order. It was also chosen over folding as each result arrives, which spreads the flaky logic over every event while CB-6 would still need a general fold.

#### Report shape

```json
{
  "testId": "[engine:junit-jupiter]/[class:...FlakyTest]/[method:flakyTest()]",
  "name": "Flaky test passed on the second run",
  "status": "passed",
  "duration": 3, "start": 1760430001200, "stop": 1760430001203,
  "filepath": "io.github.alexshamrai.integration.fake.FlakyTest",
  "tags": ["..."], "threadId": "ForkJoinPool-1-worker-1",
  "retries": 1,
  "flaky": true,
  "retryAttempts": [
    { "attempt": 1, "status": "failed", "duration": 5,
      "start": 1760430000100, "stop": 1760430000105,
      "message": "org.opentest4j.AssertionFailedError: ...", "trace": "..." }
  ]
}
```

- A test that ran once looks as it does today, with no `retries`, `retryAttempts` or `flaky`.
- A retried test is one object, its final attempt. `retries` = n, and `retryAttempts` holds the n earlier attempts, numbered 1..n in the order they ran. The final attempt is number n+1. Name, tags, `filepath`, `threadId` and `rawStatus` come from the final attempt.
- `flaky: true` only when the final attempt passed and an earlier attempt failed. Otherwise the field is omitted. Skipped (aborted), then passed, is not flaky.
- Attempt entries carry `attempt`, `status`, `duration`, `start`, `stop`, `message` and `trace`. The spec allows no other fields there, so `threadId` and `rawStatus` stay on the test object.
- The summary counts test objects, that is final attempts only. `summary.flaky` is always present, 0 when no test is flaky.
- Class failures (`initializationError`/`teardownError`) fold by their class's `testId`, as CB-7 set up.
- Model: a new `ctrf.model.RetryAttempt`, in the style of the other model classes. `Test` gets `List<RetryAttempt> retryAttempts`, and `Summary` gets `int flaky`; reading a report without `flaky` gives 0.
- Expected integration summaries (`master` @ `7235eb5`, CI flags):
  - listener: 37 entries become tests=28, passed=14, failed=7, skipped=7, flaky=1;
  - extension: 29 entries become tests=22, passed=14, failed=5, skipped=3, flaky=1.

  The 2026-10-08 numbers (19/13/4/2/1) predate the CB-5/HP-7/HP-8 fake tests.

#### Recording and folding

- `TestStateTracker`: the `CopyOnWriteArrayList<Test>` becomes a `ConcurrentLinkedQueue<Test>` of attempts, which makes adding an attempt O(1).
- `CtrfReportManager`: `processTestResult` and `onTestSkipped` add the attempt they build, as today, and no longer call `FlakyTestDetector`.
- `startTestRun`: the existing report's tests go through `AttemptFolder.unfold`. Each test yields its `retryAttempts` as attempts, copying the test's identity (`testId`, name, `filepath`, tags) and taking each entry's own status, duration, start, stop, message and trace. The test itself follows as its final attempt, with `retries`, `retryAttempts` and `flaky` cleared. A third retry round therefore extends the history. A 0.4.x report, with duplicates and no `retryAttempts`, yields one attempt per entry.
- `finishTestRun`: `AttemptFolder.fold` makes one pass over a snapshot of the queue.
  - It groups attempts by `testId`, and groups keep the order of their first attempt.
  - Within a group, attempts keep arrival order: the report file's attempts first, then this run's. A retry round starts only after the previous one ends, so this is the order they ran.
  - Each group becomes one test object, following the rules above.
  - `SummaryUtil.createSummary` also counts `flaky`.
- `AttemptFolder`: a new package-private class with no state and two static methods, `fold` and `unfold`. It replaces `FlakyTestDetector`, which is deleted; its tests move over, including CB-7's `shouldMatchClassFailuresAcrossPhases`. CB-6's merge is "unfold every shard, then fold".
- Entries without a `testId` come only from reports written before CB-7. They group by `filepath` + name among themselves and are never joined to entries that have a `testId`. `FlakyTestDetector` joins them today. That only matters when an old report is left over from an earlier build, which is the CB-6b accumulation bug anyway.
- Concurrency: attempts are added from any thread. Folding runs once in `finishTestRun`, after JUnit has reported every test, so it needs no locking.
- Cost: O(1) per result and O(n) to fold, which resolves MP-3.

#### Edge cases

- A test that failed, then was skipped (aborted) in the next round: final skipped, `retries: 1`, not flaky, counted as skipped.
- `retryAttempts` read out of order or with gaps: keep array order and renumber 1..n. This is defensive; we only read what we wrote.
- A passing test rerun because Gradle reran its whole class (`testBeforeFailingTeardown()` today): passed, passed, `retries: 1`, not flaky.

#### Tests

- `AttemptFolderTest` (replaces `FlakyTestDetectorTest`):
  - a single attempt is untouched;
  - failed → passed is flaky with `retries: 1`;
  - failed → failed and skipped → passed are not flaky;
  - three attempts are numbered 1, 2;
  - groups keep first-attempt order;
  - `initializationError` → `teardownError` of one class folds;
  - entries without `testId` fold by `filepath` + name and stay apart from entries with one;
  - `unfold` gives n+1 attempts with the identity copied;
  - `fold(unfold(fold(x)))` equals `fold(x)`.
- `SummaryUtilTest`: `flaky` is counted.
- `CtrfReportManagerTest`: a report with a folded test is read at start, a new attempt is added, and finishing gives `retries: 2`. Tests expecting per-result `retries`/`flaky` are rewritten.
- Scenarios (forked JVMs): `ScenarioRunner` can run the same fixtures several times in fresh JVMs that share one working directory and report file, as Gradle's retry rounds do. A new fixture fails until a marker file exists in the working directory. Each of these runs through both the listener and the extension:
  - flaky: one passed entry, `retries: 1`, `flaky: true`, attempt 1 failed;
  - always failing: one failed entry, `retries: 1`, not flaky;
  - three rounds: `retries: 2`, attempts 1 and 2;
  - the summary counts each test once.

  Each scenario must fail on `master`.
- `CtrfLogicTest`:
  - the summaries above;
  - every `testId` appears once;
  - `retries` equals the number of `retryAttempts`, and both are present or both absent;
  - attempts are numbered 1..n;
  - the flaky test has a failed attempt 1 and a passed final attempt;
  - the always-failing tests have a failed attempt and aren't flaky;
  - `summary.flaky` equals the number of flaky tests.

  Rewrite the comments about tests running twice because of the retry plugin. Confirm the bundled schema accepts `retryAttempts` and `summary.flaky`.
- Before the PR:
  - `./gradlew :build`;
  - both integration runs plus the validator with CI's flags;
  - `compat/run.sh 5.10.5 5.14.4 6.1.3`;
  - the MP-3 measurement (10k to 80k tests) before and after.

#### Docs and delivery

- `README.md`: a new section, "Retries and flaky tests". It covers one entry per test with `retryAttempts`, the flaky rule, and the summary counting each test once. So `tests` is lower than Gradle's console count, which counts every attempt even with `mergeReruns`, and it matches the number of `<testcase>` elements in Gradle's XML when `mergeReruns` is on.
- `CLAUDE.md`: only the lines HP-6 makes wrong.
  - the data flow (`FlakyTestDetector`);
  - the component table (`TestStateTracker`, `FlakyTestDetector`);
  - the concurrency section (`CopyOnWriteArrayList`, `FlakyTestDetector`);
  - the file tree;
  - "Flaky Test Detection" (one entry per test; no `filepath` + name joining).
- Branch `fix/hp-6-fold-retry-attempts` from `master`, one commit, PR description file.

#### Deferred from the HP-6 review

The final review of #130 rated these as minor:
- With the extension, when another extension's `BeforeAllCallback` fails in the first class, before any class has started the run, that class's new attempt is recorded before the report file's attempts. Its history is then in the wrong order. In the reproduced case both attempts failed, so only the order was wrong. Fix: keep the report file's attempts in their own list, and fold them before this run's. Consider it with CB-6.
- `AttemptFolderTest.entriesOfAnOlderReportFoldIntoOneTest` uses `testId`s, but released 0.4.x reports have none. Add a variant keyed by file path and name that goes through `unfold`, then `fold`.
- `CLAUDE.md` says `AttemptFolder` runs only in `finishTestRun`, but `unfold` also runs in `startTestRun`.

#### Acceptance Criteria
- [x] Each logical test appears exactly once in `results.tests`
- [x] Integration reports show listener tests=28, passed=14, failed=7, skipped=7, flaky=1 and extension tests=22, passed=14, failed=5, skipped=3, flaky=1
- [x] `retries` always equals `retryAttempts.size()`, `retryAttempts` is absent when `retries` is 0, and attempts are numbered 1..n
- [x] A test that passes after a failed attempt has `flaky: true`, and `summary.flaky` counts such tests
- [x] A third retry round extends `retryAttempts` instead of nesting it
- [x] Recording a result is O(1): the MP-3 measurement scales linearly
- [x] Reports validate against the official schema once HP-5 lands (#134)

---

### HP-7: Report Aborted Tests (Failed Assumptions) as Skipped

**Priority**: High
**Complexity**: Simple
**Files**: `CtrfReportManager.java`, `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java`, unit tests, integration fake tests
**Status**: Done on `master` (#129: `f6c3c7b`)

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
- [x] Failed assumptions appear as skipped, with `rawStatus` "aborted" and the assumption message
- [x] `summary.failed` does not count aborted tests
- [x] The listener and the extension behave the same way

---

### HP-8: Report Tests Inside Skipped Containers

**Priority**: High
**Complexity**: Simple
**Files**: `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java` (documented limitation), tests
**Status**: Done on `master` (#127: `732bf7e`). The extension still cannot see the tests of a class skipped as a whole; its Javadoc says so, and HP-10 resolves it.

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
- [x] Skipped counts match JUnit's `SummaryGeneratingListener` for disabled classes, class-level conditions and aborted containers (with the listener)
- [x] Each skipped test carries the container's reason

---

### HP-9: Extension Must Not Lose Test Identity ("Unknown Test")

**Priority**: High
**Complexity**: Simple
**Files**: `jupiter/CtrfExtension.java`, `CtrfReportManager.java`
**Status**: Done on `master` (#128: `7235eb5`)

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
- [x] The scenario reports `importantBusinessTest()` with its class and tags
- [x] No "Unknown Test" entries appear when an `ExtensionContext` is available

---

### HP-10: Make the TestExecutionListener the Single Core Integration Point

**Priority**: High
**Complexity**: Complex
**Files**: `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java`, `jupiter/TestRunExtension.java`, `CtrfReportManager.java`, `EnvironmentHealthTracker.java`, `README.md`
**Status**: Done (#138, `ecb4fd1`, `61df632`), 2026-10-15; released in 0.5.1. Motivated by the verified defects in CB-5, HP-8 and HP-9. Built as designed in [Phase 3 Design — 0.5.1](#phase-3-design--051): run ownership (CB-2), and the README's "Choosing a setup". The listener stays opt-in.

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
- [x] All CB-5 and HP-8 scenarios pass with the listener
- [x] The listener reports non-Jupiter engines (e.g. Vintage)
- [x] README recommends one setup and explains the trade-offs

---

### HP-11: A Class Failure That Recovers on Retry Stays Failed

**Priority**: High
**Complexity**: Moderate
**Files**: `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java`, `AttemptFolder.java`, `CtrfReportManager.java`, scenario tests
**Status**: Done on `master` with CB-6 (#132: `9084a83`). Designed 2026-10-14 together with CB-6, in the CB-6 section ("HP-11: container successes"). Found by the HP-6 review on 2026-10-14 and reproduced in forked-JVM retry rounds. `master` had the same behaviour before HP-6.

#### Why

A class-level entry (`initializationError` or `teardownError`), or one for a failed container below class level such as a `@MethodSource`, is written only when the container fails. When the Gradle test-retry plugin reruns the class and it succeeds, nothing is recorded for it, so there is no later attempt to fold into the first round's failure.

Reproduced through both entry points: `@BeforeAll` fails in round 1 and passes in round 2. The report keeps `initializationError` as failed, without `retries`, and the summary has `failed: 1` and `flaky: 0`. With `failOnPassedAfterRetry=false`, Gradle is green while CTRF consumers show the run as failed. That is the false red HP-6 removed for tests. Class setup that depends on a database or a container is a common source of such flakes.

#### Decided (2026-10-14)

- A recovered container failure stays one entry. It has status `passed`, `retries: n`, `flaky: true` and its failures in `retryAttempts`, and keeps the name of its last failure. So `summary.tests` is one higher than in a run where the class never failed, and `summary.flaky` counts the flake. Rejected: dropping the entry, which loses the evidence of the flake.
- Successes are collected separately from attempts, as `ContainerSuccess` records, and `ReportMerger` turns a success into a final passed attempt only for a container that already has a failure. That replaces the review's sketch of marker attempts inside the attempt list.

#### Acceptance Criteria
- [x] A class whose `@BeforeAll` fails in round 1 and passes in round 2 is no longer counted as failed, through both entry points
- [x] The same holds for `@AfterAll`, and for a failed container below class level that recovers (below class level: covered by `ReportMerger` unit tests, not by a scenario)
- [x] A container that fails in every round is still one failed entry, with its history in `retryAttempts`
- [x] Containers that never failed add no entries

---

### HP-12: Fill `suite` with Each Test's Container Hierarchy

**Priority**: Medium
**Complexity**: Moderate
**Files**: `launcher/CtrfListener.java`, `jupiter/CtrfExtension.java`, `model/TestDetails.java`, `TestProcessor.java`, `adapter/*`, scenario tests
**Status**: Done (#144, `04fe3c9`..`c793e51`), 2026-10-16; not released. Designed 2026-10-16; see [HP-12 Design — `suite`](#hp-12-design--suite), which settles the questions below. Split from HP-5 on 2026-10-15; HP-5 types `suite` as a list but leaves it unset.

#### Why

CTRF consumers group and filter tests by `suite`. The spec defines it as "Suite hierarchy from top-level to immediate parent": an array of strings with at least one item. This library never sets it, so every test appears ungrouped.

#### To decide (decided in [HP-12 Design](#hp-12-design--suite))

- **What goes in it:**
  - display names or class names;
  - whether the engine ("JUnit Jupiter") is included;
  - how `@Nested` classes and parameterized or dynamic method containers appear. For example, `["ATest", "Nested"]`, or `["ATest", "usesData(int)"]`.
- **How each entry point builds it:** the listener walks the `TestPlan` ancestors; the extension walks the `ExtensionContext` parents. Both must give the same `suite` for the same test.
- **Class-level failure entries:** what `suite` an `initializationError` or `teardownError` entry gets, and how a retry round folds it.

#### Acceptance Criteria
- [ ] Every test entry has a `suite` that matches the official schema
- [ ] The listener and the extension produce the same `suite` for the same test
- [ ] Scenario tests cover nested classes, parameterized tests and class-level failures

---

## Medium Priority Improvements

### MP-1: Optimize SummaryUtil with Single-Pass Counting

**Priority**: Medium
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/util/SummaryUtil.java`
**Status**: Done on `master` (`64f5bdd`); HP-6 (#130) added `summary.flaky`

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
**Status**: Done on `master` by HP-6 (#130: `56518bd`). Attempts go into a `ConcurrentLinkedQueue` and are folded once when the run finishes. Recording 80k tests took 167 s on the previous `master` and takes 0.18 s now, on the same machine. The prompt below is superseded.

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
**Status**: Superseded by CB-6 (#132), decided 2026-10-15. Not planned for 0.5.0. Since MP-9 (#138), unknown fields no longer discard the report; they are ignored, so they aren't kept either.

#### Why it is superseded

CB-6 changed when and why a previous report is read. Each JVM reads the report under a lock at the end of its execution, and merges into it only if it has the same `runId`. A report of another run, or one without a `runId`, is replaced. So:
- **Reports from earlier builds or other tools are replaced anyway.** Failing to read one loses nothing, and a missing `healthy` in one no longer matters (findings 1 and 2).
- **Finding 1's example is gone.** `runId` is now part of the model.
- **Finding 3 was in code CB-6 rewrote.** The merge copies `start` and `stop` without unboxing them, and falls back if a merge throws.
- **The library no longer leaves a broken file itself,** because writes are atomic.

What remains needs a downgrade inside one run: an *older* reporter reading a same-run report written by a *newer* one that added a field.
- The JVMs of one test task (forks, retry rounds, Surefire reruns) share one classpath, and so one reporter version.
- Different modules write to their own `ctrf-report.json` by default.
- A mixed-version merge therefore needs two test tasks or modules on different reporter versions, writing to one report path with a shared run id, or local daemon builds across a downgrade without the per-build `ctrf.run.id`. The README already advises against both.

Phase 3 (0.5.1, commit 3) closes this case anyway: the new JSON reader ignores unknown fields. Before that, the fix would have been one line: ignore unknown fields when reading (`@JsonIgnoreProperties(ignoreUnknown = true)` on the model, or `FAIL_ON_UNKNOWN_PROPERTIES` off), plus a unit test.

#### Prompt (2026-10-08, kept for reference)
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

#### Acceptance Criteria (superseded; kept for reference)
- [ ] Reports with spec-valid but unknown fields are read without losing data
- [ ] A missing `healthy` field does not mark the run unhealthy
- [ ] Unparsable files are preserved, never silently overwritten

---

### MP-6: Replace Deprecated ExtensionContext.Store.CloseableResource

**Priority**: Medium
**Complexity**: Simple
**Files**: `jupiter/TestRunExtension.java`
**Status**: Done (#138, `b52b060`), 2026-10-15; released in 0.5.1.

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
- [x] No `CloseableResource` warning on JUnit 6
- [x] The report is still written on the oldest supported JUnit 5.x

---

### MP-7: Never Let Reporter Errors Fail or Mask User Tests

**Priority**: Medium
**Complexity**: Simple
**Files**: `jupiter/CtrfExtension.java`, `jupiter/TestRunExtension.java`, `CtrfReportManager.java`
**Status**: Done (#138, `639fd06`), 2026-10-15; released in 0.5.1. The extension guards every callback; the listener needs no guard, because JUnit's launcher already catches listener exceptions.

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
- [x] No reporter exception can change a test's status or exception
- [x] Reporter failures are logged with context

---

### MP-8: Separate `message` from `trace`; Bound the Trace Size

**Priority**: Medium
**Complexity**: Simple
**Files**: `TestProcessor.java`, `config/CtrfConfig.java`, `config/ConfigReader.java`
**Status**: Done (#138, `8f91c54`), 2026-10-15; released in 0.5.1. `ctrf.max.trace.length` defaults to 20,000.

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
- [x] `message` contains the exception summary, not stack frames
- [x] `trace` respects the configured maximum

---

### MP-9: Dependency and Configuration Hygiene (owner, Env Vars, Jackson)

**Priority**: Medium
**Complexity**: Moderate
**Files**: `config/CtrfConfig.java`, `config/ConfigReader.java`, `CtrfReportFileService.java`, `build.gradle`, `README.md`
**Status**: Done (#138, `d888754`, `037e253`), 2026-10-15; released in 0.5.1. Our own config loader, JSON writer and JSON reader leave no runtime dependencies. Finding 2 was wrong: `owner` is developed again (`2.0.0-SNAPSHOT`), but hasn't released since 1.0.12 (2020-06-06).

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
- [x] `CTRF_REPORT_PATH` and the other `CTRF_*` variables work
- [x] No owner dependency; Jackson is removed or shaded
- [x] Published runtime dependencies are listed and justified in README or DEVELOPMENT.md

---

## Code Smells & Quality Issues

### CQ-1: Extract Feature Envy from CtrfReportManager

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java` + new files
**Status**: Superseded by CB-7 and HP-6; HP-6 (#130) removed `FlakyTestDetector`. Do not implement the design below as written.

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
**Files**:
- integration modules: `integration-tests-listener/build.gradle`, `integration-tests-extension/build.gradle`;
- validator module: `integration-ctrf-validator/build.gradle`, `.../integration/JunitEvents.java` (new, `src/main`), and in `src/test`: `BaseIntegrationTest.java`, `CtrfLogicTest.java`, `JunitCrossCheckTest.java` (new), `JunitEventsTest.java` (new);
- CI: `.github/workflows/integration-listener.yml`, `.github/workflows/integration-extension.yml`;
- docs: `INTEGRATION_TESTS.md`, `CLAUDE.md`.

**Status**: Done on `master` (#135: `059992b`). Designed 2026-10-15 against `master` @ `291f21d`, replacing the 2026-10-08 prompt. Follow-ups: [Deferred from the review](#deferred-from-the-ti-4-review). Earlier parts:
- requirement 1 shipped with HP-5 (#134: `291f21d`): the validator checks reports against the pinned official schema (`schema/ctrf.schema.json`) with `com.networknt:json-schema-validator` in draft-07 mode, and rejects `filepath` and a string `buildNumber`;
- requirement 6: CI runs the listener integration with 2 forks (CB-6, #132).

**Implementation plan**: [`docs/plans/2026-10-15-ti-4-junit-cross-check.md`](docs/plans/2026-10-15-ti-4-junit-cross-check.md)

#### Why

The 2026-10-08 prompt listed five problems and six requirements. After HP-5 and CB-6, three problems remain:
- **Nothing independent checks the report's tests.** The validator compares the report only with numbers written by hand (28/14/7/7 and 22/14/5/3). It can't say which test is missing or has the wrong status, and the numbers only catch a bug like CB-5, CB-6a or HP-8 if whoever wrote them knew the right count.
- **Some assertions pass when their test is missing.** `CtrfLogicTest.verifyTestStatuses` uses `findFirst().ifPresent(...)` and matches names with `contains()`, so a missing test passes, and "Second failed test" (the display name of `secondFailedTest()`) is never checked.
- **The validator only passes with CI's exact command line.**
  - `BaseIntegrationTest.DEFAULT_REPORT_PATH` is a sentence, not a path.
  - It expects exactly 2 threads, build name `system-build`, a GitHub Actions build URL, and, for the listener, `ENV_HEALTHY=false`.
  - It tells the listener from the extension by whether the report path contains "listener".

  A plain local run fails, and so would CI's manual trigger with another thread count.

**Gradle's JUnit XML can't be the independent check,** although the 2026-10-08 prompt suggested it. Measured on 2026-10-15 with CI's flags:
- The extension's XML folds by (class, name) to exactly the report's 22 tests: 29 executions, 14 passed, 5 failed, 3 skipped, 1 flaky.
- The listener's XML folds to 27 where the report correctly has 28. Gradle names both of `BrokenSourceTest`'s failing containers (`usesData(int)` and `factory()`) `initializationError`, and its XML has no unique IDs to tell them apart.
- Gradle also names a class's `@AfterAll` failure `executionError`, where the report says `teardownError`.

**JUnit's own event log can be.** JUnit Platform's Open Test Reporting (`junit-platform-reporting`) writes every node's unique ID, which is the report's `testId`, and each start and finish with its result.
- Measured on 2026-10-15 with a Gradle init script: JUnit 6.1.3 writes `open-test-report.xml` into `junit.platform.reporting.output.dir`. With a plain directory, each JVM overwrites the file and only the retry round's 11 events survive. With `{uniqueNumber}` in the path, each JVM gets its own directory: 4 for the listener, which are 2 forks and 2 JVMs in the retry round.
- Folded by unique ID, the listener's events give 24 entries plus 2 skipped classes, whose 4 tests the report lists as skipped. That is 28, and every `testId`, final status, `retries` count and `flaky` flag agrees with the report. The extension's events give exactly its 22 entries.

#### Decisions (2026-10-15)

- **The independent check is JUnit's event log, matched by `testId`.**
  - Rejected: Gradle's JUnit XML totals, for the folding problem above and because they can't check `retries` or `flaky` per test.
  - Rejected: both together, which adds code and keeps the naming caveat.
- **CI-specific expectations come from the inputs that produced the report:** the same `-Dthreads` and `-Dctrf.build.*` flags, the same `ENV_HEALTHY` variable, and the module's own `ctrf.properties`.
  - Rejected: separate `validator.expected.*` properties defaulting to CI's values, which keep local runs failing without flags.
  - Rejected: dropping the CI-only checks, which leaves nothing that checks end to end that Gradle forwards `-Dctrf.*` to the test JVM.
- **`CtrfLogicTest` is trimmed and keyed by `testId`.** Checks that the cross-check now makes are deleted. Checks of what JUnit can't know stay, and look the test up by `testId`. The fixed summary counts stay as a guard on the fake test set and on the number of tests inside skipped classes.
- **No change to the published library,** so no version bump and no release notes.

#### The JUnit cross-check

**Integration modules** (listener and extension `build.gradle`, test-only):
- `testRuntimeOnly 'org.junit.platform:junit-platform-reporting'`, versioned by the JUnit BOM;
- the test task sets `junit.platform.reporting.open.xml.enabled=true` and `junit.platform.reporting.output.dir` to `build/test-results/junit-platform/{uniqueNumber}`, so each JVM writes its own `open-test-report.xml`.

**`JunitEvents`** (new, in the validator module's `src/main`, next to `JsonSchemaValidator`):
- It reads every `open-test-report.xml` under the events directory with the JDK's XML parser, so no new dependency.
- It pairs started and finished events by unique ID, and orders each node's attempts by finish time.
- It derives what the report must contain:
  - **every test node,** with its final status (`SUCCESSFUL` → passed, `FAILED` → failed, `ABORTED` and `SKIPPED` → skipped), `retries` (its attempts minus 1, absent when 0) and `flaky` (the final attempt passed after an earlier failure);
  - **every container below the engine that failed at least once,** by the same rules. That covers CB-5's failed containers and HP-11's recovered class failures;
  - **skipped and aborted containers,** separately. JUnit never starts their tests, so its log can't list them; the report must have at least one entry for or within each, all skipped (HP-8).
- *Corrected by the final review of #135:*
  - **A skipped container's own ID counts as covered.** For a skipped `@ParameterizedTest`, `@RepeatedTest` or `@TestFactory`, the library writes one skipped entry under the container's own ID.
  - **A container aborted after its tests ran,** such as an assumption failing in `@AfterAll`, is covered by its listed tests. Only the report entries JUnit's log doesn't list must be skipped, and at least one is required only when the log lists none.
  - **A JVM that dies leaves a truncated log.** Open Test Reporting writes `</e:events>` only when the test plan finishes. `JunitEvents` reads such a log up to its last complete event, so the unfinished attempt is ignored. The cross-check's failure message names the truncated logs.
  - **Errors name the log file,** and a log whose root isn't Open Test Reporting events 0.2.0 fails clearly.

**`JunitCrossCheckTest`** (new) checks the report against `JunitEvents`, by `testId`:
- every entry JUnit's events expect is present, with the same status, `retries` and `flaky`;
- the report has no other entries, except entries for or within a skipped or aborted container;
- for each skipped or aborted container, the report's entries for or within it that JUnit's log doesn't list are all skipped, and there is at least one when the log lists none of its tests;
- a missing or empty events directory fails with a message saying how to produce it.

The events directory defaults to `junit-platform/` next to the report (`<module>/build/test-results/junit-platform`). `-Dctrf.junit.events.dir` overrides it.

#### Inputs and CI expectations

**Inputs:**
- **Report:** `-Dctrf.report.path`, defaulting to `../integration-tests-listener/build/test-results/ctrf-report.json`, relative to the validator module, which is the working directory of its tests. A missing file fails with "No report at <path>. Run `./gradlew :integration-tests-listener:test` first, or pass `-Dctrf.report.path=…`".
- **Module under test:** the directory that contains the report's `build/` directory, `integration-tests-listener` or `integration-tests-extension`. Any other location fails with a clear message.
  - It replaces the "path contains listener" heuristic in `verifyGeneratedByFieldIsCorrect`, which now checks `generatedBy` against the module's entry point.
  - It also replaces `isListenerReport()`'s use of `generatedBy`: listener-only checks use the module.

**Expectations from the run's own inputs:**
- **Threads:** `-Dthreads`, the property the integration modules read. Unset or 1: exactly one distinct thread name. N > 1: between 2 and N. The validator's `build.gradle` forwards `threads` in addition to `ctrf.*`.
- **Build name, number and URL:** the `-Dctrf.build.*` values if the validator got them, else the module's `src/test/resources/ctrf.properties`. That's the same precedence the library applies. The number is compared as a `Long`.
- **Health:** unhealthy if `ENV_HEALTHY=false`, or if the module is the extension, whose `FirstLongTest` marks the environment unhealthy; otherwise healthy.

**Running it:**
- **CI:** each validator step passes the same `-Dthreads` and `-Dctrf.build.*` flags as its generating step. The listener job keeps `ENV_HEALTHY=false` set for the whole job.
- **Locally:** `./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test` passes with no flags. Flags are passed once to that one command, and both tasks receive them.
  - For the extension, add `-Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json`.
  - That flag also reaches the extension's own test JVM. Resolved from that module's directory it names the same file, so it is harmless; the docs say so.

#### `CtrfLogicTest`

- **Deleted:** `verifyTestStatuses`, and the status, `retries` and `flaky` assertions elsewhere that the cross-check now makes.
- **Kept, but looked up by `testId`:** checks of what JUnit can't know.
  - messages ("Simulated initialization failure" and others), `rawStatus` "aborted", the contents of `retryAttempts`;
  - durations, thread IDs, and the `testId` shape of class failures;
  - the tests of skipped classes and their reason.

  One helper builds the full ID from the class and its last segment, for example `testWithId("DummyFailedTest", "[method:secondFailedTest()]")`, and fails if the entry is missing.
- **Kept as they are:** the fixed summary counts, the summary arithmetic, and the `runId` check.

#### Keeping JUnit's log and the report in step

- **The integration test task deletes `build/test-results/junit-platform` in a `doFirst` action.** That runs once per task, before the first retry round, so directories from earlier builds don't pile up.
- **The integration modules set a per-build `ctrf.run.id`,** using the snippet the README documents since CB-6.
  - A second build without `clean` then replaces the report, as the event log is replaced. Without it, the Gradle daemon merges both builds into one report while the log holds only the last build, and the cross-check fails.
  - The forks and retry rounds of one build share the id, so they still merge.

#### Tests

Each new check is watched failing first:
- **`JunitEventsTest`:** unit tests on inline XML samples, written before `JunitEvents` exists. They cover a retried test, a flaky test, a recovered container, a skipped class, an aborted test, and attempts spread over several JVM files.
- **Lost data across forks:** a worktree at `58e36f0` (before CB-6). A Gradle init script adds the event log and 2 forks to the old build without editing it. `JunitCrossCheckTest` must fail and name the lost tests. In CB-6's measurements, that commit lost 12 of 28 tests with 2 forks.
- **A plain local run:** `./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test` with no flags fails on today's validator (the default path, the thread count, the build values, health), and must pass on the new one.
- **Final runs:**
  - the listener with 2 forks and the extension, each with CI's flags, plus the validator;
  - the listener with `-Dthreads=4`;
  - the same listener command run twice without `clean`;
  - `./gradlew :build` and `compat/run.sh 5.10.5 5.14.4 6.1.3`, which this doesn't touch.

#### Docs and delivery

- **`INTEGRATION_TESTS.md`:** the one-command local run, how flags reach both tasks, the event log, and what the cross-check checks.
- **`CLAUDE.md`:** the integration-test commands.
- **`IMPROVEMENT_PLAN.md`,** after the merge: TI-4 done.
- **Delivery:** branch `test/ti-4-junit-cross-check` from `master`, one commit, and a PR description file.

**Out of scope:** TI-5's validation of each scenario's output against the schema; Windows; the Maven compat consumers.

#### Deferred from the TI-4 review

The per-task and whole-branch reviews of #135 left these open.
- **The full validator needs `clean`.** `FlakyTest` keeps its attempt counter in `build/tmp`. On a run without `clean`, the flaky fixture passes on its first attempt, so `CtrfLogicTest`'s flaky expectations fail. The cross-check itself passes. Keying the counter by `ctrf.run.id` would make reruns behave like clean runs.
- **A failing JUnit engine shows up as an unknown entry.** The library reports an engine-level failure, but `JunitEvents` ignores the engine. This only happens when the whole fake suite is broken.
- **Without `-Dctrf.report.path`, the extension's one-command run validates the listener's report.** It may be stale, and the run passes. A default derived from whichever integration task ran would avoid it.
- **The integration modules override a `-Dctrf.run.id` you pass yourself,** because each build sets a fresh one.
- **Small gaps:**
  - `ExpectedRun` doesn't mirror the library for build numbers above `Long.MAX_VALUE`, or for its environment-variable configuration layer;
  - a missing module `ctrf.properties` surfaces as a bare `NoSuchFileException`;
  - recovering a truncated log assumes Open Test Reporting's `e:` prefix;
  - an unused `configureThreads` helper remains in the validator's `build.gradle`;
  - the configuration cache wasn't checked against the integration modules' `doFirst`.
- **TI-5 can reuse the cross-check:** the forked-JVM scenario tests could check their reports against JUnit's event log the same way.

#### Acceptance Criteria
- [x] The validator fails on output that lost tests across forks (shown on `58e36f0`: 10 missing entries and a skipped class's 2 lost tests) and on output that fails the official schema (shown with HP-5, #134: 29 errors on the pre-HP-5 listener report)
- [x] The validator passes once HP-5 and CB-6 are fixed
- [x] `./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test` passes with no flags, and with CI's flags passed once
- [x] Every report entry is checked against JUnit's event log by `testId`: presence, final status, `retries` and `flaky`
- [x] No assertion passes when the test it checks is missing
- [x] CI-specific values come from the run's own inputs: CI passes the same flags to both steps, and a run with another thread count passes (`-Dthreads=4` checked locally)

---

### TI-5: Regression Tests for Every Verified Scenario

**Priority**: High
**Complexity**: Moderate
**Files**: new tests under `src/test/java/io/github/alexshamrai/scenario/` (or a new integration module), fake test classes
**Status**: Partially done. Forked-JVM scenario tests in `src/test/java/io/github/alexshamrai/scenario/` cover CB-5, CB-6 (forks, new builds, same-JVM rerun), CB-7, HP-6, HP-7, HP-8, HP-9 and HP-11, each written with its fix. CB-2 is covered since #138 (`NestedRunScenarioTest`). Since #146 (`77dce2e`, 2026-10-16), `ScenarioRunner` checks every scenario's report against the official schema, through the test helper `CtrfSchema`; all of them pass. `HeapGuardScenarioTest`, which reads its own 100,000-test report, is left out. Still open: checking each scenario's report against JUnit's event log, as TI-4 (#135) does for the integration modules. That needs the root tests to use `JunitEvents` from the validator module, `ScenarioMain` to write JUnit's event log, and rules for the scenarios that differ from JUnit on purpose (HP-8 entries, HP-11 recoveries, accumulation, same-JVM reruns, the extension's missing dynamic tests); it is a separate design. The suite isn't under a minute: its 56 tests took 67 to 72 seconds, summed, on 2026-10-16.

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
**Status**: Done (#145, `3984760`), 2026-10-16. The six points below were already fixed on `master`, mostly with the items they name. What was still wrong was in `CLAUDE.md`: both "Running Single Tests" commands failed, because `test` without the colon applied the filter to every module and the example method no longer existed; the component table and the project tree named `SuiteExecutionErrorHandler`, removed by CB-5, and `TestDetailsUtil`; and the tree left out the `adapter/` package and `ClassState`. Also checked: every `ctrf.*` key in the README and `CLAUDE.md` exists in `ConfigReader`, every class, file and path the docs name exists, and `./gradlew clean build` passes as documented.

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
- [x] Every documented setting and command works as written
- [x] README dependency snippets are test-scoped
- [x] README says to use either the listener or the extension, not both
- [x] CLAUDE.md matches the code

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

Updated 2026-10-08; progress marked 2026-10-16. This replaces the original four-phase order, whose Phase 1 and most of Phase 2 are already done on `master`.

### Already done on `master`
CB-1, CB-3, HP-2 (mostly), HP-4, MP-1, MP-2 (its regression is tracked in CB-6), TI-3 (partially)

Since 2026-10-08: Phase 1 except TI-5, which is partially done, plus HP-6, MP-3, CB-6, HP-11, HP-5 and TI-4. MP-5 is superseded by CB-6, which completes Phase 2. Phase 3 is done (#138) and released as 0.5.1. In Phase 4, HP-12 (#144), DI-3 (#145) and TI-5's schema check (#146) are done. See [Status on `master` — 2026-10-14](#status-on-master--2026-10-14).

### Phase 1: Stop wrong results and broken builds (small, independent changes)
1. CB-4: Remove JUnit runtime dependencies, fix the README scopes, add the consumer matrix. **Done** (#124)
2. CB-7: Identify tests by `uniqueId` (`testId`) and fix the flaky rule. **Done** (#126)
3. CB-5: Report every failed container. **Done** (#125, #131)
4. HP-8: Report tests inside skipped containers. **Done** (#127)
5. HP-7: Report aborted tests as skipped. **Done** (#129)
6. HP-9: Keep test identity in the extension. **Done** (#128)
7. TI-5: Write the regression scenario for each of the fixes above alongside it. **Done** for items 1–6; TI-5 itself stays partially done; its schema check is done since #146

### Phase 2: Data model and persistence (changes the report shape, so bump the minor version)

**Released as 0.5.0** on 2026-10-15, together with Phase 1 (release PR #136).
8. HP-6: One test object per logical test, with `retryAttempts` (also resolves MP-3). **Done** (#130)
9. CB-6: One report merged under a lock, run identity, atomic writes, together with HP-11 (a class failure that recovers on retry stays failed). **Done** (#132)
10. HP-5: Conform to the official schema. **Done** (#134); filling `suite` is split out as HP-12 (Phase 4)
11. TI-4: Validator using the official schema and a cross-check against JUnit's counts. **Done** (#134, #135)
12. MP-5: Read previous reports tolerantly. **Superseded by CB-6** (2026-10-15): only an older reporter reading a same-run report from a newer one is left, which needs a setup the README advises against

### Phase 3: Architecture and robustness

**Released as 0.5.1** on 2026-10-16 (release PR #139). Designed 2026-10-15; see [Phase 3 Design — 0.5.1](#phase-3-design--051). Its follow-ups are in [Follow-ups after 0.5.1](#follow-ups-after-051): the Jackson 3 one is done (#141), and so are streaming JSON (#142) and the deferred minors (#143), both for 0.5.2, not released.

13. HP-10: Listener as the single core integration point, then revisit HP-1. **Done** (#138)
14. CB-2: Only the run that started reporting may end it (nested JUnit runs). **Done** (#138)
15. MP-6: `AutoCloseable` instead of `CloseableResource`. **Done** (#138)
16. MP-7: Reporter errors never affect test outcomes. **Done** (#138)
17. MP-8: Separate `message` from `trace`. **Done** (#138)
18. MP-9: Dependency and configuration hygiene (includes HP-3, using JUL). **Done** (#138)

### Phase 4: Polish
19. HP-12: Fill `suite` with each test's container hierarchy. **Done** (#144), 2026-10-16; not released
20. DI-3: Fix incorrect documentation. **Done** (#145), 2026-10-16
21. TI-5: Check every scenario's report against the official schema. **Done** (#146), 2026-10-16; checking them against JUnit's event log stays open
22. DI-1, DI-2: Documentation
23. MP-4, CQ-2, CQ-4, TI-1, TI-2, SP-2

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
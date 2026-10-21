# Architecture

How the JUnit CTRF Reporter is put together: its entry point, how a test's result travels to the report, how the JVMs of one build share a report, and where each part lives. What the report contains, and how to set the reporter up, is in the [README](README.md).

## Entry Point

`CtrfListener` (`launcher/CtrfListener.java`) is the only entry point. `CtrfExtension`, deprecated in 0.5.2, was removed in 0.6.0.

- JUnit Platform `TestExecutionListener`, registered by the jar's own `META-INF/services` file, so it is active wherever the jar is on the test classpath; JUnit's `junit.platform.execution.listeners.deactivate` turns it off
- A user's own registration on top, by a services file or by hand, is harmless: `ServiceLoader` loads a class once, and of two instances on one launcher only the first owns the run
- Sees classes skipped as a whole, failed containers of every kind, other engines (Vintage) and dynamic tests
- JUnit's launcher catches and logs exceptions from listener callbacks, so the listener needs no guard of its own
- `TestIdentifierAdapter` (`adapter/`) is its view of a test: identity, `name`, tags, source and `suite`, from the `TestIdentifier` and its `TestPlan`

It reports through `CtrfReportManager`, and has a package-private constructor that takes the manager, for tests.

## Run Ownership

`CtrfReportManager` keeps at most one active `TestRun`: its owner, generator, start time and recorded state.
- The owner is a token that the listener creates for each test plan it runs, compared by identity. Not the `TestPlan` itself: every listener of a launcher sees the same plan, so of two `CtrfListener`s on one launcher both would own the run, and each test would be recorded twice.
- `startTestRun(owner, generator)` returns false while another owner's run is active. The listener then ignores all its events, so a JUnit run started from inside a test (CB-2) neither ends the outer run nor adds its tests.
- `finishTestRun(owner)` acts only for the owner. Results recorded while no run is active are dropped.
- `finishTestRunOnExit(owner)` ends the run when the JVM exits before the test plan finishes (CB-8). The listener adds a shutdown hook for each test plan whose run it owns, and removes it when the plan finishes. The hook reports the plan's tests that never started as skipped, adds an entry as `other` for each container that was running with nothing running below it (`onTestInterrupted`), then calls it: the tests still running become `other` attempts, and the run is written as usual. HP-11 recovers such a container entry too. The listener's callbacks hold a read lock and the hook its write lock, so the hook waits for the callbacks in progress, the run's normal write included, and the callbacks after it record nothing.
- `startTestRun(String)` and `finishTestRun()` remain, with an owner of their own.

## What the Singleton Is For

`CtrfReportManager.getInstance()` is the JVM's default manager. `createDefault()` builds its collaborators from one `ConfigReader` and passes them to the package-private constructor, which the tests use too: test processor, file service, a composer factory per generator, run id and initial health.

The singleton remains because JUnit creates `CtrfListener` itself, through its no-argument constructor, and `EnvironmentHealthTracker`'s static API must reach the same manager. Environment health is JVM-wide, not per run.

## Core Data Flow

```
Test Execution
    ↓
CtrfListener (observer)
    ↓
CtrfReportManager (coordinator; getInstance() is the default instance)
    ├─ Keeps the active TestRun: owner, generator, start time and its TestStateTracker
    ├─ Delegates to AttemptFolder to fold retry attempts into one test
    ├─ Delegates to RunIdResolver to decide which JVMs share one report
    └─ At the end, CtrfReportFileService.update → ReportMerger → CtrfJsonComposer
    ↓
TestProcessor (converts test data)
    └─ Creates CTRF Test objects from JUnit execution data
    ↓
CtrfReportFileService.update (under a JVM lock and a lock on <report>.lock)
    ├─ Reads the report's runId; reads the whole report, streaming, only if it's the same run
    ├─ ReportMerger: same runId → unfold its tests, append this execution's attempts and recoveries, fold;
    │                otherwise this execution alone
    └─ Streams a temp file and moves it over the report (default: ctrf-report.json)
```

## Key Components

| Component                       | Responsibility                                                                                        |
|---------------------------------|-------------------------------------------------------------------------------------------------------|
| **CtrfReportManager**           | Coordinator: keeps the active `TestRun` and writes it at the end; `getInstance()` is the default instance, built in `createDefault()` |
| **TestRun**                     | One run: owner (the listener's token for that test plan), generator, start time and its `TestStateTracker` |
| **TestStateTracker**            | One run's attempts (ConcurrentLinkedQueue), container successes, in-progress test details, captured output and published files (ConcurrentHashMap) |
| **AttemptFolder**               | Stateless; folds attempts into one test per testId (retries, retryAttempts, flaky) and unfolds a report's tests back into attempts |
| **RunIdResolver**               | Resolves the runId: ctrf.run.id, a CI variable, the nearest ancestor JVM, or this JVM                 |
| **ReportMerger**                | Pure; merges an execution into the report of the same run, replaces another run's report; HP-11 recoveries |
| **EnvironmentHealthTracker**    | Public API for tracking test environment health status                                                |
| **TestProcessor**               | Converts JUnit test execution data (status, duration, failures) into CTRF Test objects                |
| **CtrfJsonComposer**            | Assembles final CTRF JSON structure with metadata (tool, environment, summary)                        |
| **CtrfReportFileService**       | `update`: locked read of the same run's report, merge function, atomic streaming write                |
| **CtrfJsonMapper**              | Streams the model to and from JSON without a library (`JsonWriter`, `JsonReader`); ignores unknown fields |
| **ConfigReader**                | Reads the configuration once: system property → env var (exact name, then `CTRF_X_Y` for `ctrf.*` keys) → `ctrf.properties` (UTF-8) → default; `${...}` references; an invalid number or boolean warns and uses the default |
| **StartupDurationProcessor**    | Optionally calculates test suite startup duration                                                     |

## CTRF Model Classes

All model classes in `io.github.alexshamrai.ctrf.model` use:
- **Lombok**: `@Data`, `@Builder` for boilerplate reduction
- **Jackson annotations** (`@JsonInclude(NON_NULL)`, `@JsonAlias`, `@JsonValue`), compile-only: they serve users who read the model with their own Jackson. The library writes and reads JSON itself, with `io.github.alexshamrai.json.CtrfJsonMapper`, which applies the same rules. It streams: it reads from a `Reader` and writes to a `Writer`, so it never holds a whole report as text or as a tree. `JsonGoldenTest` pins its output to the JSON 0.5.0 wrote with Jackson 2 (`src/test/resources/json/golden-report.json` and a few short cases inline), and checks its reader against Jackson 3. The tests use only Jackson 3 (`tools.jackson`), which the schema validator needs too; the 2.x annotations, shared by Jackson 2 and 3, come from the Jackson 3 BOM. To change the JSON output on purpose, update the golden JSON and say why in the commit

**Schema:** reports must validate against the official CTRF schema, pinned at `schema/ctrf.schema.json`. It is version 0.2.0, and `schema/README.md` has its source tag and commit. The test helper `CtrfSchema` loads it as a test resource for `CtrfSchemaConformanceTest` and for every scenario test's report, which `ScenarioRunner` rejects if it breaks the schema; the integration validator loads it too; it is not in the published jar. To move to a new CTRF version, replace the file and change `CtrfJson.SPEC_VERSION` in the same commit. Field names follow the spec exactly, for example `filePath`; `buildNumber` is an integer, and a non-numeric `ctrf.build.number` goes to `buildId`.

Hierarchy:
```
CtrfJson (root)
├─ results: Results
│  ├─ tool: Tool (JUnit metadata)
│  ├─ summary: Summary (test counts, duration)
│  ├─ tests: List<Test> (individual test results)
│  ├─ environment: Environment (build, repo, OS info)
│  └─ extra: Extra (custom fields)
```

**Test Status Mapping:**
- JUnit SUCCESSFUL → CTRF "passed"
- JUnit FAILED → CTRF "failed"
- JUnit ABORTED → CTRF "skipped"
- JUnit SKIPPED → CTRF "skipped"
- Running when the JVM exited, a test or a container outside any test → CTRF "other" (CB-8)

## Concurrency Design

The library handles parallel test execution using thread-safe collections managed by **TestStateTracker**:

- `ConcurrentLinkedQueue<Test>` for attempts and `ConcurrentLinkedQueue<ContainerSuccess>` for containers that finished without failing (O(1) adds from any thread; merged once when the execution finishes)
- `ConcurrentHashMap<String, TestDetails>` for in-progress test tracking
- `AtomicReference<TestRun>` for the active run: compare-and-set to start it and to finish it, so one owner at a time

While tests run, thread safety comes from concurrent data structures and atomic operations. Writing the report is the one locked section: `CtrfReportFileService.update` takes a JVM-wide lock and an exclusive file lock on `<report>.lock`, so the JVMs of one run (forks, retry rounds) merge one after another. **ReportMerger** and **AttemptFolder** are stateless and run inside that section.

`CtrfReportManagerConcurrencyTest` pins this from many threads at once (whole test lifecycles, and concurrent finishes of one run), and `ParallelExecutionScenarioTest` runs JUnit's parallel execution on 8 threads through the listener.

## Configuration

Configuration priority (highest to lowest):
1. System properties (`-Dctrf.report.path=...`)
2. Environment variables: the exact key, then the `CTRF_REPORT_PATH` form for `ctrf.*` keys
3. `src/test/resources/ctrf.properties` file, read as UTF-8
4. Defaults

**Key Properties:**
- `ctrf.report.path` - Output file location (default: `ctrf-report.json`; a blank or invalid value warns and uses it, and a directory gets `ctrf-report.json` inside it)
- `ctrf.max.message.length` - Max error message length (default: 500)
- `ctrf.max.trace.length` - Max stack trace length; keeps whole lines and every `Caused by:` line (default: 20000, 0 = no limit)
- `ctrf.max.output.length` - Max length of a failed attempt's captured `stdout` and `stderr`; keeps the end (default: 4000, 0 = no limit)
- `ctrf.calculate.startup.duration` - Enable startup duration calculation (default: false)
- `ctrf.run.id` - Run identifier shared by the JVMs of one build (default: a CI variable, the nearest ancestor JVM, or this JVM)
- `junit.version` - JUnit version for metadata
- Environment metadata: `ctrf.app.name`, `ctrf.build.number`, `ctrf.repository.url`, `ctrf.os.platform`, etc.

**Note:** When using system properties in Gradle, ensure they're passed to the test task:
```groovy
test {
    systemProperties += System.properties.findAll { k, v -> k.toString().startsWith("ctrf") }
}
```

## Project Structure

```
src/main/java/io/github/alexshamrai/
├── adapter/                       # TestIdentifierAdapter: the listener's view of a test, with its name and suite
├── launcher/
│   └── CtrfListener.java          # JUnit Platform Listener entry point
├── ctrf/model/                    # CTRF model classes (CtrfJson, Test, Summary, etc.)
├── config/                        # Configuration loading (ConfigReader)
├── json/                          # Own JSON writer, parser and model binding (internal)
├── model/
│   └── TestDetails.java           # Internal DTO for test data
├── util/                          # Utilities (SummaryUtil)
├── CtrfReportManager.java         # Coordinator: the active TestRun; getInstance() is the default instance
├── TestStateTracker.java          # Manages test state collections
├── TestRun.java                   # One run's owner and state
├── AttemptFolder.java             # Folds retry attempts into one test
├── RunIdResolver.java             # Resolves the runId
├── ReportMerger.java              # Merges an execution into its run's report
├── ContainerSuccess.java          # A container that finished without failing (HP-11)
├── EnvironmentHealthTracker.java  # Public API for environment health tracking
├── CtrfJsonComposer.java          # JSON structure assembly
├── CtrfReportFileService.java     # File I/O operations
├── TestProcessor.java             # Test result processing
└── StartupDurationProcessor.java  # Optional startup duration calculation

src/junitStubs/java/               # Compile-only stand-ins for JUnit API newer than 5.10, such as FileEntry; not in the jar
schema/                            # Pinned official CTRF schema (test resource only)
integration-tests-listener/        # Integration tests using CtrfListener
integration-ctrf-validator/        # Schema and logic validation tests
```

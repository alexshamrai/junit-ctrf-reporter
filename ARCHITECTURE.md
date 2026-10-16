# Architecture

How the JUnit CTRF Reporter is put together: its two entry points, how a test's result travels to the report, how the JVMs of one build share a report, and where each part lives. What the report contains, and how to set the reporter up, is in the [README](README.md).

## Entry Points

The library provides two ways to enable CTRF reporting. A project uses one of them, never both.

1. **CtrfListener** (`launcher/CtrfListener.java`), recommended
   - JUnit Platform `TestExecutionListener`, registered by the user through `META-INF/services` or programmatically
   - The jar doesn't register it itself: that would switch reporting on for every project with the jar on its test classpath, and take over the reporting of `CtrfExtension` users (the listener's test plan owns the run, so the extension's run start is refused)
   - Sees classes skipped as a whole, failed containers of every kind, other engines (Vintage) and dynamic tests
   - JUnit's launcher catches and logs exceptions from listener callbacks, so the listener needs no guard of its own
2. **CtrfExtension** (`jupiter/CtrfExtension.java`), deprecated since 0.5.2 and to be removed in 0.6.0, with `TestRunExtension`; it logs a warning once per run
   - JUnit Jupiter extension, `@ExtendWith(CtrfExtension.class)`; implements `TestRunExtension`, `AfterAllCallback`, `BeforeEachCallback` and `TestWatcher`
   - The end of the run is a `RunEndResource` in the root store, which is both `CloseableResource` and `AutoCloseable`
   - Every reporter call goes through a guard: an error is logged and never becomes a test failure; a `VirtualMachineError` propagates

Both report through `CtrfReportManager`. Each has a package-private constructor that takes the manager, for tests.

## Run Ownership

`CtrfReportManager` keeps at most one active `TestRun`: its owner, generator, start time and recorded state.
- The owner is the listener's `TestPlan` or the extension's root `ExtensionContext`, compared by identity.
- `startTestRun(owner, generator)` returns false while another owner's run is active. The entry point then ignores all its events, so a JUnit run started from inside a test (CB-2) neither ends the outer run nor adds its tests.
- `finishTestRun(owner)` acts only for the owner. Results recorded while no run is active are dropped.
- `startTestRun(String)` and `finishTestRun()` remain, with an owner of their own.

## What the Singleton Is For

`CtrfReportManager.getInstance()` is the JVM's default manager. `createDefault()` builds its collaborators from one `ConfigReader` and passes them to the package-private constructor, which the tests use too: test processor, file service, a composer factory per generator, run id and initial health.

The singleton remains because JUnit creates `CtrfListener` and `CtrfExtension` itself, through their no-argument constructors, and `EnvironmentHealthTracker`'s static API must reach the same manager as they do. Environment health is JVM-wide, not per run.

## Core Data Flow

```
Test Execution
    ↓
[CtrfExtension | CtrfListener] (observers)
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
| **TestRun**                     | One run: owner (`TestPlan` or root `ExtensionContext`), generator, start time and its `TestStateTracker` |
| **TestStateTracker**            | One run's attempts (ConcurrentLinkedQueue), container successes and in-progress test details (ConcurrentHashMap)            |
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

## Concurrency Design

The library handles parallel test execution using thread-safe collections managed by **TestStateTracker**:

- `ConcurrentLinkedQueue<Test>` for attempts and `ConcurrentLinkedQueue<ContainerSuccess>` for containers that finished without failing (O(1) adds from any thread; merged once when the execution finishes)
- `ConcurrentHashMap<String, TestDetails>` for in-progress test tracking
- `AtomicReference<TestRun>` for the active run: compare-and-set to start it and to finish it, so one owner at a time

While tests run, thread safety comes from concurrent data structures and atomic operations. Writing the report is the one locked section: `CtrfReportFileService.update` takes a JVM-wide lock and an exclusive file lock on `<report>.lock`, so the JVMs of one run (forks, retry rounds) merge one after another. **ReportMerger** and **AttemptFolder** are stateless and run inside that section.

`CtrfReportManagerConcurrencyTest` pins this from many threads at once (whole test lifecycles, and concurrent finishes of one run), and `ParallelExecutionScenarioTest` runs JUnit's parallel execution on 8 threads through both entry points.

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
├── adapter/                       # TestContextAdapter and its listener and extension implementations
├── jupiter/
│   ├── ClassState.java            # What the extension tracks about one running test class
│   ├── CtrfExtension.java         # JUnit Jupiter Extension entry point
│   ├── RunEndResource.java        # End-of-run hook: CloseableResource and AutoCloseable
│   └── TestRunExtension.java      # Lifecycle hooks interface
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

schema/                            # Pinned official CTRF schema (test resource only)
integration-tests-extension/       # Integration tests using CtrfExtension
integration-tests-listener/        # Integration tests using CtrfListener
integration-ctrf-validator/        # Schema and logic validation tests
```

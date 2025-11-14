# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JUnit CTRF Reporter is a Java library that generates test reports following the CTRF (Common Test Report Format) specification. It provides both a JUnit Jupiter Extension and a JUnit Platform TestExecutionListener for JUnit 5 tests.

**Key Facts:**
- Requires Java 17+ (development requires Java 21+)
- Published to Maven Central as `io.github.alexshamrai:junit-ctrf-reporter`
- Uses Gradle for build management
- Current version: 0.4.1 (release), 0.4.2-SNAPSHOT (development)

## Common Commands

### Building and Testing

```bash
# Build the project
./gradlew build

# Run unit tests only
./gradlew :test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest

# Clean build
./gradlew clean build
```

### Integration Tests

Integration tests are split across three modules and must be run sequentially:

```bash
# Generate CTRF report using listener-based tests
./gradlew clean :integration-tests-listener:test

# Or using extension-based tests
./gradlew clean :integration-tests-extension:test

# Validate the generated report (runs after either above)
./gradlew :integration-ctrf-validator:test

# Run with parallel execution
./gradlew :integration-tests-listener:test -Dthreads=4

# Run exactly as CI (with all parameters)
./gradlew clean :integration-tests-listener:test -Dthreads=2 \
  -Dctrf.build.name=system-build \
  -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678

./gradlew :integration-ctrf-validator:test \
  -Dctrf.report.path=../integration-tests-listener/build/test-results/ctrf-report.json
```

**Important:** Integration tests use a two-phase process:
1. Generate report by running fake tests that are designed to succeed, fail, or be skipped
2. Validate the generated CTRF JSON against schema and logical correctness

### Running Single Tests

```bash
# Run a specific test class
./gradlew test --tests "io.github.alexshamrai.CtrfReportManagerTest"

# Run a specific test method
./gradlew test --tests "io.github.alexshamrai.CtrfReportManagerTest.testOnTestSuccess"
```

## Architecture Overview

### Entry Points (Dual Integration Model)

The library provides two ways to enable CTRF reporting:

1. **CtrfExtension** (`jupiter/CtrfExtension.java`)
   - JUnit Jupiter Extension using `@ExtendWith(CtrfExtension.class)` annotation
   - Implements `TestWatcher`, `BeforeEachCallback`, and `TestRunExtension`
   - Best for per-class or per-test configuration

2. **CtrfListener** (`launcher/CtrfListener.java`)
   - JUnit Platform `TestExecutionListener`
   - Registered via `META-INF/services` or programmatically with `LauncherFactory`
   - Best for global, project-wide reporting

Both delegate to the **singleton CtrfReportManager** for core functionality.

### Core Data Flow

```
Test Execution
    ↓
[CtrfExtension | CtrfListener] (observers)
    ↓
CtrfReportManager (singleton coordinator)
    ├─ Stores test details in ConcurrentHashMap during execution
    ├─ Processes test results into CTRF Test objects
    └─ Triggers report composition on test run completion
    ↓
CtrfJsonComposer (assembles JSON structure)
    ├─ Composes Tool metadata (JUnit version)
    ├─ Composes Environment metadata (build, repo, OS info)
    └─ Creates CtrfJson with Summary
    ↓
CtrfReportFileService (filesystem I/O)
    └─ Writes JSON to configured path (default: ctrf-report.json)
```

### Key Components

| Component                       | Responsibility                                                                                        |
|---------------------------------|-------------------------------------------------------------------------------------------------------|
| **CtrfReportManager**           | Singleton orchestrator; manages test lifecycle events and triggers report generation                  |
| **EnvironmentHealthTracker**    | Public API for tracking test environment health status                                                |
| **TestProcessor**               | Converts JUnit test execution data (status, duration, failures) into CTRF Test objects                |
| **CtrfJsonComposer**            | Assembles final CTRF JSON structure with metadata (tool, environment, summary)                        |
| **CtrfReportFileService**       | Handles file I/O; supports reading existing reports for test reruns                                   |
| **ConfigReader/CtrfConfig**     | Configuration facade using owner library; loads from system properties → env vars → `ctrf.properties` |
| **SuiteExecutionErrorHandler**  | Captures suite-level initialization/execution failures                                                |
| **StartupDurationProcessor**    | Optionally calculates test suite startup duration                                                     |

### CTRF Model Classes

All model classes in `io.github.alexshamrai.ctrf.model` use:
- **Lombok**: `@Data`, `@Builder` for boilerplate reduction
- **Jackson**: `@JsonInclude(NON_NULL)` to omit null fields from JSON output

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

### Concurrency Design

The library handles parallel test execution using thread-safe collections:

- `CopyOnWriteArrayList<Test>` for test results (read-heavy workload)
- `ConcurrentHashMap<String, TestDetails>` for in-progress test tracking
- `AtomicBoolean` for ensuring single execution of startup/shutdown logic

**No explicit locks are used.** Thread safety is achieved through concurrent data structures and atomic operations.

### Configuration

Configuration priority (highest to lowest):
1. System properties (`-Dctrf.report.path=...`)
2. Environment variables
3. `src/test/resources/ctrf.properties` file

**Key Properties:**
- `ctrf.report.path` - Output file location (default: `ctrf-report.json`)
- `ctrf.max.message.length` - Max error message length (default: 500)
- `ctrf.calculate.startup.duration` - Enable startup duration calculation (default: false)
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
├── jupiter/
│   ├── CtrfExtension.java         # JUnit Jupiter Extension entry point
│   └── TestRunExtension.java      # Lifecycle hooks interface
├── launcher/
│   └── CtrfListener.java          # JUnit Platform Listener entry point
├── ctrf/model/                    # CTRF model classes (CtrfJson, Test, Summary, etc.)
├── config/                        # Configuration handling (ConfigReader, CtrfConfig)
├── model/
│   └── TestDetails.java           # Internal DTO for test data
├── util/                          # Utilities (SummaryUtil, TestDetailsUtil)
├── CtrfReportManager.java         # Singleton coordinator
├── EnvironmentHealthTracker.java  # Public API for environment health tracking
├── CtrfJsonComposer.java          # JSON structure assembly
├── CtrfReportFileService.java     # File I/O operations
├── TestProcessor.java             # Test result processing
├── SuiteExecutionErrorHandler.java # Suite-level error handling
└── StartupDurationProcessor.java  # Optional startup duration calculation

integration-tests-extension/       # Integration tests using CtrfExtension
integration-tests-listener/        # Integration tests using CtrfListener
integration-ctrf-validator/        # Schema and logic validation tests
```

## Development Guidelines

### Code Style

- The project uses Checkstyle with configuration in `config/checkstyle/checkstyle.xml`
- IntelliJ IDEA code style is available at `config/checkstyle/intellij_idea_codestyle.xml`
- Import the IntelliJ style via: Settings → Editor → Code Style → Java → Scheme gear → Import Scheme

### Testing Strategy

**Unit Tests:**
- Located in `src/test/java`
- Mirror the package structure of source code
- Use Mockito for mocking, AssertJ for assertions
- Run with: `./gradlew test`

**Integration Tests:**
- Separated into three modules for isolation
- `integration-tests-extension` and `integration-tests-listener` contain fake tests that generate reports
- `integration-ctrf-validator` validates generated reports against CTRF schema and logical correctness
- Fake tests intentionally have different outcomes (success, failure, skipped) to verify report accuracy

### Release Process

The project uses GitHub Actions for automated releases to Maven Central:

1. **Prepare Release**: Run "Prepare Release" workflow with version number (e.g., 0.4.0)
   - Creates `release/X.Y.Z` branch
   - Updates `releaseVersion` in `build.gradle`
   - Updates version in `README.md`
   - Opens PR to `master`

2. **Publish**: Automatically triggered when PR from `release/*` is opened
   - Builds and signs artifact with GPG
   - Publishes to Maven Central

3. **Merge**: Review and merge PR to finalize release

4. **Post-Release**: Verify publication on Maven Central (15-30 min sync time)

**Manual release alternative:** Update versions → `./gradlew build` → `./gradlew publishAllPublicationsToMavenCentralRepository`

## Special Features

### Flaky Test Detection

A test is marked as `flaky: true` if it passed but had previous failed attempts or retries > 0.

### Rerun Support

`CtrfReportFileService` reads existing reports on startup, allowing incremental report building across multiple test runs.

### Thread Identification

Each test tracks its execution thread via `Thread.currentThread().getName()` in the `threadId` field.

### Startup Duration

When `ctrf.calculate.startup.duration=true`, the library calculates test suite initialization overhead as: `earliest_test_start - suite_start_time`

### Environment Health Tracking

The library tracks the health status of the test environment, allowing tests to signal when they're running in degraded or problematic conditions.

**Implementation:**
- **EnvironmentHealthTracker**: Public API class providing static methods:
  - `markEnvironmentUnhealthy()`: Marks environment as unhealthy (irreversible)
  - `isEnvironmentHealthy()`: Returns current health status
- **CtrfReportManager**: Stores health state in `AtomicBoolean` for thread safety
  - Initialized by checking `ENV_HEALTHY` environment variable during startup
  - Health state is preserved across test reruns via `CtrfReportFileService`
- **CtrfJsonComposer**: Includes health status in `Environment.healthy` field

**Initialization Methods:**
1. **Environment Variable**: Set `ENV_HEALTHY=false` before test execution
2. **Programmatic API**: Call `EnvironmentHealthTracker.markEnvironmentUnhealthy()` from test code

**Behavior:**
- Default state: `true` (healthy)
- Once marked unhealthy, remains unhealthy for entire test run
- State persists across reruns when using same report file
- Thread-safe for parallel test execution
- Included in CTRF report as `results.environment.healthy`

**Usage Example:**
```java
import static io.github.alexshamrai.EnvironmentHealthTracker.markEnvironmentUnhealthy;

@Test
void testWithUnhealthyEnvironment() {
    if (!externalService.isAvailable()) {
        markEnvironmentUnhealthy();
    }
    // Continue test...
}
```

## Important Notes

- **Java Version**: Requires Java 17+ runtime, Java 21+ for development (toolchain configured in build.gradle)
- **Lombok**: All model classes use Lombok annotations; ensure annotation processing is enabled in your IDE
- **Thread Safety**: The library is designed for parallel test execution; avoid modifying shared state
- **Integration Test Order**: Always run report generation before validation in integration tests
- **Configuration Precedence**: System properties override properties file; useful for CI/CD environments
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JUnit CTRF Reporter is a Java library that generates test reports following the CTRF (Common Test Report Format) specification. It reports through a JUnit Platform `TestExecutionListener`, `CtrfListener`, for JUnit 5 and 6 tests.

**Key Facts:**
- Requires Java 17+ at run time. The build compiles with JDK 25 and `--release 17` (main and test code), and tests on Java 25, 17 and 21
- Published to Maven Central as `io.github.alexshamrai:junit-ctrf-reporter`
- Uses Gradle for build management
- Current version: `releaseVersion` (the last release) and `projectVersion` (development) in `build.gradle`

## Common Commands

### Building and Testing

```bash
# Build the project
./gradlew build

# Run unit tests only (on JDK 25)
./gradlew :test

# The same tests on Java 17 and 21 (part of :build)
./gradlew :testOnJava17 :testOnJava21

# Run checkstyle
./gradlew checkstyleMain checkstyleTest checkstyleJunitStubs

# Clean build
./gradlew clean build
```

### Integration Tests

Integration tests are split across two modules and must be run in order:

```bash
# Generate the listener's report and validate it (no flags: the validator expects a plain local run)
./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test

# Exactly as CI: the same flags reach both tasks, and the validator expects them
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test \
  -Dforks=2 -Dthreads=2 \
  -Dctrf.build.name=system-build \
  -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678
```

**Important:** Integration tests use a two-phase process:
1. Generate the report by running fake tests that are designed to succeed, fail, or be skipped. The same run writes JUnit's own event log to `build/test-results/junit-platform/`
2. Validate the report: against the official schema, against JUnit's event log by `testId` (`JunitCrossCheckTest`), and for what JUnit can't know (`CtrfLogicTest`). The validator takes its expectations from the same `-Dthreads`, `-Dctrf.build.*` and `ENV_HEALTHY` as the run; see `INTEGRATION_TESTS.md`

### Running Single Tests

```bash
# Run a specific test class
./gradlew :test --tests "io.github.alexshamrai.CtrfReportManagerTest"

# Run a specific test method
./gradlew :test --tests "io.github.alexshamrai.CtrfReportManagerTest.onTestSuccess_processesResult"
```

## Architecture

The architecture and the project structure are in `ARCHITECTURE.md`, for human contributors too, and imported here:

@ARCHITECTURE.md

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
- Separated into two modules for isolation
- `integration-tests-listener` contains fake tests that generate a report through `CtrfListener`
- `integration-ctrf-validator` validates generated reports against CTRF schema and logical correctness
- Fake tests intentionally have different outcomes (success, failure, skipped) to verify report accuracy

### Release Process

The project uses GitHub Actions for automated releases to Maven Central:

1. **Prepare Release**: Run "Prepare Release" workflow with version number (e.g., 0.5.0)
   - Creates `release/X.Y.Z` branch
   - Updates `releaseVersion` and `projectVersion` (the next development version) in `build.gradle`
   - Updates the Gradle and Maven dependency versions in `README.md`, and fails before pushing if either wasn't updated
   - Opens PR to `master`

2. **Publish**: Run the "Publish to Maven Central" workflow on `release/X.Y.Z` yourself. The release branch is pushed with `GITHUB_TOKEN`, which can't start other workflows, so publishing never starts on its own
   - Builds and signs artifact with GPG
   - Publishes to Maven Central

3. **Merge**: Merge the PR once the artifact is on Maven Central (15-30 min sync time)

4. **Post-Release**: Create the GitHub Release `vX.Y.Z` on `master`

**Manual release alternative:** Update versions → `./gradlew build` → `./gradlew publishAllPublicationsToMavenCentralRepository`

## Special Features

### Retries and Flaky Tests

Each test appears once in the report, as its final attempt. Earlier attempts with the same `testId` (the JUnit unique ID) go into `retryAttempts`, numbered from 1, and `retries` is their count. A test is marked as `flaky: true` only if its final attempt passed after at least one failed attempt; `summary.flaky` counts such tests. Entries from older reports without a `testId` are grouped by file path and name among themselves, never by name alone, and never joined to entries that have a `testId`. A class failure (initializationError/teardownError) followed by the class's success in a later round becomes a passed, flaky entry (HP-11); a class entry that a JVM exit interrupted (`other`, CB-8) becomes passed, not flaky.

### Suites

Each test's `suite` is the display names of its ancestors, outermost first, without the engine. The listener walks the `TestPlan`, and `TestIdentifierAdapter`'s `getSuite()` and `getSuiteOfChildren()` hold the rule. Under `@Suite`, the listener leaves out every engine, the suite engine and the one it runs, by the last unique-ID segment's type, so the `@Suite` class comes first. With no other container, the suite is the nearest engine's name. A class's `initializationError` or `teardownError` uses `getSuiteOfChildren()`, so it ends with the class's own name and never falls back to the engine. `AttemptFolder` and `ReportMerger` carry it through retries and HP-11 recoveries, and the integration validator checks it against JUnit's own event log.

### Rerun Support

Nothing is read when a run starts. At the end of each execution, `CtrfReportFileService.update` reads the report's root `runId` under a lock. Only if it matches is the whole report read, and `ReportMerger` unfolds its tests and appends this execution's attempts, so forks, retry rounds in new JVMs and reruns in the same JVM all end up in one report. A report of another run is replaced without being read further, and `HeapGuardScenarioTest` keeps the heap a report of 100,000 tests needs under a fixed limit.

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
  - Health is merged across the JVMs of one run by `ReportMerger`: unhealthy if any was
- **CtrfJsonComposer**: Includes health status in `Environment.healthy` field

**Initialization Methods:**
1. **Environment Variable**: Set `ENV_HEALTHY=false` before test execution
2. **Programmatic API**: Call `EnvironmentHealthTracker.markEnvironmentUnhealthy()` from test code

**Behavior:**
- Default state: `true` (healthy)
- Once marked unhealthy, remains unhealthy for entire test run
- State is merged within one run and not passed on to a later run
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

- **Java Version**: Requires Java 17+ at run time. The toolchain in build.gradle is JDK 25; every compile task uses `--release 17`, so main and test code can't use an API newer than Java 17 and class files are version 61. `testOnJava17` and `testOnJava21` run the unit tests and scenarios on those versions, and the compat workflow runs the consumers on Java 17, 21 and 25
- **Lombok**: All model classes use Lombok annotations; ensure annotation processing is enabled in your IDE
- **Thread Safety**: The library is designed for parallel test execution; avoid modifying shared state
- **Integration Test Order**: Always run report generation before validation in integration tests
- **Configuration Precedence**: System properties override properties file; useful for CI/CD environments
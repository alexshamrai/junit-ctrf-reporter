# Integration Tests for JUnit CTRF Extension

This document provides a comprehensive explanation of the integration tests for the JUnit CTRF Extension project. These tests validate that the extension correctly generates CTRF-compliant reports and handles various test scenarios properly.

## Overview

The integration tests serve several key purposes:

1. **Validate CTRF Report Generation**: Ensure the extension generates valid CTRF-compliant JSON reports
2. **Verify Report Schema Compliance**: Check that the generated reports adhere to the CTRF schema specification
3. **Test Logical Correctness**: Confirm that test statuses, durations, and other metadata are correctly captured
4. **Simulate Real-World Scenarios**: Test various test outcomes (success, failure, skipped) and configurations(parallel execution, dynamic parameters, etc.)
5. **Enable reporter both Listener and as Extension**: Verify that the extension can be used both as a Listener and as an Extension


## Integration Test Structure

The integration tests are organized in separate modules (`integration-ctrf-validator`, `integration-tests-extension`, `integration-tests-listener` ).  The `integration-tests-extension` and `integration-tests-listener` modules contain fakes tests to generate CTRF report with required structure and test outcomes. Their structure is identical, the only difference is the way that CTRF reporter is enabled.
```
integration-tests-listener/
├── build.gradle                 
├── src/
    ├── test/
        ├── java/
        │   └── io/github/alexshamrai/integration/fake
        │       ├── BaseFakeTest.java          # Base class for all fake tests
        │       ├── DummyDisabledTest.java     # Tests with @Disabled annotation
        │       ├── DummyFailedTest.java       # Tests designed to fail
        │       ├── DummySuccessTest.java      # Tests designed to succeed
        │       ├── FirstLongTest.java         # Tests with specific durations
        │       ├── PaymentsTest.java          # @Nested tests under a @DisplayName, for suite
        │       └── SecondLongTest.java        # More tests with specific durations
```

### Fake Test Classes

The fake test classes are designed to simulate various test scenarios:

#### BaseFakeTest

```java
@ExtendWith(CtrfExtension.class) //only for extension
public abstract class BaseFakeTest {
    // Common lifecycle methods
}
```

- Base class for all fake tests
- Applies the CTRF extension to all subclasses
- Tagged with "fake" for selective execution
- Provides common lifecycle methods

#### Test Status Verification

- **DummySuccessTest**: Contains tests that pass successfully
- **AssumptionTest**: Contains a test aborted by a failed assumption; it is reported as skipped with `rawStatus` "aborted"
- **DummyFailedTest**: Contains tests that intentionally fail
- **DummyDisabledTest**: Contains tests marked with `@Disabled`
- **DisabledClassTest** and **ConditionallySkippedClassTest** (listener module only): whole classes that JUnit skips, one with `@Disabled` and one with a class-level condition. Each of their tests is reported as skipped with the class's reason. The extension never sees tests of a skipped class, so the extension module has no such tests

#### Test Duration Verification

- **FirstLongTest** and **SecondLongTest**: Contain tests with specific sleep durations (0.5s, 1s, 2s)
- Used to verify that test durations are correctly captured in the report

#### Container Failure Verification

- **InitializationErrorTest** and **AnotherInitializationErrorTest**: fail in `@BeforeAll`; each is reported as an `initializationError` entry
- **AfterAllFailureTest**: fails in `@AfterAll` after its test passed; reported as a `teardownError` entry
- **BrokenSourceTest** (listener module only): a `@MethodSource` and a `@TestFactory` that throw; each is reported as a failed entry under its own name. The extension cannot see failures below class level, so the extension module has no such test

#### Suite Verification

- **PaymentsTest**: a `@Nested` class `Refunds` under `@DisplayName("Payments")`, with a test and a two-value `@ParameterizedTest`, all passing. Their `suite` is `["Payments", "Refunds"]`, and `["Payments", "Refunds", "refundsEachAmount(int)"]` for the parameterized ones. `JunitCrossCheckTest` checks every entry's `suite` against JUnit's own event log

### Environment Health Testing

The integration tests verify the environment health tracking feature using different approaches for each module:

#### integration-tests-listener

For the listener-based tests, environment health is controlled via the `ENV_HEALTHY` environment variable:

- **CI Environment**: Set in `.github/workflows/integration-listener.yml`:
  ```yaml
  env:
    ENV_HEALTHY: 'false'
  ```
- **Local Testing**: Set the environment variable to check the unhealthy path as CI does. The validator then expects `healthy: false`; without it, it expects a healthy listener report:
  ```bash
  export ENV_HEALTHY=false
  ./gradlew clean :integration-tests-listener:test
  ```

This approach demonstrates how environment health can be controlled externally in CI/CD pipelines without modifying test code.

#### integration-tests-extension

For the extension-based tests, environment health is set programmatically within the test:

**File**: `integration-tests-extension/src/test/java/.../FirstLongTest.java`

```java
import static io.github.alexshamrai.EnvironmentHealthTracker.markEnvironmentUnhealthy;

@Test
void firstLongOneSecondTest() throws InterruptedException {
    System.out.println("OneSecondTest");
    Thread.sleep(1000);
    markEnvironmentUnhealthy();  // Explicitly mark environment as unhealthy
}
```

This approach demonstrates the programmatic API for marking environment health within test code, useful for detecting runtime issues like service unavailability.

#### Validation

Both approaches are validated by `integration-ctrf-validator` module:

**File**: `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`

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

This test ensures that the `environment.healthy` field in the generated CTRF report matches what the run was set up to produce.

The `integration-ctrf-validator` module contains validation logic for json file that`integration-tests-extension` and `integration-tests-listener` modules create after test execution.
```
integration-ctrf-validator/
├── build.gradle                 # Validator build configuration
└── src/
    ├── main/java/io/github/alexshamrai/integration/
    │   ├── JsonSchemaValidator.java       # Validates JSON against the CTRF schema
    │   └── JunitEvents.java               # Folds JUnit's event log into what the report must contain
    └── test/java/
        ├── integration/
        │   ├── BaseIntegrationTest.java         # Loads the report and the run's expectations
        │   ├── IntegrationRun.java              # Where the report and the event log are, from the settings
        │   ├── ExpectedRun.java                 # What the run's settings say the report holds
        │   ├── CtrfSchemaValidationTest.java    # Validates report schema compliance
        │   ├── JunitCrossCheckTest.java         # Checks every entry against JUnit's event log
        │   ├── CtrfLogicTest.java               # Verifies logical correctness of the report
        │   ├── IntegrationRunTest.java          # Unit tests of IntegrationRun
        │   └── ExpectedRunTest.java             # Unit tests of ExpectedRun
        └── io/github/alexshamrai/integration/
            └── JunitEventsTest.java             # Unit tests of JunitEvents
```

The schema is the official CTRF schema pinned at `schema/ctrf.schema.json` in the repository root (see `schema/README.md`). The module's `build.gradle` adds it to the test resources.

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
- The report has no other entries, except a skipped container's own entry and the tests within it, which JUnit never starts and so can't list
- Each class or test method that JUnit skipped as a whole is covered: the report's entries for it or within it are all skipped, and there is at least one when JUnit's log lists none of its tests
- A truncated event log (its JVM died) is read up to its last complete event, and the failure names the log

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

With no flags, the validator expects what that run produces: one thread, the build values from the module's `src/test/resources/ctrf.properties`, and a healthy environment (the extension module's `FirstLongTest` always marks it unhealthy, so for it the validator expects `healthy: false`). For the extension module, pass its report:

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

## Conclusion

The integration tests provide comprehensive validation of the JUnit CTRF Extension's functionality. They ensure that the extension generates valid CTRF reports that accurately reflect test execution results across various scenarios, including different test statuses, durations, and parallel execution.


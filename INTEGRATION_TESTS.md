# Integration Tests for JUnit CTRF Reporter

This document explains the integration tests of the JUnit CTRF Reporter. They validate that the reporter generates CTRF-compliant reports and handles various test scenarios properly.

## Overview

The integration tests serve several key purposes:

1. **Validate CTRF Report Generation**: Ensure the reporter generates valid CTRF-compliant JSON reports
2. **Verify Report Schema Compliance**: Check that the generated reports adhere to the CTRF schema specification
3. **Test Logical Correctness**: Confirm that test statuses, durations, and other metadata are correctly captured
4. **Simulate Real-World Scenarios**: Test various test outcomes (success, failure, skipped) and configurations (parallel execution, dynamic parameters, etc.)


## Integration Test Structure

The integration tests are organized in two modules. `integration-tests-listener` contains fake tests that generate a CTRF report with the required structure and test outcomes, reported by `CtrfListener`, which the jar registers itself. The module registers it again in `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`, as projects that set it up before 0.6.0 do, so the run also shows that the duplicate is harmless. `integration-ctrf-validator` checks that report.
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
public abstract class BaseFakeTest {
    // Common lifecycle methods
}
```

- Base class for all fake tests
- Tagged with "fake" for selective execution
- Provides common lifecycle methods

#### Test Status Verification

- **DummySuccessTest**: Contains tests that pass successfully
- **AssumptionTest**: Contains a test aborted by a failed assumption; it is reported as skipped with `rawStatus` "aborted"
- **DummyFailedTest**: Contains tests that intentionally fail
- **DummyDisabledTest**: Contains tests marked with `@Disabled`
- **DisabledClassTest** and **ConditionallySkippedClassTest**: whole classes that JUnit skips, one with `@Disabled` and one with a class-level condition. Each of their tests is reported as skipped with the class's reason

#### Test Duration Verification

- **FirstLongTest** and **SecondLongTest**: Contain tests with specific sleep durations (0.5s, 1s, 2s)
- Used to verify that test durations are correctly captured in the report

#### Container Failure Verification

- **InitializationErrorTest** and **AnotherInitializationErrorTest**: fail in `@BeforeAll`; each is reported as an `initializationError` entry
- **AfterAllFailureTest**: fails in `@AfterAll` after its test passed; reported as a `teardownError` entry
- **BrokenSourceTest**: a `@MethodSource` and a `@TestFactory` that throw; each is reported as a failed entry under its own name

#### Output Verification

The module turns JUnit's output capture on. `BaseFakeTest` prints in `@BeforeEach` and `@AfterEach`, and some tests print too:
- **DummyFailedTest** fails in every round, and each of its attempts keeps what it printed in `stdout`;
- **FlakyTest** prints its attempt number: its failed first attempt keeps the output, and its passed final attempt has none.

#### Attachment Verification

- **DummySuccessTest**'s `firstSuccessTest` publishes a file with `TestReporter.publishFile`; it's attached to the test with its name, media type and absolute path.

#### Name and Suite Verification

- **PaymentsTest**: a `@Nested` class `Refunds` under `@DisplayName("Payments")`, with a test and a two-value `@ParameterizedTest`, all passing. Their `suite` is `["Payments", "Refunds"]`, and `["Payments", "Refunds", "refundsEachAmount(int)"]` for the parameterized ones, which are named after their method: `refundsEachAmount(int) [1] 1`. `JunitCrossCheckTest` checks every entry's `suite` against JUnit's own event log

### Environment Health Testing

The integration tests control environment health through the `ENV_HEALTHY` environment variable:

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

This shows how environment health can be controlled from outside, in CI/CD pipelines, without changing test code. The programmatic API, `EnvironmentHealthTracker.markEnvironmentUnhealthy()`, is covered by the library's own `ParallelExecutionScenarioTest`, whose fixture calls it in a forked JVM.

#### Validation

It is validated by the `integration-ctrf-validator` module:

**File**: `integration-ctrf-validator/src/test/java/integration/CtrfLogicTest.java`

```java
@Test
void verifyEnvironmentHealthMatchesTheRun() {
    var environment = report.getResults().getEnvironment();
    assertThat(environment).isNotNull();

    assertThat(environment.isHealthy())
        .as("healthy: false when ENV_HEALTHY=false")
        .isEqualTo(expected.healthy());
}
```

This test ensures that the `environment.healthy` field in the generated CTRF report matches what the run was set up to produce.

The `integration-ctrf-validator` module contains the validation of the report that `integration-tests-listener` writes.
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
   - Uses the `integration-tests-listener:test` Gradle task
   - Ignores test failures (since some tests are designed to fail)
   - Also writes JUnit's own event log (Open Test Reporting) to `build/test-results/junit-platform/<number>/open-test-report.xml`, one file per JVM: each fork and each retry round
   - Gives each run its own `ctrf.run.id`, so a second run without `clean` replaces both the report and the event log

2. **Validation Phase**:
   - Uses the `integration-ctrf-validator:test` Gradle task
   - Turns `CtrfListener` off for the validator's own tests, through `junit.platform.execution.listeners.deactivate`: the jar registers it, and its report would otherwise replace the one the validator checks
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
- Names, those of parameterized invocations included, messages, `rawStatus`, `retryAttempts`, durations and the `testId` of class-level failures
- Captured `stdout` of failed attempts, final and in `retryAttempts`, and none on a passed attempt
- The attachment of a published file
- The run's settings: thread count, build name, number and URL, environment health and `generatedBy`

## Running Integration Tests

One command generates the listener's report and validates it:

```bash
./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test
```

With no flags, the validator expects what that run produces: one thread, the build values from the module's `src/test/resources/ctrf.properties`, and a healthy environment.

### Settings

Pass a setting once to the one command, and both tasks receive it. The validator takes its expectations from the same inputs:

| Setting | Effect on the run | What the validator expects |
|---|---|---|
| `-Dthreads=N` | runs tests on N threads | between 2 and N distinct thread names (exactly 1 without it) |
| `-Dforks=N` | runs N forked JVMs | nothing; the report and JUnit's event log must still agree |
| `-Dctrf.build.name`, `-Dctrf.build.number`, `-Dctrf.build.url` | override `ctrf.properties` | the same values in `environment`; a non-numeric build number as `buildId` |
| `ENV_HEALTHY=false` (environment variable) | marks the environment unhealthy | `healthy: false` |
| `-Dctrf.report.path` | where the validator reads the report | default `../integration-tests-listener/build/test-results/ctrf-report.json` |
| `-Dctrf.junit.events.dir` | where the validator reads JUnit's event log | default `junit-platform` next to the report |

When the two tasks run in separate commands, pass the same settings to both.

## Run tests exactly as in CI

CI passes the same flags to the report step and the validator step. Locally, in one command:

```bash
ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test \
  -Dforks=2 -Dthreads=2 \
  -Dctrf.build.name=system-build \
  -Dctrf.build.number=777 \
  -Dctrf.build.url=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678
```

## Conclusion

The integration tests validate the JUnit CTRF Reporter's behaviour. They ensure that the reporter generates valid CTRF reports that accurately reflect test execution results across various scenarios, including different test statuses, durations, and parallel execution.


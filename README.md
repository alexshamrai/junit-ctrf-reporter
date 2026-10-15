# JUnit CTRF Reporter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.alexshamrai/junit-ctrf-reporter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.alexshamrai%22%20AND%20a:%22junit-ctrf-reporter%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A JUnit 5 library that generates test reports following the standardized [CTRF (Common Test Report Format)](https://ctrf.io/) specification, providing both a JUnit Jupiter Extension and a JUnit Platform TestExecutionListener.

Requires Java 17 or higher.

## What is CTRF?

CTRF is a universal JSON test report schema that addresses the lack of a standardized format for JSON test reports.

**Consistency Across Tools:** Different testing tools and frameworks often produce reports in varied formats. CTRF ensures a uniform structure, making it easier to understand and compare reports, regardless of the testing tool used.

**Language and Framework Agnostic:** It provides a universal reporting schema that works seamlessly with any programming language and testing framework.

**Facilitates Better Analysis:** With a standardized format, programmatically analyzing test outcomes across multiple platforms becomes more straightforward.

## Features

- Generates JSON test reports for JUnit 5 tests that follow CTRF 0.2.0 and validate against its [official schema](https://github.com/ctrf-io/ctrf/blob/v0.2.0/schema/ctrf.schema.json)
- Captures test status, duration, and failure details
- Supports customization of report content via configuration
- Tracks and includes environment information
- Handles parallel test execution
- Environment health tracking (propagates to report info that a test run is executed in a degraded environment)

## Getting Started

### Add Dependency

To use the JUnit CTRF Extension in your project, add the following test dependency:

#### Gradle

Add to your `build.gradle` file:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'io.github.alexshamrai:junit-ctrf-reporter:0.5.0'
}
```

#### Maven

Add to your `pom.xml` file:

```xml
<dependency>
    <groupId>io.github.alexshamrai</groupId>
    <artifactId>junit-ctrf-reporter</artifactId>
    <version>0.5.0</version>
    <scope>test</scope>
</dependency>
```

#### JUnit version

The reporter does not bring JUnit with it: it runs on the JUnit version your project already uses. It is tested with JUnit 5.10, 5.14 and 6.1.

On Gradle 9 and later, the JUnit Platform launcher must be on the test runtime classpath, as Gradle requires:

```groovy
dependencies {
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

## Usage options

There are two ways to use the CTRF reporter: as a JUnit Jupiter Extension or as a JUnit Platform TestExecutionListener.

### 1. Use CtrfListener (JUnit Platform TestExecutionListener)
This approach is suitable when you need to enable CTRF reporting globally for all tests executed by the JUnit Platform, without modifying individual test classes.
 Register the listener by creating a file named org.junit.platform.launcher.TestExecutionListener in your src/test/resources/META-INF/services directory and add the fully qualified name of the listener:
```
io.github.alexshamrai.launcher.CtrfListener
```

Programmatically:
If you are invoking the JUnit Platform Launcher directly, you can register the listener programmatically:
``` java
// Example with LauncherDiscoveryRequest
LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
    .selectors(selectPackage("com.example"))
    .build();

Launcher launcher = LauncherFactory.create();
launcher.registerTestExecutionListeners(new CtrfListener());
launcher.execute(request);
```

### 2. Register CtrfExtension

To use the JUnit CTRF Extension, you need to register it in your JUnit test class. You can do this by adding the `@ExtendWith` annotation to your test class:

```java
@ExtendWith(CtrfExtension.class)
public class MyTest {

    @Test
    void testExample() {
        // Your test code
    }
}
```
It allows you to configure a report only for custom classes
For more details on JUnit extensions, see the [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/#extensions).

### Configure Properties

You can configure the CTRF extension in two ways:

1. **Using a properties file**: Create a `ctrf.properties` file in your project's `src/test/resources` directory.
2. **Using system properties**: Pass configuration values as JVM system properties using `-D` parameters.

#### Properties File Example

```properties
ctrf.report.path=build/test-results/test/ctrf-report.json
ctrf.max.message.length=300

junit.version=5.11.3

ctrf.report.name=My Report
ctrf.app.name=My Application
ctrf.app.version=1.0.0
ctrf.test.environment=staging

ctrf.build.name=feature-branch
ctrf.build.number=123
ctrf.build.url=http://ci.example.com/build/123
ctrf.repository.name=my-repo
ctrf.repository.url=http://github.com/my-repo
ctrf.commit=9aba36cedaab8d9404eebedeba3739c55af83a01
ctrf.branch.name=main

ctrf.os.platform=Linux
ctrf.os.release=5.4.0-42-generic
ctrf.os.version=Ubuntu 20.04
```

#### Using System Properties

You can override any property by passing it as a system property. For example:

```
-Dctrf.report.path=custom/path/report.json
-Dctrf.app.name="My Custom App Name"
```

**Note:**

You need to ensure that system properties are passed to the test task by adding the following to your `build.gradle` file:

```groovy
test {
    // This ensures "ctrf" system properties are passed to the test
    systemProperties += System.properties.findAll { k, v -> k.toString().startsWith("ctrf") }
}
```

## Configuration Reference

The following parameters can be configured in the `ctrf.properties` file:

| Parameter                         | Description                                                  | Default Value      |
|-----------------------------------|--------------------------------------------------------------|--------------------|
| `ctrf.report.path`                | The file path where the CTRF report will be saved            | `ctrf-report.json` |
| `ctrf.max.message.length`         | Maximum length for error messages in the report              | `500`              |
| `ctrf.calculate.startup.duration` | Whether to calculate and include test suite startup duration | `false`            |
| `ctrf.run.id`                     | Run identifier shared by the JVMs of one build; see [Parallel Forks, Retries and Reruns](#parallel-forks-retries-and-reruns) | detected |
| `junit.version`                   | The version of JUnit used in your project                    |                    |
| `ctrf.report.name`                | Name of the test report                                      |                    |
| `ctrf.app.name`                   | Name of the application under test                           |                    |
| `ctrf.app.version`                | Version of the application under test                        |                    |
| `ctrf.build.name`                 | Name or ID of the build                                      |                    |
| `ctrf.build.number`               | Build number from your CI/CD system. A whole number is written as `buildNumber`; any other value, such as `2026.10.15-3`, as `buildId` |                    |
| `ctrf.build.url`                  | URL to the build in your CI/CD system                        |                    |
| `ctrf.repository.name`            | Name of the source code repository                           |                    |
| `ctrf.repository.url`             | URL to the source code repository                            |                    |
| `ctrf.commit`                     | Commit hash of the code being tested                         |                    |
| `ctrf.branch.name`                | Name of the source control branch                            |                    |
| `ctrf.os.platform`                | Operating system platform                                    |                    |
| `ctrf.os.release`                 | OS release identifier                                        |                    |
| `ctrf.os.version`                 | OS version details                                           |                    |
| `ctrf.test.environment`           | Test environment identifier (e.g., dev, staging, prod)       |                    |

All mandatory parameters have default values

## Environment Health Tracking

The library supports tracking environment health status during test execution. This feature allows you to mark when tests are running in a degraded or problematic environment, which is then reflected in the CTRF report.

### When to Use

Mark the environment as unhealthy when tests encounter:
- Service or application unavailability (APIs, databases, etc.)
- Network connectivity issues
- Insufficient system resources
- Infrastructure problems
- Any condition that might affect test reliability

### Usage

There are two ways to mark the environment as unhealthy:

#### 1. Using Environment Variable

Set the `ENV_HEALTHY` environment variable to `false` before running tests:

```bash
export ENV_HEALTHY=false
./gradlew test
```

Or in your CI/CD configuration:

```yaml
env:
  ENV_HEALTHY: 'false'
```

#### 2. Programmatic API

Call the `EnvironmentHealthTracker` API from within your tests:

```java
import static io.github.alexshamrai.EnvironmentHealthTracker.markEnvironmentUnhealthy;

@Test
void testWithUnhealthyEnvironment() {
    try {
        // Attempt to connect to external service
        externalService.connect();
    } catch (ConnectionException e) {
        // Mark environment as unhealthy
        markEnvironmentUnhealthy();
    }

    // Continue with test...
}
```

You can also check the current health status:

```java
import io.github.alexshamrai.EnvironmentHealthTracker;

boolean healthy = EnvironmentHealthTracker.isEnvironmentHealthy();
```

### Behavior

- **Default State**: Environment starts as healthy (`true`)
- **Irreversible**: Once marked unhealthy, it remains unhealthy for the entire test run
- **Persistence**: When the JVMs of one run merge their results, such as parallel forks and retry rounds, the report is unhealthy if any of them was. A report from an earlier run doesn't pass its state on
- **Report Field**: The health status appears in the CTRF report under `results.environment.healthy`

### Example Report Output

When the environment is marked unhealthy, the CTRF report includes:

```json
{
  "results": {
    "environment": {
      "healthy": false,
      "appName": "My Application",
      "buildNumber": 123
      // ... other environment fields
    }
  }
}
```

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

A test class whose setup (`@BeforeAll`) or teardown (`@AfterAll`) fails in one round and succeeds in a later one is reported the same way: its `initializationError` or `teardownError` entry becomes `passed`, with `flaky: true` and the failure in `retryAttempts`.

## Parallel Forks, Retries and Reruns

A build can run tests in several JVMs that write the same report:
- parallel forks: Gradle `maxParallelForks`, Surefire `forkCount`;
- retry rounds: the Gradle test-retry plugin;
- reruns inside one JVM: Surefire `rerunFailingTestsCount`;
- several test tasks or plugin executions in one build.

The report stays one file at `ctrf.report.path`, and each JVM merges its results into it when it finishes.
- **JVMs of the same run merge.** Every test appears once, with earlier attempts in `retryAttempts`.
- **A report from another run is replaced**, for example the one left by the previous build.
- **The report is never half-written.** While a JVM merges, it holds a lock on `<report>.lock` next to the report, and it replaces the report by moving a complete file over it. Add `ctrf-report.json.lock` and `.ctrf-report.json.*.tmp` to `.gitignore`, or set `ctrf.report.path` under `build/` or `target/`.
- **If the lock can't be taken**, the report is still written, atomically but without the lock. That happens when another JVM holds it for more than 60 seconds, or on a file system that doesn't support locks, such as some network mounts. JVMs that finish at the same moment can then overwrite each other's results. A JVM killed while writing can leave a hidden `.ctrf-report.json.<id>.tmp` file, which is safe to delete.

Each JVM reads and rewrites the whole report when it finishes. With a large suite and a new JVM per test class (Gradle `forkEvery = 1`, Surefire `reuseForks=false`), this adds about a second per JVM at 80,000 tests.

### How the Run Is Identified

The report's root `runId` decides which JVMs belong together. Each JVM resolves it on its own, and the first match wins:

1. `ctrf.run.id`, a system property or a `ctrf.properties` entry. It must change with every build. A constant, such as a fixed entry in `ctrf.properties`, makes every build the same run, so reports accumulate again.
2. A CI variable:
   - GitHub Actions: `GITHUB_RUN_ID` and `GITHUB_RUN_ATTEMPT`;
   - GitLab: `CI_JOB_ID`;
   - Jenkins: `BUILD_TAG`;
   - Azure Pipelines: `BUILD_BUILDID` and `SYSTEM_JOBATTEMPT`;
   - CircleCI: `CIRCLE_WORKFLOW_JOB_ID`;
   - Buildkite: `BUILDKITE_JOB_ID`.
3. The build tool's JVM, meaning the nearest ancestor process that is a JVM. This identifies one Maven build, or one `gradle --no-daemon` build.
4. Otherwise each JVM is its own run.

Locally, Gradle runs builds in a long-lived daemon, so consecutive builds share step 3 and their results accumulate in one report. The same goes for other long-lived build servers, such as `mvnd` and Bazel. Test runs started from an IDE can behave the same way, for example VS Code's Java test runner, whose parent is a long-lived `java` process. Give each Gradle build its own id:

```groovy
tasks.withType(Test).configureEach {
    // A new id each time the task runs, shared by its forks and retry rounds.
    // The task stays up to date, and this works with the configuration cache.
    doFirst { systemProperty 'ctrf.run.id', UUID.randomUUID().toString() }
}
```

Or, with the Kotlin DSL:

```kotlin
import java.util.UUID

tasks.withType<Test>().configureEach {
    doFirst { (this as Test).systemProperty("ctrf.run.id", UUID.randomUUID().toString()) }
}
```

Each test task then gets its own id. If a build runs several test tasks, give each its own `ctrf.report.path`. This matters most when two tasks run the same tests, for example one per JDK version. Writing to one report, a test that fails on one JDK and passes on the other would fold into one passed, flaky entry.

Separate build invocations are separate runs unless they share a run id, for example `./gradlew test` followed by `./gradlew integrationTest`, or two Maven invocations. On a CI system that isn't listed above, pass the same `-Dctrf.run.id=<your CI build id>` to every invocation whose results belong in one report.

### Reports from Several Machines

When tests run on several machines, such as CI matrix jobs, each machine writes its own report. Merge them with [ctrf-cli](https://github.com/ctrf-io/ctrf-cli): `npx ctrf merge <directory>`.

## Contributing

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to contribute to this project.

## Development

For information on setting up the project locally for development, see [DEVELOPMENT.md](DEVELOPMENT.md).

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

If you encounter any issues or have questions, please [open an issue](https://github.com/alexshamrai/junit-ctrf-reporter/issues) on GitHub.

# JUnit CTRF Reporter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.alexshamrai/junit-ctrf-reporter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.alexshamrai%22%20AND%20a:%22junit-ctrf-reporter%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A JUnit 5 library that generates test reports following the standardized [CTRF (Common Test Report Format)](https://ctrf.io/) specification, through a JUnit Platform `TestExecutionListener`.

Requires Java 17 or higher.

## What is CTRF?

CTRF is a universal JSON test report schema that addresses the lack of a standardized format for JSON test reports.

**Consistency Across Tools:** Different testing tools and frameworks often produce reports in varied formats. CTRF ensures a uniform structure, making it easier to understand and compare reports, regardless of the testing tool used.

**Language and Framework Agnostic:** It provides a universal reporting schema that works seamlessly with any programming language and testing framework.

**Facilitates Better Analysis:** With a standardized format, programmatically analyzing test outcomes across multiple platforms becomes more straightforward.

## Features

- Generates JSON test reports for JUnit 5 tests that follow CTRF 0.2.0 and validate against its [official schema](https://github.com/ctrf-io/ctrf/blob/v0.2.0/schema/ctrf.schema.json)
- Captures test status, duration, and failure details
- No runtime dependencies: besides the JUnit your tests already use, it adds nothing to your test classpath
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
    testImplementation 'io.github.alexshamrai:junit-ctrf-reporter:0.5.2'
}
```

#### Maven

Add to your `pom.xml` file:

```xml
<dependency>
    <groupId>io.github.alexshamrai</groupId>
    <artifactId>junit-ctrf-reporter</artifactId>
    <version>0.5.2</version>
    <scope>test</scope>
</dependency>
```

#### JUnit version

The reporter does not bring JUnit with it: it runs on the JUnit version your project already uses. It is tested with JUnit 5.10, 5.14 and 6.1. It has no other runtime dependencies either. Up to 0.5.0 it brought `jackson-databind` and `owner` onto the test runtime classpath; if your tests use either of them, for example through a library that looks for Jackson at run time, such as REST Assured or WireMock, declare it as a test dependency yourself.

On Gradle 9 and later, the JUnit Platform launcher must be on the test runtime classpath, as Gradle requires:

```groovy
dependencies {
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

## Choosing a setup

Register `CtrfListener`, the JUnit Platform listener. The JUnit Jupiter extension, `CtrfExtension`, is deprecated and will be removed in 0.6.0; [Deprecated: CtrfExtension](#deprecated-ctrfextension) shows how to move from it. **Never register both**: only the listener would report, and the extension's registration would silently do nothing.

### Recommended: CtrfListener

`CtrfListener` is a JUnit Platform `TestExecutionListener`. It sees everything the JUnit Platform runs, so besides your tests it also reports:

- the tests of a class that JUnit skips as a whole, such as a `@Disabled` class, as skipped;
- failures outside tests, as failed entries: a class whose `@BeforeAll` or `@AfterAll` fails, a class that can't be created, a `@MethodSource` or `@TestFactory` that throws;
- tests of other engines, such as JUnit 4 tests run by the Vintage engine;
- dynamic tests, such as those of a `@TestFactory`.

Register it for the whole test run with a file named `org.junit.platform.launcher.TestExecutionListener` in `src/test/resources/META-INF/services/`, containing this line:

```
io.github.alexshamrai.launcher.CtrfListener
```

That is all Gradle and Maven need: both run tests on the JUnit Platform, which finds the file on the test classpath. Nothing goes in `build.gradle` or `pom.xml`.

If you start the JUnit Platform Launcher yourself, register the listener programmatically:

```java
LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
    .selectors(selectPackage("com.example"))
    .build();

Launcher launcher = LauncherFactory.create();
launcher.registerTestExecutionListeners(new CtrfListener());
launcher.execute(request);
```

**Why the jar doesn't register the listener itself.** It could, with the same `META-INF/services` file inside the jar. Then every project with the reporter on its test classpath would get a report whether it wanted one or not, and in projects that use `CtrfExtension` the listener would silently take over the reporting. So the listener stays opt-in.

### Deprecated: CtrfExtension

`CtrfExtension`, the JUnit Jupiter extension, is deprecated since 0.5.2 and will be removed in 0.6.0. It logs a warning when a run starts. To move to the listener:

1. Add the file `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`, containing the line `io.github.alexshamrai.launcher.CtrfListener`.
2. Remove `@ExtendWith(CtrfExtension.class)` from your test classes.

The report can gain entries after the move. The extension reports only the classes it is registered on, and it can't see:

- the tests of a class that JUnit skips as a whole, such as a `@Disabled` class: they are missing from the report;
- the tests of a class whose `@BeforeAll` aborts with a failed assumption: the class appears as one skipped `initializationError` entry instead;
- a class that can't be created, for example when its Spring context fails to load under `@TestInstance(Lifecycle.PER_CLASS)`;
- failures of containers below class level, such as a `@MethodSource` that throws;
- teardown failures of extensions registered before it;
- tests that don't run on JUnit Jupiter, such as JUnit 4 tests.

### Tests that run JUnit themselves

A test that starts the JUnit Platform itself, for example through `LauncherFactory.create()`, starts a nested run in the same JVM. Only the run that started reporting is reported: the nested run's tests are left out, and the run around it goes on.

With `CtrfExtension`, the run starts at the first class that has the extension, so a nested run that starts earlier, from a class without it, becomes the reported run and its tests reach the report. `CtrfListener` starts the run when the test plan starts, so it has no such gap. Register a `CtrfListener` instance with one launcher only: an instance shared by an outer and a nested launcher loses track of the outer run.

## Configuration

### Configure Properties

Each setting is looked up in these places, and the first one that sets it wins:

1. a JVM system property, such as `-Dctrf.report.path=build/ctrf-report.json`;
2. an environment variable with the same name, then the name in upper case with underscores, such as `CTRF_REPORT_PATH`. Only `ctrf.*` settings have the upper-case form;
3. a `ctrf.properties` file on the test classpath, usually `src/test/resources/ctrf.properties`, saved as UTF-8;
4. the default in the [Configuration Reference](#configuration-reference).

The reporter reads its configuration once, when it starts. A value can use another setting or a system property, as in `ctrf.report.path=${user.home}/reports/ctrf.json`; a name that nothing sets expands to an empty string. Numbers may have spaces around them. A number or a `true`/`false` setting that can't be read, a negative length, or a report path that is blank or isn't a valid path logs a warning and the default is used instead.

Up to 0.5.0, `CTRF_*` environment variables were ignored. They are now read, so a `CTRF_*` variable that your CI defines for another step, such as a `CTRF_REPORT_PATH` for a publishing action, configures the reporter too. A relative path is resolved against the test JVM's working directory, which is usually the module's directory. Rename such a variable if it isn't meant for the reporter.

#### Properties File Example

```properties
ctrf.report.path=build/test-results/test/ctrf-report.json
ctrf.max.message.length=300
ctrf.max.trace.length=20000

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
| `ctrf.report.path`                | The file path where the CTRF report will be saved. A directory gets `ctrf-report.json` inside it, with a warning | `ctrf-report.json` |
| `ctrf.max.message.length`         | Maximum length of a failure's `message`, which is the exception's class and message; a longer one is cut and ends with `...` | `500`              |
| `ctrf.max.trace.length`           | Maximum length of a failure's `trace`, its stack trace; `0` means no limit. A longer trace keeps the lines that fit, a line saying how much was cut, and every `Caused by:` line | `20000`            |
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

## Logging

The reporter logs warnings, for example about a report it can't read or lock, through `java.util.logging`. Its loggers are named after its classes, which all start with `io.github.alexshamrai`. By default the warnings go to the standard error.

To hide them, pass `-Djava.util.logging.config.file=<path>` to the test JVM, pointing to a file like this one:

```properties
handlers = java.util.logging.ConsoleHandler
io.github.alexshamrai.level = SEVERE
```

If your project routes `java.util.logging` to SLF4J or Log4j, for example with `jul-to-slf4j`, the warnings go there.

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

## Suites

Each test's `suite` lists the display names of its containers, outermost first: its class, any `@Nested` classes, and the method of a parameterized, repeated or dynamic test. The engine isn't part of it. A test with no other container gets the engine's name. CTRF viewers group tests by `suite`, as an IDE does.

```json
{
  "name": "[1] 1",
  "suite": ["Payments", "Refunds", "refundsEachAmount(int)"],
  "filePath": "com.example.PaymentsTest$Refunds"
}
```

A class's `initializationError` or `teardownError` is grouped under that class, with its tests: its `suite` ends with the class's name.

Under a `@Suite` class, no engine is part of `suite` either. With `CtrfListener`, the `@Suite` class's name comes first: `["PaymentsSuite", "Payments", "Refunds"]`. `CtrfExtension` sees only what Jupiter runs, so its suites start at the test class.

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

For information on setting up the project locally for development, see [DEVELOPMENT.md](DEVELOPMENT.md). How the library is put together is in [ARCHITECTURE.md](ARCHITECTURE.md).

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

If you encounter any issues or have questions, please [open an issue](https://github.com/alexshamrai/junit-ctrf-reporter/issues) on GitHub.

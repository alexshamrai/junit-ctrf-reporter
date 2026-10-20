package integration;

import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Checks what JUnit's event log can't: names, messages, durations, threads and the run's settings. Presence, final
 * status, retries and flaky of every entry are checked by {@link JunitCrossCheckTest}.
 */
public class CtrfLogicTest extends BaseIntegrationTest {

    private static final String FAKE_PACKAGE = "io.github.alexshamrai.integration.fake.";

    @Test
    void verifySummaryIsCorrect() {
        var summary = report.getResults().getSummary();
        assertThat(summary).isNotNull();

        // Each test appears once, as its final attempt: the test-retry plugin's second round is folded into
        // retryAttempts. The fake tests, AfterAllFailureTest's passing test and its teardownError, AssumptionTest's
        // aborted test, which counts as skipped, PaymentsTest's three passing tests, BrokenSourceTest's two failed
        // containers, and the four tests of DisabledClassTest and ConditionallySkippedClassTest as skipped.
        assertThat(summary.getTests()).isEqualTo(31);
        assertThat(summary.getPassed()).isEqualTo(17);
        assertThat(summary.getFailed()).isEqualTo(7);
        assertThat(summary.getSkipped()).isEqualTo(7);
        assertThat(summary.getFlaky()).isEqualTo(1);
        assertThat(summary.getPending()).isEqualTo(0);
        assertThat(summary.getOther()).isEqualTo(0);
        assertThat(summary.getStart()).isGreaterThan(0);
        assertThat(summary.getStop()).isGreaterThanOrEqualTo(summary.getStart());

        var total = summary.getTests();
        var sum = summary.getPassed() + summary.getFailed() + summary.getSkipped()
            + summary.getPending() + summary.getOther();
        assertThat(total).isEqualTo(sum);
    }

    @Test
    void verifyLongTestDurations() {
        var minimumDurations = Map.of(
            testWithId("FirstLongTest", "[method:firstLongTwoSecondTest()]"), 2000L,
            testWithId("FirstLongTest", "[method:firstLongOneSecondTest()]"), 1000L,
            testWithId("FirstLongTest", "[method:firstLongHalfSecondTest()]"), 500L,
            testWithId("SecondLongTest", "[method:secondLongTwoSecondTest()]"), 2000L,
            testWithId("SecondLongTest", "[method:secondLongOneSecondTest()]"), 1000L,
            testWithId("SecondLongTest", "[method:secondLongHalfSecondTest()]"), 500L);

        minimumDurations.forEach((test, minimum) -> assertThat(test.getDuration())
            .as("duration of %s", test.getTestId())
            .isGreaterThanOrEqualTo(minimum));
    }

    @Test
    void verifyThreadCountMatchesTheRun() {
        var threadIds = report.getResults().getTests().stream()
            .map(io.github.alexshamrai.ctrf.model.Test::getThreadId)
            .distinct()
            .toList();

        assertThat(threadIds).as("thread IDs").doesNotContainNull();
        if (expected.threads() <= 1) {
            assertThat(threadIds).as("thread IDs of a run without -Dthreads").hasSize(1);
        } else {
            assertThat(threadIds.size())
                .as("distinct thread IDs of a run with -Dthreads=%d", expected.threads())
                .isBetween(2, expected.threads());
        }
    }

    // The run's -Dctrf.build.* flags, or the module's ctrf.properties: checks that Gradle forwards -Dctrf.* end to end
    @Test
    void verifyBuildValuesMatchTheRun() {
        var environment = report.getResults().getEnvironment();
        assertThat(environment).isNotNull();

        assertThat(environment.getBuildName()).as("buildName").isEqualTo(expected.buildName());
        assertThat(environment.getBuildNumber()).as("buildNumber").isEqualTo(expected.buildNumber());
        assertThat(environment.getBuildId()).as("buildId").isEqualTo(expected.buildId());
        assertThat(environment.getBuildUrl()).as("buildUrl").isEqualTo(expected.buildUrl());
    }

    @Test
    void verifySummaryDurationSpansTheRun() {
        var summary = report.getResults().getSummary();

        assertThat(summary.getDuration())
            .as("summary.duration should be stop minus start, across every JVM merged into the report")
            .isEqualTo(summary.getStop() - summary.getStart())
            .isPositive();
    }

    @Test
    void verifyFlakyTestKeepsItsFailedAttempt() {
        var flakyTest = testWithId("FlakyTest", "[method:flakyTestPassesOnTheSecondRun()]");

        assertThat(flakyTest.getName()).isEqualTo("Flaky test passed on the second run");
        assertThat(flakyTest.getRetryAttempts())
            .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus)
            .containsExactly(tuple(1, TestStatus.FAILED));
    }

    @Test
    void verifyGeneratedByIsTheListener() {
        assertThat(report.getGeneratedBy())
            .as("generatedBy of the listener module's report")
            .isEqualTo(expected.generatedBy());
    }

    @Test
    void verifyEnvironmentHealthMatchesTheRun() {
        var environment = report.getResults().getEnvironment();
        assertThat(environment).isNotNull();

        assertThat(environment.isHealthy())
            .as("healthy: false when ENV_HEALTHY=false")
            .isEqualTo(expected.healthy());
    }

    @Test
    void verifyInitializationErrorsKeepTheirClassAndMessage() {
        var first = testWithId("InitializationErrorTest", null);
        var second = testWithId("AnotherInitializationErrorTest", null);

        assertThat(first.getName()).isEqualTo("initializationError");
        assertThat(first.getFilePath()).isEqualTo(FAKE_PACKAGE + "InitializationErrorTest");
        assertThat(first.getMessage()).contains("Simulated initialization failure");
        assertThat(second.getName()).isEqualTo("initializationError");
        assertThat(second.getFilePath()).isEqualTo(FAKE_PACKAGE + "AnotherInitializationErrorTest");
        assertThat(second.getMessage()).contains("Another initialization failure");
    }

    @Test
    void verifyAfterAllFailureIsReportedAsTeardownError() {
        var teardown = testWithId("AfterAllFailureTest", null);

        assertThat(teardown.getName()).isEqualTo("teardownError");
        assertThat(teardown.getMessage()).contains("Simulated teardown failure");
        assertThat(testWithId("AfterAllFailureTest", "[method:testBeforeFailingTeardown()]").getName())
            .isEqualTo("testBeforeFailingTeardown()");
    }

    @Test
    void verifyFailedContainersBelowClassLevelAreReported() {
        assertThat(testWithId("BrokenSourceTest", "[test-template:usesData(int)]").getName()).isEqualTo("usesData(int)");
        assertThat(testWithId("BrokenSourceTest", "[test-factory:factory()]").getName()).isEqualTo("factory()");
    }

    @Test
    void verifyEveryTestHasATestId() {
        var tests = report.getResults().getTests();
        assertThat(tests).isNotNull().isNotEmpty();

        assertThat(tests).allSatisfy(test ->
            assertThat(test.getTestId()).as("testId of %s", test.getName()).isNotBlank());
    }

    @Test
    void verifyClassFailuresAreIdentifiedByTheirClass() {
        var classFailures = report.getResults().getTests().stream()
            .filter(test -> "initializationError".equals(test.getName()) || "teardownError".equals(test.getName()))
            .toList();
        assertThat(classFailures)
            .extracting(io.github.alexshamrai.ctrf.model.Test::getName)
            .contains("initializationError", "teardownError");

        assertThat(classFailures).allSatisfy(test ->
            assertThat(test.getTestId())
                .as("testId of the %s of %s", test.getName(), test.getFilePath())
                .isEqualTo("[engine:junit-jupiter]/[class:" + test.getFilePath() + "]"));
    }

    @Test
    void verifyTestsOfSkippedClassesAreReportedWithTheirReason() {
        for (String method : List.of("firstTestOfDisabledClass()", "secondTestOfDisabledClass()")) {
            assertThat(testWithId("DisabledClassTest", "[method:" + method + "]").getMessage())
                .as("reason for %s", method)
                .contains("Simulated disabled class");
        }
        for (String method : List.of("firstTestOfSkippedClass()", "secondTestOfSkippedClass()")) {
            assertThat(testWithId("ConditionallySkippedClassTest", "[method:" + method + "]").getMessage())
                .as("reason for %s", method)
                .contains("ctrf.integration.never.set");
        }
    }

    @Test
    void verifyFailedAssumptionKeepsTheAbortedStatus() {
        var abortedTest = testWithId("AssumptionTest", "[method:requiresUnavailableService()]");

        assertThat(abortedTest.getRawStatus()).isEqualTo("aborted");
        assertThat(abortedTest.getMessage()).contains("Simulated assumption failure");
    }

    @Test
    void verifyEachTestAppearsOnce() {
        var testIds = report.getResults().getTests().stream()
            .map(io.github.alexshamrai.ctrf.model.Test::getTestId)
            .toList();

        assertThat(testIds).as("testIds in the report").doesNotHaveDuplicates();
    }

    @Test
    void verifyRetryFieldsAreConsistent() {
        assertThat(report.getResults().getTests()).allSatisfy(test -> {
            if (test.getRetryAttempts() == null) {
                assertThat(test.getRetries()).as("retries of %s without retryAttempts", test.getName()).isNull();
            } else {
                assertThat(test.getRetries()).as("retries of %s", test.getName()).isEqualTo(test.getRetryAttempts().size());
                assertThat(test.getRetryAttempts())
                    .extracting(RetryAttempt::getAttempt)
                    .as("attempt numbers of %s", test.getName())
                    .isEqualTo(IntStream.rangeClosed(1, test.getRetries()).boxed().toList());
            }
        });
    }

    @Test
    void verifyTestsFailingInEveryRoundKeepTheirFailedAttempts() {
        for (String method : List.of("firstFailedTest()", "secondFailedTest()")) {
            assertThat(testWithId("DummyFailedTest", "[method:" + method + "]").getRetryAttempts())
                .extracting(RetryAttempt::getStatus)
                .as("earlier attempts of %s", method)
                .containsExactly(TestStatus.FAILED);
        }
    }

    @Test
    void verifyFailedAttemptsKeepTheirCapturedOutput() {
        var failing = testWithId("DummyFailedTest", "[method:firstFailedTest()]");
        var printed = List.of("BaseIntegrationTest beforeEach()", "DummyFailedTest firstFailedTest()",
            "BaseIntegrationTest afterEach()");
        assertThat(failing.getStdout()).as("the final, failed attempt").isEqualTo(printed);
        assertThat(failing.getRetryAttempts()).singleElement().extracting(RetryAttempt::getStdout).isEqualTo(printed);

        var flaky = testWithId("FlakyTest", "[method:flakyTestPassesOnTheSecondRun()]");
        assertThat(flaky.getStdout()).as("the final, passed attempt drops its output").isNull();
        assertThat(flaky.getRetryAttempts()).singleElement().extracting(RetryAttempt::getStdout)
            .isEqualTo(List.of("BaseIntegrationTest beforeEach()", "Running test attempt #1", "BaseIntegrationTest afterEach()"));
    }

    @Test
    void verifyPublishedFileIsAttached() {
        var test = testWithId("DummySuccessTest", "[method:firstSuccessTest(org.junit.jupiter.api.TestReporter)]");

        assertThat(test.getAttachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.getName()).isEqualTo("dummy-success.txt");
            assertThat(attachment.getContentType()).isEqualTo("text/plain; charset=UTF-8");
            assertThat(Path.of(attachment.getPath())).isAbsolute().exists().hasFileName("dummy-success.txt");
        });
    }

    @Test
    void verifySummaryCountsFlakyTests() {
        long flakyTests = report.getResults().getTests().stream()
            .filter(test -> Boolean.TRUE.equals(test.getFlaky()))
            .count();

        assertThat(report.getResults().getSummary().getFlaky()).isEqualTo((int) flakyTests);
    }

    @Test
    void verifyReportHasARunId() {
        assertThat(report.getRunId()).as("root runId").isNotBlank();
    }

    /**
     * The report entry whose testId is the given fake test class's ID, followed by {@code /segment} unless the segment
     * is {@code null} (a class-level entry). Fails if the report doesn't have exactly one.
     */
    @Test
    void verifyEveryTestHasASuite() {
        assertThat(report.getResults().getTests()).allSatisfy(test ->
            assertThat(test.getSuite()).as("suite of %s", test.getTestId()).isNotEmpty().doesNotContainNull());
    }

    @Test
    void verifyNestedAndParameterizedTestsAreGroupedByDisplayName() {
        assertThat(testWithId("PaymentsTest", "[nested-class:Refunds]/[method:refundsTheFee()]").getSuite())
            .containsExactly("Payments", "Refunds");
        assertThat(testWithId("PaymentsTest",
            "[nested-class:Refunds]/[test-template:refundsEachAmount(int)]/[test-template-invocation:#1]").getSuite())
            .containsExactly("Payments", "Refunds", "refundsEachAmount(int)");
    }

    private io.github.alexshamrai.ctrf.model.Test testWithId(String fakeClass, String segment) {
        var testId = "[engine:junit-jupiter]/[class:" + FAKE_PACKAGE + fakeClass + "]" + (segment == null ? "" : "/" + segment);
        var matches = report.getResults().getTests().stream()
            .filter(test -> testId.equals(test.getTestId()))
            .toList();
        assertThat(matches).as("entries with testId %s", testId).hasSize(1);
        return matches.get(0);
    }
}

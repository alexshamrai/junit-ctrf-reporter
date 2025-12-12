package integration;

import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CtrfLogicTest extends BaseIntegrationTest {

    @Test
    void verifyTestStatuses() {
        var tests = report.getResults().getTests();
        assertThat(tests).isNotNull().isNotEmpty();

        tests.forEach(test -> {
            assertThat(test.getName()).isNotNull().isNotEmpty();
            assertThat(test.getStatus()).isNotNull();
        });

        var testNameToExpectedStatus = Map.of(
            "firstDisabledTest", TestStatus.SKIPPED,
            "secondDisabledTest", TestStatus.SKIPPED,
            "firstFailedTest", TestStatus.FAILED,
            "secondFailedTest", TestStatus.FAILED,
            "firstSuccessTest", TestStatus.PASSED,
            "secondSuccessTest", TestStatus.PASSED
        );

        testNameToExpectedStatus.forEach((testName, expectedStatus) -> {
            tests.stream()
                .filter(test -> test.getName() != null && test.getName().contains(testName))
                .findFirst()
                .ifPresent(test ->
                    assertThat(test.getStatus())
                        .as(testName + " should have " + expectedStatus + " status")
                        .isEqualTo(expectedStatus)
                );
        });
    }

    @Test
    void verifySummaryIsCorrect() {
        var summary = report.getResults().getSummary();
        assertThat(summary).isNotNull();

        assertThat(summary.getTests()).isEqualTo(24);
        assertThat(summary.getPassed()).isEqualTo(13);
        assertThat(summary.getFailed()).isEqualTo(9);
        assertThat(summary.getSkipped()).isEqualTo(2);
        assertThat(summary.getPending()).isEqualTo(0);
        assertThat(summary.getOther()).isEqualTo(0);
        assertThat(summary.getStart()).isGreaterThan(0);
        assertThat(summary.getStop()).isGreaterThanOrEqualTo(summary.getStart());

        var total = summary.getTests();
        var sum = summary.getPassed() + summary.getFailed() + summary.getSkipped() +
                  summary.getPending() + summary.getOther();
        assertThat(total).isEqualTo(sum);
    }

    @Test
    void verifyLongTestDurations() {
        var tests = report.getResults().getTests();
        assertThat(tests).isNotNull().isNotEmpty();

        var testNameToDuration = Map.of(
            "firstLongTwoSecondTest()", 2000L,
            "firstLongOneSecondTest()", 1000L,
            "firstLongHalfSecondTest()", 500L,
            "secondLongTwoSecondTest()", 2000L,
            "secondLongOneSecondTest()", 1000L,
            "secondLongHalfSecondTest()", 500L
        );

        testNameToDuration.forEach((testName, expectedMinDuration) -> {
            var testOptional = tests.stream()
                .filter(test -> test.getName() != null && test.getName().equals(testName))
                .findFirst();

            assertThat(testOptional.isPresent())
                .as("Test with name '" + testName + "' should be present in the report")
                .isTrue();

            var test = testOptional.get();
            assertThat(test.getDuration())
                .as(testName + " should have duration equal to or longer than " + expectedMinDuration + "ms")
                .isGreaterThanOrEqualTo(expectedMinDuration);
        });
    }

    // On ci tests are run in 2 threads. Test is checking that the number of threads is correct.
    @Test
    void verifyTestsRunInMultipleThreads() {
        var tests = report.getResults().getTests();
        assertThat(tests).isNotNull().isNotEmpty();

        var threadIds = tests.stream()
            .map(io.github.alexshamrai.ctrf.model.Test::getThreadId)
            .distinct()
            .toList();

        assertThat(threadIds)
            .as("Tests should run in exactly 2 different threads")
            .hasSize(2);

        tests.forEach(test -> {
            assertThat(test.getThreadId())
                .as("Test '" + test.getName() + "' should have a thread ID")
                .isNotNull();
        });
    }

    // Verify that build parameters passed as system properties like "-Dctrf.report.name="Overridden Report Name"" are correctly set
    // The values should be exactly as in check-ctrf.yml command
    @Test
    void verifyBuildParametersFromSystemProperties() {
        var environment = report.getResults().getEnvironment();
        assertThat(environment).isNotNull();

        assertThat(environment.getBuildName())
            .as("Build name should match the value passed via -Dctrf.build.name")
            .isEqualTo("system-build");
        assertThat(environment.getBuildNumber())
            .as("Build number should be numeric")
            .matches("\\d+");
        assertThat(environment.getBuildUrl())
            .as("Build URL should follow the pattern https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/ + number")
            .matches("https://github\\.com/alexshamrai/junit-ctrf-reporter/actions/runs/\\d+");
    }

    @Test
    void verifyFlakyTestContainsValidData() {
        var tests = report.getResults().getTests();
        var flakyTest = tests.stream()
            .filter(test -> test.getName().equals("Flaky test passed on the second run"))
            .filter(test -> test.getStatus().equals(TestStatus.PASSED))
            .findFirst().get();

        assertThat(flakyTest.getFlaky()).isTrue();
        assertThat(flakyTest.getRetries()).isPositive();
    }

    @Test
    void verifyGeneratedByFieldIsCorrect() {
        String reportPath = System.getProperty("ctrf.report.path", "");
        if (reportPath.contains("listener")) {
            assertThat(report.getGeneratedBy())
                .as("The 'generatedBy' field should be set by the listener")
                .isEqualTo("io.github.alexshamrai.launcher.CtrfListener");
        } else {
            assertThat(report.getGeneratedBy())
                .as("The 'generatedBy' field should be set by the extension")
                .isEqualTo("io.github.alexshamrai.jupiter.CtrfExtension");
        }
    }

    @Test
    void verifyEnvironmentHealthIsFalse() {
        var environment = report.getResults().getEnvironment();
        assertThat(environment).isNotNull();

        assertThat(environment.isHealthy())
            .as("Environment health should be false")
            .isFalse();
    }

    @Test
    void verifyInitializationErrorIsCaptured() {
        var tests = report.getResults().getTests();
        assertThat(tests).isNotNull().isNotEmpty();

        // Look for the specific InitializationErrorTest (not AnotherInitializationErrorTest)
        var initErrorTest = tests.stream()
            .filter(test -> "initializationError".equals(test.getName()))
            .filter(test -> test.getFilepath() != null
                && test.getFilepath().contains("InitializationErrorTest")
                && !test.getFilepath().contains("Another"))
            .findFirst();

        assertThat(initErrorTest)
            .as("initializationError test from InitializationErrorTest should be present in the report")
            .isPresent();

        var test = initErrorTest.get();
        assertThat(test.getStatus())
            .as("initializationError should have FAILED status")
            .isEqualTo(TestStatus.FAILED);

        assertThat(test.getMessage())
            .as("initializationError should have an error message")
            .isNotNull()
            .contains("Simulated initialization failure");

        assertThat(test.getFilepath())
            .as("initializationError should have filepath pointing to InitializationErrorTest")
            .contains("InitializationErrorTest");
    }

    @Test
    void verifyMultipleInitializationErrorsAreCaptured() {
        var tests = report.getResults().getTests();
        assertThat(tests).isNotNull().isNotEmpty();

        // Find all initializationError entries
        var initErrorTests = tests.stream()
            .filter(test -> "initializationError".equals(test.getName()))
            .toList();

        // Should have entries from both test classes (with retry, 4 total)
        assertThat(initErrorTests)
            .as("Should have multiple initializationError entries from different test classes")
            .hasSizeGreaterThanOrEqualTo(2);

        // Get distinct filepaths to verify different classes are captured
        var distinctFilepaths = initErrorTests.stream()
            .map(io.github.alexshamrai.ctrf.model.Test::getFilepath)
            .distinct()
            .toList();

        assertThat(distinctFilepaths)
            .as("initializationError should come from 2 different test classes")
            .hasSize(2);

        // Verify InitializationErrorTest is captured
        assertThat(distinctFilepaths)
            .as("Should have initializationError from InitializationErrorTest")
            .anyMatch(path -> path.contains("InitializationErrorTest") && !path.contains("Another"));

        // Verify AnotherInitializationErrorTest is captured
        assertThat(distinctFilepaths)
            .as("Should have initializationError from AnotherInitializationErrorTest")
            .anyMatch(path -> path.contains("AnotherInitializationErrorTest"));

        // Verify distinct error messages
        var distinctMessages = initErrorTests.stream()
            .map(io.github.alexshamrai.ctrf.model.Test::getMessage)
            .distinct()
            .toList();

        assertThat(distinctMessages)
            .as("Should have 2 distinct error messages from different test classes")
            .hasSize(2);

        assertThat(distinctMessages)
            .as("Should contain the original initialization error message")
            .anyMatch(msg -> msg.contains("Simulated initialization failure"));

        assertThat(distinctMessages)
            .as("Should contain the second initialization error message")
            .anyMatch(msg -> msg.contains("Another initialization failure"));
    }
}


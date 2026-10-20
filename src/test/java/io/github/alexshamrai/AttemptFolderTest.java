package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Attachment;
import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AttemptFolderTest {

    private static final String CLASS_A = "com.example.ATest";
    private static final String CLASS_B = "com.example.BTest";
    private static final String TEST_1 = "[engine:junit-jupiter]/[class:com.example.ATest]/[method:test1()]";
    private static final String TEST_2 = "[engine:junit-jupiter]/[class:com.example.ATest]/[method:test2()]";

    @org.junit.jupiter.api.Test
    @DisplayName("A test that ran once is returned as it is, without retry fields")
    void singleAttemptIsUntouched() {
        var only = attempt(TEST_1, "test1()", PASSED, 100L);

        assertThat(AttemptFolder.fold(List.of(only))).containsExactly(only);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test that passed after a failed attempt is one flaky test with the failed attempt in its history")
    void failedThenPassedIsFlaky() {
        var failed = attempt(TEST_1, "test1()", FAILED, 100L).toBuilder().message("boom").trace("trace of boom").build();
        var passed = attempt(TEST_1, "test1()", PASSED, 200L);

        var tests = AttemptFolder.fold(List.of(failed, passed));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(PASSED);
            assertThat(test.getStart()).isEqualTo(200L);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isTrue();
            assertThat(test.getRetryAttempts()).containsExactly(RetryAttempt.builder()
                .attempt(1).status(FAILED).duration(5).start(100L).stop(105L)
                .message("boom").trace("trace of boom")
                .build());
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test that failed in every attempt is not flaky")
    void failedThenFailedIsNotFlaky() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_1, "test1()", FAILED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(FAILED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A passing test that ran again, because its whole class was rerun, is not flaky")
    void passedThenPassedIsNotFlaky() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", PASSED, 100L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(PASSED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test that passed after a skipped (aborted) attempt is not flaky")
    void skippedThenPassedIsNotFlaky() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", SKIPPED, 100L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A test that failed and then was skipped is skipped, with the failure in its history")
    void failedThenSkippedIsSkippedAndNotFlaky() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_1, "test1()", SKIPPED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(SKIPPED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getRetryAttempts()).extracting(RetryAttempt::getStatus).containsExactly(FAILED);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Earlier attempts are numbered from 1 in the order they ran")
    void earlierAttemptsAreNumberedFromOne() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_1, "test1()", FAILED, 200L),
            attempt(TEST_1, "test1()", PASSED, 300L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(2);
            assertThat(test.getRetryAttempts())
                .extracting(RetryAttempt::getAttempt, RetryAttempt::getStart)
                .containsExactly(tuple(1, 100L), tuple(2, 200L));
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Tests keep the order of their first attempt")
    void testsKeepTheOrderOfTheirFirstAttempt() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_2, "test2()", PASSED, 150L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(tests).extracting(Test::getTestId).containsExactly(TEST_1, TEST_2);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A class failure in another phase is an earlier attempt of the same class")
    void classFailuresFoldAcrossPhases() {
        var classId = "[engine:junit-jupiter]/[class:com.example.ATest]";

        var tests = AttemptFolder.fold(List.of(
            attempt(classId, "initializationError", FAILED, 100L),
            attempt(classId, "teardownError", FAILED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getName()).isEqualTo("teardownError");
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Tests with different testIds are never folded, even with the same name")
    void differentTestIdsAreNotFolded() {
        var templates = "[engine:junit-jupiter]/[class:com.example.ATest]/[test-template:";
        var tests = AttemptFolder.fold(List.of(
            Test.builder().testId("[engine:junit-jupiter]/[class:com.example.BTest]/[method:shouldWork()]")
                .filePath(CLASS_B).name("shouldWork()").status(FAILED).build(),
            Test.builder().testId("[engine:junit-jupiter]/[class:com.example.ATest]/[method:shouldWork()]")
                .filePath(CLASS_A).name("shouldWork()").status(PASSED).build(),
            Test.builder().testId(templates + "first(int)]/[test-template-invocation:#1]")
                .filePath(CLASS_A).name("[1] 1").status(FAILED).build(),
            Test.builder().testId(templates + "second(int)]/[test-template-invocation:#1]")
                .filePath(CLASS_A).name("[1] 1").status(PASSED).build()));

        assertThat(tests).hasSize(4).allSatisfy(test -> assertThat(test.getRetries()).isNull());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Entries without a testId fold by file path and name, apart from entries with a testId")
    void entriesWithoutTestIdFoldByFilePathAndNameOnly() {
        var legacyFailed = Test.builder().filePath(CLASS_A).name("test1()").status(FAILED).build();
        var legacyPassed = Test.builder().filePath(CLASS_A).name("test1()").status(PASSED).build();
        var current = attempt(TEST_1, "test1()", PASSED, 300L);

        var tests = AttemptFolder.fold(List.of(legacyFailed, legacyPassed, current));

        assertThat(tests).extracting(Test::getTestId, Test::getRetries, Test::getFlaky)
            .containsExactly(tuple(null, 1, true), tuple(TEST_1, null, null));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Entries without a testId or a file path are never folded by name alone")
    void entriesWithOnlyANameAreNeverFolded() {
        var tests = AttemptFolder.fold(List.of(
            Test.builder().name("test1()").status(FAILED).build(),
            Test.builder().name("test1()").status(PASSED).build()));

        assertThat(tests).hasSize(2).allSatisfy(test -> assertThat(test.getRetries()).isNull());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("An attempt without a status is kept and never makes a test flaky")
    void attemptWithoutStatusIsKept() {
        var tests = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", null, 100L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Folding does not change the attempts it is given")
    void foldDoesNotChangeItsInput() {
        var failed = attempt(TEST_1, "test1()", FAILED, 100L);
        var passed = attempt(TEST_1, "test1()", PASSED, 200L);

        AttemptFolder.fold(List.of(failed, passed));

        assertThat(passed.getRetries()).isNull();
        assertThat(passed.getRetryAttempts()).isNull();
        assertThat(passed.getFlaky()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Unfolding gives the earlier attempts with the test's identity, then the final attempt")
    void unfoldGivesEarlierAttemptsThenTheFinalAttempt() {
        var test = Test.builder().testId(TEST_1).filePath(CLASS_A).name("test1()").tags(List.of("smoke"))
            .status(PASSED).start(300L).stop(305L).duration(5).threadId("worker-1")
            .retries(1).flaky(true)
            .retryAttempts(List.of(RetryAttempt.builder().attempt(1).status(FAILED).duration(7)
                .start(100L).stop(107L).message("boom").trace("trace of boom").build()))
            .build();

        var attempts = AttemptFolder.unfold(List.of(test));

        assertThat(attempts).containsExactly(
            Test.builder().testId(TEST_1).filePath(CLASS_A).name("test1()").tags(List.of("smoke"))
                .status(FAILED).duration(7).start(100L).stop(107L).message("boom").trace("trace of boom").build(),
            test.toBuilder().retries(null).flaky(null).retryAttempts(null).build());
        assertThat(test.getRetryAttempts()).as("the input is not changed").hasSize(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Unfolding keeps a test without retryAttempts as one attempt")
    void unfoldKeepsATestThatRanOnce() {
        var test = attempt(TEST_1, "test1()", PASSED, 100L);

        assertThat(AttemptFolder.unfold(List.of(test))).containsExactly(test);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Unfolding keeps the order of retryAttempts, and folding renumbers them from 1")
    void attemptsAreRenumberedInArrayOrder() {
        var test = attempt(TEST_1, "test1()", PASSED, 300L).toBuilder()
            .retries(2)
            .retryAttempts(List.of(
                RetryAttempt.builder().attempt(4).status(FAILED).start(100L).build(),
                RetryAttempt.builder().attempt(9).status(SKIPPED).start(200L).build()))
            .build();

        var refolded = AttemptFolder.fold(AttemptFolder.unfold(List.of(test)));

        assertThat(refolded).singleElement().satisfies(folded ->
            assertThat(folded.getRetryAttempts())
                .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus, RetryAttempt::getStart)
                .containsExactly(tuple(1, FAILED, 100L), tuple(2, SKIPPED, 200L)));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Folding what was unfolded gives the same tests")
    void foldUnfoldFoldIsStable() {
        var once = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_2, "test2()", PASSED, 150L),
            attempt(TEST_1, "test1()", PASSED, 200L)));

        assertThat(AttemptFolder.fold(AttemptFolder.unfold(once))).isEqualTo(once);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A further retry round extends the history instead of nesting it")
    void thirdRoundExtendsTheHistory() {
        var afterTwoRounds = AttemptFolder.fold(List.of(
            attempt(TEST_1, "test1()", FAILED, 100L),
            attempt(TEST_1, "test1()", FAILED, 200L)));
        var attempts = new ArrayList<>(AttemptFolder.unfold(afterTwoRounds));
        attempts.add(attempt(TEST_1, "test1()", PASSED, 300L));

        var tests = AttemptFolder.fold(attempts);

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(2);
            assertThat(test.getRetryAttempts())
                .extracting(RetryAttempt::getAttempt, RetryAttempt::getStatus)
                .containsExactly(tuple(1, FAILED), tuple(2, FAILED));
            assertThat(test.getFlaky()).isTrue();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Entries of a 0.4.x report, one per attempt, fold into one test")
    void entriesOfAnOlderReportFoldIntoOneTest() {
        var first = attempt(TEST_1, "test1()", FAILED, 100L);
        var second = attempt(TEST_1, "test1()", PASSED, 200L).toBuilder().retries(1).flaky(true).build();

        var tests = AttemptFolder.fold(AttemptFolder.unfold(List.of(first, second)));

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isTrue();
            assertThat(test.getRetryAttempts()).extracting(RetryAttempt::getStatus).containsExactly(FAILED);
        });
    }

    private static Test attempt(String testId, String name, Test.TestStatus status, long start) {
        return Test.builder()
            .testId(testId)
            .filePath(CLASS_A)
            .name(name)
            .status(status)
            .start(start)
            .stop(start + 5)
            .duration(5)
            .build();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Unfolding gives the earlier attempts the test's suite, and folding keeps the final attempt's")
    void suiteSurvivesUnfoldAndFold() {
        var suite = List.of("Payments", "Refunds");
        var test = Test.builder().testId(TEST_1).filePath(CLASS_A).name("test1()").suite(suite)
            .status(PASSED).start(300L).stop(305L).duration(5)
            .retries(1)
            .retryAttempts(List.of(RetryAttempt.builder().attempt(1).status(FAILED).duration(7).start(100L).stop(107L).build()))
            .build();

        var attempts = AttemptFolder.unfold(List.of(test));

        assertThat(attempts).extracting(Test::getSuite).containsExactly(suite, suite);
        assertThat(AttemptFolder.fold(attempts)).singleElement().extracting(Test::getSuite).isEqualTo(suite);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Each attempt keeps its own output through folding and unfolding")
    void outputSurvivesFoldAndUnfold() {
        var failed = attempt(TEST_1, "test1()", FAILED, 100L).toBuilder()
            .stdout(List.of("first try")).stderr(List.of("oops")).build();
        var passed = attempt(TEST_1, "test1()", PASSED, 200L);

        var folded = AttemptFolder.fold(List.of(failed, passed));

        assertThat(folded).singleElement().satisfies(test -> {
            assertThat(test.getStdout()).isNull();
            assertThat(test.getRetryAttempts()).singleElement().satisfies(earlier -> {
                assertThat(earlier.getStdout()).containsExactly("first try");
                assertThat(earlier.getStderr()).containsExactly("oops");
            });
        });
        assertThat(AttemptFolder.unfold(folded)).extracting(Test::getStdout).containsExactly(List.of("first try"), null);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Each attempt keeps its own attachments through folding and unfolding")
    void attachmentsSurviveFoldAndUnfold() {
        var firstScreenshot = Attachment.builder().name("first.png").contentType("image/png").path("/tmp/first.png").build();
        var secondScreenshot = Attachment.builder().name("second.png").contentType("image/png").path("/tmp/second.png").build();
        var failed = attempt(TEST_1, "test1()", FAILED, 100L).toBuilder().attachments(List.of(firstScreenshot)).build();
        var passed = attempt(TEST_1, "test1()", PASSED, 200L).toBuilder().attachments(List.of(secondScreenshot)).build();

        var folded = AttemptFolder.fold(List.of(failed, passed));

        assertThat(folded).singleElement().satisfies(test -> {
            assertThat(test.getAttachments()).containsExactly(secondScreenshot);
            assertThat(test.getRetryAttempts()).singleElement()
                .satisfies(earlier -> assertThat(earlier.getAttachments()).containsExactly(firstScreenshot));
        });
        assertThat(AttemptFolder.unfold(folded)).extracting(Test::getAttachments)
            .containsExactly(List.of(firstScreenshot), List.of(secondScreenshot));
    }
}

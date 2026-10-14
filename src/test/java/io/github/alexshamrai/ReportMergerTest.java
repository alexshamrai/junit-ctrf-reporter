package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Results;
import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ReportMergerTest {

    private static final String RUN = "run-1";
    private static final String CLASS_ID = "[engine:junit-jupiter]/[class:com.example.DbTest]";

    @org.junit.jupiter.api.Test
    @DisplayName("Without a report on disk, the run is this JVM's results")
    void noExistingReportGivesThisJvmsResults() {
        var run = ReportMerger.merge(null, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("id-1");
        assertThat(run.start()).isEqualTo(100L);
        assertThat(run.stop()).isEqualTo(200L);
        assertThat(run.healthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report of the same run is unfolded, and this JVM's attempts extend its tests' history")
    void sameRunAppendsThisJvmsAttemptsToTheReport() {
        var existing = report(RUN, 50L, 90L, true, attempt("id-1", FAILED, 60L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(PASSED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isTrue();
            assertThat(test.getRetryAttempts()).extracting(RetryAttempt::getStart).containsExactly(60L);
        });
        assertThat(run.start()).isEqualTo(50L);
        assertThat(run.stop()).isEqualTo(200L);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Tests written by another fork of the same run are kept, before this JVM's")
    void sameRunKeepsTheTestsOfOtherForks() {
        var existing = report(RUN, 50L, 90L, true, attempt("from-fork-1", PASSED, 60L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("from-fork-2", FAILED, 70L)), List.of(), 55L, 95L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("from-fork-1", "from-fork-2");
        assertThat(run.start()).isEqualTo(50L);
        assertThat(run.stop()).isEqualTo(95L);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("The run is unhealthy if the report on disk or this JVM is")
    void sameRunIsUnhealthyIfEitherReportIs() {
        assertThat(ReportMerger.merge(report(RUN, 50L, 90L, false), RUN, List.of(), List.of(), 100L, 200L, true).healthy()).isFalse();
        assertThat(ReportMerger.merge(report(RUN, 50L, 90L, true), RUN, List.of(), List.of(), 100L, 200L, false).healthy()).isFalse();
        assertThat(ReportMerger.merge(report(RUN, 50L, 90L, true), RUN, List.of(), List.of(), 100L, 200L, true).healthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report of another run is replaced, and passes on neither its start time nor its health")
    void differentRunIsReplaced() {
        var existing = report("run-0", 10L, 20L, false, attempt("old", FAILED, 15L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("new", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("new");
        assertThat(run.tests().get(0).getRetries()).isNull();
        assertThat(run.start()).isEqualTo(100L);
        assertThat(run.healthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report without a runId, as 0.4.x and other tools write it, is never merged")
    void reportWithoutRunIdIsNeverMerged() {
        var existing = report(null, 10L, 20L, true, attempt("id-1", FAILED, 15L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> assertThat(test.getRetries()).isNull());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report of the same run without summary or environment still merges")
    void sameRunReportWithoutSummaryOrEnvironmentStillMerges() {
        var existing = CtrfJson.builder()
            .runId(RUN)
            .results(Results.builder().tests(List.of(attempt("id-1", FAILED, 60L))).build())
            .build();

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> assertThat(test.getFlaky()).isTrue());
        assertThat(run.start()).isEqualTo(100L);
        assertThat(run.stop()).isEqualTo(200L);
        assertThat(run.healthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A class failure followed by the class's success is passed and flaky, under the failure's name")
    void recoveredContainerIsPassedAndFlaky() {
        var failure = attempt(CLASS_ID, FAILED, 60L).toBuilder().name("initializationError").message("Connection refused").build();
        var existing = report(RUN, 50L, 90L, true, failure);

        var run = ReportMerger.merge(existing, RUN, List.of(), List.of(new ContainerSuccess(CLASS_ID, 300L, 310L)), 100L, 400L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> {
            assertThat(test.getName()).isEqualTo("initializationError");
            assertThat(test.getStatus()).isEqualTo(PASSED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isTrue();
            assertThat(test.getStart()).isEqualTo(300L);
            assertThat(test.getDuration()).isEqualTo(10L);
            assertThat(test.getRetryAttempts())
                .extracting(RetryAttempt::getStatus, RetryAttempt::getMessage)
                .containsExactly(tuple(FAILED, "Connection refused"));
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A success of a container that never failed adds nothing")
    void successWithoutAnEarlierFailureAddsNothing() {
        var run = ReportMerger.merge(null, RUN, List.of(attempt("id-1", PASSED, 150L)),
            List.of(new ContainerSuccess(CLASS_ID, 100L, 200L)), 100L, 200L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("id-1");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A container that fails in every round stays failed, with its history")
    void containerFailingEveryRoundStaysFailed() {
        var existing = report(RUN, 50L, 90L, true, attempt(CLASS_ID, FAILED, 60L));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt(CLASS_ID, FAILED, 160L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(FAILED);
            assertThat(test.getRetries()).isEqualTo(1);
            assertThat(test.getFlaky()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A success can't recover a failure written by another run")
    void successDoesNotRecoverAnotherRunsFailure() {
        var existing = report("run-0", 50L, 90L, true, attempt(CLASS_ID, FAILED, 60L));

        var run = ReportMerger.merge(existing, RUN, List.of(), List.of(new ContainerSuccess(CLASS_ID, 300L, 310L)), 100L, 400L, true);

        assertThat(run.tests()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A success doesn't turn a skipped container into a passed one")
    void successDoesNotRecoverASkippedContainer() {
        String templateId = CLASS_ID + "/[test-template:usesData(int)]";
        var existing = report(RUN, 50L, 90L, true, attempt(templateId, SKIPPED, 60L));

        var run = ReportMerger.merge(existing, RUN, List.of(), List.of(new ContainerSuccess(templateId, 300L, 310L)), 100L, 400L, true);

        assertThat(run.tests()).singleElement().satisfies(test -> {
            assertThat(test.getStatus()).isEqualTo(SKIPPED);
            assertThat(test.getRetries()).isNull();
        });
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report that can't be merged is ignored, and this JVM's results are written")
    void mergeThatThrowsFallsBackToThisJvm() {
        var existing = report(RUN, 50L, 90L, true);
        existing.getResults().setTests(new ArrayList<>(Arrays.asList((Test) null)));

        var run = ReportMerger.merge(existing, RUN, List.of(attempt("id-1", PASSED, 150L)), List.of(), 100L, 200L, true);

        assertThat(run.tests()).extracting(Test::getTestId).containsExactly("id-1");
        assertThat(run.start()).isEqualTo(100L);
    }

    private static Test attempt(String testId, Test.TestStatus status, long start) {
        return Test.builder()
            .testId(testId)
            .name(testId)
            .filepath("com.example.DbTest")
            .status(status)
            .start(start)
            .stop(start + 5)
            .duration(5)
            .build();
    }

    private static CtrfJson report(String runId, long start, long stop, boolean healthy, Test... tests) {
        return CtrfJson.builder()
            .runId(runId)
            .results(Results.builder()
                .tests(new ArrayList<>(List.of(tests)))
                .summary(Summary.builder().start(start).stop(stop).build())
                .environment(Environment.builder().healthy(healthy).build())
                .build())
            .build();
    }
}

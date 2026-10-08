package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.AbortedAfterSkippedTestFixture;
import io.github.alexshamrai.scenario.fixtures.AbortedAfterTemplateMethodsRanFixture;
import io.github.alexshamrai.scenario.fixtures.AbortedClassFixture;
import io.github.alexshamrai.scenario.fixtures.AbortedOuterWithDisabledNestedFixture;
import io.github.alexshamrai.scenario.fixtures.ConditionalClassFixture;
import io.github.alexshamrai.scenario.fixtures.DisabledClassFixture;
import io.github.alexshamrai.scenario.fixtures.DisabledClassWithTemplateMethodFixture;
import io.github.alexshamrai.scenario.fixtures.DisabledTemplateMethodsFixture;
import io.github.alexshamrai.scenario.fixtures.PassingFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests inside a skipped or aborted container are reported as skipped (HP-8).
 */
class SkippedContainerScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerCountsMatchJunitWhenWholeClassesAreSkipped() throws Exception {
        var result = ScenarioRunner.runWithListener(
            DisabledClassFixture.class, ConditionalClassFixture.class, PassingFixture.class);

        var summary = result.report().getResults().getSummary();
        assertThat(result.junitCounts()).containsEntry("skipped", 4L);
        assertThat(summary.getSkipped()).isEqualTo(4);
        assertThat((long) summary.getTests()).isEqualTo(result.junitCounts().get("found"));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsEachTestOfADisabledClassWithTheClassReason() throws Exception {
        var result = ScenarioRunner.runWithListener(DisabledClassFixture.class);

        var entries = result.testsOf(DisabledClassFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("one()", SKIPPED), tuple("two()", SKIPPED));
        assertThat(entries).allSatisfy(test -> assertThat(test.getMessage()).contains("whole class disabled"));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsEachTestOfAConditionallySkippedClassWithTheConditionReason() throws Exception {
        var result = ScenarioRunner.runWithListener(ConditionalClassFixture.class);

        var entries = result.testsOf(ConditionalClassFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("one()", SKIPPED), tuple("two()", SKIPPED));
        assertThat(entries).allSatisfy(test -> assertThat(test.getMessage()).contains("ctrf.scenario.never.set"));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsEachTestOfAnAbortedClassAsSkippedWithTheAssumptionMessage() throws Exception {
        var result = ScenarioRunner.runWithListener(AbortedClassFixture.class);

        assertThat(result.junitCounts()).containsEntry("found", 2L);
        var entries = result.testsOf(AbortedClassFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("one()", SKIPPED), tuple("two()", SKIPPED));
        assertThat(entries).allSatisfy(test -> assertThat(test.getMessage()).contains("Docker is not available"));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsATestSkippedOnItsOwnOnceWhenItsClassAbortsLater() throws Exception {
        var result = ScenarioRunner.runWithListener(AbortedAfterSkippedTestFixture.class);

        assertThat(result.junitCounts()).containsEntry("found", 2L).containsEntry("skipped", 1L);
        var entries = result.testsOf(AbortedAfterSkippedTestFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("runs()", PASSED), tuple("disabledOne()", SKIPPED));
        assertThat(entries).filteredOn(test -> test.getStatus() == SKIPPED)
            .allSatisfy(test -> assertThat(test.getMessage()).contains("method disabled"));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsTestsOfADisabledNestedClassOnceWhenTheOuterClassAbortsLater() throws Exception {
        var result = ScenarioRunner.runWithListener(AbortedOuterWithDisabledNestedFixture.class);

        assertThat(result.junitCounts()).containsEntry("found", 2L);
        assertThat(result.tests()).extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("outer()", PASSED), tuple("inner()", SKIPPED));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsDisabledTestMethodsWhoseTestsJunitCreatesOnlyWhileRunningThem() throws Exception {
        var result = ScenarioRunner.runWithListener(DisabledTemplateMethodsFixture.class);

        assertThat(result.junitCounts()).containsEntry("containersSkipped", 3L);
        var entries = result.testsOf(DisabledTemplateMethodsFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus).containsExactlyInAnyOrder(
            tuple("runs()", PASSED),
            tuple("param(int)", SKIPPED),
            tuple("repeated()", SKIPPED),
            tuple("factory()", SKIPPED));
        assertThat(entries).filteredOn(test -> test.getStatus() == SKIPPED)
            .allSatisfy(test -> assertThat(test.getMessage()).contains("disabled"));
    }

    @org.junit.jupiter.api.Test
    void listenerReportsATemplateMethodOfADisabledClassAsSkipped() throws Exception {
        var result = ScenarioRunner.runWithListener(DisabledClassWithTemplateMethodFixture.class);

        var entries = result.testsOf(DisabledClassWithTemplateMethodFixture.class);
        assertThat(entries).extracting(Test::getName, Test::getStatus)
            .containsExactlyInAnyOrder(tuple("plain()", SKIPPED), tuple("param(int)", SKIPPED));
        assertThat(entries).allSatisfy(test ->
            assertThat(test.getMessage()).contains("class with a template method disabled"));
    }

    @org.junit.jupiter.api.Test
    void listenerDoesNotReportTestMethodsThatAlreadyRanAsSkippedWhenTheirClassAbortsLater() throws Exception {
        var result = ScenarioRunner.runWithListener(AbortedAfterTemplateMethodsRanFixture.class);

        assertThat(result.testsOf(AbortedAfterTemplateMethodsRanFixture.class))
            .filteredOn(test -> test.getStatus() == SKIPPED)
            .extracting(Test::getName)
            .isEmpty();
    }
}

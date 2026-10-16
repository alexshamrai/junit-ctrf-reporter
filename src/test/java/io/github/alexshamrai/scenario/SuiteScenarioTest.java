package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.AfterAllFailureFixture;
import io.github.alexshamrai.scenario.fixtures.DisabledClassFixture;
import io.github.alexshamrai.scenario.fixtures.ExtAfterAllFailureWithDisabledTestsFixture;
import io.github.alexshamrai.scenario.fixtures.ExtSetupAlwaysFailsFixture;
import io.github.alexshamrai.scenario.fixtures.ExtSuiteFixture;
import io.github.alexshamrai.scenario.fixtures.SetupAlwaysFailsFixture;
import io.github.alexshamrai.scenario.fixtures.SuiteFixture;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Every entry's suite is its place in the container hierarchy, by display names, without the engine (HP-12).
 */
class SuiteScenarioTest {

    @org.junit.jupiter.api.Test
    void listenerGroupsEveryEntryByItsContainers() throws Exception {
        var result = ScenarioRunner.runWithListener(SuiteFixture.class);

        assertNestedSuites(result);
        assertThat(suitesWithin(result, "[test-factory:generated()]")).singleElement()
            .isEqualTo(List.of("Payments", "generated()", "group"));
    }

    @org.junit.jupiter.api.Test
    void extensionGroupsEveryEntryByItsContainers() throws Exception {
        assertNestedSuites(ScenarioRunner.runWithExtension(ExtSuiteFixture.class));
    }

    @org.junit.jupiter.api.Test
    void listenerAndExtensionGiveTheSameSuites() throws Exception {
        Map<String, List<String>> listener = suitesById(ScenarioRunner.runWithListener(SuiteFixture.class), "SuiteFixture");
        Map<String, List<String>> extension = suitesById(ScenarioRunner.runWithExtension(ExtSuiteFixture.class), "ExtSuiteFixture");
        listener.keySet().removeIf(testId -> testId.contains("[test-factory:"));

        assertThat(listener.values()).as("the listener's suites").isNotEmpty().doesNotContainNull();
        assertThat(extension).isEqualTo(listener);
    }

    @org.junit.jupiter.api.Test
    void listenerGroupsClassErrorsAndTestsOfASkippedClassUnderTheirClass() throws Exception {
        var result = ScenarioRunner.runWithListener(SetupAlwaysFailsFixture.class, AfterAllFailureFixture.class,
            DisabledClassFixture.class);

        assertThat(result.tests())
            .extracting(Test::getName, Test::getSuite)
            .containsExactlyInAnyOrder(
                tuple("initializationError", List.of("SetupAlwaysFailsFixture")),
                tuple("ok()", List.of("AfterAllFailureFixture")),
                tuple("teardownError", List.of("AfterAllFailureFixture")),
                tuple("one()", List.of("DisabledClassFixture")),
                tuple("two()", List.of("DisabledClassFixture")));
    }

    @org.junit.jupiter.api.Test
    void extensionGroupsClassErrorsUnderTheirClass() throws Exception {
        var result = ScenarioRunner.runWithExtension(ExtSetupAlwaysFailsFixture.class,
            ExtAfterAllFailureWithDisabledTestsFixture.class);

        assertThat(result.tests())
            .extracting(Test::getName, Test::getSuite)
            .containsExactlyInAnyOrder(
                tuple("initializationError", List.of("ExtSetupAlwaysFailsFixture")),
                tuple("disabled()", List.of("ExtAfterAllFailureWithDisabledTestsFixture")),
                tuple("teardownError", List.of("ExtAfterAllFailureWithDisabledTestsFixture")));
    }

    private static void assertNestedSuites(ScenarioRunner.Result result) {
        assertThat(suiteOf(result, "/[method:charges()]")).containsExactly("Payments");
        assertThat(suiteOf(result, "/[nested-class:Refunds]/[method:refundsTheFee()]")).containsExactly("Payments", "Refunds");
        assertThat(suitesWithin(result, "[test-template:refundsEachAmount(int)]"))
            .hasSize(2)
            .allSatisfy(suite -> assertThat(suite).containsExactly("Payments", "Refunds", "refundsEachAmount(int)"));
        assertThat(suitesWithin(result, "[test-template:refundsTwice()]"))
            .hasSize(2)
            .allSatisfy(suite -> assertThat(suite).containsExactly("Payments", "Refunds", "refundsTwice()"));
        assertThat(suiteOf(result, "/[nested-class:Broken]")).containsExactly("Payments", "Broken");
    }

    private static List<String> suiteOf(ScenarioRunner.Result result, String testIdEnd) {
        var matches = result.tests().stream().filter(test -> test.getTestId().endsWith(testIdEnd)).toList();
        assertThat(matches).as("entries whose testId ends with %s", testIdEnd).hasSize(1);
        return matches.get(0).getSuite();
    }

    private static List<List<String>> suitesWithin(ScenarioRunner.Result result, String segment) {
        return result.tests().stream().filter(test -> test.getTestId().contains(segment)).map(Test::getSuite).toList();
    }

    // The fixtures' class names differ, so their testIds are compared without them
    private static Map<String, List<String>> suitesById(ScenarioRunner.Result result, String fixture) {
        Map<String, List<String>> suites = new TreeMap<>();
        result.tests().forEach(test -> suites.put(test.getTestId().replace("." + fixture + "]", ".Fixture]"), test.getSuite()));
        return suites;
    }
}

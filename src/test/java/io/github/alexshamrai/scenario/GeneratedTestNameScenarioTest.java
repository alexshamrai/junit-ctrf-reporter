package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import io.github.alexshamrai.scenario.fixtures.GeneratedTestNamesFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that a test method creates are named after it (MP-13), in two rounds in new JVMs, as the Gradle test-retry
 * plugin runs them.
 */
class GeneratedTestNameScenarioTest {

    private static final String FIXTURE = "[engine:junit-jupiter]/[class:" + GeneratedTestNamesFixture.class.getName() + "]";

    private static Map<String, io.github.alexshamrai.ctrf.model.Test> byTestId;

    @BeforeAll
    static void runTwoRounds() throws Exception {
        var result = ScenarioRunner.runRoundsWithListener(2, GeneratedTestNamesFixture.class);
        byTestId = result.tests().stream()
            .collect(Collectors.toMap(io.github.alexshamrai.ctrf.model.Test::getTestId, Function.identity()));
    }

    @Test
    void testsThatATestMethodCreatesAreNamedAfterIt() {
        assertThat(test("/[test-template:add(int)]/[test-template-invocation:#2]").getName()).isEqualTo("add(int) [2] 2");
        assertThat(test("/[test-template:repeated()]/[test-template-invocation:#1]").getName())
            .isEqualTo("repeated() repetition 1 of 2");
        assertThat(test("/[test-factory:factory()]/[dynamic-test:#1]").getName()).isEqualTo("factory() direct");
        assertThat(test("/[test-factory:factory()]/[dynamic-container:#2]/[dynamic-test:#1]").getName())
            .isEqualTo("factory() group inner");
    }

    @Test
    void otherTestsAndNamesThatAlreadyStartWithTheMethodsAreUnchanged() {
        assertThat(test("/[method:ordinary()]").getName()).isEqualTo("ordinary()");
        assertThat(test("/[test-template:named(int)]/[test-template-invocation:#1]").getName()).isEqualTo("named(int) with 1");
    }

    @Test
    void retriedInvocationIsOneFlakyTestUnderItsNewName() {
        var invocation = test("/[test-template:add(int)]/[test-template-invocation:#1]");

        assertThat(invocation.getName()).isEqualTo("add(int) [1] 1");
        assertThat(invocation.getStatus()).isEqualTo(TestStatus.PASSED);
        assertThat(invocation.getFlaky()).isTrue();
        assertThat(invocation.getRetries()).isEqualTo(1);
        assertThat(invocation.getSuite()).containsExactly("GeneratedTestNamesFixture", "add(int)");
    }

    @Test
    void failedDynamicContainerIsNamedAfterItsTestFactory() {
        var container = test("/[test-factory:factory()]/[dynamic-container:#3]");

        assertThat(container.getName()).isEqualTo("factory() broken group");
        assertThat(container.getStatus()).isEqualTo(TestStatus.FAILED);
        assertThat(container.getMessage()).contains("cannot create the group's tests");
    }

    @Test
    void invocationsOfTwoMethodsWithTheSameDisplayNamesHaveDistinctNames() {
        List<String> names = byTestId.values().stream().map(io.github.alexshamrai.ctrf.model.Test::getName).toList();

        assertThat(names).doesNotHaveDuplicates();
    }

    private static io.github.alexshamrai.ctrf.model.Test test(String segments) {
        var test = byTestId.get(FIXTURE + segments);
        assertThat(test).as("entry %s among %s", segments, byTestId.keySet()).isNotNull();
        return test;
    }
}

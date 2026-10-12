package io.github.alexshamrai.scenario;

import io.github.alexshamrai.scenario.fixtures.CollisionAFixture;
import io.github.alexshamrai.scenario.fixtures.CollisionBFixture;
import io.github.alexshamrai.scenario.fixtures.SameNameFirstFixture;
import io.github.alexshamrai.scenario.fixtures.SameNameSecondFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that only share a display name with another test are not retries of it (CB-7).
 */
class TestIdentityScenarioTest {

    @Test
    void testsSharingDisplayNamesWithFailedTestsInAnotherClassAreNotRetries() throws Exception {
        var result = ScenarioRunner.runWithListener(CollisionAFixture.class, CollisionBFixture.class);

        var passingTests = result.testsOf(CollisionBFixture.class);
        assertThat(passingTests).hasSize(5);
        assertThat(passingTests).allSatisfy(test -> {
            assertThat(test.getRetries()).as("retries of %s", test.getName()).isNull();
            assertThat(test.getFlaky()).as("flaky of %s", test.getName()).isNull();
        });
    }

    @Test
    void everyReportedTestCarriesItsJunitUniqueIdAsTestId() throws Exception {
        var result = ScenarioRunner.runWithListener(SameNameFirstFixture.class, SameNameSecondFixture.class);

        assertThat(result.tests()).extracting(io.github.alexshamrai.ctrf.model.Test::getTestId).containsExactlyInAnyOrder(
            "[engine:junit-jupiter]/[class:io.github.alexshamrai.scenario.fixtures.SameNameFirstFixture]/[method:common()]",
            "[engine:junit-jupiter]/[class:io.github.alexshamrai.scenario.fixtures.SameNameSecondFixture]/[method:common()]");
    }

    @Test
    void passingTestsWithTheSameMethodNameInTwoClassesAreNotFlaky() throws Exception {
        var result = ScenarioRunner.runWithListener(SameNameFirstFixture.class, SameNameSecondFixture.class);

        assertThat(result.tests()).hasSize(2);
        assertThat(result.tests()).allSatisfy(test -> {
            assertThat(test.getRetries()).as("retries of %s", test.getFilepath()).isNull();
            assertThat(test.getFlaky()).as("flaky of %s", test.getFilepath()).isNull();
        });
    }
}

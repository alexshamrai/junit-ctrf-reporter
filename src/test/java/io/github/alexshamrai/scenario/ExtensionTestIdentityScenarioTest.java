package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.ExtFailingSetupFixture;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The extension reports a test under its own identity even when its beforeEach did not run (HP-9).
 */
class ExtensionTestIdentityScenarioTest {

    @org.junit.jupiter.api.Test
    void extensionKeepsTheIdentityOfATestWhoseSetupFailedInAnotherExtension() throws Exception {
        var result = ScenarioRunner.runWithExtension(ExtFailingSetupFixture.class);

        assertThat(result.tests()).hasSize(1);
        Test test = result.tests().get(0);
        assertThat(test.getName()).isEqualTo("importantBusinessTest()");
        assertThat(test.getFilepath()).isEqualTo(ExtFailingSetupFixture.class.getName());
        assertThat(test.getTags()).contains("payments", "scenario-fixture");
        assertThat(test.getStatus()).isEqualTo(FAILED);
        assertThat(test.getMessage()).contains("fixture setup failed");
    }
}

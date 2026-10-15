package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.fixtures.ExtFirstFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The extension's end-of-run hook on JUnit 6, which closes AutoCloseable store values and warns about values that are
 * only a CloseableResource (MP-6). The compat matrix covers JUnit 5.10.5 and 5.14.4.
 */
class ExtensionRunEndScenarioTest {

    @org.junit.jupiter.api.Test
    void extensionWritesTheReportWithoutTheCloseableResourceWarning() throws Exception {
        var result = ScenarioRunner.runWithExtension(ExtFirstFixture.class);

        assertThat(result.tests()).extracting(Test::getName).containsExactly("first()");
        assertThat(result.log()).doesNotContain("CloseableResource");
    }
}

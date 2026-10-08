package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A disabled class with a parameterized test, whose invocations the test plan does not contain.
 */
@Tag("scenario-fixture")
@Disabled("class with a template method disabled")
public class DisabledClassWithTemplateMethodFixture {

    @Test
    void plain() {
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void param(int value) {
    }
}

package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Passing tests that only share their display names with the tests in {@link CollisionAFixture}.
 */
@Tag("scenario-fixture")
public class CollisionBFixture {

    @Test
    void shouldWork() {
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void param(int value) {
    }

    @RepeatedTest(2)
    void repeated() {
    }
}

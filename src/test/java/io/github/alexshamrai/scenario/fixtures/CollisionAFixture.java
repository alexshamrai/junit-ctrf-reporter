package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Failing tests whose display names also occur in {@link CollisionBFixture}.
 */
@Tag("scenario-fixture")
public class CollisionAFixture {

    @Test
    void shouldWork() {
        fail("CollisionAFixture.shouldWork fails");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void param(int value) {
        fail("CollisionAFixture.param fails");
    }

    @RepeatedTest(2)
    void repeated() {
        fail("CollisionAFixture.repeated fails");
    }
}

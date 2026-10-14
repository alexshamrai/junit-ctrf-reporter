package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A class whose {@code @AfterAll} fails in every round.
 */
@Tag("scenario-fixture")
public class TeardownAlwaysFailsFixture {

    @AfterAll
    static void disconnect() {
        throw new IllegalStateException("teardown fails in every round");
    }

    @Test
    void works() {
        // passes in every round
    }
}

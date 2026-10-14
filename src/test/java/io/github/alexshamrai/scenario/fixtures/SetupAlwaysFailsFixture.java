package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A class whose {@code @BeforeAll} fails in every round.
 */
@Tag("scenario-fixture")
public class SetupAlwaysFailsFixture {

    @BeforeAll
    static void connect() {
        throw new IllegalStateException("setup fails in every round");
    }

    @Test
    void works() {
        // never runs
    }
}

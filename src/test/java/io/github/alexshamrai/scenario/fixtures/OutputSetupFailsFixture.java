package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A class whose {@code @BeforeAll} prints, then fails (HP-13).
 */
@Tag("scenario-fixture")
public class OutputSetupFailsFixture {

    @BeforeAll
    static void connect() {
        System.out.println("connecting to the database");
        throw new IllegalStateException("setup fails");
    }

    @Test
    void neverRuns() {
    }
}

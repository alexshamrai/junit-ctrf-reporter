package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A class whose {@code @BeforeAll} ends the JVM with {@code System.exit(3)}, while no test runs (CB-8).
 */
@Tag("scenario-fixture")
public class JvmExitInSetupFixture {

    @BeforeAll
    static void exitsTheJvm() {
        System.exit(3);
    }

    @Test
    void neverStarts() {
    }
}

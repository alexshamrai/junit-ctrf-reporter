package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A whole class that an assumption in {@code @BeforeAll} aborts, so none of its tests run.
 */
@Tag("scenario-fixture")
public class AbortedClassFixture {

    @BeforeAll
    static void requireDocker() {
        assumeTrue(false, "Docker is not available");
    }

    @Test
    void one() {
    }

    @Test
    void two() {
    }
}

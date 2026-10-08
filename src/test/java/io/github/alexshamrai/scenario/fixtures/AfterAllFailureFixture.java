package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A class that fails in {@code @AfterAll}, after its test passed.
 */
@Tag("scenario-fixture")
public class AfterAllFailureFixture {

    @AfterAll
    static void tearDown() {
        throw new IllegalStateException("cleanup failed");
    }

    @Test
    void ok() {
    }
}

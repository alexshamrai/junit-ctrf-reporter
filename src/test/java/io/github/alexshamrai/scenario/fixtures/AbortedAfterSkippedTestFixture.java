package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A class that an assumption in {@code @AfterAll} aborts after one test passed and another was skipped on its own.
 */
@Tag("scenario-fixture")
public class AbortedAfterSkippedTestFixture {

    @AfterAll
    static void requireCleanTeardown() {
        assumeTrue(false, "teardown assumption failed");
    }

    @Test
    void runs() {
    }

    @Test
    @Disabled("method disabled")
    void disabledOne() {
    }
}

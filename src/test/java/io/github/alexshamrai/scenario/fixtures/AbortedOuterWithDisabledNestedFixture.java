package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A class that an assumption in {@code @AfterAll} aborts after its disabled nested class was skipped.
 */
@Tag("scenario-fixture")
public class AbortedOuterWithDisabledNestedFixture {

    @AfterAll
    static void requireCleanTeardown() {
        assumeTrue(false, "outer teardown assumption failed");
    }

    @Test
    void outer() {
    }

    @Nested
    @Disabled("nested class disabled")
    class Inner {

        @Test
        void inner() {
        }
    }
}

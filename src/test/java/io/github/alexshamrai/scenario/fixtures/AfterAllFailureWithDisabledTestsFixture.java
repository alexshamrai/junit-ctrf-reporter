package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A class that fails in {@code @AfterAll}, after JUnit skipped its only test.
 */
@Tag("scenario-fixture")
public class AfterAllFailureWithDisabledTestsFixture {

    @AfterAll
    static void tearDown() {
        throw new IllegalStateException("cleanup failed");
    }

    @Test
    @Disabled("not ready")
    void disabled() {
    }
}

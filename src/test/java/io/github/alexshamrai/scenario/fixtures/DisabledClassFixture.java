package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A whole class that is disabled: JUnit reports only the class as skipped, not its tests.
 */
@Tag("scenario-fixture")
@Disabled("whole class disabled")
public class DisabledClassFixture {

    @Test
    void one() {
    }

    @Test
    void two() {
    }
}

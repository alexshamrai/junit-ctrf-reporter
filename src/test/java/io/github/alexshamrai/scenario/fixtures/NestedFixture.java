package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The class a nested JUnit run runs (CB-2). None of its tests may reach the outer run's report.
 */
@Tag("scenario-fixture")
public class NestedFixture {

    @Test
    void nestedPasses() {
    }

    @Test
    void nestedFails() {
        fail("a nested run's failure must not reach the outer report");
    }
}

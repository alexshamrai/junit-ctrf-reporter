package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A test that a failed assumption aborts, next to a passing one.
 */
@Tag("scenario-fixture")
public class AssumptionFixture {

    @Test
    void requiresDocker() {
        assumeTrue(false, "Docker not available on this agent");
    }

    @Test
    void passes() {
    }
}

package io.github.alexshamrai.integration.fake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A test that a failed assumption aborts. It must be reported as skipped, not as failed.
 */
public class AssumptionTest extends BaseFakeTest {

    @Test
    void requiresUnavailableService() {
        assumeTrue(false, "Simulated assumption failure - service not available");
    }
}

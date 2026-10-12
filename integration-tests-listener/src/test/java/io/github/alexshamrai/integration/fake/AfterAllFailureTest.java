package io.github.alexshamrai.integration.fake;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Test class that fails in @AfterAll, after its test passed.
 * Verifies that the failure is reported as a "teardownError" entry.
 */
public class AfterAllFailureTest {

    @AfterAll
    static void afterAll() {
        throw new IllegalStateException("Simulated teardown failure - resources could not be released");
    }

    @Test
    void testBeforeFailingTeardown() {
        System.out.println("AfterAllFailureTest testBeforeFailingTeardown()");
    }
}

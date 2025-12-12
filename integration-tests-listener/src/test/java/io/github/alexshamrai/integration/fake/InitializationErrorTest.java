package io.github.alexshamrai.integration.fake;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Test class that fails during initialization (@BeforeAll).
 * This tests that the CtrfListener correctly captures container-level failures
 * and reports them as "initializationError" test entries.
 */
public class InitializationErrorTest {

    @BeforeAll
    static void beforeAll() {
        throw new RuntimeException("Simulated initialization failure - Spring context failed to load");
    }

    @Test
    void testThatNeverRuns() {
        // This test will never execute because @BeforeAll fails
        System.out.println("This should never be printed");
    }
}

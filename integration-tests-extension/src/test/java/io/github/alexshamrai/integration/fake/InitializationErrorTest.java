package io.github.alexshamrai.integration.fake;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test class that fails during initialization (@BeforeAll).
 * This tests that the CtrfExtension correctly captures @BeforeAll failures
 * and reports them as "initializationError" test entries.
 */
@ExtendWith(CtrfExtension.class)
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

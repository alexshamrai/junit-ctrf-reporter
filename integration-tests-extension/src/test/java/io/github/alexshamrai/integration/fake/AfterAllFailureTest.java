package io.github.alexshamrai.integration.fake;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test class that fails in @AfterAll, after its test passed.
 * Verifies that the CtrfExtension reports the failure as a "teardownError" entry.
 */
@ExtendWith(CtrfExtension.class)
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

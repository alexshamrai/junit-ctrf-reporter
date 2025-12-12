package io.github.alexshamrai.integration.fake;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Another test class that fails during initialization (@BeforeAll).
 * Verifies that multiple initialization errors from different classes are all captured.
 */
@ExtendWith(CtrfExtension.class)
public class AnotherInitializationErrorTest {

    @BeforeAll
    static void beforeAll() {
        throw new IllegalStateException("Another initialization failure - database connection failed");
    }

    @Test
    void anotherTestThatNeverRuns() {
        System.out.println("This should never be printed");
    }
}

package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A class that fails in {@code @AfterAll}, after its only test failed in an extension registered before
 * CtrfExtension, so CtrfExtension's beforeEach never ran.
 */
@Tag("scenario-fixture")
@ExtendWith({FailingBeforeEachCallback.class, CtrfExtension.class})
public class ExtAfterAllFailureAfterFailedSetupFixture {

    @AfterAll
    static void tearDown() {
        throw new IllegalStateException("cleanup failed");
    }

    @Test
    void ok() {
    }
}

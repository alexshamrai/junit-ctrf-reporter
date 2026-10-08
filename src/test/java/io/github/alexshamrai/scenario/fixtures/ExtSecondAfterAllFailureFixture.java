package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A class using CtrfExtension that fails in {@code @AfterAll}, after its test passed.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtSecondAfterAllFailureFixture {

    @AfterAll
    static void tearDown() {
        throw new IllegalStateException("cleanup failed");
    }

    @Test
    void ok() {
    }
}

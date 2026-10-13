package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The same as {@link AfterAllFailureWithDisabledTestsFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtAfterAllFailureWithDisabledTestsFixture {

    @AfterAll
    static void tearDown() {
        throw new IllegalStateException("cleanup failed");
    }

    @Test
    @Disabled("not ready")
    void disabled() {
    }
}

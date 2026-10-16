package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A class whose {@code @AfterAll} aborts with a failed assumption, after its test passed.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtAbortedClassTeardownFixture {

    @AfterAll
    static void cleanUpOnlyOnCi() {
        assumeTrue(false, "not on CI");
    }

    @Test
    void passes() {
    }
}

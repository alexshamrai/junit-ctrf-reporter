package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The same as {@link SetupAlwaysFailsFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtSetupAlwaysFailsFixture {

    @BeforeAll
    static void connect() {
        throw new IllegalStateException("setup fails in every round");
    }

    @Test
    void works() {
        // never runs
    }
}

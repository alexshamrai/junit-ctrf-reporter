package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The same as {@link TeardownAlwaysFailsFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtTeardownAlwaysFailsFixture {

    @AfterAll
    static void disconnect() {
        throw new IllegalStateException("teardown fails in every round");
    }

    @Test
    void works() {
        // passes in every round
    }
}

package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A class whose {@code @BeforeAll} aborts with a failed assumption, so JUnit skips its tests.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtAbortedClassSetupFixture {

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(false, "no database on this agent");
    }

    @Test
    void readsOrders() {
    }

    @Test
    void writesOrders() {
    }
}

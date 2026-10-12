package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The same as {@link AssumptionFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtAssumptionFixture {

    @Test
    void requiresDocker() {
        assumeTrue(false, "Docker not available on this agent");
    }

    @Test
    void passes() {
    }
}

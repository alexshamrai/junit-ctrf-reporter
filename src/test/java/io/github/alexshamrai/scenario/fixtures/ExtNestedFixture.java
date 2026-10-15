package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The class a nested JUnit run runs (CB-2), with the extension. None of its tests may reach the outer run's report.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtNestedFixture {

    @Test
    void nestedPasses() {
    }

    @Test
    void nestedFails() {
        fail("a nested run's failure must not reach the outer report");
    }
}

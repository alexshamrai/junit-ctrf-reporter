package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The class after the nested JUnit run (CB-2), reporting through the extension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtNestedRunCFixture {

    @Test
    void third() {
    }

    @Test
    void fourth() {
    }
}

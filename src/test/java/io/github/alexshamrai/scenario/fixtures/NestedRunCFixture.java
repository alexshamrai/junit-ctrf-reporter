package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The class after the nested JUnit run (CB-2): its tests were lost when the nested run ended the outer one.
 */
@Tag("scenario-fixture")
public class NestedRunCFixture {

    @Test
    void third() {
    }

    @Test
    void fourth() {
    }
}

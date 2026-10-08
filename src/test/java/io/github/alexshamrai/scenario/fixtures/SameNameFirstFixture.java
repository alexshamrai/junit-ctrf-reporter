package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A passing test whose method name also occurs in {@link SameNameSecondFixture}.
 */
@Tag("scenario-fixture")
public class SameNameFirstFixture {

    @Test
    void common() {
    }
}

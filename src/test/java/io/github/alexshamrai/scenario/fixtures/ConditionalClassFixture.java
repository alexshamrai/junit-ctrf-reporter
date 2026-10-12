package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * A whole class that a class-level condition disables (the property is never set).
 */
@Tag("scenario-fixture")
@EnabledIfSystemProperty(named = "ctrf.scenario.never.set", matches = "true")
public class ConditionalClassFixture {

    @Test
    void one() {
    }

    @Test
    void two() {
    }
}

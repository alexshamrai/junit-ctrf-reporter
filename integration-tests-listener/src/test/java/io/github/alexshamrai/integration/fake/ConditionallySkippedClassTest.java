package io.github.alexshamrai.integration.fake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * A whole class that a class-level condition disables (the property is never set).
 * CtrfListener must report each of its tests as skipped.
 */
@EnabledIfSystemProperty(named = "ctrf.integration.never.set", matches = "true")
public class ConditionallySkippedClassTest {

    @Test
    void firstTestOfSkippedClass() {
        System.out.println("This should never be printed");
    }

    @Test
    void secondTestOfSkippedClass() {
        System.out.println("This should never be printed");
    }
}

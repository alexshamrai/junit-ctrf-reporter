package io.github.alexshamrai.integration.fake;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * A whole class that is disabled. JUnit reports only the class as skipped;
 * CtrfListener must report each of its tests as skipped.
 */
@Disabled("Simulated disabled class")
public class DisabledClassTest {

    @Test
    void firstTestOfDisabledClass() {
        System.out.println("This should never be printed");
    }

    @Test
    void secondTestOfDisabledClass() {
        System.out.println("This should never be printed");
    }
}

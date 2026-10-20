package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A failing and a passing test, both printing to standard output and standard error (HP-13).
 */
@Tag("scenario-fixture")
public class OutputFixture {

    @Test
    void failsAfterPrinting() {
        System.out.println("out 1");
        System.out.println("out 2");
        System.err.println("err 1");
        fail("fails after printing");
    }

    @Test
    void passesAfterPrinting() {
        System.out.println("passing out");
        System.err.println("passing err");
    }
}

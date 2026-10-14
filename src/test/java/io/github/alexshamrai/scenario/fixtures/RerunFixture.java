package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A test that fails only on its first call in a JVM, for reruns in the same JVM, and a stable one.
 */
@Tag("scenario-fixture")
public class RerunFixture {

    private static final AtomicInteger FLAKY_CALLS = new AtomicInteger();

    @Test
    void flakyOnce() {
        if (FLAKY_CALLS.incrementAndGet() == 1) {
            fail("fails on its first call");
        }
    }

    @Test
    void stable() {
        // passes
    }
}

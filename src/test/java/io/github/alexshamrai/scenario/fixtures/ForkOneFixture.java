package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * One of three fixtures that run in parallel JVMs of one run. Its last test waits for the other JVMs, so all
 * three finish together.
 */
@Tag("scenario-fixture")
public class ForkOneFixture {

    @Test
    void failsInThisFork() {
        fail("fails in fork one");
    }

    @Test
    void waitsForTheOtherForks() throws Exception {
        ForkBarrier.await("fork-one", 3);
    }
}

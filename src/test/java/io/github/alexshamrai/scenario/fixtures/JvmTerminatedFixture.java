package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Its second test waits until the scenario terminates the JVM, as a build tool does when a fork times out, so the
 * third never starts (CB-8).
 */
@Tag("scenario-fixture")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JvmTerminatedFixture {

    /**
     * Printed when the second test starts waiting, so that the scenario knows when to terminate the JVM.
     */
    public static final String WAITING = "JvmTerminatedFixture is waiting to be terminated";

    @Test
    @Order(1)
    void passes() {
    }

    @Test
    @Order(2)
    void waitsToBeTerminated() throws InterruptedException {
        System.out.println(WAITING);
        Thread.sleep(120_000);
    }

    @Test
    @Order(3)
    void neverStarts() {
    }
}

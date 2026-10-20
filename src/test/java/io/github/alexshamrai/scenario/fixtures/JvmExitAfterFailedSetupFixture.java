package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;

/**
 * A class whose setup fails, so that JUnit never runs its test, and then a class that ends the JVM with
 * {@code System.exit(3)} (CB-8).
 */
@Tag("scenario-fixture")
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
public class JvmExitAfterFailedSetupFixture {

    @Nested
    @Order(1)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class SetupFails {

        @BeforeAll
        void connect() {
            throw new IllegalStateException("setup fails");
        }

        @Test
        void neverRunsBecauseItsSetupFailed() {
        }
    }

    @Nested
    @Order(2)
    class ExitsLater {

        @Test
        void exitsTheJvm() {
            System.exit(3);
        }
    }
}

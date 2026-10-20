package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Its second test ends the JVM with {@code System.exit(3)}, as a test might by mistake, so the tests after it never
 * start (CB-8).
 */
@Tag("scenario-fixture")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JvmExitFixture {

    @Test
    @Order(1)
    void passes() {
    }

    @Test
    @Order(2)
    void exitsTheJvm() {
        System.exit(3);
    }

    @Test
    @Order(3)
    void neverStarts() {
    }

    @ParameterizedTest
    @Order(4)
    @ValueSource(ints = {1, 2})
    void neverStartsEither(int value) {
    }

    @Nested
    class Later {

        @Test
        void neverStartsInANestedClass() {
        }
    }
}

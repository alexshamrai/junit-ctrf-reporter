package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

/**
 * Tests at every depth that {@code suite} describes (HP-12): directly in a class with a display name, in a
 * {@code @Nested} class, parameterized, repeated and dynamic, and a nested class whose setup fails.
 */
@Tag("scenario-fixture")
@DisplayName("Payments")
public class SuiteFixture {

    @Test
    void charges() {
    }

    @TestFactory
    Stream<DynamicNode> generated() {
        return Stream.of(DynamicContainer.dynamicContainer("group",
            Stream.of(DynamicTest.dynamicTest("generated test", () -> { }))));
    }

    @Nested
    class Refunds {

        @Test
        void refundsTheFee() {
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        void refundsEachAmount(int amount) {
        }

        @RepeatedTest(2)
        void refundsTwice() {
        }
    }

    @Nested
    class Broken {

        @BeforeAll
        static void connect() {
            throw new IllegalStateException("setup of a nested class fails");
        }

        @Test
        void neverRuns() {
        }
    }
}

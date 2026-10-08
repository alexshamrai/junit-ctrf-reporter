package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Two containers below class level that fail before producing any test.
 */
@Tag("scenario-fixture")
public class BrokenSourceFixture {

    static Stream<Integer> data() {
        throw new IllegalStateException("cannot load test data");
    }

    @ParameterizedTest
    @MethodSource("data")
    void usesData(int value) {
    }

    @TestFactory
    Stream<DynamicTest> factory() {
        throw new IllegalStateException("factory exploded");
    }
}

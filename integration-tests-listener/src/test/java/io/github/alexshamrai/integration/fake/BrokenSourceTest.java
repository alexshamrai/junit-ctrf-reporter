package io.github.alexshamrai.integration.fake;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Containers below class level that fail before producing any test.
 * Verifies that CtrfListener reports them as failed entries.
 */
public class BrokenSourceTest {

    static Stream<Integer> data() {
        throw new IllegalStateException("Simulated failure - test data could not be loaded");
    }

    @ParameterizedTest
    @MethodSource("data")
    void usesData(int value) {
        System.out.println("This should never be printed");
    }

    @TestFactory
    Stream<DynamicTest> factory() {
        throw new IllegalStateException("Simulated failure - dynamic tests could not be created");
    }
}

package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A test method whose source fails, in a class that an {@code @AfterAll} assumption aborts afterwards.
 */
@Tag("scenario-fixture")
public class AbortedAfterContainerFailedFixture {

    @AfterAll
    static void requireCleanTeardown() {
        assumeTrue(false, "teardown assumption failed");
    }

    static Stream<Integer> data() {
        throw new IllegalStateException("cannot load test data");
    }

    @ParameterizedTest
    @MethodSource("data")
    void usesData(int value) {
    }
}

package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test methods that ran without creating any test, in a class that an {@code @AfterAll} assumption aborts afterwards.
 */
@Tag("scenario-fixture")
public class AbortedAfterTemplateMethodsRanFixture {

    @AfterAll
    static void requireCleanTeardown() {
        assumeTrue(false, "teardown assumption failed");
    }

    @TestFactory
    Stream<DynamicTest> emptyFactory() {
        return Stream.empty();
    }

    @ParameterizedTest
    @MethodSource("noArguments")
    void withoutArguments(int value) {
    }

    static Stream<Integer> noArguments() {
        return Stream.empty();
    }
}

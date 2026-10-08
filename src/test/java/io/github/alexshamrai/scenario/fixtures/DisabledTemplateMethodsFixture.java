package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

/**
 * Disabled test methods whose tests JUnit creates only while running them, so the test plan has none of them.
 */
@Tag("scenario-fixture")
public class DisabledTemplateMethodsFixture {

    @Test
    void runs() {
    }

    @Disabled("parameterized test disabled")
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void param(int value) {
    }

    @Disabled("repeated test disabled")
    @RepeatedTest(2)
    void repeated() {
    }

    @Disabled("test factory disabled")
    @TestFactory
    Stream<DynamicTest> factory() {
        return Stream.empty();
    }
}

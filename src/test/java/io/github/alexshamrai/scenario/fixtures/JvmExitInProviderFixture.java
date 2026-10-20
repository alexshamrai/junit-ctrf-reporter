package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * A parameterized test whose {@code @MethodSource} ends the JVM with {@code System.exit(3)}, before any of its tests
 * exists (CB-8).
 */
@Tag("scenario-fixture")
public class JvmExitInProviderFixture {

    @ParameterizedTest
    @MethodSource("data")
    void usesData(int value) {
    }

    static Stream<Integer> data() {
        System.exit(3);
        return Stream.of(1);
    }
}

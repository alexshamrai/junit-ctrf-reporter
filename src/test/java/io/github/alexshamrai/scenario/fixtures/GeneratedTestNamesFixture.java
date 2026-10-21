package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that test methods create, and an ordinary one (MP-13). {@code add} and {@code subtract} have invocations of the
 * same display names. The first invocation of {@code add} fails only in the first round in a working directory, and
 * the {@code broken group} container fails in every round.
 */
@Tag("scenario-fixture")
public class GeneratedTestNamesFixture {

    @Test
    void ordinary() {
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void add(int value) throws IOException {
        Path marker = Path.of("first-round-done.marker");
        if (value == 1 && Files.notExists(marker)) {
            Files.createFile(marker);
            fail("fails in the first round");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void subtract(int value) {
    }

    @ParameterizedTest(name = "{displayName} with {0}")
    @ValueSource(ints = 1)
    void named(int value) {
    }

    @RepeatedTest(2)
    void repeated() {
    }

    @TestFactory
    Stream<DynamicNode> factory() {
        Stream<DynamicNode> broken = Stream.of("never created")
            .map(name -> {
                throw new IllegalStateException("cannot create the group's tests");
            });
        return Stream.of(
            dynamicTest("direct", () -> { }),
            dynamicContainer("group", Stream.of(dynamicTest("inner", () -> { }))),
            dynamicContainer("broken group", broken));
    }
}

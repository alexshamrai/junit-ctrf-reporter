package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A test that prints its round, and fails only in the first round in a working directory (HP-13).
 */
@Tag("scenario-fixture")
public class OutputRetryFixture {

    @Test
    void printsAndPassesFromTheSecondRound() throws IOException {
        Path marker = Path.of("first-round-done.marker");
        boolean firstRound = Files.notExists(marker);
        System.out.println(firstRound ? "first round" : "second round");
        if (firstRound) {
            Files.createFile(marker);
            fail("fails in the first round");
        }
    }
}

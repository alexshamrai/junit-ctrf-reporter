package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for retry rounds: one fails only in the first round in a working directory, one fails in every round.
 */
@Tag("scenario-fixture")
public class RetryRoundsFixture {

    @Test
    void passesFromTheSecondRound() throws IOException {
        Path marker = Path.of("first-round-done.marker");
        if (Files.notExists(marker)) {
            Files.createFile(marker);
            fail("fails in the first round");
        }
    }

    @Test
    void alwaysFails() {
        fail("fails in every round");
    }
}

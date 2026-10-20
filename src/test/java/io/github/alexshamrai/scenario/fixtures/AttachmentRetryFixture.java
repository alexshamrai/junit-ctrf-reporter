package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A test that publishes the same file in every round, saying which round wrote it, and fails only in the first round
 * in a working directory (MP-10).
 */
@Tag("scenario-fixture")
public class AttachmentRetryFixture {

    @Test
    void publishesAFileAndPassesFromTheSecondRound(TestReporter reporter) throws IOException {
        Path marker = Path.of("first-round-done.marker");
        boolean firstRound = Files.notExists(marker);
        String round = firstRound ? "first round" : "later round";
        reporter.publishFile("round.txt", MediaType.TEXT_PLAIN_UTF_8, path -> Files.writeString(path, round));
        if (firstRound) {
            Files.createFile(marker);
            fail("fails in the first round");
        }
    }
}

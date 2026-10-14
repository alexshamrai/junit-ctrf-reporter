package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The same as {@link RetryRoundsFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtRetryRoundsFixture {

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

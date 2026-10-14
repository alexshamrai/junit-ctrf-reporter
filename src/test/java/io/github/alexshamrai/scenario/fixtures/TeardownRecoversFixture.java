package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A class whose {@code @AfterAll} fails in the first round in a working directory and passes afterwards.
 */
@Tag("scenario-fixture")
public class TeardownRecoversFixture {

    @AfterAll
    static void disconnect() throws IOException {
        Path marker = Path.of("teardown-recovers.marker");
        if (Files.notExists(marker)) {
            Files.createFile(marker);
            throw new IllegalStateException("teardown fails in the first round");
        }
    }

    @Test
    void works() {
        // passes in every round
    }
}

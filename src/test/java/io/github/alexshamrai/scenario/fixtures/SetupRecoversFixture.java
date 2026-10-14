package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A class whose {@code @BeforeAll} fails in the first round in a working directory and passes afterwards.
 */
@Tag("scenario-fixture")
public class SetupRecoversFixture {

    @BeforeAll
    static void connect() throws IOException {
        Path marker = Path.of("setup-recovers.marker");
        if (Files.notExists(marker)) {
            Files.createFile(marker);
            throw new IllegalStateException("setup fails in the first round");
        }
    }

    @Test
    void works() {
        // passes once the class is set up
    }
}

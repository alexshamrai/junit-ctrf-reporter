package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The same as {@link TeardownRecoversFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtTeardownRecoversFixture {

    @AfterAll
    static void disconnect() throws IOException {
        Path marker = Path.of("ext-teardown-recovers.marker");
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

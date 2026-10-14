package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The same as {@link SetupRecoversFixture}, reported through CtrfExtension.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtSetupRecoversFixture {

    @BeforeAll
    static void connect() throws IOException {
        Path marker = Path.of("ext-setup-recovers.marker");
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

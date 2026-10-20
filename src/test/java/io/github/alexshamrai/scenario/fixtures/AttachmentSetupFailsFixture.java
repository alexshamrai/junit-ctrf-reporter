package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.nio.file.Files;

/**
 * A class whose {@code @BeforeAll} publishes a file, then fails (MP-10).
 */
@Tag("scenario-fixture")
public class AttachmentSetupFailsFixture {

    @BeforeAll
    static void connect(TestReporter reporter) {
        reporter.publishFile("setup.log", MediaType.TEXT_PLAIN_UTF_8, path -> Files.writeString(path, "connecting"));
        throw new IllegalStateException("setup fails");
    }

    @Test
    void neverRuns() {
    }
}

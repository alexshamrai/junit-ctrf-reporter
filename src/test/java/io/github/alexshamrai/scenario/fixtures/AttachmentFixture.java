package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that publish a file, then pass or fail, and one that publishes a directory (MP-10).
 */
@Tag("scenario-fixture")
public class AttachmentFixture {

    @Test
    void publishesAFileAndPasses(TestReporter reporter) {
        reporter.publishFile("note.txt", MediaType.TEXT_PLAIN_UTF_8, path -> Files.writeString(path, "a note"));
    }

    @Test
    void publishesAFileAndFails(TestReporter reporter) {
        reporter.publishFile("screen.png", MediaType.IMAGE_PNG, path -> Files.write(path, new byte[] {1}));
        fail("fails after publishing a file");
    }

    @Test
    void publishesADirectory(TestReporter reporter) {
        reporter.publishDirectory("screens", directory -> Files.writeString(directory.resolve("one.txt"), "one"));
    }
}

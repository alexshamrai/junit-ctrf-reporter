package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A test that fails, and an extension that publishes a screenshot of every failed test, as UI tests do (MP-10).
 */
@Tag("scenario-fixture")
@ExtendWith(AttachmentOnFailureFixture.ScreenshotOnFailure.class)
public class AttachmentOnFailureFixture {

    @Test
    void fails() {
        fail("the page shows an error");
    }

    /**
     * Publishes a screenshot when a test fails.
     */
    public static class ScreenshotOnFailure implements TestWatcher {

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            context.publishFile("failure.png", MediaType.IMAGE_PNG, path -> Files.write(path, new byte[] {1}));
        }
    }
}

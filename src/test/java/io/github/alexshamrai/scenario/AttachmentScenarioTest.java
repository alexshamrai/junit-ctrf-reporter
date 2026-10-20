package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.Attachment;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.scenario.ScenarioRunner.Jvm;
import io.github.alexshamrai.scenario.fixtures.AttachmentFixture;
import io.github.alexshamrai.scenario.fixtures.AttachmentOnFailureFixture;
import io.github.alexshamrai.scenario.fixtures.AttachmentRetryFixture;
import io.github.alexshamrai.scenario.fixtures.AttachmentSetupFailsFixture;
import org.junit.jupiter.api.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A file that a test publishes with {@code TestReporter.publishFile} is attached to it, whether it passed or failed,
 * by its absolute path; a published directory isn't (MP-10).
 */
class AttachmentScenarioTest {

    @org.junit.jupiter.api.Test
    void publishedFilesAreAttachedToTheirTests() throws Exception {
        var result = ScenarioRunner.runWithListener(AttachmentFixture.class);

        assertAttached(named(result, "publishesAFileAndPasses(TestReporter)").getAttachments(), "note.txt",
            MediaType.TEXT_PLAIN_UTF_8.toString());
        assertAttached(named(result, "publishesAFileAndFails(TestReporter)").getAttachments(), "screen.png",
            MediaType.IMAGE_PNG.toString());
        assertThat(named(result, "publishesADirectory(TestReporter)").getAttachments()).isNull();
    }

    @org.junit.jupiter.api.Test
    void eachAttemptKeepsItsOwnFileWhenEachJvmWritesToItsOwnDirectory() throws Exception {
        // JUnit writes a test's files to the same directory in every attempt, unless the output directory has
        // {uniqueNumber}. In the third round, the first round's attachment is read back from the report
        var round = Jvm.listener(AttachmentRetryFixture.class)
            .withSystemProperty("junit.platform.reporting.output.dir", "junit-{uniqueNumber}");
        var result = ScenarioRunner.runOneAfterAnother(round, round, round);

        Test test = named(result, "publishesAFileAndPassesFromTheSecondRound(TestReporter)");
        List<Attachment> attempts = new ArrayList<>();
        test.getRetryAttempts().forEach(attempt -> attempts.add(onlyAttachment(attempt.getAttachments())));
        attempts.add(onlyAttachment(test.getAttachments()));
        assertThat(attempts).extracting(Attachment::getPath).doesNotHaveDuplicates();
        assertThat(attempts).extracting(attachment -> Files.readString(Path.of(attachment.getPath())))
            .containsExactly("first round", "later round", "later round");
    }

    @org.junit.jupiter.api.Test
    void fileThatAClassPublishedBeforeItsSetupFailedIsAttachedToItsInitializationError() throws Exception {
        var result = ScenarioRunner.runWithListener(AttachmentSetupFailsFixture.class);

        assertAttached(named(result, "initializationError").getAttachments(), "setup.log", MediaType.TEXT_PLAIN_UTF_8.toString());
    }

    @org.junit.jupiter.api.Test
    void screenshotThatATestWatcherPublishesForAFailedTestIsAttachedToIt() throws Exception {
        var result = ScenarioRunner.runWithListener(AttachmentOnFailureFixture.class);

        assertAttached(named(result, "fails()").getAttachments(), "failure.png", MediaType.IMAGE_PNG.toString());
    }

    private static Attachment onlyAttachment(List<Attachment> attachments) {
        assertThat(attachments).hasSize(1);
        return attachments.get(0);
    }

    private static void assertAttached(List<Attachment> attachments, String name, String contentType) {
        assertThat(attachments).singleElement().satisfies(attachment -> {
            assertThat(attachment.getName()).isEqualTo(name);
            assertThat(attachment.getContentType()).isEqualTo(contentType);
            assertThat(Path.of(attachment.getPath())).isAbsolute().exists().hasFileName(name);
        });
    }

    private static Test named(ScenarioRunner.Result result, String name) {
        var matches = result.tests().stream().filter(test -> name.equals(test.getName())).toList();
        assertThat(matches).as("entries named %s", name).hasSize(1);
        return matches.get(0);
    }
}

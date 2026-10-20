package io.github.alexshamrai.integration.fake;

import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.nio.file.Files;

@Tag("dummy")
public class DummySuccessTest extends BaseFakeTest {

    @Test
    void firstSuccessTest(TestReporter reporter) {
        System.out.println("DummySuccessTest firstSuccessTest()");
        reporter.publishFile("dummy-success.txt", MediaType.TEXT_PLAIN_UTF_8, path -> Files.writeString(path, "passed"));
    }

    @Test
    void secondSuccessTest() {
        System.out.println("DummySuccessTest secondSuccessTest()");
    }
}
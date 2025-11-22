package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class TestProcessorTest {

    @Mock
    private ConfigReader configReader;

    private TestProcessor testProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testProcessor = new TestProcessor(configReader);
    }

    @org.junit.jupiter.api.Test
    void setFailureDetails_withShortMessage_setsFullMessage() {
        var test = Test.builder().build();
        var exception = new RuntimeException("Short test exception");
        when(configReader.getMaxMessageLength()).thenReturn(1000);

        testProcessor.setFailureDetails(test, exception);

        assertNotNull(test.getMessage());
        assertNotNull(test.getTrace());
        assertTrue(test.getMessage().contains("Short test exception"));
        assertTrue(test.getTrace().contains("Short test exception"));
    }

    @org.junit.jupiter.api.Test
    void setFailureDetails_withLongMessage_truncatesMessage() {
        var test = Test.builder().build();
        var longMessage = "Long test exception: " + "X".repeat(1000);
        var exception = new RuntimeException(longMessage);
        when(configReader.getMaxMessageLength()).thenReturn(50);

        testProcessor.setFailureDetails(test, exception);

        assertNotNull(test.getMessage());
        assertNotNull(test.getTrace());
        assertTrue(test.getMessage().length() <= 53); // 50 chars + "..."
        assertTrue(test.getMessage().endsWith("..."));
        assertTrue(test.getTrace().length() > test.getMessage().length());
    }

    @org.junit.jupiter.api.Test
    void createTest_withValidDetails_createsTestObject() {
        var displayName = "Test Display Name";
        var tags = new HashSet<>(Arrays.asList("tag1", "tag2"));
        var filePath = "path/to/test/file.java";
        long startTime = System.currentTimeMillis();
        long stopTime = startTime + 1000;

        var details = new TestDetails(startTime, tags, filePath, null, null);

        var result = testProcessor.createTest(displayName, details, stopTime);

        assertNotNull(result);
        assertEquals(displayName, result.getName());
        assertEquals(new ArrayList<>(tags), result.getTags());
        assertEquals(filePath, result.getFilepath());
        assertEquals(startTime, result.getStart());
        assertEquals(stopTime, result.getStop());
        assertEquals(1000, result.getDuration());
        assertNotNull(result.getThreadId());
        assertEquals(Thread.currentThread().getName(), result.getThreadId());
    }
}
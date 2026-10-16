package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals(filePath, result.getFilePath());
        assertEquals(startTime, result.getStart());
        assertEquals(stopTime, result.getStop());
        assertEquals(1000, result.getDuration());
        assertNotNull(result.getThreadId());
        assertEquals(Thread.currentThread().getName(), result.getThreadId());
    }

    @org.junit.jupiter.api.Test
    void createTest_usesUniqueIdAsTestId() {
        var uniqueId = "[engine:junit-jupiter]/[class:com.example.ATest]/[method:test1()]";
        var details = new TestDetails(1_000L, Set.of(), "com.example.ATest", uniqueId, "test1()");

        var result = testProcessor.createTest("test1()", details, 2_000L);

        assertEquals(uniqueId, result.getTestId());
    }

    @org.junit.jupiter.api.Test
    void message_isTheExceptionClassAndMessage() {
        var test = Test.builder().build();
        when(configReader.getMaxMessageLength()).thenReturn(500);

        testProcessor.setFailureDetails(test, new IllegalStateException("broken"));

        assertEquals("java.lang.IllegalStateException: broken", test.getMessage());
    }

    @org.junit.jupiter.api.Test
    void message_ofAnExceptionWithoutAMessage_isItsClassName() {
        var test = Test.builder().build();
        when(configReader.getMaxMessageLength()).thenReturn(500);

        testProcessor.setFailureDetails(test, new IllegalStateException());

        assertEquals("java.lang.IllegalStateException", test.getMessage());
    }

    @org.junit.jupiter.api.Test
    void message_ofAnExceptionWithACause_leavesTheCauseToTheTrace() {
        var test = Test.builder().build();
        when(configReader.getMaxMessageLength()).thenReturn(500);

        testProcessor.setFailureDetails(test, new RuntimeException("outer", new IOException("inner")));

        assertEquals("java.lang.RuntimeException: outer", test.getMessage());
        assertTrue(test.getTrace().contains("Caused by: java.io.IOException: inner"));
    }

    @org.junit.jupiter.api.Test
    void message_longerThanTheLimit_isCutAndEndsWithDots() {
        var test = Test.builder().build();
        when(configReader.getMaxMessageLength()).thenReturn(20);

        testProcessor.setFailureDetails(test, new IllegalStateException("a long explanation"));

        assertEquals("java.lang.IllegalSta...", test.getMessage());
    }

    @org.junit.jupiter.api.Test
    void trace_underTheLimit_isTheWholeStackTrace() {
        var test = Test.builder().build();
        var exception = new IllegalStateException("broken");
        when(configReader.getMaxMessageLength()).thenReturn(500);
        when(configReader.getMaxTraceLength()).thenReturn(1_000_000);

        testProcessor.setFailureDetails(test, exception);

        assertEquals(printed(exception), test.getTrace());
    }

    @org.junit.jupiter.api.Test
    void trace_withLimitZero_isNeverCut() {
        var test = Test.builder().build();
        var exception = chainOfCauses(5);
        when(configReader.getMaxMessageLength()).thenReturn(500);
        when(configReader.getMaxTraceLength()).thenReturn(0);

        testProcessor.setFailureDetails(test, exception);

        assertEquals(printed(exception), test.getTrace());
    }

    @org.junit.jupiter.api.Test
    void trace_overTheLimit_keepsWholeLinesTheMarkerAndEveryCausedByLine() {
        var test = Test.builder().build();
        var exception = chainOfCauses(5);
        String full = printed(exception);
        int limit = 400;
        when(configReader.getMaxMessageLength()).thenReturn(500);
        when(configReader.getMaxTraceLength()).thenReturn(limit);

        testProcessor.setFailureDetails(test, exception);

        List<String> fullLines = full.lines().toList();
        List<String> lines = test.getTrace().lines().toList();
        int marker = lines.indexOf(lines.stream().filter(line -> line.startsWith("... ")).findFirst().orElseThrow());
        int headLength = fullLines.subList(0, marker).stream().mapToInt(line -> line.length() + System.lineSeparator().length()).sum();
        assertEquals(fullLines.subList(0, marker), lines.subList(0, marker));
        assertTrue(headLength <= limit);
        assertTrue(headLength + fullLines.get(marker).length() + System.lineSeparator().length() > limit);
        assertEquals("... " + (full.length() - headLength) + " characters truncated (ctrf.max.trace.length=" + limit + ")",
            lines.get(marker));
        assertEquals(causedByLines(fullLines.subList(marker, fullLines.size())), lines.subList(marker + 1, lines.size()));
        assertEquals(5, causedByLines(lines).size());
    }

    @org.junit.jupiter.api.Test
    void trace_whoseFirstLineIsLongerThanTheLimit_isCutAtTheLimit() {
        var test = Test.builder().build();
        var exception = new IllegalStateException("x".repeat(500));
        String full = printed(exception);
        when(configReader.getMaxMessageLength()).thenReturn(500);
        when(configReader.getMaxTraceLength()).thenReturn(100);

        testProcessor.setFailureDetails(test, exception);

        assertTrue(test.getTrace().startsWith(full.substring(0, 100) + System.lineSeparator()
            + "... " + (full.length() - 100) + " characters truncated (ctrf.max.trace.length=100)" + System.lineSeparator()));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Review Focus 4: a failure whose toString() throws is still recorded, by its class name")
    void failureWhoseToStringThrowsIsStillRecorded() {
        var test = Test.builder().build();
        var exception = new IllegalStateException() {
            @Override
            public String getMessage() {
                throw new UnsupportedOperationException("the message is gone");
            }
        };
        when(configReader.getMaxMessageLength()).thenReturn(500);

        testProcessor.setFailureDetails(test, exception);

        assertEquals(exception.getClass().getName(), test.getMessage());
        assertEquals(exception.getClass().getName(), test.getTrace());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A failure whose toString() throws an Error is still recorded, by its class name")
    void failureWhoseToStringThrowsAnErrorIsStillRecorded() {
        var test = Test.builder().build();
        var exception = new IllegalStateException() {
            @Override
            public String toString() {
                throw new AssertionError("toString is broken");
            }
        };
        when(configReader.getMaxMessageLength()).thenReturn(500);

        testProcessor.setFailureDetails(test, exception);

        assertEquals(exception.getClass().getName(), test.getMessage());
        assertEquals(exception.getClass().getName(), test.getTrace());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A VirtualMachineError from a failure's toString() isn't swallowed")
    void virtualMachineErrorFromAFailuresToStringPropagates() {
        var test = Test.builder().build();
        var exception = new IllegalStateException() {
            @Override
            public String toString() {
                throw new OutOfMemoryError("no memory for the message");
            }
        };
        when(configReader.getMaxMessageLength()).thenReturn(500);

        assertThrows(OutOfMemoryError.class, () -> testProcessor.setFailureDetails(test, exception));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Review Focus 5: a trace with Windows line endings is cut after a whole line, and keeps them")
    void capTraceKeepsWholeWindowsLines() {
        String first = "java.lang.RuntimeException: outer\r\n";
        String second = "\tat com.example.A.a(A.java:1)\r\n";
        String third = "\tat com.example.B.b(B.java:2)\r\n";
        String cause = "Caused by: java.io.IOException: inner\r\n";
        String fourth = "\tat com.example.C.c(C.java:3)\r\n";
        String trace = first + second + third + cause + fourth;
        int limit = first.length() + second.length() + 5;

        String capped = TestProcessor.capTrace(trace, limit);

        int cut = trace.length() - first.length() - second.length();
        assertEquals(first + second + "... " + cut + " characters truncated (ctrf.max.trace.length=" + limit + ")\r\n" + cause, capped);
    }

    private static Throwable chainOfCauses(int causes) {
        Throwable failure = new IllegalArgumentException("root cause");
        for (int i = 1; i <= causes; i++) {
            failure = new IllegalStateException("wrapper " + i, failure);
        }
        return failure;
    }

    private static String printed(Throwable throwable) {
        var writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static List<String> causedByLines(List<String> lines) {
        return lines.stream().filter(line -> line.strip().startsWith("Caused by:")).toList();
    }
}

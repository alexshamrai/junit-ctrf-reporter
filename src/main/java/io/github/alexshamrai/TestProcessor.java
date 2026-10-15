package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import lombok.RequiredArgsConstructor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

@RequiredArgsConstructor
public class TestProcessor {

    private static final String CAUSED_BY = "Caused by:";

    private final ConfigReader configReader;

    /**
     * Sets a failed test's {@code message} and {@code trace}.
     * <p>
     * The message is the failure's {@code toString()}: its class and message. Longer than
     * {@code ctrf.max.message.length} characters, it is cut and ends with "...". The trace is the printed stack trace;
     * longer than {@code ctrf.max.trace.length} characters (0 means no limit), it keeps the whole lines that fit, a line
     * saying how much was cut, and every "Caused by:" line of the part that was cut.
     *
     * @param test  the failed test
     * @param cause the failure
     */
    public void setFailureDetails(Test test, Throwable cause) {
        test.setMessage(truncate(describe(cause), configReader.getMaxMessageLength()));
        test.setTrace(capTrace(stackTraceOf(cause), configReader.getMaxTraceLength()));
    }

    /**
     * Creates a new Test object with details from the test execution.
     *
     * <p>This method is now framework-agnostic and uses the provided details to build
     * a CTRF-compliant Test object.</p>
     *
     * @param displayName the name of the test to be displayed in the report
     * @param details     the test details gathered during execution (start time, tags, etc.)
     * @param stopTime    the timestamp when the test completed
     * @return a fully populated Test object
     */
    public Test createTest(String displayName, TestDetails details, long stopTime) {
        return Test.builder()
            .testId(details.uniqueId())
            .name(displayName) // Use the displayName parameter
            .tags(details.tags() != null ? new ArrayList<>(details.tags()) : new ArrayList<>())
            .filePath(details.filePath())
            .start(details.startTime())
            .stop(stopTime)
            .duration(stopTime - details.startTime())
            .threadId(Thread.currentThread().getName())
            .build();
    }

    /**
     * Caps a printed stack trace at {@code maxLength} characters, 0 meaning no limit. A longer trace keeps the whole
     * lines that fit, then a line saying how many characters were cut, then every "Caused by:" line of the cut part,
     * so that the root cause stays visible. A line longer than the limit, whether the first line or a "Caused by:"
     * line, is cut at the limit. The added lines end like the trace's own, with "\r\n" or "\n".
     *
     * @param trace     the printed stack trace
     * @param maxLength the limit, or 0
     * @return the trace, capped
     */
    static String capTrace(String trace, int maxLength) {
        if (maxLength == 0 || trace.length() <= maxLength) {
            return trace;
        }
        String newline = trace.contains("\r\n") ? "\r\n" : "\n";
        int headEnd = trace.lastIndexOf('\n', maxLength - 1) + 1;
        String head = headEnd > 0 ? trace.substring(0, headEnd) : trace.substring(0, maxLength) + newline;
        String cut = trace.substring(headEnd > 0 ? headEnd : maxLength);
        var capped = new StringBuilder(head)
            .append("... ").append(cut.length()).append(" characters truncated (ctrf.max.trace.length=")
            .append(maxLength).append(')').append(newline);
        cut.lines()
            .filter(line -> line.strip().startsWith(CAUSED_BY))
            .forEach(line -> capped.append(line.length() > maxLength ? line.substring(0, maxLength) : line).append(newline));
        return capped.toString();
    }

    private static String truncate(String message, int maxLength) {
        return message.length() > maxLength ? message.substring(0, maxLength) + "..." : message;
    }

    // A failure whose toString() throws or returns null is still reported, by its class name
    private static String describe(Throwable cause) {
        try {
            String text = cause.toString();
            return text != null ? text : cause.getClass().getName();
        } catch (RuntimeException e) {
            return cause.getClass().getName();
        }
    }

    // printStackTrace starts with the failure's toString(), so it throws when that throws
    private static String stackTraceOf(Throwable cause) {
        var writer = new StringWriter();
        try {
            cause.printStackTrace(new PrintWriter(writer));
        } catch (RuntimeException e) {
            return describe(cause);
        }
        return writer.toString();
    }
}

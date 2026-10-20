package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import lombok.RequiredArgsConstructor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a finished test into a CTRF test object: its name, status, timing, tags, suite and, for a failure, its
 * {@code message} and {@code trace}, cut to the configured lengths.
 */
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
     * Sets a test's {@code stdout} and {@code stderr} from the output JUnit captured, as lines. Output longer than
     * {@code ctrf.max.output.length} characters (0 means no limit) keeps its end, where a failure shows: a line saying
     * how much was cut, then the whole lines that fit.
     *
     * @param test   the test
     * @param stdout the captured standard output, or {@code null}
     * @param stderr the captured standard error, or {@code null}
     */
    public void setOutput(Test test, String stdout, String stderr) {
        int maxLength = configReader.getMaxOutputLength();
        test.setStdout(linesOf(stdout, maxLength));
        test.setStderr(linesOf(stderr, maxLength));
    }

    /**
     * Splits captured output into lines, keeping its last {@code maxLength} characters at most, 0 meaning no limit; a
     * line ending at the very end doesn't count. The kept part starts at a line, unless the last line alone is longer
     * than the limit; then its end is kept, without splitting a character.
     *
     * @param output    the captured output, or {@code null}
     * @param maxLength the limit, or 0
     * @return the lines, after a line saying how much was cut if any was; {@code null} for no output
     */
    static List<String> linesOf(String output, int maxLength) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        String text = withoutFinalLineEnding(output);
        if (maxLength == 0 || text.length() <= maxLength) {
            return output.lines().toList();
        }
        int newline = text.indexOf('\n', text.length() - maxLength - 1);
        int keptFrom = newline >= 0 && newline < text.length() - 1 ? newline + 1 : text.length() - maxLength;
        if (Character.isLowSurrogate(text.charAt(keptFrom))) {
            keptFrom++;
        }
        List<String> lines = new ArrayList<>();
        lines.add("... " + keptFrom + " characters truncated (ctrf.max.output.length=" + maxLength + ")");
        text.substring(keptFrom).lines().forEach(lines::add);
        return lines;
    }

    private static String withoutFinalLineEnding(String output) {
        int end = output.length();
        if (end > 0 && output.charAt(end - 1) == '\n') {
            end--;
        }
        if (end > 0 && output.charAt(end - 1) == '\r') {
            end--;
        }
        return output.substring(0, end);
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
            .suite(details.suite().isEmpty() ? null : new ArrayList<>(details.suite()))
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
     * line, is cut at the limit, but never between the "\r" and the "\n" of a line ending. The added lines end like
     * the trace's own, with "\r\n" or "\n".
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
        // A first line cut at the limit doesn't keep the "\r" of its line ending
        int lineCut = trace.charAt(maxLength - 1) == '\r' ? maxLength - 1 : maxLength;
        String head = headEnd > 0 ? trace.substring(0, headEnd) : trace.substring(0, lineCut) + newline;
        String cut = trace.substring(headEnd > 0 ? headEnd : lineCut);
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

    // A failure whose toString() throws, even an Error, or returns null is still reported, by its class name. A
    // VirtualMachineError isn't caught, as everywhere in the reporter: the JVM can't recover from it
    private static String describe(Throwable cause) {
        try {
            String text = cause.toString();
            return text != null ? text : cause.getClass().getName();
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            return cause.getClass().getName();
        }
    }

    // printStackTrace starts with the failure's toString(), so it throws when that throws
    private static String stackTraceOf(Throwable cause) {
        var writer = new StringWriter();
        try {
            cause.printStackTrace(new PrintWriter(writer));
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            return describe(cause);
        }
        return writer.toString();
    }
}

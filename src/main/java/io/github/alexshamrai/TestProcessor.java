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

    private final ConfigReader configReader;

    public void setFailureDetails(Test test, Throwable cause) {
        int maxMessageLength = configReader.getMaxMessageLength();
        var stringWriter = new StringWriter();
        cause.printStackTrace(new PrintWriter(stringWriter));
        var trace = stringWriter.toString();
        var message = trace.length() > maxMessageLength ? trace.substring(0, maxMessageLength) + "..." : trace;
        test.setMessage(message);
        test.setTrace(trace);
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
            .name(displayName) // Use the displayName parameter
            .tags(details.tags() != null ? new ArrayList<>(details.tags()) : new ArrayList<>())
            .filepath(details.filePath())
            .start(details.startTime())
            .stop(stopTime)
            .duration(stopTime - details.startTime())
            .threadId(Thread.currentThread().getName())
            .build();
    }
}
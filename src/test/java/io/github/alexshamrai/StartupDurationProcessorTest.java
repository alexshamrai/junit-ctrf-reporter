
package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Extra;
import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StartupDurationProcessorTest {

    private StartupDurationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StartupDurationProcessor();
    }

    @org.junit.jupiter.api.Test
    void testProcessStartupDurationWithValidData() {
        long summaryStartTime = 2000L;
        var summary = Summary.builder()
            .start(summaryStartTime)
            .build();

        long firstTestStartTime = 5000L;
        long secondTestStartTime = 6000L;
        var tests = List.of(
            Test.builder().start(secondTestStartTime).build(),
            Test.builder().start(firstTestStartTime).build()
        );

        long expectedStartupDuration = 3000L;

        processor.processStartupDuration(summary, tests);

        assertNotNull(summary.getExtra());
        assertEquals(expectedStartupDuration, summary.getExtra().getStartupDuration());
    }

    @org.junit.jupiter.api.Test
    void testProcessStartupDurationWithNullSummary() {
        var tests = List.of(Test.builder().start(1000L).build());

        processor.processStartupDuration(null, tests);

        // No exception should be thrown, method should handle null gracefully
    }

    @org.junit.jupiter.api.Test
    void testProcessStartupDurationWithEmptyTests() {
        var summary = Summary.builder().start(5000L).build();
        List<Test> tests = Collections.emptyList();

        processor.processStartupDuration(summary, tests);

        assertNull(summary.getExtra());
    }

    @org.junit.jupiter.api.Test
    void testProcessStartupDurationWithTestsWithoutStartTime() {
        var summary = Summary.builder().start(5000L).build();
        var tests = List.of(
            Test.builder().build(),
            Test.builder().build()
        );

        processor.processStartupDuration(summary, tests);

        assertNull(summary.getExtra());
    }

    @org.junit.jupiter.api.Test
    void testProcessStartupDurationWithSummaryZeroStartTime() {
        var summary = Summary.builder().start(0L).build();
        var tests = List.of(Test.builder().start(1000L).build());

        processor.processStartupDuration(summary, tests);

        assertNull(summary.getExtra());
    }

    @org.junit.jupiter.api.Test
    void testProcessStartupDurationWithExistingExtra() {
        var summary = Summary.builder()
            .start(2000L)
            .extra(Extra.builder()
                .customData(new HashMap<>(Map.of("existingKey", "existingValue")))
                .build())
            .build();

        var tests = List.of(Test.builder().start(5000L).build());

        processor.processStartupDuration(summary, tests);

        assertNotNull(summary.getExtra());
        assertEquals(3000L, summary.getExtra().getStartupDuration());
        assertNotNull(summary.getExtra().getCustomData());
        assertEquals("existingValue", summary.getExtra().getCustomData().get("existingKey"));
    }

    @org.junit.jupiter.api.Test
    void testProcessStartupDurationCountsEarlierAttemptsOfRetriedTests() {
        var summary = Summary.builder().start(2000L).build();
        var tests = List.of(
            Test.builder().start(60000L)
                .retries(1)
                .retryAttempts(List.of(RetryAttempt.builder().attempt(1).start(2100L).build()))
                .build(),
            Test.builder().start(30000L).build()
        );

        processor.processStartupDuration(summary, tests);

        assertNotNull(summary.getExtra());
        assertEquals(100L, summary.getExtra().getStartupDuration());
    }
}

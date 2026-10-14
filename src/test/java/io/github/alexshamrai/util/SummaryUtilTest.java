package io.github.alexshamrai.util;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SummaryUtilTest {

    @org.junit.jupiter.api.Test
    void shouldCreateSummaryWithCorrectCounts() {
        var tests = List.of(
            Test.builder().status(TestStatus.PASSED).build(),
            Test.builder().status(TestStatus.FAILED).build(),
            Test.builder().status(TestStatus.PENDING).build(),
            Test.builder().status(TestStatus.SKIPPED).build(),
            Test.builder().status(TestStatus.OTHER).build()
        );

        var startTime = 1000L;
        var stopTime = 2000L;

        var summary = SummaryUtil.createSummary(tests, startTime, stopTime);

        assertEquals(5, summary.getTests());
        assertEquals(1, summary.getPassed());
        assertEquals(1, summary.getFailed());
        assertEquals(1, summary.getPending());
        assertEquals(1, summary.getSkipped());
        assertEquals(1, summary.getOther());
        assertEquals(startTime, summary.getStart());
        assertEquals(stopTime, summary.getStop());
    }

    @org.junit.jupiter.api.Test
    void shouldHandleEmptyTestList() {
        var tests = new ArrayList<Test>();

        var startTime = 1500L;
        var stopTime = 2500L;

        var summary = SummaryUtil.createSummary(tests, startTime, stopTime);

        assertEquals(0, summary.getTests());
        assertEquals(0, summary.getPassed());
        assertEquals(0, summary.getFailed());
        assertEquals(0, summary.getPending());
        assertEquals(0, summary.getSkipped());
        assertEquals(0, summary.getOther());
        assertEquals(0, summary.getFlaky());
        assertEquals(startTime, summary.getStart());
        assertEquals(stopTime, summary.getStop());
    }

    @org.junit.jupiter.api.Test
    void shouldCountCorrectStatusCountsWhenMultipleTestsHaveSameStatus() {
        var tests = List.of(
            Test.builder().status(TestStatus.PASSED).build(),
            Test.builder().status(TestStatus.PASSED).build(),
            Test.builder().status(TestStatus.FAILED).build(),
            Test.builder().status(TestStatus.FAILED).build(),
            Test.builder().status(TestStatus.PASSED).build()
        );

        var startTime = 1200L;
        var stopTime = 2200L;

        var summary = SummaryUtil.createSummary(tests, startTime, stopTime);

        assertEquals(5, summary.getTests());
        assertEquals(3, summary.getPassed());
        assertEquals(2, summary.getFailed());
        assertEquals(0, summary.getPending());
        assertEquals(0, summary.getSkipped());
        assertEquals(0, summary.getOther());
        assertEquals(startTime, summary.getStart());
        assertEquals(stopTime, summary.getStop());
    }

    @org.junit.jupiter.api.Test
    void shouldHandleNullStatusInTests() {
        var tests = List.of(
            Test.builder().status(null).build(),
            Test.builder().status(TestStatus.PASSED).build()
        );

        var startTime = 1300L;
        var stopTime = 2300L;

        var summary = SummaryUtil.createSummary(tests, startTime, stopTime);

        assertEquals(2, summary.getTests());
        assertEquals(1, summary.getPassed());
        assertEquals(0, summary.getFailed());
        assertEquals(0, summary.getPending());
        assertEquals(0, summary.getSkipped());
        assertEquals(0, summary.getOther());
        assertEquals(startTime, summary.getStart());
        assertEquals(stopTime, summary.getStop());
    }

    @org.junit.jupiter.api.Test
    void shouldCountFlakyTests() {
        var tests = List.of(
            Test.builder().status(TestStatus.PASSED).flaky(true).build(),
            Test.builder().status(TestStatus.PASSED).build(),
            Test.builder().status(TestStatus.FAILED).build()
        );

        var summary = SummaryUtil.createSummary(tests, 1000L, 2000L);

        assertEquals(3, summary.getTests());
        assertEquals(1, summary.getFlaky());
    }
}

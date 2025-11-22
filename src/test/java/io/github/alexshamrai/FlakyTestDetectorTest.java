package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;

class FlakyTestDetectorTest {

    @org.junit.jupiter.api.Test
    @DisplayName("Should not mark test as flaky when no previous tests exist")
    void shouldNotMarkFlakyWhenNoPreviousTests() {
        var newTest = Test.builder().name("test1").status(PASSED).build();

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of());

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should set retry count when previous tests exist")
    void shouldSetRetryCountWhenPreviousTestsExist() {
        var previousTest1 = Test.builder().name("test1").status(FAILED).build();
        var previousTest2 = Test.builder().name("test1").status(FAILED).build();
        var newTest = Test.builder().name("test1").status(PASSED).build();

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(previousTest1, previousTest2));

        assertThat(newTest.getRetries()).isEqualTo(2);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should mark test as flaky when passed after previous failures")
    void shouldMarkFlakyWhenPassedAfterFailures() {
        var previousTest = Test.builder().name("test1").status(FAILED).build();
        var newTest = Test.builder().name("test1").status(PASSED).build();

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(previousTest));

        assertThat(newTest.getFlaky()).isTrue();
        assertThat(newTest.getRetries()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should mark test as flaky when passed with retries > 0")
    void shouldMarkFlakyWhenPassedWithRetries() {
        var previousTest = Test.builder().name("test1").status(PASSED).build();
        var newTest = Test.builder().name("test1").status(PASSED).build();

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(previousTest));

        assertThat(newTest.getFlaky()).isTrue();
        assertThat(newTest.getRetries()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should not mark failed test as flaky")
    void shouldNotMarkFailedTestAsFlaky() {
        var previousTest = Test.builder().name("test1").status(FAILED).build();
        var newTest = Test.builder().name("test1").status(FAILED).build();

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(previousTest));

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should only count tests with same name")
    void shouldOnlyCountTestsWithSameName() {
        var otherTest1 = Test.builder().name("test2").status(FAILED).build();
        var otherTest2 = Test.builder().name("test3").status(FAILED).build();
        var newTest = Test.builder().name("test1").status(PASSED).build();

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(otherTest1, otherTest2));

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should handle null test names gracefully")
    void shouldHandleNullTestNames() {
        var previousTest = Test.builder().name(null).status(FAILED).build();
        var newTest = Test.builder().name("test1").status(PASSED).build();

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(previousTest));

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should count multiple retries correctly")
    void shouldCountMultipleRetriesCorrectly() {
        var previousTest1 = Test.builder().name("test1").status(FAILED).build();
        var previousTest2 = Test.builder().name("test1").status(FAILED).build();
        var previousTest3 = Test.builder().name("test1").status(FAILED).build();
        var newTest = Test.builder().name("test1").status(PASSED).build();

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(previousTest1, previousTest2, previousTest3));

        assertThat(newTest.getFlaky()).isTrue();
        assertThat(newTest.getRetries()).isEqualTo(3);
    }
}

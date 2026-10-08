package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static org.assertj.core.api.Assertions.assertThat;

class FlakyTestDetectorTest {

    private static final String CLASS_A = "com.example.ATest";
    private static final String CLASS_B = "com.example.BTest";
    private static final String TEST_1 = "[engine:junit-jupiter]/[class:com.example.ATest]/[method:test1()]";

    @org.junit.jupiter.api.Test
    @DisplayName("Should not mark test as flaky when no previous tests exist")
    void shouldNotMarkFlakyWhenNoPreviousTests() {
        var newTest = attempt(TEST_1, CLASS_A, "test1()", PASSED);

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of());

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should count earlier attempts of the same test as retries")
    void shouldCountEarlierAttemptsOfTheSameTestAsRetries() {
        var first = attempt(TEST_1, CLASS_A, "test1()", FAILED);
        var second = attempt(TEST_1, CLASS_A, "test1()", FAILED);
        var newTest = attempt(TEST_1, CLASS_A, "test1()", PASSED);

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(first, second));

        assertThat(newTest.getRetries()).isEqualTo(2);
        assertThat(newTest.getFlaky()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should mark test as flaky when it passes after a failed attempt")
    void shouldMarkFlakyWhenPassedAfterFailure() {
        var previous = attempt(TEST_1, CLASS_A, "test1()", FAILED);
        var newTest = attempt(TEST_1, CLASS_A, "test1()", PASSED);

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(previous));

        assertThat(newTest.getFlaky()).isTrue();
        assertThat(newTest.getRetries()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should not mark test as flaky when every earlier attempt passed")
    void shouldNotMarkFlakyWhenPassedBefore() {
        var previous = attempt(TEST_1, CLASS_A, "test1()", PASSED);
        var newTest = attempt(TEST_1, CLASS_A, "test1()", PASSED);

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(previous));

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should not mark failed test as flaky")
    void shouldNotMarkFailedTestAsFlaky() {
        var previous = attempt(TEST_1, CLASS_A, "test1()", FAILED);
        var newTest = attempt(TEST_1, CLASS_A, "test1()", FAILED);

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(previous));

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should not treat a test with the same method name in another class as a retry")
    void shouldNotMatchSameMethodNameInAnotherClass() {
        var otherClass = attempt("[engine:junit-jupiter]/[class:com.example.BTest]/[method:shouldWork()]",
            CLASS_B, "shouldWork()", FAILED);
        var newTest = attempt("[engine:junit-jupiter]/[class:com.example.ATest]/[method:shouldWork()]",
            CLASS_A, "shouldWork()", PASSED);

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(otherClass));

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should not treat invocations of different parameterized tests as retries")
    void shouldNotMatchInvocationsOfDifferentParameterizedTests() {
        var otherMethod = attempt(
            "[engine:junit-jupiter]/[class:com.example.ATest]/[test-template:first(int)]/[test-template-invocation:#1]",
            CLASS_A, "[1] 1", FAILED);
        var newTest = attempt(
            "[engine:junit-jupiter]/[class:com.example.ATest]/[test-template:second(int)]/[test-template-invocation:#1]",
            CLASS_A, "[1] 1", PASSED);

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(otherMethod));

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should match a test from an older report without testId by file path and name")
    void shouldMatchLegacyTestByFilePathAndName() {
        var legacy = Test.builder().filepath(CLASS_A).name("test1()").status(FAILED).build();
        var newTest = attempt(TEST_1, CLASS_A, "test1()", PASSED);

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(legacy));

        assertThat(newTest.getRetries()).isEqualTo(1);
        assertThat(newTest.getFlaky()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should not match a test from an older report that belongs to another file")
    void shouldNotMatchLegacyTestFromAnotherFile() {
        var legacy = Test.builder().filepath(CLASS_B).name("test1()").status(FAILED).build();
        var newTest = attempt(TEST_1, CLASS_A, "test1()", PASSED);

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(legacy));

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should never match tests by name alone")
    void shouldNotMatchByNameAlone() {
        var legacy = Test.builder().name("test1()").status(FAILED).build();
        var newTest = Test.builder().name("test1()").status(PASSED).build();

        FlakyTestDetector.detectAndMarkFlaky(newTest, List.of(legacy));

        assertThat(newTest.getFlaky()).isNull();
        assertThat(newTest.getRetries()).isNull();
    }

    private static Test attempt(String testId, String filepath, String name, Test.TestStatus status) {
        return Test.builder().testId(testId).filepath(filepath).name(name).status(status).build();
    }
}

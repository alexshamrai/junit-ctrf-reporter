package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStateTrackerTest {

    private TestStateTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TestStateTracker();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should start with empty state")
    void shouldStartEmpty() {
        assertThat(tracker.getAllTests()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should add and retrieve test")
    void shouldAddAndRetrieveTest() {
        var test = Test.builder().name("test1").build();

        tracker.addTest(test);

        assertThat(tracker.getAllTests()).containsExactly(test);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should store and retrieve test details")
    void shouldStoreAndRetrieveTestDetails() {
        var details = TestDetails.builder()
            .uniqueId("id-1")
            .displayName("Test 1")
            .build();

        tracker.putTestDetails("id-1", details);
        var retrieved = tracker.removeTestDetails("id-1");

        assertThat(retrieved).isEqualTo(details);
        assertThat(tracker.removeTestDetails("id-1")).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should return null for non-existent test details")
    void shouldReturnNullForNonExistentDetails() {
        assertThat(tracker.removeTestDetails("non-existent")).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should add multiple tests")
    void shouldAddMultipleTests() {
        var test1 = Test.builder().name("test1").build();
        var test2 = Test.builder().name("test2").build();
        var testsToAdd = List.of(test1, test2);

        tracker.addAllTests(testsToAdd);

        assertThat(tracker.getAllTests()).containsExactly(test1, test2);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should clear all state")
    void shouldClearAllState() {
        var test = Test.builder().name("test1").build();
        var details = TestDetails.builder().uniqueId("id-1").build();

        tracker.addTest(test);
        tracker.putTestDetails("id-1", details);

        tracker.clear();

        assertThat(tracker.getAllTests()).isEmpty();
        assertThat(tracker.removeTestDetails("id-1")).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should maintain order of added tests")
    void shouldMaintainOrderOfTests() {
        var test1 = Test.builder().name("test1").build();
        var test2 = Test.builder().name("test2").build();
        var test3 = Test.builder().name("test3").build();

        tracker.addTest(test1);
        tracker.addTest(test2);
        tracker.addTest(test3);

        assertThat(tracker.getAllTests()).containsExactly(test1, test2, test3);
    }
}

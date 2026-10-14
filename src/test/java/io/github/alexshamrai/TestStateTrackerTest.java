package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        assertThat(tracker.getAllAttempts()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should add and retrieve test")
    void shouldAddAndRetrieveTest() {
        var test = Test.builder().name("test1").build();

        tracker.addAttempt(test);

        assertThat(tracker.getAllAttempts()).containsExactly(test);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should store and retrieve test details")
    void shouldStoreAndRetrieveTestDetails() {
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Test 1");

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

        tracker.addAllAttempts(testsToAdd);

        assertThat(tracker.getAllAttempts()).containsExactly(test1, test2);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should clear all state")
    void shouldClearAllState() {
        var test = Test.builder().name("test1").build();
        var details = new TestDetails(System.currentTimeMillis(), Set.of(), null, "id-1", "Test");

        tracker.addAttempt(test);
        tracker.putTestDetails("id-1", details);

        tracker.clear();

        assertThat(tracker.getAllAttempts()).isEmpty();
        assertThat(tracker.removeTestDetails("id-1")).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should maintain order of added tests")
    void shouldMaintainOrderOfTests() {
        var test1 = Test.builder().name("test1").build();
        var test2 = Test.builder().name("test2").build();
        var test3 = Test.builder().name("test3").build();

        tracker.addAttempt(test1);
        tracker.addAttempt(test2);
        tracker.addAttempt(test3);

        assertThat(tracker.getAllAttempts()).containsExactly(test1, test2, test3);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Attempts added from many threads are all kept")
    void shouldKeepAttemptsAddedConcurrently() throws InterruptedException {
        int threads = 8;
        int perThread = 1_000;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    tracker.addAttempt(Test.builder().name("test").build());
                }
                return null;
            });
        }

        start.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(tracker.getAllAttempts()).hasSize(threads * perThread);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("getAllAttempts returns a copy that later attempts do not change")
    void getAllAttemptsReturnsASnapshot() {
        tracker.addAttempt(Test.builder().name("test1").build());

        var snapshot = tracker.getAllAttempts();
        tracker.addAttempt(Test.builder().name("test2").build());

        assertThat(snapshot).hasSize(1);
    }
}

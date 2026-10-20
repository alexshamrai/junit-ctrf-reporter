package io.github.alexshamrai;

import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

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
    @DisplayName("Removing the tests in progress returns each of them once")
    void removeTestsInProgressReturnsEachOnce() {
        var first = new TestDetails(1_000L, Set.of(), null, "id-1", "Test 1");
        var second = new TestDetails(2_000L, Set.of(), null, "id-2", "Test 2");
        tracker.putTestDetails("id-1", first);
        tracker.putTestDetails("id-2", second);

        assertThat(tracker.removeTestsInProgress()).containsExactlyInAnyOrder(first, second);
        assertThat(tracker.removeTestsInProgress()).isEmpty();
        assertThat(tracker.removeTestDetails("id-1")).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should return null for non-existent test details")
    void shouldReturnNullForNonExistentDetails() {
        assertThat(tracker.removeTestDetails("non-existent")).isNull();
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

    @org.junit.jupiter.api.Test
    @DisplayName("Container successes are kept in order")
    void shouldKeepContainerSuccessesInOrder() {
        var first = new ContainerSuccess("class-1", 1L, 2L);
        var second = new ContainerSuccess("class-2", 3L, 4L);

        tracker.addContainerSuccess(first);
        tracker.addContainerSuccess(second);

        assertThat(tracker.getAllContainerSuccesses()).containsExactly(first, second);
    }
}

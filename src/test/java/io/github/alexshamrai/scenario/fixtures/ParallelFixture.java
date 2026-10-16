package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.EnvironmentHealthTracker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that run concurrently when parallel execution is on (TI-1): 802 of them, each taking a millisecond, so that
 * they overlap. Every tenth parameterized one fails, one aborts, and one marks the environment unhealthy. A nested
 * class aborts in {@code @AfterAll}, after its tests ran: the listener must not report them again as skipped.
 */
@Tag("scenario-fixture")
@Execution(ExecutionMode.CONCURRENT)
public class ParallelFixture {

    @RepeatedTest(150)
    @Execution(ExecutionMode.CONCURRENT)
    void repeated() throws InterruptedException {
        Thread.sleep(1);
    }

    @ParameterizedTest
    @MethodSource("values")
    @Execution(ExecutionMode.CONCURRENT)
    void parameterized(int value) throws InterruptedException {
        Thread.sleep(1);
        assertNotEquals(0, value % 10, "every tenth value fails");
    }

    @Test
    void aborted() {
        assumeTrue(false, "aborted on purpose");
    }

    @Test
    void marksTheEnvironmentUnhealthy() {
        EnvironmentHealthTracker.markEnvironmentUnhealthy();
    }

    static IntStream values() {
        return IntStream.range(0, 100);
    }

    @Nested
    @Execution(ExecutionMode.CONCURRENT)
    class AbortedAfterItsTests {

        @AfterAll
        static void abort() {
            assumeTrue(false, "aborted after its tests ran");
        }

        @RepeatedTest(500)
        @Execution(ExecutionMode.CONCURRENT)
        void ranBeforeTheAbort() throws InterruptedException {
            Thread.sleep(1);
        }
    }

    @Nested
    @Execution(ExecutionMode.CONCURRENT)
    class Inner {

        @RepeatedTest(50)
        @Execution(ExecutionMode.CONCURRENT)
        void nestedRepeated() throws InterruptedException {
            Thread.sleep(1);
        }
    }
}

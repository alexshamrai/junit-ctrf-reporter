package compat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs in its own Surefire fork and fails only on its first call, so Surefire's rerunFailingTestsCount reruns it
 * in the same JVM. compat/check_report.py expects it once, with retries 1 and flaky true.
 */
class RerunTest {

    private static final AtomicInteger CALLS = new AtomicInteger();

    @Test
    void flakyOnce() throws Exception {
        VersionProbe.recordFork("RerunTest");
        if (CALLS.incrementAndGet() == 1) {
            fail("fails on its first call");
        }
    }
}

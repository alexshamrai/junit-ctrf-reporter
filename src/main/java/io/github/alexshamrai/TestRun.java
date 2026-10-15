package io.github.alexshamrai;

/**
 * One run's state: what started it, the entry point that reports it, when it started, and what it recorded so far.
 * <p>
 * The owner is the listener's {@code TestPlan} or the extension's root {@code ExtensionContext}, compared by identity.
 * Only the owner can end the run, so a JUnit run started from inside a test can't end the run around it (CB-2).
 */
final class TestRun {

    private final Object owner;
    private final String generator;
    private final long startTime;
    private final TestStateTracker state = new TestStateTracker();

    TestRun(Object owner, String generator, long startTime) {
        this.owner = owner;
        this.generator = generator;
        this.startTime = startTime;
    }

    Object owner() {
        return owner;
    }

    String generator() {
        return generator;
    }

    long startTime() {
        return startTime;
    }

    /**
     * Returns the attempts, the container successes and the tests in progress recorded so far.
     *
     * @return the run's state
     */
    TestStateTracker state() {
        return state;
    }
}

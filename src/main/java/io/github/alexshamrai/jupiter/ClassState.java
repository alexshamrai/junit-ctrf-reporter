package io.github.alexshamrai.jupiter;

/**
 * What {@link CtrfExtension} tracks about one test class while it runs: when it started and whether
 * any of its tests or nested classes started. A class that fails before that failed in its setup;
 * one that fails after it failed in its teardown.
 */
final class ClassState {

    private final long startTime;
    private volatile boolean childrenStarted;

    ClassState(long startTime) {
        this.startTime = startTime;
    }

    long startTime() {
        return startTime;
    }

    boolean childrenStarted() {
        return childrenStarted;
    }

    void markChildrenStarted() {
        childrenStarted = true;
    }
}

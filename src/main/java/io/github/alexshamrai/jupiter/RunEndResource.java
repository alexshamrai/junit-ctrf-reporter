package io.github.alexshamrai.jupiter;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Ends a test run when JUnit closes the root context's store, after the run's last test.
 * <p>
 * JUnit before 5.13 closes the store values that are {@link ExtensionContext.Store.CloseableResource}s. JUnit 5.13 and
 * later close {@link AutoCloseable} values instead, and log a warning for a value that is only a
 * {@code CloseableResource}. Being both, this is closed once by every JUnit version, without the warning.
 */
final class RunEndResource implements ExtensionContext.Store.CloseableResource, AutoCloseable {

    private final Runnable endOfRun;

    RunEndResource(Runnable endOfRun) {
        this.endOfRun = endOfRun;
    }

    @Override
    public void close() {
        endOfRun.run();
    }
}

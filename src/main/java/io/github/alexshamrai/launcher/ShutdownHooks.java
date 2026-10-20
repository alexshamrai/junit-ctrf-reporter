package io.github.alexshamrai.launcher;

/**
 * Adds and removes JVM shutdown hooks: the runtime's in a test run, the tests' own in the listener's unit tests.
 */
interface ShutdownHooks {

    void add(Thread hook);

    void remove(Thread hook);

    /**
     * The JVM's shutdown hooks. While the JVM shuts down, the runtime refuses both, and they are ignored here: a hook
     * that can't be removed runs then anyway and finds the run finished, and a test plan that starts then gets none.
     */
    ShutdownHooks RUNTIME = new ShutdownHooks() {
        @Override
        public void add(Thread hook) {
            try {
                Runtime.getRuntime().addShutdownHook(hook);
            } catch (IllegalStateException shuttingDown) {
                // Too late for a hook
            }
        }

        @Override
        public void remove(Thread hook) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException shuttingDown) {
                // The hook runs, and does nothing
            }
        }
    };
}

package io.github.alexshamrai;

/**
 * Public API for tracking environment health during test execution.
 * <p>
 * This class provides static methods to mark the test environment as unhealthy
 * and check its current health status. The health status is included in the
 * CTRF report and can be useful for identifying tests that ran in degraded
 * or problematic environments.
 * <p>
 * Environment health can be set in two ways:
 * <ul>
 *   <li>Setting the {@code ENV_HEALTHY=false} environment variable before test execution</li>
 *   <li>Calling {@link #markEnvironmentUnhealthy()} during test execution</li>
 * </ul>
 * <p>
 * Once marked as unhealthy, the environment remains unhealthy for the entire test run.
 * The unhealthy state is also preserved across test reruns when using the same report file.
 *
 * @since 0.4.2
 */
public final class EnvironmentHealthTracker {

    private EnvironmentHealthTracker() {
        // Utility class - prevent instantiation
    }

    /**
     * Marks the test environment as unhealthy.
     * <p>
     * This is typically called when tests detect degraded environment conditions,
     * such as:
     * <ul>
     *   <li>External service unavailability</li>
     *   <li>Database connection issues</li>
     *   <li>Insufficient system resources</li>
     *   <li>Network problems</li>
     * </ul>
     * <p>
     * Once marked unhealthy, the environment cannot be marked healthy again
     * during the same test run.
     *
     * @since 0.4.2
     */
    public static void markEnvironmentUnhealthy() {
        CtrfReportManager.getInstance().markEnvironmentUnhealthyInternal();
    }

    /**
     * Checks whether the test environment is currently considered healthy.
     * <p>
     * The environment is healthy by default unless:
     * <ul>
     *   <li>The {@code ENV_HEALTHY=false} environment variable was set</li>
     *   <li>{@link #markEnvironmentUnhealthy()} was called</li>
     *   <li>A previous test run marked it unhealthy (and the same report file is being reused)</li>
     * </ul>
     *
     * @return {@code true} if the environment is healthy, {@code false} otherwise
     * @since 0.4.2
     */
    public static boolean isEnvironmentHealthy() {
        return CtrfReportManager.getInstance().isEnvironmentHealthyInternal();
    }

    /**
     * Package-private method to check the ENV_HEALTHY environment variable
     * and return whether it indicates an unhealthy environment.
     * <p>
     * This method is called internally by the library at initialization
     * and before report generation to ensure ENV_HEALTHY changes are captured.
     * <p>
     * Not part of the public API - only for internal library use.
     *
     * @return true if ENV_HEALTHY is set to "false", false otherwise
     */
    static boolean isEnvironmentVariableUnhealthy() {
        String envHealthy = System.getenv("ENV_HEALTHY");
        return envHealthy != null && "false".equalsIgnoreCase(envHealthy);
    }
}
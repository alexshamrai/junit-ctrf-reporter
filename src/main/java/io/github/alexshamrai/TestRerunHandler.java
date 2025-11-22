package io.github.alexshamrai;

/**
 * Handles test rerun scenarios by loading data from previous test runs.
 * <p>
 * When tests are rerun (e.g., with test retry frameworks), this class coordinates
 * loading previous test results and environment state from existing report files.
 * This allows the library to:
 * <ul>
 *   <li>Preserve test start times across reruns</li>
 *   <li>Accumulate test results from multiple runs</li>
 *   <li>Maintain environment health state</li>
 *   <li>Detect flaky tests based on retry patterns</li>
 * </ul>
 */
final class TestRerunHandler {

    private final CtrfReportFileService fileService;

    /**
     * Creates a new test rerun handler.
     *
     * @param fileService the file service for reading existing reports
     */
    TestRerunHandler(CtrfReportFileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Loads the test run start time from a previous run, or returns current time if none exists.
     *
     * @return the start time in milliseconds since epoch
     */
    long loadOrCreateStartTime() {
        Long existingStartTime = fileService.getExistingStartTime();
        return existingStartTime != null ? existingStartTime : System.currentTimeMillis();
    }

    /**
     * Checks if the previous run had an unhealthy environment.
     * <p>
     * Environment health is preserved across reruns to ensure that if any run
     * detected environmental issues, the final report reflects this.
     *
     * @return true if the previous run was unhealthy, false otherwise
     */
    boolean wasPreviousRunUnhealthy() {
        return !fileService.getExistingEnvironmentHealth();
    }

    /**
     * Returns the file service for accessing existing report data.
     * <p>
     * Package-private to allow CtrfReportManager to load existing tests.
     *
     * @return the file service
     */
    CtrfReportFileService getFileService() {
        return fileService;
    }
}

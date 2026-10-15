package integration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The report under validation, the integration module that wrote it, and that run's JUnit event log.
 *
 * @param reportFile the CTRF report
 * @param moduleDir  the integration module's directory
 * @param module     the module's name: {@link #LISTENER_MODULE} or {@link #EXTENSION_MODULE}
 * @param eventsDir  the directory holding JUnit's event log of the same run
 */
record IntegrationRun(File reportFile, Path moduleDir, String module, Path eventsDir) {

    static final String LISTENER_MODULE = "integration-tests-listener";
    static final String EXTENSION_MODULE = "integration-tests-extension";
    // Relative to the validator module, which is the working directory of its tests
    static final String DEFAULT_REPORT_PATH = "../" + LISTENER_MODULE + "/build/test-results/ctrf-report.json";

    /**
     * Locates the run from {@code -Dctrf.report.path} and {@code -Dctrf.junit.events.dir}; either may be {@code null}.
     */
    static IntegrationRun locate(String reportPathProperty, String eventsDirProperty) {
        Path report = Path.of(reportPathProperty != null ? reportPathProperty : DEFAULT_REPORT_PATH).toAbsolutePath().normalize();
        if (!Files.isRegularFile(report)) {
            throw new IllegalStateException("No report at " + report + ". Run ./gradlew :" + LISTENER_MODULE
                + ":test first, or pass -Dctrf.report.path=<report>.");
        }
        Path moduleDir = moduleDirOf(report);
        Path eventsDir = eventsDirProperty != null
            ? Path.of(eventsDirProperty).toAbsolutePath().normalize()
            : report.getParent().resolve("junit-platform");
        return new IntegrationRun(report.toFile(), moduleDir, moduleDir.getFileName().toString(), eventsDir);
    }

    boolean isListener() {
        return LISTENER_MODULE.equals(module);
    }

    private static Path moduleDirOf(Path report) {
        for (Path dir = report.getParent(); dir != null && dir.getParent() != null; dir = dir.getParent()) {
            String parentName = dir.getParent().getFileName() == null ? "" : dir.getParent().getFileName().toString();
            if (dir.getFileName().toString().equals("build")
                && (parentName.equals(LISTENER_MODULE) || parentName.equals(EXTENSION_MODULE))) {
                return dir.getParent();
            }
        }
        throw new IllegalStateException("Can't tell which integration module wrote " + report + ": expected it under "
            + LISTENER_MODULE + "/build or " + EXTENSION_MODULE + "/build.");
    }
}

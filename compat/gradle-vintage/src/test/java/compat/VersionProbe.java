package compat;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records which JUnit artifacts are on the test runtime classpath, for compat/check_report.py.
 */
final class VersionProbe {

    private VersionProbe() {
    }

    static void write() throws Exception {
        String text = "jupiter-api=" + versionOf("org.junit.jupiter.api.Test") + "\n"
            + "platform-launcher=" + versionOf("org.junit.platform.launcher.Launcher") + "\n"
            + "vintage-engine=" + versionOf("org.junit.vintage.engine.VintageTestEngine") + "\n";
        Files.writeString(Path.of(System.getProperty("compat.out")), text);
    }

    private static String versionOf(String className) {
        try {
            String version = Class.forName(className).getPackage().getImplementationVersion();
            return version == null ? "unknown" : version;
        } catch (ClassNotFoundException e) {
            return "absent";
        }
    }
}

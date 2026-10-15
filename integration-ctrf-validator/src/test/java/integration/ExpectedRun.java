package integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

/**
 * What the report must say about the run that wrote it, taken from that run's own inputs: the same {@code -Dthreads}
 * and {@code -Dctrf.build.*} flags, the same {@code ENV_HEALTHY} variable, and the module's {@code ctrf.properties}.
 *
 * @param threads     the distinct thread names to expect: exactly 1 when 1, otherwise between 2 and this
 * @param buildName   the expected {@code environment.buildName}
 * @param buildNumber the expected {@code environment.buildNumber}, or {@code null}
 * @param buildId     the expected {@code environment.buildId}, or {@code null}
 * @param buildUrl    the expected {@code environment.buildUrl}
 * @param healthy     the expected {@code environment.healthy}
 * @param generatedBy the expected {@code generatedBy}
 */
record ExpectedRun(int threads, String buildName, Long buildNumber, String buildId, String buildUrl, boolean healthy,
                   String generatedBy) {

    static final String LISTENER_ENTRY_POINT = "io.github.alexshamrai.launcher.CtrfListener";
    static final String EXTENSION_ENTRY_POINT = "io.github.alexshamrai.jupiter.CtrfExtension";

    static ExpectedRun of(IntegrationRun run) throws IOException {
        var moduleProperties = new Properties();
        try (InputStream in = Files.newInputStream(run.moduleDir().resolve("src/test/resources/ctrf.properties"))) {
            moduleProperties.load(in);
        }
        return from(run.module(), moduleProperties, System.getProperties(), System.getenv("ENV_HEALTHY"));
    }

    static ExpectedRun from(String module, Properties moduleProperties, Properties systemProperties, String envHealthy) {
        boolean extension = IntegrationRun.EXTENSION_MODULE.equals(module);
        String number = setting("ctrf.build.number", moduleProperties, systemProperties);
        String stripped = number == null ? "" : number.strip();
        boolean wholeNumber = stripped.matches("[0-9]+");
        return new ExpectedRun(
            threads(systemProperties.getProperty("threads")),
            setting("ctrf.build.name", moduleProperties, systemProperties),
            wholeNumber ? Long.valueOf(stripped) : null,
            wholeNumber || stripped.isEmpty() ? null : stripped,
            setting("ctrf.build.url", moduleProperties, systemProperties),
            // The extension module's FirstLongTest calls markEnvironmentUnhealthy()
            !("false".equalsIgnoreCase(envHealthy) || extension),
            extension ? EXTENSION_ENTRY_POINT : LISTENER_ENTRY_POINT);
    }

    // The library's precedence: a system property wins over ctrf.properties
    private static String setting(String key, Properties moduleProperties, Properties systemProperties) {
        String value = systemProperties.getProperty(key);
        return value != null ? value : moduleProperties.getProperty(key);
    }

    // The integration modules run tests in parallel only for an integer above 1
    private static int threads(String value) {
        try {
            return value == null ? 1 : Math.max(1, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

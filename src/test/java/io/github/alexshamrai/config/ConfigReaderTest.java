package io.github.alexshamrai.config;

import io.github.alexshamrai.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loader keeps owner 1.0.12's behaviour, apart from the fixes in the spec's table "owner 1.0.12 behaviour the
 * loader keeps". The tests named "row N" follow that table; the others cover the rest of the contract.
 */
class ConfigReaderTest {

    @RegisterExtension
    final LogCapture logs = new LogCapture(ConfigReader.class);

    @Test
    @DisplayName("row 1: a system property beats ctrf.properties")
    void systemPropertyBeatsThePropertiesFile() {
        var reader = new ConfigReader(Map.of("ctrf.app.name", "from-system"), Map.of(), Map.of("ctrf.app.name", "from-file"));

        assertEquals("from-system", reader.getAppName());
    }

    @Test
    @DisplayName("A system property beats an environment variable")
    void systemPropertyBeatsTheEnvironment() {
        var reader = new ConfigReader(Map.of("ctrf.app.name", "from-system"), Map.of("ctrf.app.name", "from-env"), Map.of());

        assertEquals("from-system", reader.getAppName());
    }

    @Test
    @DisplayName("row 2: an environment variable with the exact key is used")
    void environmentVariableWithTheExactKeyIsUsed() {
        var reader = new ConfigReader(Map.of(), Map.of("ctrf.report.path", "env.json"), Map.of("ctrf.report.path", "file.json"));

        assertEquals("env.json", reader.getReportPath());
    }

    @Test
    @DisplayName("row 3: CTRF_REPORT_PATH is used")
    void upperCaseEnvironmentVariableIsUsed() {
        var reader = new ConfigReader(Map.of(), Map.of("CTRF_REPORT_PATH", "upper.json"), Map.of());

        assertEquals("upper.json", reader.getReportPath());
    }

    @Test
    @DisplayName("CTRF_* variables rank below system properties and above ctrf.properties")
    void upperCaseEnvironmentVariableIsBelowSystemPropertiesAndAboveTheFile() {
        var reader = new ConfigReader(
            Map.of("ctrf.app.name", "from-system"),
            Map.of("CTRF_APP_NAME", "from-env", "CTRF_BUILD_NAME", "from-env"),
            Map.of("ctrf.app.name", "from-file", "ctrf.build.name", "from-file"));

        assertEquals("from-system", reader.getAppName());
        assertEquals("from-env", reader.getBuildName());
    }

    @Test
    @DisplayName("The exact environment key beats its CTRF_* form")
    void exactEnvironmentKeyBeatsItsUpperCaseForm() {
        var reader = new ConfigReader(Map.of(), Map.of("ctrf.commit", "exact", "CTRF_COMMIT", "upper"), Map.of());

        assertEquals("exact", reader.getCommit());
    }

    @Test
    @DisplayName("junit.version isn't a ctrf.* key, so JUNIT_VERSION isn't read")
    void junitVersionHasNoUpperCaseForm() {
        assertNull(new ConfigReader(Map.of(), Map.of("JUNIT_VERSION", "6.1.3"), Map.of()).getJUnitVersion());
        assertEquals("6.1.3", new ConfigReader(Map.of(), Map.of("junit.version", "6.1.3"), Map.of()).getJUnitVersion());
    }

    @Test
    @DisplayName("Every ctrf.* key can be set through its CTRF_* variable")
    void everyCtrfKeyHasAnUpperCaseForm() {
        var environment = new HashMap<String, String>();
        environment.put("CTRF_REPORT_PATH", "env.json");
        environment.put("CTRF_MAX_MESSAGE_LENGTH", "42");
        environment.put("CTRF_CALCULATE_STARTUP_DURATION", "true");
        environment.put("CTRF_REPORT_NAME", "report");
        environment.put("CTRF_APP_NAME", "app");
        environment.put("CTRF_APP_VERSION", "1.0");
        environment.put("CTRF_BUILD_NAME", "build");
        environment.put("CTRF_BUILD_NUMBER", "7");
        environment.put("CTRF_BUILD_URL", "https://ci.example.com/7");
        environment.put("CTRF_REPOSITORY_NAME", "repo");
        environment.put("CTRF_REPOSITORY_URL", "https://github.com/example/repo");
        environment.put("CTRF_COMMIT", "c4093eb");
        environment.put("CTRF_BRANCH_NAME", "main");
        environment.put("CTRF_OS_PLATFORM", "linux");
        environment.put("CTRF_OS_RELEASE", "6.8");
        environment.put("CTRF_OS_VERSION", "Ubuntu 24.04");
        environment.put("CTRF_TEST_ENVIRONMENT", "staging");
        environment.put("CTRF_RUN_ID", "run-7");

        var reader = new ConfigReader(Map.of(), environment, Map.of());

        assertEquals("env.json", reader.getReportPath());
        assertEquals(42, reader.getMaxMessageLength());
        assertTrue(reader.calculateStartupDuration());
        assertEquals("report", reader.getReportName());
        assertEquals("app", reader.getAppName());
        assertEquals("1.0", reader.getAppVersion());
        assertEquals("build", reader.getBuildName());
        assertEquals("7", reader.getBuildNumber());
        assertEquals("https://ci.example.com/7", reader.getBuildUrl());
        assertEquals("repo", reader.getRepositoryName());
        assertEquals("https://github.com/example/repo", reader.getRepositoryUrl());
        assertEquals("c4093eb", reader.getCommit());
        assertEquals("main", reader.getBranchName());
        assertEquals("linux", reader.getOsPlatform());
        assertEquals("6.8", reader.getOsRelease());
        assertEquals("Ubuntu 24.04", reader.getOsVersion());
        assertEquals("staging", reader.getTestEnvironment());
        assertEquals("run-7", reader.getRunId());
    }

    @Test
    @DisplayName("Every key is read from ctrf.properties")
    void everyKeyIsReadFromThePropertiesFile() {
        var file = new HashMap<String, String>();
        file.put("ctrf.report.path", "ctrf.json");
        file.put("ctrf.max.message.length", "987");
        file.put("ctrf.calculate.startup.duration", "true");
        file.put("junit.version", "5.9.2");
        file.put("ctrf.report.name", "DynamicTestReport");
        file.put("ctrf.app.name", "DynamicTestApp");
        file.put("ctrf.app.version", "3.0.0");
        file.put("ctrf.build.name", "DynamicBuild");
        file.put("ctrf.build.number", "1234");
        file.put("ctrf.build.url", "http://dynamic.example.com/build");
        file.put("ctrf.repository.name", "DynamicRepository");
        file.put("ctrf.repository.url", "http://dynamic.example.com/repo");
        file.put("ctrf.commit", "dynamic123456");
        file.put("ctrf.branch.name", "dynamic-branch");
        file.put("ctrf.os.platform", "DynamicOS");
        file.put("ctrf.os.release", "DynamicRelease");
        file.put("ctrf.os.version", "1.0.0");
        file.put("ctrf.test.environment", "dynamic-environment");
        file.put("ctrf.run.id", "run-1");

        var reader = new ConfigReader(Map.of(), Map.of(), file);

        assertEquals("ctrf.json", reader.getReportPath());
        assertEquals(987, reader.getMaxMessageLength());
        assertTrue(reader.calculateStartupDuration());
        assertEquals("5.9.2", reader.getJUnitVersion());
        assertEquals("DynamicTestReport", reader.getReportName());
        assertEquals("DynamicTestApp", reader.getAppName());
        assertEquals("3.0.0", reader.getAppVersion());
        assertEquals("DynamicBuild", reader.getBuildName());
        assertEquals("1234", reader.getBuildNumber());
        assertEquals("http://dynamic.example.com/build", reader.getBuildUrl());
        assertEquals("DynamicRepository", reader.getRepositoryName());
        assertEquals("http://dynamic.example.com/repo", reader.getRepositoryUrl());
        assertEquals("dynamic123456", reader.getCommit());
        assertEquals("dynamic-branch", reader.getBranchName());
        assertEquals("DynamicOS", reader.getOsPlatform());
        assertEquals("DynamicRelease", reader.getOsRelease());
        assertEquals("1.0.0", reader.getOsVersion());
        assertEquals("dynamic-environment", reader.getTestEnvironment());
        assertEquals("run-1", reader.getRunId());
        assertEquals(List.of(), logs.messages());
    }

    @Test
    @DisplayName("row 4: ${user.home} and ${other.key} expand through the same lookup, defaults included")
    void referencesExpandThroughTheSameLookup() {
        var reader = new ConfigReader(
            Map.of("user.home", "/home/ci"),
            Map.of("GIT_SHA", "c4093eb"),
            Map.of(
                "ctrf.report.path", "${user.home}/ctrf.json",
                "ctrf.build.name", "nightly",
                "ctrf.app.name", "${ctrf.build.name}-app",
                "ctrf.commit", "${GIT_SHA}",
                "ctrf.report.name", "limit ${ctrf.max.message.length}"));

        assertEquals("/home/ci/ctrf.json", reader.getReportPath());
        assertEquals("nightly-app", reader.getAppName());
        assertEquals("c4093eb", reader.getCommit());
        assertEquals("limit 500", reader.getReportName());
    }

    @Test
    @DisplayName("row 5: an unknown ${no.such.key} expands to an empty string")
    void unknownReferenceExpandsToAnEmptyString() {
        var reader = new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.report.path", "${no.such.key}/report.json"));

        assertEquals("/report.json", reader.getReportPath());
    }

    @Test
    @DisplayName("row 6: a cyclic reference warns and expands to an empty string, instead of overflowing the stack")
    void cyclicReferenceWarnsAndExpandsToAnEmptyString() {
        var reader = new ConfigReader(Map.of(), Map.of(),
            Map.of("ctrf.app.name", "app-${ctrf.build.name}", "ctrf.build.name", "build-${ctrf.app.name}"));

        assertEquals("app-build-", reader.getAppName());
        assertEquals("build-app-", reader.getBuildName());
        assertTrue(logs.text().contains("refers back to itself"), logs.text());
    }

    @Test
    @DisplayName("An unclosed ${ stays as it is")
    void unclosedReferenceStaysAsItIs() {
        assertEquals("${unclosed", new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.build.name", "${unclosed")).getBuildName());
    }

    @Test
    @DisplayName("row 7: trailing spaces of a string are kept")
    void trailingSpacesOfAStringAreKept() {
        assertEquals("My Report  ", new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.report.name", "My Report  ")).getReportName());
    }

    @Test
    @DisplayName("row 8: an empty value stays empty")
    void emptyValueStaysEmpty() {
        assertEquals("", new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.os.version", "")).getOsVersion());
    }

    @Test
    @DisplayName("row 9: an unset key gives its default, or null")
    void unsetKeyGivesItsDefaultOrNull() {
        var reader = new ConfigReader(Map.of(), Map.of(), Map.of());

        assertEquals("ctrf-report.json", reader.getReportPath());
        assertEquals(500, reader.getMaxMessageLength());
        assertFalse(reader.calculateStartupDuration());
        assertNull(reader.getJUnitVersion());
        assertNull(reader.getReportName());
        assertNull(reader.getAppName());
        assertNull(reader.getAppVersion());
        assertNull(reader.getBuildName());
        assertNull(reader.getBuildNumber());
        assertNull(reader.getBuildUrl());
        assertNull(reader.getRepositoryName());
        assertNull(reader.getRepositoryUrl());
        assertNull(reader.getCommit());
        assertNull(reader.getBranchName());
        assertNull(reader.getOsPlatform());
        assertNull(reader.getOsRelease());
        assertNull(reader.getOsVersion());
        assertNull(reader.getTestEnvironment());
        assertNull(reader.getRunId());
        assertEquals(List.of(), logs.messages());
    }

    @Test
    @DisplayName("row 10: a number with spaces around it is read")
    void numberWithSpacesAroundItIsRead() {
        assertEquals(300, new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.max.message.length", " 300 ")).getMaxMessageLength());
        assertEquals(List.of(), logs.messages());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"abc", "-5", "", "1.5", "99999999999"})
    @DisplayName("row 11: an invalid number or a negative length warns once, and the default is used")
    void invalidLengthWarnsAndUsesTheDefault(String value) {
        var reader = new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.max.message.length", value));

        assertEquals(500, reader.getMaxMessageLength());
        assertEquals(500, reader.getMaxMessageLength());
        assertEquals(1, logs.messages().size(), logs.text());
        assertTrue(logs.text().contains("ctrf.max.message.length"), logs.text());
        assertTrue(logs.text().contains("\"" + value + "\""), logs.text());
    }

    @Test
    @DisplayName("row 12: a boolean ignores case")
    void booleanIgnoresCase() {
        assertTrue(new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.calculate.startup.duration", "TRUE")).calculateStartupDuration());
        assertFalse(new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.calculate.startup.duration", "False")).calculateStartupDuration());
    }

    @Test
    @DisplayName("A boolean with spaces around it is read; owner threw")
    void booleanWithSpacesAroundItIsRead() {
        assertTrue(new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.calculate.startup.duration", " true ")).calculateStartupDuration());
        assertEquals(List.of(), logs.messages());
    }

    @Test
    @DisplayName("row 13: an invalid boolean warns, and the default is used")
    void invalidBooleanWarnsAndUsesTheDefault() {
        var reader = new ConfigReader(Map.of(), Map.of(), Map.of("ctrf.calculate.startup.duration", "yes"));

        assertFalse(reader.calculateStartupDuration());
        assertEquals(1, logs.messages().size(), logs.text());
        assertTrue(logs.text().contains("ctrf.calculate.startup.duration"), logs.text());
    }

    @Test
    @DisplayName("Review Focus 1: CTRF_* variables are found under a Turkish default locale")
    void upperCaseVariablesAreFoundUnderATurkishLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            var reader = new ConfigReader(Map.of(),
                Map.of("CTRF_BUILD_URL", "https://ci.example.com/7", "CTRF_CALCULATE_STARTUP_DURATION", "TRUE"), Map.of());

            assertEquals("https://ci.example.com/7", reader.getBuildUrl());
            assertTrue(reader.calculateStartupDuration());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("The no-argument constructor reads this JVM's system properties")
    void defaultConstructorReadsThisJvmsSystemProperties() {
        System.setProperty("ctrf.report.name", "from this JVM");
        try {
            assertEquals("from this JVM", new ConfigReader().getReportName());
        } finally {
            System.clearProperty("ctrf.report.name");
        }
    }

    @Test
    @DisplayName("Review Focus 2: ctrf.properties is read as UTF-8, as owner read it")
    void propertiesFileIsReadAsUtf8(@TempDir Path directory) throws Exception {
        String name = "Zahlungsdienst M" + (char) 0xFC + "ller";
        Files.writeString(directory.resolve("ctrf.properties"), "ctrf.app.name=" + name + "\n", StandardCharsets.UTF_8);

        assertEquals(name, readWithPropertiesFrom(directory).getAppName());
    }

    @Test
    @DisplayName("Review Focus 2: a ctrf.properties that isn't valid UTF-8 is read as ISO-8859-1")
    void propertiesFileThatIsNotUtf8IsReadAsIso88591(@TempDir Path directory) throws Exception {
        String name = "Caf" + (char) 0xE9;
        Files.writeString(directory.resolve("ctrf.properties"), "ctrf.app.name=" + name + "\n", StandardCharsets.ISO_8859_1);

        assertEquals(name, readWithPropertiesFrom(directory).getAppName());
    }

    @Test
    @DisplayName("A ctrf.properties that can't be parsed is ignored, with a warning")
    void malformedPropertiesFileIsIgnoredWithAWarning(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("ctrf.properties"), "ctrf.app.name=\\uZZZZ\n");

        assertNull(readWithPropertiesFrom(directory).getAppName());
        assertTrue(logs.text().contains("ctrf.properties"), logs.text());
    }

    /**
     * Creates a reader with the directory on the context class loader's classpath, as Gradle and Surefire put
     * {@code src/test/resources}.
     */
    private static ConfigReader readWithPropertiesFrom(Path directory) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try (var loader = new URLClassLoader(new URL[] {directory.toUri().toURL()}, original)) {
            thread.setContextClassLoader(loader);
            return new ConfigReader();
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}

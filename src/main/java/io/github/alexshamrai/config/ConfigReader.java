package io.github.alexshamrai.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the reporter's configuration, once, when it is created.
 * <p>
 * Each key is looked up in these sources, and the first one that sets it wins:
 * <ol>
 *   <li>the system property, such as {@code ctrf.report.path};</li>
 *   <li>the environment variable with the same name, then, for a {@code ctrf.*} key, the name in upper case with
 *       underscores, such as {@code CTRF_REPORT_PATH};</li>
 *   <li>{@code ctrf.properties} on the classpath, found through the context class loader, then through the library's
 *       own. It is read as UTF-8, or as ISO-8859-1 if it isn't valid UTF-8;</li>
 *   <li>the key's default.</li>
 * </ol>
 * A value can use any name these sources set as {@code ${name}}, which is looked up the same way; a name that nothing
 * sets expands to an empty string. A number or a boolean that can't be read, or a negative length, logs a warning and
 * the default is used.
 */
public class ConfigReader {

    private static final Logger LOGGER = Logger.getLogger(ConfigReader.class.getName());
    private static final String PROPERTIES_FILE = "ctrf.properties";
    private static final String CTRF_PREFIX = "ctrf.";
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{(.+?)}");

    private static final String REPORT_PATH = "ctrf.report.path";
    private static final String MAX_MESSAGE_LENGTH = "ctrf.max.message.length";
    private static final String MAX_TRACE_LENGTH = "ctrf.max.trace.length";
    private static final String CALCULATE_STARTUP_DURATION = "ctrf.calculate.startup.duration";
    private static final String BYTE_ORDER_MARK = String.valueOf((char) 0xFEFF);
    private static final Map<String, String> DEFAULTS = Map.of(
        REPORT_PATH, "ctrf-report.json",
        MAX_MESSAGE_LENGTH, "500",
        MAX_TRACE_LENGTH, "20000",
        CALCULATE_STARTUP_DURATION, "false");

    private final Map<String, String> systemProperties;
    private final Map<String, String> environment;
    private final Map<String, String> fileProperties;

    private final String reportPath;
    private final int maxMessageLength;
    private final int maxTraceLength;
    private final String junitVersion;
    private final String reportName;
    private final String appName;
    private final String appVersion;
    private final String buildName;
    private final String buildNumber;
    private final String buildUrl;
    private final String repositoryName;
    private final String repositoryUrl;
    private final String commit;
    private final String branchName;
    private final String osPlatform;
    private final String osRelease;
    private final String osVersion;
    private final String testEnvironment;
    private final boolean calculateStartupDuration;
    private final String runId;

    /**
     * Creates a reader of this JVM's system properties, its environment variables and {@code ctrf.properties}.
     */
    public ConfigReader() {
        this(toMap(System.getProperties()), System.getenv(), loadPropertiesFile());
    }

    /**
     * Creates a reader of the given sources only, for tests and for code that runs the reporter itself.
     *
     * @param systemProperties the system properties
     * @param environment      the environment variables
     * @param fileProperties   the contents of {@code ctrf.properties}
     * @throws NullPointerException if a map, or a key or a value in one, is {@code null}
     */
    public ConfigReader(Map<String, String> systemProperties, Map<String, String> environment,
                        Map<String, String> fileProperties) {
        this.systemProperties = Map.copyOf(systemProperties);
        this.environment = Map.copyOf(environment);
        this.fileProperties = Map.copyOf(fileProperties);

        this.reportPath = string(REPORT_PATH);
        this.maxMessageLength = length(MAX_MESSAGE_LENGTH);
        this.maxTraceLength = length(MAX_TRACE_LENGTH);
        this.junitVersion = string("junit.version");
        this.reportName = string("ctrf.report.name");
        this.appName = string("ctrf.app.name");
        this.appVersion = string("ctrf.app.version");
        this.buildName = string("ctrf.build.name");
        this.buildNumber = string("ctrf.build.number");
        this.buildUrl = string("ctrf.build.url");
        this.repositoryName = string("ctrf.repository.name");
        this.repositoryUrl = string("ctrf.repository.url");
        this.commit = string("ctrf.commit");
        this.branchName = string("ctrf.branch.name");
        this.osPlatform = string("ctrf.os.platform");
        this.osRelease = string("ctrf.os.release");
        this.osVersion = string("ctrf.os.version");
        this.testEnvironment = string("ctrf.test.environment");
        this.calculateStartupDuration = bool(CALCULATE_STARTUP_DURATION);
        this.runId = string("ctrf.run.id");
    }

    public String getReportPath() {
        return reportPath;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    /**
     * Returns the longest {@code trace} the report holds, in characters; 0 means no limit.
     *
     * @return {@code ctrf.max.trace.length}, 20000 by default
     */
    public int getMaxTraceLength() {
        return maxTraceLength;
    }

    public String getJUnitVersion() {
        return junitVersion;
    }

    public String getReportName() {
        return reportName;
    }

    public String getAppName() {
        return appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getBuildName() {
        return buildName;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getCommit() {
        return commit;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getOsPlatform() {
        return osPlatform;
    }

    public String getOsRelease() {
        return osRelease;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getTestEnvironment() {
        return testEnvironment;
    }

    public boolean calculateStartupDuration() {
        return calculateStartupDuration;
    }

    public String getRunId() {
        return runId;
    }

    private String string(String key) {
        return resolve(key, new HashSet<>());
    }

    /**
     * A length: a whole number of 0 or more, with spaces around it allowed. Anything else warns and gives the default.
     */
    private int length(String key) {
        String value = string(key);
        int fallback = Integer.parseInt(DEFAULTS.get(key));
        try {
            int parsed = Integer.parseInt(value.strip());
            if (parsed >= 0) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            // Warned below
        }
        LOGGER.warning(key + " must be a whole number of 0 or more, but is \"" + value + "\"; using the default, " + fallback);
        return fallback;
    }

    /**
     * A boolean: true or false in any case, with spaces around it allowed. Anything else warns and gives the default.
     */
    private boolean bool(String key) {
        String value = string(key);
        String trimmed = value.strip();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        boolean fallback = Boolean.parseBoolean(DEFAULTS.get(key));
        LOGGER.warning(key + " must be true or false, but is \"" + value + "\"; using the default, " + fallback);
        return fallback;
    }

    /**
     * The value of a key with its references expanded, or {@code null} if no source sets it and it has no default.
     *
     * @param resolving the keys being expanded, to detect a reference back to one of them
     */
    private String resolve(String key, Set<String> resolving) {
        String raw = rawValue(key);
        if (raw == null) {
            return null;
        }
        resolving.add(key);
        try {
            return expand(raw, resolving);
        } finally {
            resolving.remove(key);
        }
    }

    private String expand(String value, Set<String> resolving) {
        Matcher reference = REFERENCE.matcher(value);
        StringBuilder expanded = new StringBuilder();
        while (reference.find()) {
            String name = reference.group(1);
            String replacement;
            if (resolving.contains(name)) {
                LOGGER.warning("${" + name + "} refers back to itself in the CTRF configuration, so it expands to an empty string");
                replacement = "";
            } else {
                String resolved = resolve(name, resolving);
                replacement = resolved != null ? resolved : "";
            }
            reference.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        }
        reference.appendTail(expanded);
        return expanded.toString();
    }

    private String rawValue(String key) {
        String value = systemProperties.get(key);
        if (value == null) {
            value = environment.get(key);
        }
        if (value == null && key.startsWith(CTRF_PREFIX)) {
            // Locale.ROOT: under a Turkish locale, "i" would become a dotted capital I
            value = environment.get(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        }
        if (value == null) {
            value = fileProperties.get(key);
        }
        return value != null ? value : DEFAULTS.get(key);
    }

    private static Map<String, String> loadPropertiesFile() {
        try (InputStream in = openPropertiesFile()) {
            if (in == null) {
                return Map.of();
            }
            var properties = new Properties();
            properties.load(new StringReader(decode(in.readAllBytes())));
            return toMap(properties);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warning(PROPERTIES_FILE + " can't be read, so it is ignored - " + e.getMessage());
            return Map.of();
        }
    }

    private static InputStream openPropertiesFile() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        InputStream in = context != null ? context.getResourceAsStream(PROPERTIES_FILE) : null;
        return in != null ? in : ConfigReader.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE);
    }

    // UTF-8, as owner read it, without a leading byte order mark, which owner kept as part of the first key;
    // ISO-8859-1, the traditional encoding of .properties files, when it isn't valid UTF-8
    private static String decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
            return text.startsWith(BYTE_ORDER_MARK) ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static Map<String, String> toMap(Properties properties) {
        Map<String, String> map = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return map;
    }
}

package io.github.alexshamrai.config;

import org.aeonbits.owner.ConfigFactory;

/**
 * A reader for configuration properties defined in the {@link CtrfConfig} interface.
 */
public class ConfigReader {

    private final CtrfConfig config;

    /**
     * Creates a new {@code ConfigReader} with the configuration in ctrf.properties.
     */
    public ConfigReader() {
        this.config = ConfigFactory.create(CtrfConfig.class);
    }

    /**
     * Creates a new {@code ConfigReader} with the specified configuration.
     * This constructor is primarily used for testing purposes.
     *
     * @param config the configuration to use
     */
    public ConfigReader(CtrfConfig config) {
        this.config = config;
    }

    public String getReportPath() {
        return config.reportPath();
    }

    public int getMaxMessageLength() {
        return config.maxMessageLength();
    }

    public String getJUnitVersion() {
        return config.junitVersion();
    }

    public String getReportName() {
        return config.reportName();
    }

    public String getAppName() {
        return config.appName();
    }

    public String getAppVersion() {
        return config.appVersion();
    }

    public String getBuildName() {
        return config.buildName();
    }

    public String getBuildNumber() {
        return config.buildNumber();
    }

    public String getBuildUrl() {
        return config.buildUrl();
    }

    public String getRepositoryName() {
        return config.repositoryName();
    }

    public String getRepositoryUrl() {
        return config.repositoryUrl();
    }

    public String getCommit() {
        return config.commit();
    }

    public String getBranchName() {
        return config.branchName();
    }

    public String getOsPlatform() {
        return config.osPlatform();
    }

    public String getOsRelease() {
        return config.osRelease();
    }

    public String getOsVersion() {
        return config.osVersion();
    }

    public String getTestEnvironment() {
        return config.testEnvironment();
    }

    public boolean calculateStartupDuration() {
        return config.calculateStartupDuration();
    }

    public String getRunId() {
        return config.runId();
    }
}

package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Results;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.ctrf.model.Tool;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Composes the final CTRF JSON object from test results and configuration.
 * <p>
 * This class is responsible for creating the complete CTRF JSON structure according
 * to the CTRF standard, including tool information, test environment details,
 * and test results.
 */
@RequiredArgsConstructor
public class CtrfJsonComposer {

    private static final String TOOL_NAME = "JUnit";
    private final ConfigReader configReader;
    private final StartupDurationProcessor startupDurationProcessor;
    private final String generatedBy;

    /**
     * Generates a complete CTRF JSON object containing test results and metadata.
     *
     * @param summary the test execution summary
     * @param tests the list of test results
     * @param isEnvironmentHealthy whether the test environment is considered healthy
     * @return a complete CTRF JSON object ready for serialization
     */
    public CtrfJson generateCtrfJson(Summary summary, List<Test> tests, boolean isEnvironmentHealthy) {
        if (configReader.calculateStartupDuration()) {
            startupDurationProcessor.processStartupDuration(summary, tests);
        }

        var results = Results.builder()
            .tool(composeTool())
            .summary(summary)
            .tests(tests)
            .environment(composeEnvironment(isEnvironmentHealthy))
            .build();

        return CtrfJson.builder()
            .results(results)
            .reportId(UUID.randomUUID().toString())
            .timestamp(Instant.now().toString())
            .generatedBy(this.generatedBy)
            .build();
    }

    /**
     * Creates the tool section of the CTRF JSON, which identifies JUnit as the test tool.
     *
     * @return a Tool object containing JUnit information
     */
    private Tool composeTool() {
        var toolBuilder = Tool.builder()
            .name(TOOL_NAME)
            .version(configReader.getJUnitVersion());

        return toolBuilder.build();
    }

    /**
     * Creates the environment section of the CTRF JSON, including details about
     * the application, build, repository, operating system, and test environment.
     *
     * @param isEnvironmentHealthy whether the test environment is considered healthy
     * @return an Environment object containing all available environment information
     */
    private Environment composeEnvironment(boolean isEnvironmentHealthy) {
        return Environment.builder()
            .reportName(configReader.getReportName())
            .appName(configReader.getAppName())
            .appVersion(configReader.getAppVersion())
            .buildName(configReader.getBuildName())
            .buildNumber(configReader.getBuildNumber())
            .buildUrl(configReader.getBuildUrl())
            .repositoryName(configReader.getRepositoryName())
            .repositoryUrl(configReader.getRepositoryUrl())
            .commit(configReader.getCommit())
            .branchName(configReader.getBranchName())
            .osPlatform(configReader.getOsPlatform())
            .osRelease(configReader.getOsRelease())
            .osVersion(configReader.getOsVersion())
            .testEnvironment(configReader.getTestEnvironment())
            .healthy(isEnvironmentHealthy)
            .build();
    }
}
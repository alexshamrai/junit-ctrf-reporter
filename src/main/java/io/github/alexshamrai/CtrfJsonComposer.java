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
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
    private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]+");
    private static final Logger LOGGER = Logger.getLogger(CtrfJsonComposer.class.getName());
    private final ConfigReader configReader;
    private final StartupDurationProcessor startupDurationProcessor;
    private final String generatedBy;

    /**
     * Generates a complete CTRF JSON object containing test results and metadata.
     *
     * @param summary the test execution summary
     * @param tests the list of test results
     * @param isEnvironmentHealthy whether the test environment is considered healthy
     * @param runId the run this report belongs to (CTRF {@code runId})
     * @return a complete CTRF JSON object ready for serialization
     */
    public CtrfJson generateCtrfJson(Summary summary, List<Test> tests, boolean isEnvironmentHealthy, String runId) {
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
            .runId(runId)
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
        var environment = Environment.builder()
            .reportName(configReader.getReportName())
            .appName(configReader.getAppName())
            .appVersion(configReader.getAppVersion())
            .buildName(configReader.getBuildName())
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
        setBuildNumber(environment, configReader.getBuildNumber());
        return environment;
    }

    /**
     * CTRF types {@code buildNumber} as an integer. A whole number goes there; any other value goes to
     * {@code buildId}, with a warning, so the report stays valid. A blank value sets neither.
     * It takes the built Environment, not its Lombok builder, which javadoc can't resolve.
     */
    private static void setBuildNumber(Environment environment, String configured) {
        if (configured == null || configured.isBlank()) {
            return;
        }
        var value = configured.strip();
        var number = toWholeNumber(value);
        if (number != null) {
            environment.setBuildNumber(number);
            return;
        }
        LOGGER.warning("ctrf.build.number is not a whole number, so the report gives it as buildId: " + value);
        environment.setBuildId(value);
    }

    private static Long toWholeNumber(String value) {
        if (!WHOLE_NUMBER.matcher(value).matches()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // More digits than a long holds
            return null;
        }
    }
}
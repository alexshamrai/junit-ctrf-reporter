package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CtrfJsonComposerTest {

    private static final String DEFAULT_REPORT_FORMAT = "CTRF";
    private static final String DEFAULT_SPEC_VERSION = "0.2.0";
    @RegisterExtension
    final LogCapture logs = new LogCapture(CtrfJsonComposer.class);
    private ConfigReader configReader;
    private StartupDurationProcessor startupDurationProcessor;
    private CtrfJsonComposer composer;

    @BeforeEach
    void setUp() {
        configReader = Mockito.mock(ConfigReader.class);
        startupDurationProcessor = new StartupDurationProcessor();
        composer = new CtrfJsonComposer(configReader, startupDurationProcessor, "Listener");

        Mockito.when(configReader.getJUnitVersion()).thenReturn("mockedJUnitVersion");
        Mockito.when(configReader.getReportName()).thenReturn("mockedReportName");
        Mockito.when(configReader.getAppName()).thenReturn("mockedAppName");
        Mockito.when(configReader.getAppVersion()).thenReturn("mockedAppVersion");
        Mockito.when(configReader.getBuildName()).thenReturn("mockedBuildName");
        Mockito.when(configReader.getBuildNumber()).thenReturn("777");
        Mockito.when(configReader.getBuildUrl()).thenReturn("mockedBuildUrl");
        Mockito.when(configReader.getRepositoryName()).thenReturn("mockedRepositoryName");
        Mockito.when(configReader.getRepositoryUrl()).thenReturn("mockedRepositoryUrl");
        Mockito.when(configReader.getCommit()).thenReturn("mockedCommit");
        Mockito.when(configReader.getBranchName()).thenReturn("mockedBranchName");
        Mockito.when(configReader.getOsPlatform()).thenReturn("mockedOsPlatform");
        Mockito.when(configReader.getOsRelease()).thenReturn("mockedOsRelease");
        Mockito.when(configReader.getOsVersion()).thenReturn("mockedOsVersion");
        Mockito.when(configReader.getTestEnvironment()).thenReturn("mockedTestEnvironment");
        Mockito.when(configReader.calculateStartupDuration()).thenReturn(false);
    }

    @org.junit.jupiter.api.Test
    void testGenerateCtrfJsonWithValidInput() {
        var summary = Summary.builder().build();
        var tests = List.of(Test.builder().build(), Test.builder().build());

        var result = composer.generateCtrfJson(summary, tests, true, "run-1");

        assertNotNull(result);
        assertNotNull(result.getResults());
        assertEquals(tests, result.getResults().getTests());
        assertEquals(summary, result.getResults().getSummary());

        assertEquals(DEFAULT_REPORT_FORMAT, result.getReportFormat());
        assertEquals(DEFAULT_SPEC_VERSION, result.getSpecVersion());

        var tool = result.getResults().getTool();
        assertNotNull(tool);
        assertEquals("JUnit", tool.getName());
        assertEquals("mockedJUnitVersion", tool.getVersion());

        Environment environment = result.getResults().getEnvironment();
        assertNotNull(environment);
        assertEquals("mockedReportName", environment.getReportName());
        assertEquals("mockedAppName", environment.getAppName());
        assertEquals("mockedAppVersion", environment.getAppVersion());
        assertEquals("mockedBuildName", environment.getBuildName());
        assertEquals(777L, environment.getBuildNumber());
        assertNull(environment.getBuildId());
        assertEquals("mockedBuildUrl", environment.getBuildUrl());
        assertEquals("mockedRepositoryName", environment.getRepositoryName());
        assertEquals("mockedRepositoryUrl", environment.getRepositoryUrl());
        assertEquals("mockedCommit", environment.getCommit());
        assertEquals("mockedBranchName", environment.getBranchName());
        assertEquals("mockedOsPlatform", environment.getOsPlatform());
        assertEquals("mockedOsRelease", environment.getOsRelease());
        assertEquals("mockedOsVersion", environment.getOsVersion());
        assertEquals("mockedTestEnvironment", environment.getTestEnvironment());
        assertEquals(true, environment.isHealthy());
    }

    @org.junit.jupiter.api.Test
    void testGenerateCtrfJsonWithEmptyTestList() {
        var summary = Summary.builder().build();
        List<Test> tests = Collections.emptyList();

        var result = composer.generateCtrfJson(summary, tests, true, "run-1");

        assertNotNull(result);
        assertNotNull(result.getResults());
        assertEquals(tests, result.getResults().getTests());
        assertEquals(summary, result.getResults().getSummary());
    }

    @org.junit.jupiter.api.Test
    void testGenerateCtrfJsonWithNullSummary() {
        var tests = List.of(Test.builder().build());

        var result = composer.generateCtrfJson(null, tests, true, "run-1");

        assertNotNull(result);
        assertNotNull(result.getResults());
        assertEquals(tests, result.getResults().getTests());
        assertNull(result.getResults().getSummary());
    }

    @org.junit.jupiter.api.Test
    void testGenerateCtrfJsonWithStartupDurationCalculation() {
        Mockito.when(configReader.calculateStartupDuration()).thenReturn(true);

        long summaryStartTime = 2000L;
        var summary = Summary.builder()
            .start(summaryStartTime)
            .build();

        long firstTestStartTime = 5000L;
        long secondTestStartTime = 6000L;
        var tests = List.of(
            Test.builder().start(secondTestStartTime).build(),
            Test.builder().start(firstTestStartTime).build()
        );

        long expectedStartupDuration = 3000L;

        var result = composer.generateCtrfJson(summary, tests, true, "run-1");

        assertNotNull(result);
        assertNotNull(result.getResults());
        assertNotNull(result.getResults().getSummary());
        assertNotNull(result.getResults().getSummary().getExtra());
        assertEquals(expectedStartupDuration, result.getResults().getSummary().getExtra().getStartupDuration());
    }

    @org.junit.jupiter.api.Test
    void testGenerateCtrfJsonWithUnhealthyEnvironment() {
        var summary = Summary.builder().build();
        var tests = List.of(Test.builder().build());

        var result = composer.generateCtrfJson(summary, tests, false, "run-1");

        assertNotNull(result);
        Environment environment = result.getResults().getEnvironment();
        assertNotNull(environment);
        assertEquals(false, environment.isHealthy());
    }

    @org.junit.jupiter.api.Test
    void testGenerateCtrfJsonSetsTheRunIdAndANewReportIdEachTime() {
        var first = composer.generateCtrfJson(Summary.builder().build(), List.of(), true, "run-1");
        var second = composer.generateCtrfJson(Summary.builder().build(), List.of(), true, "run-1");

        assertEquals("run-1", first.getRunId());
        assertNotEquals(first.getReportId(), second.getReportId());
    }

    @ParameterizedTest(name = "\"{0}\" is buildNumber {1}")
    @CsvSource({"777, 777", "' 777 ', 777", "0, 0", "00123, 123", "9223372036854775807, 9223372036854775807"})
    void wholeBuildNumberIsWrittenAsBuildNumber(String configured, long expected) {
        Mockito.when(configReader.getBuildNumber()).thenReturn(configured);

        var environment = composeEnvironment();

        assertEquals(expected, environment.getBuildNumber());
        assertNull(environment.getBuildId());
        assertEquals(List.of(), logs.messages());
    }

    @ParameterizedTest(name = "\"{0}\" is buildId \"{1}\"")
    @CsvSource({
        "2026.10.15-3, 2026.10.15-3",
        "' 2026.10.15-3 ', 2026.10.15-3",
        "+777, +777",
        "-1, -1",
        "1e3, 1e3",
        "9223372036854775808, 9223372036854775808",
        "١٢٣, ١٢٣"
    })
    void otherBuildNumberIsWrittenAsBuildIdWithOneWarning(String configured, String expected) {
        Mockito.when(configReader.getBuildNumber()).thenReturn(configured);

        var environment = composeEnvironment();

        assertNull(environment.getBuildNumber());
        assertEquals(expected, environment.getBuildId());
        var warnings = logs.messages();
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("buildId"));
        assertTrue(warnings.get(0).contains(expected));
    }

    @ParameterizedTest(name = "\"{0}\" sets neither field")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankBuildNumberSetsNeitherField(String configured) {
        Mockito.when(configReader.getBuildNumber()).thenReturn(configured);

        var environment = composeEnvironment();

        assertNull(environment.getBuildNumber());
        assertNull(environment.getBuildId());
        assertEquals(List.of(), logs.messages());
    }

    private Environment composeEnvironment() {
        return composer.generateCtrfJson(Summary.builder().build(), List.of(), true, "run-1").getResults().getEnvironment();
    }
}

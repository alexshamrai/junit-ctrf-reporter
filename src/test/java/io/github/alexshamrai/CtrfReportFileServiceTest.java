package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.config.CtrfConfig;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Results;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public class CtrfReportFileServiceTest {

    private ConfigReader configReader;
    private CtrfReportFileService ctrfReportFileService;
    private CtrfJson ctrfJson;
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalErr = System.err;
    private final String filePath = "ctrf.json";

    @BeforeEach
    void setup() {
        var customConfig = new HashMap<String, String>();
        customConfig.put("ctrf.report.path", filePath);
        var mockConfig = ConfigFactory.create(CtrfConfig.class, customConfig);
        configReader = new ConfigReader(mockConfig);
        ctrfReportFileService = new CtrfReportFileService(configReader);
        ctrfJson = new CtrfJson();
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(Paths.get(filePath));
        System.setErr(originalErr);
    }

    @Test
    void shouldSuccessfullyWriteResultsToFile() throws IOException {
        ctrfReportFileService.writeResultsToFile(ctrfJson);

        var path = Paths.get(filePath);
        assertThat(Files.exists(path)).isTrue();
        assertThat(Files.readString(path)).isNotEmpty();
    }

    @Test
    void shouldOverwriteExistingFile() throws IOException {
        var path = Paths.get(filePath);
        Files.createFile(path);
        var initialModifiedTime = Files.getLastModifiedTime(path).toMillis();

        ctrfReportFileService.writeResultsToFile(ctrfJson);
        var newModifiedTime = Files.getLastModifiedTime(path).toMillis();

        assertThat(initialModifiedTime).isNotEqualTo(newModifiedTime);
        assertThat(errContent.toString()).isEmpty();
    }

    @Test
    void shouldHandleAccessDeniedException() {
        var nestedFilePath = "nested/ctrf.json";
        configReader = createConfigReaderWithPath(nestedFilePath);
        ctrfReportFileService = new CtrfReportFileService(configReader);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.createDirectories(Paths.get("nested"))).thenThrow(new AccessDeniedException("nested"));
            mockedFiles.when(() -> Files.exists(any())).thenReturn(false);

            ctrfReportFileService.writeResultsToFile(ctrfJson);

            mockedFiles.verify(() -> Files.createDirectories(Paths.get("nested")), times(1));
            assertThat(errContent.toString()).contains("Access denied: " + nestedFilePath);
        }
    }

    @Test
    void shouldHandleIoException() {
        var nestedFilePath = "test-dir/ctrf.json";
        configReader = createConfigReaderWithPath(nestedFilePath);
        ctrfReportFileService = new CtrfReportFileService(configReader);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.createDirectories(Paths.get("test-dir"))).thenThrow(new IOException("IO error"));
            mockedFiles.when(() -> Files.exists(any())).thenReturn(false);

            ctrfReportFileService.writeResultsToFile(ctrfJson);

            mockedFiles.verify(() -> Files.createDirectories(Paths.get("test-dir")), times(1));
            assertThat(errContent.toString()).contains("Failed to write results to file: " + nestedFilePath);
        }
    }

    private ConfigReader createConfigReaderWithPath(String path) {
        var customConfig = new HashMap<String, String>();
        customConfig.put("ctrf.report.path", path);
        var mockConfig = ConfigFactory.create(CtrfConfig.class, customConfig);
        return new ConfigReader(mockConfig);
    }

    @Test
    void shouldReturnEmptyListWhenReportFileDoesNotExist() {
        List<io.github.alexshamrai.ctrf.model.Test> tests = ctrfReportFileService.getExistingTests();

        assertThat(tests).isEmpty();
    }

    @Test
    void shouldReturnTestsFromExistingReport() {
        var test1 = io.github.alexshamrai.ctrf.model.Test.builder().name("Test1").build();
        var test2 = io.github.alexshamrai.ctrf.model.Test.builder().name("Test2").build();
        var testsList = new ArrayList<io.github.alexshamrai.ctrf.model.Test>();
        testsList.add(test1);
        testsList.add(test2);

        var results = Results.builder().tests(testsList).build();
        var ctrfJsonWithTests = CtrfJson.builder().results(results).build();

        ctrfReportFileService.writeResultsToFile(ctrfJsonWithTests);

        var existingTests = ctrfReportFileService.getExistingTests();

        assertThat(existingTests).hasSize(2);
        assertThat(existingTests).extracting("name").containsExactly("Test1", "Test2");
    }

    @Test
    void shouldHandleErrorWhenReadingExistingReport() throws IOException {
        Files.writeString(Paths.get(filePath), "invalid json");

        var existingTests = ctrfReportFileService.getExistingTests();

        assertThat(existingTests).isEmpty();
        assertThat(errContent.toString()).contains("Failed to read existing report file");
    }

    @Test
    void shouldReturnEmptyListWhenReportHasNullResults() {
        var ctrfJsonWithNullResults = CtrfJson.builder().results(null).build();

        ctrfReportFileService.writeResultsToFile(ctrfJsonWithNullResults);

        var existingTests = ctrfReportFileService.getExistingTests();

        assertThat(existingTests).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenReportHasNullTestsList() {
        var results = Results.builder().tests(null).build();
        var ctrfJsonWithNullTests = CtrfJson.builder().results(results).build();

        ctrfReportFileService.writeResultsToFile(ctrfJsonWithNullTests);

        var existingTests = ctrfReportFileService.getExistingTests();

        assertThat(existingTests).isEmpty();
    }

    @Test
    void shouldReturnNullWhenReportFileDoesNotExist() {
        var startTime = ctrfReportFileService.getExistingStartTime();

        assertThat(startTime).isNull();
    }

    @Test
    void shouldReturnStartTimeFromExistingReport() {
        var expectedStartTime = 1234567890L;
        var summary = io.github.alexshamrai.ctrf.model.Summary.builder().start(expectedStartTime).build();
        var results = Results.builder().summary(summary).build();
        var ctrfJsonWithSummary = CtrfJson.builder().results(results).build();

        ctrfReportFileService.writeResultsToFile(ctrfJsonWithSummary);

        var existingStartTime = ctrfReportFileService.getExistingStartTime();

        assertThat(existingStartTime).isEqualTo(expectedStartTime);
    }

    @Test
    void shouldReturnNullWhenExistingReportHasNoSummary() {
        var results = Results.builder().summary(null).build();
        var ctrfJsonWithoutSummary = CtrfJson.builder().results(results).build();

        ctrfReportFileService.writeResultsToFile(ctrfJsonWithoutSummary);

        var existingStartTime = ctrfReportFileService.getExistingStartTime();

        assertThat(existingStartTime).isNull();
    }

    @Test
    void shouldCacheReportAndReadFileOnlyOnce() {
        // Setup a report with all data
        var test1 = io.github.alexshamrai.ctrf.model.Test.builder().name("Test1").build();
        var test2 = io.github.alexshamrai.ctrf.model.Test.builder().name("Test2").build();
        var testsList = List.of(test1, test2);
        var expectedStartTime = 1234567890L;
        var summary = io.github.alexshamrai.ctrf.model.Summary.builder().start(expectedStartTime).build();
        var environment = io.github.alexshamrai.ctrf.model.Environment.builder().healthy(false).build();
        var results = Results.builder()
            .tests(testsList)
            .summary(summary)
            .environment(environment)
            .build();
        var fullReport = CtrfJson.builder().results(results).build();

        ctrfReportFileService.writeResultsToFile(fullReport);

        // Clear the output stream to track only the read operations
        errContent.reset();
        var outContent = new ByteArrayOutputStream();
        var originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            // Call all three getter methods
            var existingTests = ctrfReportFileService.getExistingTests();
            var existingStartTime = ctrfReportFileService.getExistingStartTime();
            var existingHealth = ctrfReportFileService.getExistingEnvironmentHealth();

            // Verify all values are correct
            assertThat(existingTests).hasSize(2);
            assertThat(existingStartTime).isEqualTo(expectedStartTime);
            assertThat(existingHealth).isFalse();

            // Verify the "File already exists" message appears only once
            var output = outContent.toString();
            var messageCount = output.split("File already exists", -1).length - 1;
            assertThat(messageCount).isEqualTo(1);
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void shouldCacheNullResultWhenFileDoesNotExist() {
        // Don't create any file, so readExistingReport returns null

        // Clear the output stream
        errContent.reset();

        // Call all three getter methods multiple times
        var tests1 = ctrfReportFileService.getExistingTests();
        var startTime1 = ctrfReportFileService.getExistingStartTime();
        var health1 = ctrfReportFileService.getExistingEnvironmentHealth();

        var tests2 = ctrfReportFileService.getExistingTests();
        var startTime2 = ctrfReportFileService.getExistingStartTime();
        var health2 = ctrfReportFileService.getExistingEnvironmentHealth();

        // Verify all values are correct defaults
        assertThat(tests1).isEmpty();
        assertThat(startTime1).isNull();
        assertThat(health1).isTrue();

        assertThat(tests2).isEmpty();
        assertThat(startTime2).isNull();
        assertThat(health2).isTrue();

        // Verify no errors were logged multiple times
        assertThat(errContent.toString()).isEmpty();
    }
}

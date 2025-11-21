package io.github.alexshamrai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Test;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * Handles writing the CTRF JSON report to a file on the filesystem.
 * <p>
 * This class takes care of creating necessary directories, handling file system errors,
 * and serializing the CTRF JSON object to a file. The target file path is determined
 * by the configuration provided through {@link ConfigReader}.
 */
@RequiredArgsConstructor
public class CtrfReportFileService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigReader configReader;

    /**
     * Writes the provided CTRF JSON object to a file.
     * <p>
     * The method handles directory creation if needed and logs errors to the standard error
     * if any issues occur during file writing.
     *
     * @param ctrfJson the CTRF JSON object to write to file
     */
    public void writeResultsToFile(CtrfJson ctrfJson) {
        var filePath = configReader.getReportPath();
        var path = Paths.get(filePath);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            objectMapper.writeValue(path.toFile(), ctrfJson);
        } catch (AccessDeniedException e) {
            System.err.println("Access denied: " + filePath + " - " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Failed to write results to file: " + filePath + " - " + e.getMessage());
        }
    }

    /**
     * Gets the list of tests from an existing report file, if available.
     *
     * @return List of tests from the existing report, or an empty list if no report exists
     */
    public List<Test> getExistingTests() {
        CtrfJson existingReport = readExistingReport();
        return existingReport != null
            && existingReport.getResults() != null
            && existingReport.getResults().getTests() != null
            ? existingReport.getResults().getTests()
            : Collections.emptyList();
    }

    /**
     * Gets the start time from an existing report if available.
     * If the start time exists in the previous report, it should be returned.
     * If no report exists or the start time cannot be extracted, returns null.
     *
     * @return Long representing the start time from the existing report, or null if not available
     */
    public Long getExistingStartTime() {
        CtrfJson existingReport = readExistingReport();
        if (existingReport != null && existingReport.getResults() != null
            && existingReport.getResults().getSummary() != null) {
            return existingReport.getResults().getSummary().getStart();
        }
        return null;
    }

    /**
     * Gets the environment health status from an existing report if available.
     * If the environment was marked unhealthy in the previous report, that status should be preserved.
     * If no report exists or the health status cannot be extracted, returns true (healthy by default).
     *
     * @return Boolean representing the health status from the existing report, or true if not available
     */
    public boolean getExistingEnvironmentHealth() {
        var existingReport = readExistingReport();
        if (existingReport != null && existingReport.getResults() != null
            && existingReport.getResults().getEnvironment() != null) {
            return existingReport.getResults().getEnvironment().isHealthy();
        }
        return true;
    }

    /**
     * Reads an existing CTRF JSON report file if it exists.
     *
     * @return CtrfJson object containing the report data, or null if the file doesn't exist or can't be read
     */
    private CtrfJson readExistingReport() {
        var filePath = configReader.getReportPath();
        var path = Paths.get(filePath);

        if (!Files.exists(path)) {
            return null;
        }

        try {
            var ctrf = objectMapper.readValue(path.toFile(), CtrfJson.class);
            System.out.println("File already exists: " + filePath + ". Tests might have been rerun.");
            return ctrf;
        } catch (IOException e) {
            System.err.println("Failed to read existing report file: " + filePath + " - " + e.getMessage());
            return null;
        }
    }
}

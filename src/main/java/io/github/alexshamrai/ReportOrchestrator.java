package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.util.SummaryUtil;

import java.util.List;

/**
 * Orchestrates CTRF report generation and file writing.
 * <p>
 * This class coordinates the final steps of report generation:
 * <ol>
 *   <li>Creates a test summary from test results</li>
 *   <li>Composes the CTRF JSON structure</li>
 *   <li>Writes the report to the configured file location</li>
 * </ol>
 * <p>
 * It delegates the actual JSON composition to {@link CtrfJsonComposer} and
 * file writing to {@link CtrfReportFileService}.
 */
final class ReportOrchestrator {

    private final ConfigReader configReader;
    private final CtrfReportFileService fileService;
    private final CtrfJsonComposer jsonComposer;

    /**
     * Creates a new report orchestrator with nullable JSON composer.
     * <p>
     * If jsonComposer is null, a default instance will be created during report generation.
     *
     * @param configReader the configuration reader
     * @param fileService  the file service for writing reports
     * @param jsonComposer the JSON composer, or null to create a default
     */
    ReportOrchestrator(ConfigReader configReader,
                       CtrfReportFileService fileService,
                       CtrfJsonComposer jsonComposer) {
        this.configReader = configReader;
        this.fileService = fileService;
        this.jsonComposer = jsonComposer;
    }

    /**
     * Generates and writes the CTRF report.
     *
     * @param tests              the test results to include in the report
     * @param testRunStartTime   the test run start time in milliseconds
     * @param testRunStopTime    the test run stop time in milliseconds
     * @param isHealthy          whether the environment is healthy
     * @param generator          the name of the generator (Extension or Listener class name)
     */
    void generateAndWriteReport(List<Test> tests,
                                long testRunStartTime,
                                long testRunStopTime,
                                boolean isHealthy,
                                String generator) {
        var composer = this.jsonComposer;
        if (composer == null) {
            var startupProcessor = new StartupDurationProcessor();
            composer = new CtrfJsonComposer(this.configReader, startupProcessor, generator);
        }

        var summary = SummaryUtil.createSummary(tests, testRunStartTime, testRunStopTime);
        var ctrfJson = composer.generateCtrfJson(summary, tests, isHealthy);

        fileService.writeResultsToFile(ctrfJson);
    }
}

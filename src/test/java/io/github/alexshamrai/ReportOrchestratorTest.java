package io.github.alexshamrai;

import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.util.SummaryUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportOrchestratorTest {

    @Mock
    private ConfigReader configReader;

    @Mock
    private CtrfReportFileService fileService;

    @Mock
    private CtrfJsonComposer jsonComposer;

    private ReportOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should generate and write report with provided composer")
    void shouldGenerateAndWriteReportWithProvidedComposer() {
        orchestrator = new ReportOrchestrator(configReader, fileService, jsonComposer);

        var test = Test.builder().name("test1").build();
        var tests = List.of(test);
        var mockCtrfJson = CtrfJson.builder().build();

        try (MockedStatic<SummaryUtil> summaryUtil = Mockito.mockStatic(SummaryUtil.class)) {
            var mockSummary = new Summary();
            summaryUtil.when(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong()))
                .thenReturn(mockSummary);

            when(jsonComposer.generateCtrfJson(any(Summary.class), anyList(), eq(true)))
                .thenReturn(mockCtrfJson);

            orchestrator.generateAndWriteReport(tests, 1000L, 2000L, true, "TestGenerator");

            summaryUtil.verify(() -> SummaryUtil.createSummary(eq(tests), eq(1000L), eq(2000L)));
            verify(jsonComposer).generateCtrfJson(mockSummary, tests, true);
            verify(fileService).writeResultsToFile(mockCtrfJson);
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should create default composer when null provided")
    void shouldCreateDefaultComposerWhenNull() {
        orchestrator = new ReportOrchestrator(configReader, fileService, null);

        var test = Test.builder().name("test1").build();
        var tests = List.of(test);

        try (MockedStatic<SummaryUtil> summaryUtil = Mockito.mockStatic(SummaryUtil.class)) {
            var mockSummary = new Summary();
            summaryUtil.when(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong()))
                .thenReturn(mockSummary);

            orchestrator.generateAndWriteReport(tests, 1000L, 2000L, true, "TestGenerator");

            verify(fileService).writeResultsToFile(any(CtrfJson.class));
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Should pass environment health status to composer")
    void shouldPassEnvironmentHealthToComposer() {
        orchestrator = new ReportOrchestrator(configReader, fileService, jsonComposer);

        var tests = List.<Test>of();
        var mockCtrfJson = CtrfJson.builder().build();

        try (MockedStatic<SummaryUtil> summaryUtil = Mockito.mockStatic(SummaryUtil.class)) {
            var mockSummary = new Summary();
            summaryUtil.when(() -> SummaryUtil.createSummary(anyList(), anyLong(), anyLong()))
                .thenReturn(mockSummary);

            when(jsonComposer.generateCtrfJson(any(Summary.class), anyList(), eq(false)))
                .thenReturn(mockCtrfJson);

            orchestrator.generateAndWriteReport(tests, 1000L, 2000L, false, "TestGenerator");

            verify(jsonComposer).generateCtrfJson(mockSummary, tests, false);
        }
    }
}

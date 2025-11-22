package io.github.alexshamrai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TestRerunHandlerTest {

    @Mock
    private CtrfReportFileService fileService;

    private TestRerunHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new TestRerunHandler(fileService);
    }

    @Test
    @DisplayName("Should return existing start time when available")
    void shouldReturnExistingStartTime() {
        long existingTime = 1234567890L;
        when(fileService.getExistingStartTime()).thenReturn(existingTime);

        long result = handler.loadOrCreateStartTime();

        assertThat(result).isEqualTo(existingTime);
    }

    @Test
    @DisplayName("Should return current time when no existing start time")
    void shouldReturnCurrentTimeWhenNoExisting() {
        when(fileService.getExistingStartTime()).thenReturn(null);

        long before = System.currentTimeMillis();
        long result = handler.loadOrCreateStartTime();
        long after = System.currentTimeMillis();

        assertThat(result).isBetween(before, after);
    }

    @Test
    @DisplayName("Should detect unhealthy previous run")
    void shouldDetectUnhealthyPreviousRun() {
        when(fileService.getExistingEnvironmentHealth()).thenReturn(false);

        boolean result = handler.wasPreviousRunUnhealthy();

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should detect healthy previous run")
    void shouldDetectHealthyPreviousRun() {
        when(fileService.getExistingEnvironmentHealth()).thenReturn(true);

        boolean result = handler.wasPreviousRunUnhealthy();

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should expose file service")
    void shouldExposeFileService() {
        CtrfReportFileService result = handler.getFileService();

        assertThat(result).isSameAs(fileService);
    }
}

package integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected IntegrationRun run;
    protected ExpectedRun expected;
    protected CtrfJson report;
    protected File reportFile;

    @BeforeAll
    void setup() throws IOException {
        run = IntegrationRun.locate(System.getProperty("ctrf.report.path"), System.getProperty("ctrf.junit.events.dir"));
        expected = ExpectedRun.of(run);
        reportFile = run.reportFile();
        report = objectMapper.readValue(reportFile, CtrfJson.class);
    }
}

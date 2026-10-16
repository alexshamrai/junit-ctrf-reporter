package io.github.alexshamrai;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.github.alexshamrai.config.ConfigReader;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.json.CtrfJsonMapper;
import io.github.alexshamrai.model.TestDetails;
import io.github.alexshamrai.util.SummaryUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validates the library's reports against the official CTRF schema, pinned at {@code schema/ctrf.schema.json}.
 */
class CtrfSchemaConformanceTest {

    private static final Schema SCHEMA = loadPinnedSchema();
    private static final String GENERATED_BY = "io.github.alexshamrai.launcher.CtrfListener";
    private static final String CLASS_NAME = "com.example.PaymentTest";
    private static final String CLASS_ID = "[engine:junit-jupiter]/[class:" + CLASS_NAME + "]";

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final ConfigReader configReader = mock(ConfigReader.class);
    private final TestProcessor processor = new TestProcessor(configReader);
    @RegisterExtension
    final LogCapture logs = new LogCapture(CtrfJsonComposer.class);

    @BeforeEach
    void configureEveryEnvironmentValue() {
        when(configReader.getJUnitVersion()).thenReturn("6.1.3");
        when(configReader.getReportName()).thenReturn("Nightly");
        when(configReader.getAppName()).thenReturn("payments");
        when(configReader.getAppVersion()).thenReturn("1.2.3");
        when(configReader.getBuildName()).thenReturn("system-build");
        when(configReader.getBuildNumber()).thenReturn("777");
        when(configReader.getBuildUrl()).thenReturn("https://ci.example.com/builds/777");
        when(configReader.getRepositoryName()).thenReturn("payments");
        when(configReader.getRepositoryUrl()).thenReturn("https://github.com/example/payments");
        when(configReader.getCommit()).thenReturn("c4093eb");
        when(configReader.getBranchName()).thenReturn("master");
        when(configReader.getOsPlatform()).thenReturn("linux");
        when(configReader.getOsRelease()).thenReturn("6.8");
        when(configReader.getOsVersion()).thenReturn("Ubuntu 24.04");
        when(configReader.getTestEnvironment()).thenReturn("staging");
        when(configReader.getMaxMessageLength()).thenReturn(500);
        when(configReader.calculateStartupDuration()).thenReturn(true);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report with every field the library writes has no schema errors")
    void fullyPopulatedReportConformsToTheSchema() throws IOException {
        assertThat(schemaErrorsOf(composeReport(fullRun()))).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A non-numeric build number is written as buildId, and the report has no schema errors")
    void reportWithABuildIdConformsToTheSchema() throws IOException {
        when(configReader.getBuildNumber()).thenReturn("2026.10.15-3");

        var report = composeReport(fullRun());

        assertThat(objectMapper.readTree(report).at("/results/environment/buildId").asString()).isEqualTo("2026.10.15-3");
        assertThat(schemaErrorsOf(report)).isEmpty();
        assertThat(logs.text()).contains("buildId");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A run in which no test ran has no schema errors")
    void reportOfARunWithoutTestsConformsToTheSchema() throws IOException {
        assertThat(schemaErrorsOf(composeReport(List.of()))).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("The validator rejects a test with the legacy filepath key")
    void legacyFilepathKeyIsRejected() throws IOException {
        var report = (ObjectNode) objectMapper.readTree(composeReport(fullRun()));
        var test = (ObjectNode) report.at("/results/tests/0");
        test.remove("filePath");
        test.put("filepath", CLASS_NAME);

        assertThat(schemaErrorsOf(objectMapper.writeValueAsString(report)))
            .anySatisfy(error -> assertThat(error).startsWith("/results/tests/0:").contains("'filepath'"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("The validator rejects a buildNumber written as a string")
    void stringBuildNumberIsRejected() throws IOException {
        var report = (ObjectNode) objectMapper.readTree(composeReport(fullRun()));
        ((ObjectNode) report.at("/results/environment")).put("buildNumber", "777");

        assertThat(schemaErrorsOf(objectMapper.writeValueAsString(report)))
            .anySatisfy(error -> assertThat(error).startsWith("/results/environment/buildNumber:"));
    }

    /**
     * Attempts as the listener and the extension record them: a test that failed and then passed on retry, and an
     * aborted test, which the report shows as skipped with JUnit's raw status.
     */
    private List<Test> fullRun() {
        var failed = attempt("pays()", Set.of("smoke"), 1_000L, 1_005L);
        failed.setStatus(FAILED);
        processor.setFailureDetails(failed, new AssertionError("expected 200 but was 500"));

        var passed = attempt("pays()", Set.of("smoke"), 1_010L, 1_020L);
        passed.setStatus(PASSED);

        var aborted = attempt("refunds()", Set.of(), 1_030L, 1_031L);
        aborted.setStatus(SKIPPED);
        aborted.setRawStatus("aborted");
        aborted.setMessage("Assumption failed: the payments sandbox is down");

        return AttemptFolder.fold(List.of(failed, passed, aborted));
    }

    private Test attempt(String method, Set<String> tags, long start, long stop) {
        var details = new TestDetails(start, tags, CLASS_NAME, CLASS_ID + "/[method:" + method + "]", method);
        return processor.createTest(method, details, stop);
    }

    private String composeReport(List<Test> tests) throws IOException {
        var summary = SummaryUtil.createSummary(tests, 900L, 1_100L);
        var composer = new CtrfJsonComposer(configReader, new StartupDurationProcessor(), GENERATED_BY);
        return CtrfJsonMapper.write(composer.generateCtrfJson(summary, tests, true, "run-1"));
    }

    private static List<String> schemaErrorsOf(String report) {
        return SCHEMA.validate(report, InputFormat.JSON).stream().map(Object::toString).toList();
    }

    private static Schema loadPinnedSchema() {
        try (InputStream schema = CtrfSchemaConformanceTest.class.getResourceAsStream("/ctrf.schema.json")) {
            if (schema == null) {
                throw new IllegalStateException("ctrf.schema.json is not on the test classpath; see schema/README.md");
            }
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schema);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

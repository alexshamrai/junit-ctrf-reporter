package integration;

import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import io.github.alexshamrai.integration.JsonSchemaValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CtrfSchemaValidationTest extends BaseIntegrationTest {

    private JsonSchemaValidator schemaValidator;

    @BeforeAll
    void loadThePinnedSchema() throws IOException {
        try (InputStream schema = getClass().getResourceAsStream("/ctrf.schema.json")) {
            assertThat(schema)
                .as("ctrf.schema.json should be on the test classpath; see schema/README.md")
                .isNotNull();
            schemaValidator = new JsonSchemaValidator(schema);
        }
    }

    @Test
    void validateSchemaCompliance() throws IOException {
        assertThat(schemaValidator.validate(Files.readString(reportFile.toPath())))
            .as("Errors against the official CTRF schema in %s", reportFile)
            .isEmpty();

        assertThat(report.getReportFormat())
            .as("Report format should be 'CTRF'")
            .isEqualTo("CTRF");
        assertThat(report.getSpecVersion())
            .as("Spec version should be the version of the pinned schema")
            .isEqualTo(CtrfJson.SPEC_VERSION);

        assertThat(report.getResults()).isNotNull();

        assertThat(report.getResults().getTool()).isNotNull();
        assertThat(report.getResults().getTool().getName())
            .as("Tool name should not be null or empty")
            .isNotNull()
            .isNotEmpty();

        assertThat(report.getResults().getSummary()).isNotNull();
        assertThat(report.getResults().getSummary().getTests())
            .as("Summary tests count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getPassed())
            .as("Summary passed count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getFailed())
            .as("Summary failed count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getSkipped())
            .as("Summary skipped count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getPending())
            .as("Summary pending count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getOther())
            .as("Summary other count should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getStart())
            .as("Summary start timestamp should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);
        assertThat(report.getResults().getSummary().getStop())
            .as("Summary stop timestamp should be a non-negative integer")
            .isGreaterThanOrEqualTo(0);

        validateTests(report.getResults().getTests());
    }

    @Test
    void validatorRejectsTheLegacyFilepathKey() throws IOException {
        var tree = (ObjectNode) objectMapper.readTree(reportFile);
        var firstTest = (ObjectNode) tree.at("/results/tests/0");
        firstTest.remove("filePath");
        firstTest.put("filepath", "integration.LegacyTest");

        assertThat(schemaValidator.validate(objectMapper.writeValueAsString(tree)))
            .anySatisfy(error -> assertThat(error).startsWith("/results/tests/0:").contains("'filepath'"));
    }

    @Test
    void validatorRejectsAStringBuildNumber() throws IOException {
        var tree = (ObjectNode) objectMapper.readTree(reportFile);
        ((ObjectNode) tree.at("/results/environment")).put("buildNumber", "777");

        assertThat(schemaValidator.validate(objectMapper.writeValueAsString(tree)))
            .anySatisfy(error -> assertThat(error).startsWith("/results/environment/buildNumber:"));
    }

    private void validateTests(List<io.github.alexshamrai.ctrf.model.Test> tests) {
        assertThat(tests)
            .as("Tests list should not be null or empty")
            .isNotNull()
            .isNotEmpty();

        tests.forEach(test -> {
            assertThat(test.getName())
                .as("Test name should not be null or empty")
                .isNotNull()
                .isNotEmpty();

            assertThat(test.getStatus())
                .as("Test status should be one of the valid enum values")
                .isNotNull()
                .satisfies(status -> {
                    assertThat(status.name())
                        .isIn(TestStatus.PASSED.name(), TestStatus.FAILED.name(),
                            TestStatus.SKIPPED.name(), TestStatus.PENDING.name(),
                            TestStatus.OTHER.name());
                });
        });
    }

}

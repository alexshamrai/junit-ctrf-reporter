package io.github.alexshamrai.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Results;
import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cases the differential test against Jackson can't cover: input Jackson didn't write, and the places where the
 * mapper deliberately differs from Jackson.
 */
class CtrfJsonMapperTest {

    @org.junit.jupiter.api.Test
    void prettyPrintedReportIsRead() throws Exception {
        var report = CtrfJson.builder()
            .runId("run-1")
            .results(Results.builder().tests(List.of(Test.builder().name("t()").status(PASSED).build())).build())
            .build();
        String pretty = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report);

        assertThat(CtrfJsonMapper.read(pretty, CtrfJson.class)).isEqualTo(report);
    }

    @org.junit.jupiter.api.Test
    void reportStartingWithAByteOrderMarkIsRead() {
        assertThat(CtrfJsonMapper.read((char) 0xFEFF + "{\"runId\":\"run-1\"}", CtrfJson.class).getRunId()).isEqualTo("run-1");
    }

    @org.junit.jupiter.api.Test
    void legacyFilepathIsReadIntoFilePath() {
        var test = CtrfJsonMapper.read("{\"name\":\"t()\",\"filepath\":\"com.example.PaymentTest\"}", Test.class);

        assertThat(test.getFilePath()).isEqualTo("com.example.PaymentTest");
    }

    @org.junit.jupiter.api.Test
    void numericStringBuildNumberIsReadAsANumber() {
        var environment = CtrfJsonMapper.read("{\"buildNumber\":\" 777 \",\"healthy\":true}", Environment.class);

        assertThat(environment.getBuildNumber()).isEqualTo(777L);
        assertThat(environment.isHealthy()).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A 0.4 report whose buildNumber isn't a number still can't be read")
    void nonNumericBuildNumberCantBeRead() {
        String json = "{\"results\":{\"environment\":{\"buildNumber\":\"2026.10.15-3\"}}}";

        assertThatThrownBy(() -> CtrfJsonMapper.read(json, CtrfJson.class))
            .isInstanceOf(JsonException.class)
            .hasMessageContaining("$.results.environment.buildNumber");
    }

    @org.junit.jupiter.api.Test
    void unknownFieldsAreIgnored() {
        var report = CtrfJsonMapper.read("{\"runId\":\"run-1\",\"fieldFromTheFuture\":{\"a\":[1,2]},"
            + "\"results\":{\"tests\":[{\"name\":\"t()\",\"status\":\"passed\",\"flagFromTheFuture\":true}]}}", CtrfJson.class);

        assertThat(report.getRunId()).isEqualTo("run-1");
        assertThat(report.getResults().getTests()).extracting(Test::getName).containsExactly("t()");
    }

    @org.junit.jupiter.api.Test
    void loneSurrogateIsWrittenAsAnEscapeAndReadBack() {
        String name = "x" + (char) 0xD83D + "y";

        String json = CtrfJsonMapper.write(Test.builder().name(name).build());

        assertThat(json).isEqualTo("{\"name\":\"x\\uD83Dy\",\"duration\":0}");
        assertThat(CtrfJsonMapper.read(json, Test.class).getName()).isEqualTo(name);
    }

    @org.junit.jupiter.api.Test
    void statusIsReadIgnoringCase() {
        assertThat(CtrfJsonMapper.read("{\"status\":\"PASSED\"}", Test.class).getStatus()).isEqualTo(PASSED);
        assertThat(CtrfJsonMapper.read("{\"status\":\"Failed\"}", Test.class).getStatus()).isEqualTo(FAILED);
    }

    @org.junit.jupiter.api.Test
    void unknownStatusCantBeRead() {
        assertThatThrownBy(() -> CtrfJsonMapper.read("{\"status\":\"done\"}", Test.class))
            .isInstanceOf(JsonException.class)
            .hasMessageContaining("$.status");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Review Focus 1: statuses are written in English lower case under a Turkish default locale")
    void statusIsWrittenInLowerCaseUnderATurkishLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(CtrfJsonMapper.write(Test.builder().status(FAILED).build())).isEqualTo("{\"status\":\"failed\",\"duration\":0}");
            assertThat(SKIPPED.toLowerCase()).isEqualTo("skipped");
        } finally {
            Locale.setDefault(original);
        }
    }

    @org.junit.jupiter.api.Test
    void nullInAPrimitiveFieldIsReadAsZeroOrFalse() {
        assertThat(CtrfJsonMapper.read("{\"duration\":null}", Test.class).getDuration()).isZero();
        assertThat(CtrfJsonMapper.read("{\"healthy\":null}", Environment.class).isHealthy()).isFalse();
    }

    @org.junit.jupiter.api.Test
    void numberOrBooleanInATextFieldIsReadAsText() {
        var test = CtrfJsonMapper.read("{\"name\":123,\"message\":true}", Test.class);

        assertThat(test.getName()).isEqualTo("123");
        assertThat(test.getMessage()).isEqualTo("true");
    }

    @org.junit.jupiter.api.Test
    void emptyListsAreWrittenAndNullFieldsAreLeftOut() {
        assertThat(CtrfJsonMapper.write(Test.builder().tags(List.of()).build())).isEqualTo("{\"duration\":0,\"tags\":[]}");
    }

    @org.junit.jupiter.api.Test
    void objectOfAnotherTypeInExtraIsWrittenAsItsText() {
        var report = CtrfJson.builder().extra(Map.of("when", LocalDate.of(2026, 10, 15))).build();

        assertThat(CtrfJsonMapper.write(report)).endsWith("\"extra\":{\"when\":\"2026-10-15\"}}");
    }

    @org.junit.jupiter.api.Test
    void textThatIsNotJsonCantBeRead() {
        assertThatThrownBy(() -> CtrfJsonMapper.read("{ not json", CtrfJson.class)).isInstanceOf(JsonException.class);
        assertThatThrownBy(() -> CtrfJsonMapper.read("[]", CtrfJson.class)).isInstanceOf(JsonException.class);
    }
}

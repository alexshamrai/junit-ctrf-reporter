package io.github.alexshamrai.json;

import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Results;
import io.github.alexshamrai.ctrf.model.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.alexshamrai.ctrf.model.Test.TestStatus.FAILED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.PASSED;
import static io.github.alexshamrai.ctrf.model.Test.TestStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cases {@link JsonGoldenTest} can't cover: input our writer doesn't produce, and the places where the mapper
 * deliberately differs from Jackson.
 */
class CtrfJsonMapperTest {

    @org.junit.jupiter.api.Test
    void prettyPrintedReportIsRead() throws Exception {
        var report = CtrfJson.builder()
            .runId("run-1")
            .results(Results.builder().tests(List.of(Test.builder().name("t()").status(PASSED).build())).build())
            .build();
        String pretty = JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report);

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

    /**
     * Reports that are valid JSON but don't fit the model, and the exact message each gives. Captured from the mapper
     * of 0.5.1.
     */
    static Stream<Arguments> reportsThatDontFitTheModel() {
        return Stream.of(
            Arguments.of("[]", "$: expected an object"),
            Arguments.of("{\"results\":[]}", "$.results: expected an object"),
            Arguments.of("{\"results\":{\"tests\":{}}}", "$.results.tests: expected an array"),
            Arguments.of("{\"results\":{\"tests\":[{\"status\":5}]}}", "$.results.tests[0].status: unknown test status 5"),
            Arguments.of("{\"results\":{\"tests\":[{\"tags\":\"a\"}]}}", "$.results.tests[0].tags: expected an array"),
            Arguments.of("{\"results\":{\"tests\":[{\"name\":[1,{\"a\":2}]}]}}", "$.results.tests[0].name: expected a string"),
            Arguments.of("{\"results\":{\"tests\":[{\"duration\":1e30}]}}",
                "$.results.tests[0].duration: \"1.0E30\" is not a whole number in range"),
            Arguments.of("{\"results\":{\"summary\":{\"tests\":\"x\"}}}", "$.results.summary.tests: \"x\" is not a whole number in range"),
            Arguments.of("{\"results\":{\"summary\":{\"tests\":3000000000}}}", "$.results.summary.tests: 3000000000 is out of range"),
            Arguments.of("{\"results\":{\"environment\":{\"healthy\":\"maybe\"}}}",
                "$.results.environment.healthy: expected true or false, not maybe"),
            Arguments.of("{\"runId\":{}}", "$.runId: expected a string"),
            Arguments.of("{\"extra\":[1]}", "$.extra: expected an object"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("reportsThatDontFitTheModel")
    void reportThatDoesNotFitTheModelNamesTheFieldAndTheProblem(String json, String message) {
        assertThatThrownBy(() -> CtrfJsonMapper.read(json, CtrfJson.class)).isInstanceOf(JsonException.class).hasMessage(message);
    }

    @ParameterizedTest(name = "chunks of {0}")
    @ValueSource(ints = {1, 7, 8191})
    void reportIsReadTheSameFromAReaderInAnyChunkSize(int size) throws IOException {
        String json = JsonGoldenTest.goldenReport();

        assertThat(CtrfJsonMapper.read(new ChunkedReader(json, size), CtrfJson.class)).isEqualTo(CtrfJsonMapper.read(json, CtrfJson.class));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Review Focus 1: a trace of 20,000 characters, longer than the reader's buffer, is read whole")
    void textLongerThanTheBufferIsReadWhole() throws IOException {
        String trace = "java.lang.AssertionError: expected \"a\"\n\tat com.example.PaymentTest.pays(PaymentTest.java:42)\n"
            .repeat(250).substring(0, 20_000);
        String json = "{\"name\":\"pays()\",\"trace\":" + JsonWriter.write(trace) + "}";

        assertThat(json.length()).isGreaterThan(2 * 8192);
        assertThat(CtrfJsonMapper.read(new StringReader(json), Test.class).getTrace()).isEqualTo(trace);
    }

    @org.junit.jupiter.api.Test
    void readerIsReadToTheEndOfTheText() {
        assertThatThrownBy(() -> CtrfJsonMapper.read(new StringReader("{\"runId\":\"run-1\"} {}"), CtrfJson.class))
            .isInstanceOf(JsonException.class)
            .hasMessage("Unexpected text after the JSON value at character 18");
    }

    @org.junit.jupiter.api.Test
    void ioExceptionFromTheWriterPropagates() {
        Writer failing = new Writer() {
            @Override
            public void write(char[] chars, int offset, int length) throws IOException {
                throw new IOException("disk full");
            }

            @Override
            public void flush() {
                // Nothing buffered
            }

            @Override
            public void close() {
                // Nothing to release
            }
        };

        assertThatThrownBy(() -> CtrfJsonMapper.write(Test.builder().name("t()").build(), failing))
            .isInstanceOf(IOException.class)
            .hasMessage("disk full");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Review Focus 1: a report larger than the writer's buffer is written whole")
    void reportLargerThanTheBufferIsWrittenWhole() throws IOException {
        String trace = "x".repeat(20_000);
        var out = new StringWriter();

        CtrfJsonMapper.write(Test.builder().trace(trace).build(), out);

        assertThat(out).hasToString("{\"duration\":0,\"trace\":\"" + trace + "\"}");
    }
}

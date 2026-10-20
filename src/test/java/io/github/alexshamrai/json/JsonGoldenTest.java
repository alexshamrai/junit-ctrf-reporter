package io.github.alexshamrai.json;

import io.github.alexshamrai.ctrf.model.Attachment;
import io.github.alexshamrai.ctrf.model.CtrfJson;
import io.github.alexshamrai.ctrf.model.Environment;
import io.github.alexshamrai.ctrf.model.Extra;
import io.github.alexshamrai.ctrf.model.Results;
import io.github.alexshamrai.ctrf.model.RetryAttempt;
import io.github.alexshamrai.ctrf.model.Step;
import io.github.alexshamrai.ctrf.model.Summary;
import io.github.alexshamrai.ctrf.model.Test;
import io.github.alexshamrai.ctrf.model.Tool;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Our JSON code against the JSON the reporter wrote up to 0.5.0, with Jackson 2. Each case of the corpus has its golden
 * JSON: the full report's is {@code src/test/resources/json/golden-report.json}, the others are inline. All of it was
 * checked, before Jackson 2 left the build, to be byte-identical to Jackson 2's output. The writer must keep writing
 * exactly that JSON, and the reader must read it as Jackson 3 does.
 */
class JsonGoldenTest {

    private static final JsonMapper JACKSON = JsonMapper.builder().build();

    /**
     * Every control character, the characters JSON escapes, DEL, and text outside ASCII: German, Japanese, the two
     * Unicode line separators and an emoji, which needs a surrogate pair.
     */
    private static final String TRICKY_TEXT = IntStream.range(0, 0x20).mapToObj(c -> String.valueOf((char) c))
        .collect(Collectors.joining())
        + "\"\\/ " + (char) 0x7F + " " + new String(new int[] {0xFC, 0xDF, 0x65E5, 0x672C, 0x2028, 0x2029, 0x1F600}, 0, 7) + " end";

    /**
     * The full report holds every field, all kinds of numbers, nested values and {@link #TRICKY_TEXT}. The short cases
     * pin what is written for fields left unset, and that empty collections are written.
     */
    static Stream<Arguments> corpus() {
        String reportDefaults = "{\"reportFormat\":\"CTRF\",\"specVersion\":\"0.2.0\"}";
        String testDefaults = "{\"duration\":0}";
        return Stream.of(
            Arguments.of("full-report", fullReport(), goldenReport()),
            Arguments.of("report-builder-defaults", CtrfJson.builder().build(), reportDefaults),
            Arguments.of("report-no-args", new CtrfJson(), reportDefaults),
            Arguments.of("test-builder-defaults", Test.builder().build(), testDefaults),
            Arguments.of("test-no-args", new Test(), testDefaults),
            Arguments.of("summary-builder-defaults", Summary.builder().build(),
                "{\"tests\":0,\"passed\":0,\"failed\":0,\"pending\":0,\"skipped\":0,\"other\":0,\"flaky\":0,"
                    + "\"start\":0,\"stop\":0,\"duration\":0}"),
            Arguments.of("environment-no-args", new Environment(), "{\"healthy\":false}"),
            Arguments.of("test-empty-collections",
                Test.builder().tags(List.of()).suite(List.of()).parameters(Map.of()).attachments(List.of()).build(),
                "{\"duration\":0,\"suite\":[],\"tags\":[],\"attachments\":[],\"parameters\":{}}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void writerWritesExactlyTheGoldenJson(String name, Object model, String golden) {
        assertThat(CtrfJsonMapper.write(model)).isEqualTo(golden);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void writerWritesExactlyTheGoldenJsonToAWriter(String name, Object model, String golden) throws IOException {
        var out = new StringWriter();

        CtrfJsonMapper.write(model, out);

        assertThat(out).hasToString(golden);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void readerReadsTheGoldenJsonAsJacksonDoes(String name, Object model, String golden) {
        assertThat(CtrfJsonMapper.read(golden, model.getClass())).isEqualTo(JACKSON.readValue(golden, model.getClass()));
    }

    @org.junit.jupiter.api.Test
    void readerReadsALargeGeneratedReportAsJacksonDoes() throws IOException {
        String json = CtrfJsonMapper.write(generatedReport(2_000));

        assertThat(CtrfJsonMapper.read(new StringReader(json), CtrfJson.class)).isEqualTo(JACKSON.readValue(json, CtrfJson.class));
    }

    static String goldenReport() {
        try (InputStream in = JsonGoldenTest.class.getResourceAsStream("/json/golden-report.json")) {
            if (in == null) {
                throw new IllegalStateException("No src/test/resources/json/golden-report.json");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A report with {@code count} tests of every status, every tenth failing with a message and a trace, every 25th
     * retried, and every 100th with nested {@code extra} values and numeric parameters.
     */
    private static CtrfJson generatedReport(int count) {
        Test.TestStatus[] statuses = Test.TestStatus.values();
        List<Test> tests = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            var test = Test.builder()
                .testId("[engine:junit-jupiter]/[class:com.example.Generated" + (n / 50) + "]/[method:test" + n + "()]")
                .name("test" + n + "()")
                .status(statuses[n % statuses.length])
                .duration(n)
                .start(1_000L + n)
                .stop(1_000L + 2L * n)
                .filePath("com.example.Generated" + (n / 50))
                .threadId("worker-" + (n % 4))
                .tags(List.of("smoke", "t" + (n % 7)));
            if (n % 10 == 0) {
                test.message("expected: <" + n + "> but was: <" + (n + 1) + ">")
                    .trace(TRICKY_TEXT + "\n\tat com.example.Generated.test" + n + "(Generated.java:" + n + ")");
            }
            if (n % 25 == 0) {
                test.retries(1).flaky(true).retryAttempts(List.of(RetryAttempt.builder()
                    .attempt(1).status(Test.TestStatus.FAILED).duration(1).start(1L).stop(2L).message("first try").build()));
            }
            if (n % 100 == 0) {
                test.extra(Extra.builder().customData(nested()).build()).parameters(numbers());
            }
            tests.add(test.build());
        }
        var results = Results.builder()
            .tool(Tool.builder().name("JUnit").version("6.1.3").build())
            .summary(Summary.builder().tests(count).start(1_000L).stop(9_000L).build())
            .tests(tests)
            .build();
        return CtrfJson.builder().runId("generated").results(results).build();
    }

    private static CtrfJson fullReport() {
        var extra = Extra.builder().customData(nested()).startupDuration(42L).build();
        var earlier = RetryAttempt.builder().attempt(1).status(Test.TestStatus.FAILED).duration(5).start(100L).stop(105L)
            .message("first try failed").trace("trace\n\tat Foo.bar(Foo.java:1)").build();
        var test = Test.builder()
            .testId("[engine:junit-jupiter]/[class:com.example.PaymentTest]/[method:pays()]")
            .name("pays()")
            .status(Test.TestStatus.PASSED)
            .duration(10)
            .start(110L)
            .stop(120L)
            .suite(List.of("PaymentTest", "Refunds"))
            .message("message")
            .trace("trace")
            .snippet("snippet")
            .line(17)
            .ai("ai")
            .rawStatus("aborted")
            .tags(List.of("smoke", "payments"))
            .type("unit")
            .filePath("com.example.PaymentTest")
            .retries(1)
            .flaky(true)
            .retryAttempts(List.of(earlier))
            .stdout(List.of("out"))
            .stderr(List.of("err"))
            .browser("chrome")
            .device("pixel")
            .screenshot("c2NyZWVu")
            .attachments(List.of(Attachment.builder().name("log.txt").contentType("text/plain").path("/tmp/log.txt").build()))
            .threadId("main")
            .parameters(numbers())
            .steps(List.of(Step.builder().name("step").status("passed").extra(extra).build()))
            .extra(extra)
            .build();
        var summary = Summary.builder().tests(2).passed(1).failed(0).pending(0).skipped(0).other(1).flaky(1).suites(1)
            .start(100L).stop(200L).duration(100L).extra(extra).build();
        var environment = Environment.builder().reportName("Nightly").appName("payments").appVersion("1.2.3")
            .buildName("system-build").buildNumber(777L).buildId("2026.10.15-3").buildUrl("https://ci.example.com/777")
            .repositoryName("payments").repositoryUrl("https://github.com/example/payments").commit("c4093eb")
            .branchName("master").osPlatform("linux").osRelease("6.8").osVersion("Ubuntu 24.04")
            .testEnvironment("staging").healthy(true).extra(extra).build();
        var results = Results.builder()
            .tool(Tool.builder().name("JUnit").version("6.1.3").extra(extra).build())
            .summary(summary)
            .tests(List.of(test, Test.builder().name("bare").build()))
            .environment(environment)
            .extra(extra)
            .build();
        return CtrfJson.builder().reportId("report-1").runId("run-1").timestamp("2026-10-15T12:00:00Z")
            .generatedBy("io.github.alexshamrai.launcher.CtrfListener").results(results).extra(nested()).build();
    }

    private static Map<String, Object> numbers() {
        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("intMin", Integer.MIN_VALUE);
        numbers.put("intMax", Integer.MAX_VALUE);
        numbers.put("longMin", Long.MIN_VALUE);
        numbers.put("longMax", Long.MAX_VALUE);
        numbers.put("big", new BigInteger("123456789012345678901234567890"));
        numbers.put("negativeBig", new BigInteger("-123456789012345678901234567890"));
        numbers.put("tenth", 0.1);
        numbers.put("tiny", 1e-7);
        numbers.put("huge", 1.0e21);
        numbers.put("negativeZero", -0.0);
        numbers.put("doubleMax", Double.MAX_VALUE);
        numbers.put("doubleMin", Double.MIN_VALUE);
        numbers.put("float", 1.5f);
        numbers.put("decimal", new BigDecimal("1.50"));
        numbers.put("notANumber", Double.NaN);
        numbers.put("infinity", Double.POSITIVE_INFINITY);
        return numbers;
    }

    private static Map<String, Object> nested() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("null", null);
        inner.put("list", Arrays.asList(1, "two", null, true, List.of(), Map.of()));
        inner.put("text", TRICKY_TEXT);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("inner", inner);
        outer.put("empty", new LinkedHashMap<>());
        outer.put("flag", false);
        return outer;
    }
}

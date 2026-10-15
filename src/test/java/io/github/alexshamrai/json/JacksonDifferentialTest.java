package io.github.alexshamrai.json;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Our JSON code against Jackson, which wrote and read the reports up to 0.5.0. For every model object of the corpus,
 * the writer's bytes are Jackson's, and the reader reads Jackson's output, and its own, into what Jackson reads.
 */
class JacksonDifferentialTest {

    private static final ObjectMapper JACKSON = new ObjectMapper();

    /**
     * Every control character, the characters JSON escapes, DEL, and text outside ASCII: German, Japanese, the two
     * Unicode line separators and an emoji, which needs a surrogate pair.
     */
    private static final String TRICKY_TEXT = IntStream.range(0, 0x20).mapToObj(c -> String.valueOf((char) c))
        .collect(Collectors.joining())
        + "\"\\/ " + (char) 0x7F + " " + new String(new int[] {0xFC, 0xDF, 0x65E5, 0x672C, 0x2028, 0x2029, 0x1F600}, 0, 7) + " end";

    static Stream<Object> corpus() {
        return Stream.of(
            fullReport(),
            CtrfJson.builder().build(),
            new CtrfJson(),
            Test.builder().build(),
            new Test(),
            Summary.builder().build(),
            new Environment(),
            Test.builder().name(TRICKY_TEXT).message(TRICKY_TEXT).tags(List.of(TRICKY_TEXT, "")).build(),
            Test.builder().tags(List.of()).suite(List.of()).parameters(Map.of()).attachments(List.of()).build(),
            CtrfJson.builder().extra(numbers()).build(),
            CtrfJson.builder().extra(nested()).build());
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void writerOutputIsJacksonsByteForByte(Object model) throws Exception {
        byte[] jackson = JACKSON.writeValueAsBytes(model);

        String ours = CtrfJsonMapper.write(model);

        assertThat(ours).isEqualTo(new String(jackson, StandardCharsets.UTF_8));
        assertThat(ours.getBytes(StandardCharsets.UTF_8)).isEqualTo(jackson);
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void readerReadsJacksonsOutputAsJacksonDoes(Object model) throws Exception {
        String json = JACKSON.writeValueAsString(model);

        assertThat(CtrfJsonMapper.read(json, model.getClass())).isEqualTo(JACKSON.readValue(json, model.getClass()));
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void readerReadsItsOwnOutputAsJacksonDoes(Object model) throws Exception {
        String json = CtrfJsonMapper.write(model);

        assertThat(CtrfJsonMapper.read(json, model.getClass())).isEqualTo(JACKSON.readValue(json, model.getClass()));
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
            .attachments(Arrays.asList("a", 1, null, Map.of("name", "log.txt")))
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

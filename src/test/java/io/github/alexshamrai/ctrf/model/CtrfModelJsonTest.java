package io.github.alexshamrai.ctrf.model;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CtrfModelJsonTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @org.junit.jupiter.api.Test
    void testThatRanOnceHasNoRetryFields() throws Exception {
        var test = Test.builder().name("test1()").status(Test.TestStatus.PASSED).build();

        var json = objectMapper.readTree(objectMapper.writeValueAsString(test));

        assertThat(json.has("retries")).isFalse();
        assertThat(json.has("retryAttempts")).isFalse();
        assertThat(json.has("flaky")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void retryAttemptsAreWrittenAsSpecEntriesAndReadBack() throws Exception {
        var test = Test.builder()
            .name("test1()")
            .status(Test.TestStatus.PASSED)
            .retries(1)
            .flaky(true)
            .retryAttempts(List.of(RetryAttempt.builder()
                .attempt(1).status(Test.TestStatus.FAILED).duration(5).start(100L).stop(105L)
                .message("boom").trace("trace of boom")
                .build()))
            .build();

        var written = objectMapper.writeValueAsString(test);
        var entry = objectMapper.readTree(written).get("retryAttempts").get(0);

        assertThat(entry.get("attempt").asInt()).isEqualTo(1);
        assertThat(entry.get("status").asString()).isEqualTo("failed");
        assertThat(entry.get("duration").asLong()).isEqualTo(5);
        assertThat(entry.get("message").asString()).isEqualTo("boom");
        assertThat(objectMapper.readValue(written, Test.class)).isEqualTo(test);
    }

    @org.junit.jupiter.api.Test
    void summaryAlwaysHasFlakyAndOlderReportsReadAsZero() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(Summary.builder().build()));
        assertThat(json.get("flaky").asInt()).isZero();

        var older = objectMapper.readValue(
            "{\"tests\":1,\"passed\":1,\"failed\":0,\"pending\":0,\"skipped\":0,\"other\":0,\"start\":1,\"stop\":2}",
            Summary.class);
        assertThat(older.getFlaky()).isZero();
    }

    @org.junit.jupiter.api.Test
    void toBuilderCopiesATest() {
        var test = Test.builder().testId("id-1").name("test1()").status(Test.TestStatus.FAILED).build();

        var copy = test.toBuilder().status(Test.TestStatus.PASSED).build();

        assertThat(copy.getTestId()).isEqualTo("id-1");
        assertThat(copy.getStatus()).isEqualTo(Test.TestStatus.PASSED);
        assertThat(test.getStatus()).isEqualTo(Test.TestStatus.FAILED);
    }

    @org.junit.jupiter.api.Test
    void runIdIsWrittenAtTheRootOnlyWhenSet() throws Exception {
        var withRunId = objectMapper.readTree(objectMapper.writeValueAsString(CtrfJson.builder().runId("run-1").build()));
        var withoutRunId = objectMapper.readTree(objectMapper.writeValueAsString(CtrfJson.builder().build()));

        assertThat(withRunId.get("runId").asString()).isEqualTo("run-1");
        assertThat(withoutRunId.has("runId")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void filePathIsWrittenAsFilePath() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
            Test.builder().name("test1()").filePath("com.example.PaymentTest").build()));

        assertThat(json.get("filePath").asString()).isEqualTo("com.example.PaymentTest");
        assertThat(json.has("filepath")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void legacyFilepathIsReadIntoFilePath() throws Exception {
        var test = objectMapper.readValue("{\"name\":\"test1()\",\"filepath\":\"com.example.PaymentTest\"}", Test.class);

        assertThat(test.getFilePath()).isEqualTo("com.example.PaymentTest");
    }

    @org.junit.jupiter.api.Test
    void buildNumberIsWrittenAsANumberAndBuildIdAsAString() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
            Environment.builder().buildNumber(777L).buildId("2026.10.15-3").build()));

        assertThat(json.get("buildNumber").isIntegralNumber()).isTrue();
        assertThat(json.get("buildNumber").asLong()).isEqualTo(777L);
        assertThat(json.get("buildId").asString()).isEqualTo("2026.10.15-3");
    }

    @org.junit.jupiter.api.Test
    void legacyStringBuildNumberIsReadAsANumber() throws Exception {
        var environment = objectMapper.readValue("{\"buildNumber\":\"777\",\"healthy\":true}", Environment.class);

        assertThat(environment.getBuildNumber()).isEqualTo(777L);
    }

    @org.junit.jupiter.api.Test
    void specVersionIsTheSupportedCtrfVersion() {
        assertThat(CtrfJson.SPEC_VERSION).isEqualTo("0.2.0");
        assertThat(CtrfJson.builder().build().getSpecVersion()).isEqualTo(CtrfJson.SPEC_VERSION);
    }

    @org.junit.jupiter.api.Test
    void summaryWritesItsDuration() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
            Summary.builder().start(1_000L).stop(3_500L).duration(2_500L).build()));

        assertThat(json.get("duration").asLong()).isEqualTo(2_500L);
    }

    @org.junit.jupiter.api.Test
    void suiteIsWrittenAsAnArray() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
            Test.builder().name("test1()").suite(List.of("PaymentTest", "Refunds")).build()));

        assertThat(json.get("suite").isArray()).isTrue();
        assertThat(json.get("suite").get(1).asString()).isEqualTo("Refunds");
    }
}

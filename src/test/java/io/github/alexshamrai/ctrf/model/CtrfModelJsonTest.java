package io.github.alexshamrai.ctrf.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CtrfModelJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        assertThat(entry.get("status").asText()).isEqualTo("failed");
        assertThat(entry.get("duration").asLong()).isEqualTo(5);
        assertThat(entry.get("message").asText()).isEqualTo("boom");
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
}

package io.github.alexshamrai.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonParserTest {

    @Test
    void wholeNumbersBecomeTheSmallestTypeThatHoldsThem() {
        assertThat(JsonParser.parse("2147483647")).isEqualTo(Integer.MAX_VALUE);
        assertThat(JsonParser.parse("-2147483648")).isEqualTo(Integer.MIN_VALUE);
        assertThat(JsonParser.parse("2147483648")).isEqualTo(2147483648L);
        assertThat(JsonParser.parse("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        assertThat(JsonParser.parse("9223372036854775808")).isEqualTo(new BigInteger("9223372036854775808"));
        assertThat(JsonParser.parse("-0")).isEqualTo(0);
    }

    @Test
    void otherNumbersBecomeDoubles() {
        assertThat(JsonParser.parse("1.5")).isEqualTo(1.5);
        assertThat(JsonParser.parse("-1.5e3")).isEqualTo(-1500.0);
        assertThat(JsonParser.parse("1E2")).isEqualTo(100.0);
        assertThat(JsonParser.parse("4.9E-324")).isEqualTo(Double.MIN_VALUE);
    }

    @Test
    void whitespaceAndAByteOrderMarkAreAccepted() {
        Object value = JsonParser.parse((char) 0xFEFF + " \n\t{ \"a\" : [ 1 , true , null ] }\r\n");

        assertThat(value).isEqualTo(Map.of("a", Arrays.asList(1, true, null)));
    }

    @Test
    void escapesAreDecoded() {
        String expected = "\" \\ / \b \f \n \r \t " + (char) 0xE9 + " " + new String(Character.toChars(0x1F600));

        assertThat(JsonParser.parse("\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t \\u00e9 \\uD83D\\uDE00\"")).isEqualTo(expected);
    }

    @Test
    void membersKeepTheirOrderAndARepeatedNameKeepsTheLastValue() {
        Object value = JsonParser.parse("{\"b\":1,\"a\":2,\"b\":3}");

        assertThat(value).isInstanceOf(LinkedHashMap.class);
        assertThat(new ArrayList<Object>(((Map<?, ?>) value).keySet())).containsExactly("b", "a");
        assertThat(((Map<?, ?>) value).get("b")).isEqualTo(3);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", " ", "{", "{\"a\":}", "{\"a\":1", "{\"a\" 1}", "[1,]", "[1 2]", "01", "-", "1.", "1e", ".5",
        "tru", "nul", "\"open", "\"bad \\x escape\"", "\"\\u12G4\"", "{} {}", "{1:2}", "'single'", "NaN"})
    void malformedJsonThrows(String json) {
        assertThatThrownBy(() -> JsonParser.parse(json)).isInstanceOf(JsonException.class);
    }

    @Test
    void rawControlCharacterInAStringThrows() {
        assertThatThrownBy(() -> JsonParser.parse("\"tab\there\"")).isInstanceOf(JsonException.class);
    }

    @Test
    void numberLongerThanTheLimitThrows() {
        assertThatThrownBy(() -> JsonParser.parse("1".repeat(JsonParser.MAX_NUMBER_LENGTH + 1))).isInstanceOf(JsonException.class);
    }

    @Test
    @DisplayName("Review Focus 3: a document nested to the limit is read and written within a 256 KB thread stack")
    void nestingUpToTheLimitFitsInASmallStack() throws Exception {
        String json = "[".repeat(JsonParser.MAX_DEPTH) + "]".repeat(JsonParser.MAX_DEPTH);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> written = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try {
                written.set(JsonWriter.write(JsonParser.parse(json)));
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "small-stack", 256 * 1024);
        thread.start();
        thread.join();

        assertThat(failure.get()).isNull();
        assertThat(written.get()).isEqualTo(json);
    }

    @Test
    @DisplayName("Review Focus 3: deeper nesting throws instead of overflowing the stack")
    void deeperNestingThrowsInsteadOfOverflowingTheStack() {
        String json = "[".repeat(100_000) + "]".repeat(100_000);

        assertThatThrownBy(() -> JsonParser.parse(json)).isInstanceOf(JsonException.class).hasMessageContaining("deeper than 100 ");
    }
}

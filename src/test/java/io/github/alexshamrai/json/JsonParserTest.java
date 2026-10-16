package io.github.alexshamrai.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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

    /**
     * Malformed input and the exact message it gives, character position included. Captured from the parser of 0.5.1.
     */
    static Stream<Arguments> malformedJson() {
        String bom = String.valueOf((char) 0xFEFF);
        return Stream.of(
            Arguments.of("", "Unexpected end of the JSON text at character 0"),
            Arguments.of(" ", "Unexpected end of the JSON text at character 1"),
            Arguments.of("{", "Expected a member name at character 1"),
            Arguments.of("{1:2}", "Expected a member name at character 1"),
            Arguments.of("{\"a\"", "Expected ':' at character 4"),
            Arguments.of("{\"a\" 1}", "Expected ':' at character 5"),
            Arguments.of("{\"a\":}", "Unexpected character '}' at character 5"),
            Arguments.of("{\"a\":1", "Expected '}' at character 6"),
            Arguments.of("{\"a\":1 2}", "Expected '}' at character 7"),
            Arguments.of("{\"a\":1,", "Expected a member name at character 7"),
            Arguments.of("{\"a\":1,}", "Expected a member name at character 7"),
            Arguments.of("{} {}", "Unexpected text after the JSON value at character 3"),
            Arguments.of("[", "Unexpected end of the JSON text at character 1"),
            Arguments.of("[1", "Expected ']' at character 2"),
            Arguments.of("[1,]", "Unexpected character ']' at character 3"),
            Arguments.of("[1 2]", "Expected ']' at character 3"),
            Arguments.of(" [ 1 , 2 ] x", "Unexpected text after the JSON value at character 11"),
            Arguments.of("01", "Unexpected text after the JSON value at character 1"),
            Arguments.of("-", "Expected a digit at character 1"),
            Arguments.of("1.", "Expected a digit at character 2"),
            Arguments.of("1e", "Expected a digit at character 2"),
            Arguments.of(".5", "Unexpected character '.' at character 0"),
            Arguments.of("NaN", "Unexpected character 'N' at character 0"),
            Arguments.of("'single'", "Unexpected character ''' at character 0"),
            Arguments.of("tru", "Expected true at character 0"),
            Arguments.of("falsy", "Expected false at character 0"),
            Arguments.of("[true,fals]", "Expected false at character 6"),
            Arguments.of("nul", "Expected null at character 0"),
            Arguments.of("\"open", "Unterminated string at character 5"),
            Arguments.of("\"tab\there\"", "Unescaped control character in a string at character 5"),
            Arguments.of("\"\\", "Unterminated escape at character 2"),
            Arguments.of("\"end\\", "Unterminated escape at character 5"),
            Arguments.of("\"bad \\x escape\"", "Invalid escape \\x at character 7"),
            Arguments.of("\"\\u12G4\"", "Invalid \\u escape at character 3"),
            Arguments.of("\"\\u", "Truncated \\u escape at character 3"),
            Arguments.of("\"\\u12", "Truncated \\u escape at character 3"),
            Arguments.of("\"\\u1G", "Truncated \\u escape at character 3"),
            Arguments.of("  " + bom + "{}", "Unexpected character '" + bom + "' at character 2"),
            Arguments.of(bom + " \"x\" y", "Unexpected text after the JSON value at character 6"),
            Arguments.of("1".repeat(1001), "Number longer than 1000 characters at character 1001"),
            Arguments.of("-" + "2".repeat(1000), "Number longer than 1000 characters at character 1001"),
            Arguments.of("[".repeat(101), "Arrays and objects nest deeper than 100 levels at character 100"),
            Arguments.of("{\"a\":".repeat(101), "Arrays and objects nest deeper than 100 levels at character 500"));
    }

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("malformedJson")
    void malformedJsonIsReportedWithThePositionOfTheProblem(String json, String message) {
        assertThatThrownBy(() -> JsonParser.parse(json)).isInstanceOf(JsonException.class).hasMessage(message);
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

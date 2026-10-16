package io.github.alexshamrai.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static io.github.alexshamrai.json.JsonReader.Kind.ARRAY;
import static io.github.alexshamrai.json.JsonReader.Kind.BOOLEAN;
import static io.github.alexshamrai.json.JsonReader.Kind.NULL;
import static io.github.alexshamrai.json.JsonReader.Kind.NUMBER;
import static io.github.alexshamrai.json.JsonReader.Kind.OBJECT;
import static io.github.alexshamrai.json.JsonReader.Kind.STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonReaderTest {

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    static List<String> wellFormedJson() {
        return List.of(
            JsonGoldenTest.goldenReport(),
            (char) 0xFEFF + " \n\t{ \"a\" : [ 1 , true , null ] }\r\n",
            "\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t \\u00e9 \\uD83D\\uDE00\"",
            "[0,-0,1.5,-1.5e3,1E2,4.9E-324,2147483648,9223372036854775808,123456789012345678901234567890]",
            "[[[]],{},{\"b\":1,\"a\":2,\"b\":3},[true,false,null]]");
    }

    @ParameterizedTest(name = "chunks of {0}")
    @ValueSource(ints = {1, 2, 3, 7})
    void readsTheSameInChunksAsFromAString(int size) throws IOException {
        for (String json : wellFormedJson()) {
            assertThat(read(new ChunkedReader(json, size))).as(json).isEqualTo(JsonParser.parse(json));
        }
    }

    @ParameterizedTest(name = "chunks of {0}")
    @ValueSource(ints = {1, 2, 3, 7})
    void reportsErrorsAtTheSamePositionInChunks(int size) {
        JsonParserTest.malformedJson().forEach(arguments -> {
            String json = (String) arguments.get()[0];
            String message = (String) arguments.get()[1];
            assertThatThrownBy(() -> read(new ChunkedReader(json, size)))
                .as(json)
                .isInstanceOf(JsonException.class)
                .hasMessage(message);
        });
    }

    static IntStream paddings() {
        return IntStream.rangeClosed(8150, 8200);
    }

    @ParameterizedTest(name = "{0} characters before the escapes")
    @MethodSource("paddings")
    void tokensCutByTheEndOfTheBufferAreReadWhole(int padding) throws IOException {
        String json = "[\"" + "a".repeat(padding) + "\\u00e9\\uD83D\\uDE00\",true,null,-12.5e3]";

        Object value = read(new StringReader(json));

        assertThat(value).isEqualTo(Arrays.asList("a".repeat(padding) + (char) 0xE9 + EMOJI, true, null, -12500.0));
    }

    @Test
    void peekTellsTheKindOfTheNextValue() throws IOException {
        var reader = new JsonReader(new StringReader("[{}, [], \"s\", -1, true, false, null]"));
        List<JsonReader.Kind> kinds = new ArrayList<>();

        assertThat(reader.beginArray()).isTrue();
        do {
            kinds.add(reader.peek());
            reader.skipValue();
        } while (reader.nextElement());
        reader.endOfText();

        assertThat(kinds).containsExactly(OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, BOOLEAN, NULL);
    }

    @Test
    void membersAreReadOneByOneAndAValueCanBeSkipped() throws IOException {
        var reader = new JsonReader(new StringReader("{\"skip\":{\"a\":[1,\"x\",{\"b\":null}],\"c\":true},\"keep\":2}"));

        assertThat(reader.beginObject()).isTrue();
        assertThat(reader.nextName()).isEqualTo("skip");
        reader.skipValue();
        assertThat(reader.nextMember()).isTrue();
        assertThat(reader.nextName()).isEqualTo("keep");
        assertThat(reader.nextValue()).isEqualTo(2);
        assertThat(reader.nextMember()).isFalse();
        reader.endOfText();
    }

    @Test
    void emptyObjectsAndArraysHaveNoMembersOrElements() throws IOException {
        var reader = new JsonReader(new StringReader("[ { } , [ ] ]"));

        assertThat(reader.beginArray()).isTrue();
        assertThat(reader.beginObject()).isFalse();
        assertThat(reader.nextElement()).isTrue();
        assertThat(reader.beginArray()).isFalse();
        assertThat(reader.nextElement()).isFalse();
        reader.endOfText();
    }

    @Test
    void skippingIsAsStrictAsReading() throws IOException {
        var reader = new JsonReader(new StringReader("{\"skip\":[1,]}"));
        reader.beginObject();
        reader.nextName();

        assertThatThrownBy(reader::skipValue).isInstanceOf(JsonException.class).hasMessage("Unexpected character ']' at character 11");
    }

    @Test
    void skippingKeepsTheNestingLimit() throws IOException {
        var reader = new JsonReader(new StringReader("{\"skip\":" + "[".repeat(200) + "]".repeat(200) + "}"));
        reader.beginObject();
        reader.nextName();

        assertThatThrownBy(reader::skipValue).isInstanceOf(JsonException.class).hasMessageContaining("deeper than 100 ");
    }

    private static Object read(Reader in) throws IOException {
        var reader = new JsonReader(in);
        Object value = reader.nextValue();
        reader.endOfText();
        return value;
    }
}

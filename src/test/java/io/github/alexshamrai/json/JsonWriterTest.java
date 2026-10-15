package io.github.alexshamrai.json;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonWriterTest {

    @Test
    void writesCompactJson() {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("a", 1);
        object.put("b", Arrays.asList(true, null, "x", 1.5, List.of()));
        object.put("c", Map.of());

        assertThat(JsonWriter.write(object)).isEqualTo("{\"a\":1,\"b\":[true,null,\"x\",1.5,[]],\"c\":{}}");
    }

    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        String text = "\" \\ \b \t \n \f \r " + (char) 0x01 + (char) 0x1F;

        assertThat(JsonWriter.write(text)).isEqualTo("\"\\\" \\\\ \\b \\t \\n \\f \\r \\u0001\\u001F\"");
    }

    @Test
    void writesOtherCharactersAsTheyAre() {
        String text = "/ " + (char) 0x7F + " " + (char) 0xFC + " " + (char) 0x65E5 + " " + (char) 0x2028;

        assertThat(JsonWriter.write(text)).isEqualTo("\"" + text + "\"");
    }

    @Test
    void writesACharacterOutsideTheBasicPlaneAsTheEscapesOfItsSurrogates() {
        String text = "a" + new String(Character.toChars(0x1F600)) + "b";

        assertThat(JsonWriter.write(text)).isEqualTo("\"a\\uD83D\\uDE00b\"");
    }

    @Test
    void writesALoneSurrogateAsAnEscape() {
        String text = "a" + (char) 0xD83D + "b" + (char) 0xDE00;

        assertThat(JsonWriter.write(text)).isEqualTo("\"a\\uD83Db\\uDE00\"");
    }

    @Test
    void writesNumbersThatAreNotFiniteAsStrings() {
        assertThat(JsonWriter.write(Arrays.asList(Double.NaN, Double.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)))
            .isEqualTo("[\"NaN\",\"-Infinity\",\"Infinity\"]");
    }

    @Test
    void rejectsAValueWithoutAJsonForm() {
        assertThatThrownBy(() -> JsonWriter.write(List.of(new Object()))).isInstanceOf(JsonException.class);
    }
}

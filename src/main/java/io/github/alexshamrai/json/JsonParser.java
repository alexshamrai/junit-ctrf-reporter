package io.github.alexshamrai.json;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Parses JSON text into {@code null}, {@link String}, {@link Boolean}, {@link Integer}, {@link Long},
 * {@link BigInteger}, {@link Double}, {@link LinkedHashMap} and {@link ArrayList}.
 * <p>
 * A whole number becomes the smallest of {@code Integer}, {@code Long} and {@code BigInteger} that holds it, and any
 * other number a {@code Double}, as Jackson reads numbers into {@code Object}. An object keeps its members in order,
 * and a repeated name keeps its last value. Whitespace may surround any token, and the text may start with a byte
 * order mark. Anything else that isn't strict JSON throws. So do a number longer than 1000 characters and nesting
 * deeper than 100 levels, which would otherwise take minutes or overflow the stack. It reads through
 * {@code JsonReader}, which has the rules.
 */
public final class JsonParser {

    /**
     * How deep arrays and objects may nest.
     */
    static final int MAX_DEPTH = JsonReader.MAX_DEPTH;

    /**
     * How many characters a number may have, as with Jackson.
     */
    static final int MAX_NUMBER_LENGTH = JsonReader.MAX_NUMBER_LENGTH;

    private JsonParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses one JSON value.
     *
     * @param text the JSON text
     * @return the value
     * @throws JsonException if the text isn't exactly one JSON value
     */
    public static Object parse(String text) {
        try {
            var reader = new JsonReader(new StringReader(text));
            Object value = reader.nextValue();
            reader.endOfText();
            return value;
        } catch (IOException e) {
            throw new UncheckedIOException("A StringReader doesn't fail", e);
        }
    }
}

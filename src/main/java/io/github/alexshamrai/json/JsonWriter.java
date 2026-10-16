package io.github.alexshamrai.json;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Map;

/**
 * Writes JSON as compact text, with no whitespace between tokens, to a {@link Writer}.
 * <p>
 * It writes {@code null}, {@link String}, {@link Boolean}, {@link Number}, {@link Map} with {@code String} keys and
 * {@link Iterable}. Strings are escaped as Jackson escapes them: the quotation mark and the backslash, the short
 * escapes for backspace, tab, newline, form feed and carriage return, and a six-character escape with upper-case hex
 * digits for the other control characters. Every other character is written as it is, except surrogates: a character
 * outside the Basic Multilingual Plane, such as an emoji, is written as the escapes of its two surrogates, as Jackson
 * writes it to bytes, and a surrogate without its pair, which UTF-8 can't encode, is escaped the same way.
 * A double or a float that isn't finite is written as a string, such as {@code "NaN"}, as Jackson writes it.
 * <p>
 * It collects text in its own buffer, passes it on whenever the buffer fills, and on {@code flush()}. It puts the
 * commas between members and elements itself.
 */
public final class JsonWriter {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final int BUFFER_SIZE = 8192;

    private final Writer out;
    private final char[] buffer = new char[BUFFER_SIZE];
    private int count;
    private boolean afterValue;

    JsonWriter(Writer out) {
        this.out = out;
    }

    /**
     * Writes a JSON value as text.
     *
     * @param value the value
     * @return its JSON text
     * @throws JsonException if the value, or a value inside it, has no JSON form
     */
    public static String write(Object value) {
        var text = new StringWriter();
        try {
            var writer = new JsonWriter(text);
            writer.value(value);
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("A StringWriter doesn't fail", e);
        }
        return text.toString();
    }

    /**
     * Writes a value: {@code null}, a string, a boolean, a number, a map with string keys, or an iterable of values.
     */
    void value(Object value) throws IOException {
        if (value instanceof Map<?, ?> object) {
            beginObject();
            for (Map.Entry<?, ?> member : object.entrySet()) {
                if (!(member.getKey() instanceof String name)) {
                    throw new JsonException("A JSON member name must be a string, not " + member.getKey());
                }
                name(name);
                value(member.getValue());
            }
            endObject();
        } else if (value instanceof Iterable<?> array) {
            beginArray();
            for (Object element : array) {
                value(element);
            }
            endArray();
        } else {
            separate();
            scalar(value);
            afterValue = true;
        }
    }

    void beginObject() throws IOException {
        separate();
        append('{');
        afterValue = false;
    }

    void endObject() throws IOException {
        append('}');
        afterValue = true;
    }

    void beginArray() throws IOException {
        separate();
        append('[');
        afterValue = false;
    }

    void endArray() throws IOException {
        append(']');
        afterValue = true;
    }

    /**
     * Writes a member's name and the colon after it.
     */
    void name(String name) throws IOException {
        separate();
        quote(name);
        append(':');
        afterValue = false;
    }

    /**
     * Passes the buffered text on to the {@code Writer}, and flushes it.
     */
    void flush() throws IOException {
        out.write(buffer, 0, count);
        count = 0;
        out.flush();
    }

    private void separate() throws IOException {
        if (afterValue) {
            append(',');
        }
    }

    private void scalar(Object value) throws IOException {
        if (value == null) {
            append("null");
        } else if (value instanceof String text) {
            quote(text);
        } else if (value instanceof Boolean) {
            append(value.toString());
        } else if (value instanceof Number number) {
            boolean floatingPoint = number instanceof Double || number instanceof Float;
            if (floatingPoint && !Double.isFinite(number.doubleValue())) {
                quote(number.toString());
            } else {
                append(number.toString());
            }
        } else {
            throw new JsonException("No JSON form for a " + value.getClass().getName());
        }
    }

    private void quote(String text) throws IOException {
        append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                append('\\');
                append(c);
            } else if (c < 0x20) {
                controlCharacter(c);
            } else if (Character.isSurrogate(c)) {
                unicodeEscape(c);
            } else {
                append(c);
            }
        }
        append('"');
    }

    private void controlCharacter(char c) throws IOException {
        switch (c) {
            case '\b':
                append("\\b");
                break;
            case '\t':
                append("\\t");
                break;
            case '\n':
                append("\\n");
                break;
            case '\f':
                append("\\f");
                break;
            case '\r':
                append("\\r");
                break;
            default:
                unicodeEscape(c);
        }
    }

    private void unicodeEscape(char c) throws IOException {
        append('\\');
        append('u');
        append(HEX_DIGITS[(c >> 12) & 0xF]);
        append(HEX_DIGITS[(c >> 8) & 0xF]);
        append(HEX_DIGITS[(c >> 4) & 0xF]);
        append(HEX_DIGITS[c & 0xF]);
    }

    private void append(char c) throws IOException {
        if (count == buffer.length) {
            out.write(buffer, 0, count);
            count = 0;
        }
        buffer[count++] = c;
    }

    private void append(String text) throws IOException {
        for (int i = 0; i < text.length(); i++) {
            append(text.charAt(i));
        }
    }
}

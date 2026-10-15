package io.github.alexshamrai.json;

import java.util.Map;

/**
 * Writes JSON values as compact text, with no whitespace between tokens.
 * <p>
 * It writes {@code null}, {@link String}, {@link Boolean}, {@link Number}, {@link Map} with {@code String} keys and
 * {@link Iterable}. Strings are escaped as Jackson escapes them: the quotation mark and the backslash, the short
 * escapes for backspace, tab, newline, form feed and carriage return, and a six-character escape with upper-case hex
 * digits for the other control characters. Every other character is written as it is, except surrogates: a character
 * outside the Basic Multilingual Plane, such as an emoji, is written as the escapes of its two surrogates, as Jackson
 * writes it to bytes, and a surrogate without its pair, which UTF-8 can't encode, is escaped the same way.
 * A double or a float that isn't finite is written as a string, such as {@code "NaN"}, as Jackson writes it.
 */
public final class JsonWriter {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private JsonWriter() {
        // Utility class - prevent instantiation
    }

    /**
     * Writes a JSON value.
     *
     * @param value the value
     * @return its JSON text
     * @throws JsonException if the value, or a value inside it, has no JSON form
     */
    public static String write(Object value) {
        var out = new StringBuilder();
        writeValue(value, out);
        return out.toString();
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(text, out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number number) {
            writeNumber(number, out);
        } else if (value instanceof Map<?, ?> object) {
            writeObject(object, out);
        } else if (value instanceof Iterable<?> array) {
            writeArray(array, out);
        } else {
            throw new JsonException("No JSON form for a " + value.getClass().getName());
        }
    }

    private static void writeNumber(Number number, StringBuilder out) {
        boolean floatingPoint = number instanceof Double || number instanceof Float;
        if (floatingPoint && !Double.isFinite(number.doubleValue())) {
            writeString(number.toString(), out);
        } else {
            out.append(number);
        }
    }

    private static void writeObject(Map<?, ?> object, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> member : object.entrySet()) {
            if (!(member.getKey() instanceof String name)) {
                throw new JsonException("A JSON member name must be a string, not " + member.getKey());
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(name, out);
            out.append(':');
            writeValue(member.getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(Iterable<?> array, StringBuilder out) {
        out.append('[');
        boolean first = true;
        for (Object element : array) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(element, out);
        }
        out.append(']');
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                writeControlCharacter(c, out);
            } else if (Character.isSurrogate(c)) {
                writeUnicodeEscape(c, out);
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }

    private static void writeControlCharacter(char c, StringBuilder out) {
        switch (c) {
            case '\b':
                out.append("\\b");
                break;
            case '\t':
                out.append("\\t");
                break;
            case '\n':
                out.append("\\n");
                break;
            case '\f':
                out.append("\\f");
                break;
            case '\r':
                out.append("\\r");
                break;
            default:
                writeUnicodeEscape(c, out);
        }
    }

    private static void writeUnicodeEscape(char c, StringBuilder out) {
        out.append("\\u")
            .append(HEX_DIGITS[(c >> 12) & 0xF])
            .append(HEX_DIGITS[(c >> 8) & 0xF])
            .append(HEX_DIGITS[(c >> 4) & 0xF])
            .append(HEX_DIGITS[c & 0xF]);
    }
}

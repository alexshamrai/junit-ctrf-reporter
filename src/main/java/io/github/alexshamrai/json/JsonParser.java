package io.github.alexshamrai.json;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses JSON text into {@code null}, {@link String}, {@link Boolean}, {@link Integer}, {@link Long},
 * {@link BigInteger}, {@link Double}, {@link LinkedHashMap} and {@link ArrayList}.
 * <p>
 * A whole number becomes the smallest of {@code Integer}, {@code Long} and {@code BigInteger} that holds it, and any
 * other number a {@code Double}, as Jackson reads numbers into {@code Object}. An object keeps its members in order,
 * and a repeated name keeps its last value. Whitespace may surround any token, and the text may start with a byte
 * order mark. Anything else that isn't strict JSON throws. So do a number longer than 1000 characters and nesting
 * deeper than 100 levels, which would otherwise take minutes or overflow the stack.
 */
public final class JsonParser {

    /**
     * How deep arrays and objects may nest. Real reports nest fewer than ten levels; 100 keeps the recursive reader
     * and writer well inside small thread stacks.
     */
    static final int MAX_DEPTH = 100;

    /**
     * How many characters a number may have, as with Jackson.
     */
    static final int MAX_NUMBER_LENGTH = 1000;

    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

    private final String text;
    private int position;
    private int depth;

    private JsonParser(String text) {
        this.text = text;
    }

    /**
     * Parses one JSON value.
     *
     * @param text the JSON text
     * @return the value
     * @throws JsonException if the text isn't exactly one JSON value
     */
    public static Object parse(String text) {
        var parser = new JsonParser(text);
        if (parser.peek(BYTE_ORDER_MARK)) {
            parser.position++;
        }
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position < text.length()) {
            throw parser.error("Unexpected text after the JSON value");
        }
        return value;
    }

    private Object readValue() {
        if (position >= text.length()) {
            throw error("Unexpected end of the JSON text");
        }
        char c = text.charAt(position);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                readWord("true");
                return Boolean.TRUE;
            case 'f':
                readWord("false");
                return Boolean.FALSE;
            case 'n':
                readWord("null");
                return null;
            default:
                if (c == '-' || isDigit(c)) {
                    return readNumber();
                }
                throw error("Unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> readObject() {
        enterNesting();
        position++;
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) {
            position++;
            depth--;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (!peek('"')) {
                throw error("Expected a member name");
            }
            String name = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(name, readValue());
            skipWhitespace();
            if (!peek(',')) {
                expect('}');
                depth--;
                return object;
            }
            position++;
        }
    }

    private List<Object> readArray() {
        enterNesting();
        position++;
        List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            position++;
            depth--;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(readValue());
            skipWhitespace();
            if (!peek(',')) {
                expect(']');
                depth--;
                return array;
            }
            position++;
        }
    }

    private String readString() {
        position++;
        var out = new StringBuilder();
        while (true) {
            if (position >= text.length()) {
                throw error("Unterminated string");
            }
            char c = text.charAt(position++);
            if (c == '"') {
                return out.toString();
            } else if (c == '\\') {
                readEscape(out);
            } else if (c < 0x20) {
                throw error("Unescaped control character in a string");
            } else {
                out.append(c);
            }
        }
    }

    private void readEscape(StringBuilder out) {
        if (position >= text.length()) {
            throw error("Unterminated escape");
        }
        char c = text.charAt(position++);
        switch (c) {
            case '"':
            case '\\':
            case '/':
                out.append(c);
                break;
            case 'b':
                out.append('\b');
                break;
            case 'f':
                out.append('\f');
                break;
            case 'n':
                out.append('\n');
                break;
            case 'r':
                out.append('\r');
                break;
            case 't':
                out.append('\t');
                break;
            case 'u':
                out.append(readHexCharacter());
                break;
            default:
                throw error("Invalid escape \\" + c);
        }
    }

    private char readHexCharacter() {
        if (position + 4 > text.length()) {
            throw error("Truncated \\u escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = hexValue(text.charAt(position + i));
            if (digit < 0) {
                throw error("Invalid \\u escape");
            }
            value = value * 16 + digit;
        }
        position += 4;
        return (char) value;
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private Number readNumber() {
        int start = position;
        boolean whole = true;
        if (peek('-')) {
            position++;
        }
        if (peek('0')) {
            position++;
        } else {
            readDigits();
        }
        if (peek('.')) {
            whole = false;
            position++;
            readDigits();
        }
        if (peek('e') || peek('E')) {
            whole = false;
            position++;
            if (peek('+') || peek('-')) {
                position++;
            }
            readDigits();
        }
        if (position - start > MAX_NUMBER_LENGTH) {
            throw error("Number longer than " + MAX_NUMBER_LENGTH + " characters");
        }
        String number = text.substring(start, position);
        return whole ? wholeNumber(number) : Double.valueOf(number);
    }

    private void readDigits() {
        if (position >= text.length() || !isDigit(text.charAt(position))) {
            throw error("Expected a digit");
        }
        while (position < text.length() && isDigit(text.charAt(position))) {
            position++;
        }
    }

    private static Number wholeNumber(String digits) {
        var value = new BigInteger(digits);
        if (value.bitLength() < Integer.SIZE) {
            return value.intValue();
        }
        if (value.bitLength() < Long.SIZE) {
            return value.longValue();
        }
        return value;
    }

    private void readWord(String word) {
        if (!text.startsWith(word, position)) {
            throw error("Expected " + word);
        }
        position += word.length();
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("Expected '" + c + "'");
        }
        position++;
    }

    private boolean peek(char c) {
        return position < text.length() && text.charAt(position) == c;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void skipWhitespace() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            position++;
        }
    }

    private void enterNesting() {
        depth++;
        if (depth > MAX_DEPTH) {
            throw error("Arrays and objects nest deeper than " + MAX_DEPTH + " levels");
        }
    }

    private JsonException error(String problem) {
        return new JsonException(problem + " at character " + position);
    }
}

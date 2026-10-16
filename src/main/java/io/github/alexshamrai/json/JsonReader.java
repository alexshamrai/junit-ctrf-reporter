package io.github.alexshamrai.json;

import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads JSON text token by token from a {@link Reader}, through its own buffer, so the text is never held whole.
 * <p>
 * A whole number becomes the smallest of {@code Integer}, {@code Long} and {@code BigInteger} that holds it, and any
 * other number a {@code Double}, as Jackson reads numbers into {@code Object}. Whitespace may surround any token, and
 * the text may start with a byte order mark. Anything else that isn't strict JSON throws, as do a number longer than
 * {@value #MAX_NUMBER_LENGTH} characters and nesting deeper than {@value #MAX_DEPTH} levels, which would otherwise
 * take minutes or overflow the stack. A message names the problem and its position, counted in characters from the
 * start of the text, the byte order mark included.
 * <p>
 * An object is read with {@link #beginObject}, then for each member {@link #nextName}, its value, and
 * {@link #nextMember}; an array with {@link #beginArray}, then for each element its value and {@link #nextElement}.
 */
final class JsonReader {

    /**
     * What a JSON value is, as {@link #peek} tells it from the value's first character.
     */
    enum Kind {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
    }

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
    private static final int BUFFER_SIZE = 8192;

    private final Reader in;
    private final char[] buffer = new char[BUFFER_SIZE];
    private final StringBuilder text = new StringBuilder();
    private int pos;
    private int limit;
    private long consumedBeforeBuffer;
    private int depth;
    private boolean started;

    JsonReader(Reader in) {
        this.in = in;
    }

    /**
     * Tells what the next value is, without reading it.
     */
    Kind peek() throws IOException {
        skipWhitespace();
        int c = peekChar();
        switch (c) {
            case -1:
                throw error("Unexpected end of the JSON text");
            case '{':
                return Kind.OBJECT;
            case '[':
                return Kind.ARRAY;
            case '"':
                return Kind.STRING;
            case 't':
            case 'f':
                return Kind.BOOLEAN;
            case 'n':
                return Kind.NULL;
            default:
                if (c == '-' || isDigit(c)) {
                    return Kind.NUMBER;
                }
                throw error("Unexpected character '" + (char) c + "'");
        }
    }

    /**
     * Reads an object's opening brace, and returns whether a member follows; if none does, the closing brace is read
     * too.
     */
    boolean beginObject() throws IOException {
        skipWhitespace();
        enterNesting();
        expect('{');
        skipWhitespace();
        if (peekChar() == '}') {
            pos++;
            depth--;
            return false;
        }
        return true;
    }

    /**
     * Reads a member's name and the colon after it.
     */
    String nextName() throws IOException {
        skipWhitespace();
        if (peekChar() != '"') {
            throw error("Expected a member name");
        }
        String name = readString();
        skipWhitespace();
        expect(':');
        return name;
    }

    /**
     * Reads what follows a member's value, and returns whether it's a comma, so another member follows, rather than
     * the object's closing brace.
     */
    boolean nextMember() throws IOException {
        skipWhitespace();
        if (peekChar() == ',') {
            pos++;
            return true;
        }
        expect('}');
        depth--;
        return false;
    }

    /**
     * Reads an array's opening bracket, and returns whether an element follows; if none does, the closing bracket is
     * read too.
     */
    boolean beginArray() throws IOException {
        skipWhitespace();
        enterNesting();
        expect('[');
        skipWhitespace();
        if (peekChar() == ']') {
            pos++;
            depth--;
            return false;
        }
        return true;
    }

    /**
     * Reads what follows an element, and returns whether it's a comma, so another element follows, rather than the
     * array's closing bracket.
     */
    boolean nextElement() throws IOException {
        skipWhitespace();
        if (peekChar() == ',') {
            pos++;
            return true;
        }
        expect(']');
        depth--;
        return false;
    }

    /**
     * Reads the next value: a {@code String}, a {@code Boolean}, a number, {@code null}, or an object or an array as a
     * {@code LinkedHashMap} or an {@code ArrayList}. An object keeps its members in order, and a repeated name keeps
     * its last value.
     */
    Object nextValue() throws IOException {
        switch (peek()) {
            case OBJECT:
                return readObject();
            case ARRAY:
                return readArray();
            case STRING:
                return readString();
            case NUMBER:
                return readNumber();
            case BOOLEAN:
                return readBoolean();
            default:
                readWord("null");
                return null;
        }
    }

    /**
     * Reads past the next value without keeping it, as strictly as {@link #nextValue}: objects and arrays aren't
     * built, and strings and numbers are dropped as soon as they're read.
     */
    void skipValue() throws IOException {
        switch (peek()) {
            case OBJECT:
                if (beginObject()) {
                    do {
                        nextName();
                        skipValue();
                    } while (nextMember());
                }
                break;
            case ARRAY:
                if (beginArray()) {
                    do {
                        skipValue();
                    } while (nextElement());
                }
                break;
            default:
                nextValue();
        }
    }

    /**
     * Checks that only whitespace follows.
     */
    void endOfText() throws IOException {
        skipWhitespace();
        if (peekChar() != -1) {
            throw error("Unexpected text after the JSON value");
        }
    }

    private Map<String, Object> readObject() throws IOException {
        Map<String, Object> object = new LinkedHashMap<>();
        if (beginObject()) {
            do {
                String name = nextName();
                object.put(name, nextValue());
            } while (nextMember());
        }
        return object;
    }

    private List<Object> readArray() throws IOException {
        List<Object> array = new ArrayList<>();
        if (beginArray()) {
            do {
                array.add(nextValue());
            } while (nextElement());
        }
        return array;
    }

    // Starts at the opening quote, which peek has seen
    private String readString() throws IOException {
        pos++;
        text.setLength(0);
        while (true) {
            if (pos == limit && ensureAvailable(1) == 0) {
                throw error("Unterminated string");
            }
            char c = buffer[pos++];
            if (c == '"') {
                return text.toString();
            } else if (c == '\\') {
                readEscape();
            } else if (c < 0x20) {
                throw error("Unescaped control character in a string");
            } else {
                text.append(c);
            }
        }
    }

    private void readEscape() throws IOException {
        if (pos == limit && ensureAvailable(1) == 0) {
            throw error("Unterminated escape");
        }
        char c = buffer[pos++];
        switch (c) {
            case '"':
            case '\\':
            case '/':
                text.append(c);
                break;
            case 'b':
                text.append('\b');
                break;
            case 'f':
                text.append('\f');
                break;
            case 'n':
                text.append('\n');
                break;
            case 'r':
                text.append('\r');
                break;
            case 't':
                text.append('\t');
                break;
            case 'u':
                text.append(readHexCharacter());
                break;
            default:
                throw error("Invalid escape \\" + c);
        }
    }

    private char readHexCharacter() throws IOException {
        if (ensureAvailable(4) < 4) {
            throw error("Truncated \\u escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = hexValue(buffer[pos + i]);
            if (digit < 0) {
                throw error("Invalid \\u escape");
            }
            value = value * 16 + digit;
        }
        pos += 4;
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

    private Number readNumber() throws IOException {
        long start = position();
        text.setLength(0);
        boolean whole = true;
        if (peekChar() == '-') {
            take();
        }
        if (peekChar() == '0') {
            take();
        } else {
            readDigits();
        }
        if (peekChar() == '.') {
            whole = false;
            take();
            readDigits();
        }
        int c = peekChar();
        if (c == 'e' || c == 'E') {
            whole = false;
            take();
            c = peekChar();
            if (c == '+' || c == '-') {
                take();
            }
            readDigits();
        }
        if (position() - start > MAX_NUMBER_LENGTH) {
            throw error("Number longer than " + MAX_NUMBER_LENGTH + " characters");
        }
        String number = text.toString();
        return whole ? wholeNumber(number) : Double.valueOf(number);
    }

    // Takes the character peekChar has seen into the number; past the length limit, which throws once the number
    // ends, it isn't kept
    private void take() {
        if (text.length() <= MAX_NUMBER_LENGTH) {
            text.append(buffer[pos]);
        }
        pos++;
    }

    private void readDigits() throws IOException {
        if (!isDigit(peekChar())) {
            throw error("Expected a digit");
        }
        while (isDigit(peekChar())) {
            take();
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

    private Boolean readBoolean() throws IOException {
        if (peekChar() == 't') {
            readWord("true");
            return Boolean.TRUE;
        }
        readWord("false");
        return Boolean.FALSE;
    }

    private void readWord(String word) throws IOException {
        int available = ensureAvailable(word.length());
        for (int i = 0; i < word.length(); i++) {
            if (i >= available || buffer[pos + i] != word.charAt(i)) {
                throw error("Expected " + word);
            }
        }
        pos += word.length();
    }

    private void expect(char c) throws IOException {
        if (peekChar() != c) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    // The byte order mark may come only first, before any whitespace
    private void skipWhitespace() throws IOException {
        if (!started) {
            started = true;
            if (peekChar() == BYTE_ORDER_MARK) {
                pos++;
            }
        }
        while (true) {
            int c = peekChar();
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    // The next character, without taking it, or -1 at the end of the text
    private int peekChar() throws IOException {
        if (pos == limit && ensureAvailable(1) == 0) {
            return -1;
        }
        return buffer[pos];
    }

    /**
     * Makes up to {@code count} characters available from {@code pos}, at most the buffer's size, and returns how many
     * are: fewer only at the end of the text. Unread characters move to the front of the buffer first.
     */
    private int ensureAvailable(int count) throws IOException {
        if (limit - pos < count) {
            if (pos > 0) {
                System.arraycopy(buffer, pos, buffer, 0, limit - pos);
                consumedBeforeBuffer += pos;
                limit -= pos;
                pos = 0;
            }
            while (limit < count) {
                int read = in.read(buffer, limit, buffer.length - limit);
                if (read < 0) {
                    break;
                }
                limit += read;
            }
        }
        return limit - pos;
    }

    private long position() {
        return consumedBeforeBuffer + pos;
    }

    private void enterNesting() {
        depth++;
        if (depth > MAX_DEPTH) {
            throw error("Arrays and objects nest deeper than " + MAX_DEPTH + " levels");
        }
    }

    private JsonException error(String problem) {
        return new JsonException(problem + " at character " + position());
    }
}

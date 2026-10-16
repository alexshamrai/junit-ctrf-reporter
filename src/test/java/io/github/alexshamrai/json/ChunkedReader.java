package io.github.alexshamrai.json;

import java.io.Reader;

/**
 * Hands out at most {@code size} characters per read, so that tokens are cut across a reader's buffer refills.
 */
final class ChunkedReader extends Reader {

    private final String text;
    private final int size;
    private int position;

    ChunkedReader(String text, int size) {
        this.text = text;
        this.size = size;
    }

    @Override
    public int read(char[] target, int offset, int length) {
        if (position >= text.length()) {
            return -1;
        }
        int count = Math.min(Math.min(size, length), text.length() - position);
        text.getChars(position, position + count, target, offset);
        position += count;
        return count;
    }

    @Override
    public void close() {
        // Nothing to release
    }
}

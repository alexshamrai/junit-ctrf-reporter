package io.github.alexshamrai.json;

/**
 * Thrown when text isn't valid JSON, or when JSON doesn't fit the CTRF model, or the model can't be written as JSON.
 */
public final class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception.
     *
     * @param message what went wrong, and where
     */
    public JsonException(String message) {
        super(message);
    }

    /**
     * Creates an exception with its cause.
     *
     * @param message what went wrong, and where
     * @param cause   the underlying error
     */
    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}

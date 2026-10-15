package io.github.alexshamrai;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures what one class logs through {@code java.util.logging} during each test, and keeps it off the console.
 * Register it with {@code @RegisterExtension}.
 */
public final class LogCapture implements BeforeEachCallback, AfterEachCallback {

    /**
     * Held for as long as the capture, because the logging framework keeps loggers only weakly.
     */
    private final Logger logger;
    private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // Nothing is buffered
        }

        @Override
        public void close() {
            // Nothing to release
        }
    };
    private boolean usedParentHandlers;

    /**
     * Captures the logger named after the given class, which is the logger that class logs to.
     *
     * @param loggingClass the class whose warnings to capture
     */
    public LogCapture(Class<?> loggingClass) {
        this.logger = Logger.getLogger(loggingClass.getName());
        handler.setLevel(Level.ALL);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        records.clear();
        usedParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        logger.removeHandler(handler);
        logger.setUseParentHandlers(usedParentHandlers);
    }

    /**
     * Returns the records captured in this test, oldest first.
     *
     * @return a copy of the records
     */
    public List<LogRecord> records() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    /**
     * Returns the messages captured in this test, oldest first.
     *
     * @return the messages
     */
    public List<String> messages() {
        return records().stream().map(LogRecord::getMessage).toList();
    }

    /**
     * Returns the messages captured in this test, one per line.
     *
     * @return the messages, or an empty string if nothing was logged
     */
    public String text() {
        return String.join("\n", messages());
    }
}

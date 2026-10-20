package org.junit.platform.engine.reporting;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A compile-only stand-in for JUnit's {@code FileEntry}, which JUnit 5.12 added, with the methods that the reporter
 * calls. The reporter compiles against JUnit 5.10, the oldest version it supports, and against this class. It isn't in
 * the jar: at run time, JUnit 5.12 and later bring their own, and JUnit 5.10 and 5.11 never use it.
 */
public final class FileEntry {

    private FileEntry() {
    }

    /**
     * Returns the path of the published file or directory.
     *
     * @return the path
     */
    public Path getPath() {
        throw new UnsupportedOperationException("A compile-only stand-in");
    }

    /**
     * Returns the media type the file was published with.
     *
     * @return the media type, if there is one
     */
    public Optional<String> getMediaType() {
        throw new UnsupportedOperationException("A compile-only stand-in");
    }
}

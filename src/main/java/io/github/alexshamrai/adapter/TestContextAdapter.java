package io.github.alexshamrai.adapter;

import java.util.Optional;
import java.util.Set;

/**
 * Adapter interface for accessing test context information from different JUnit sources.
 * <p>
 * This interface provides a unified way to extract test metadata from both
 * JUnit Jupiter ExtensionContext and JUnit Platform TestIdentifier. It enables
 * code reuse by abstracting away the differences between these two contexts.
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@link ExtensionContextAdapter} - wraps JUnit Jupiter ExtensionContext</li>
 *   <li>{@link TestIdentifierAdapter} - wraps JUnit Platform TestIdentifier</li>
 * </ul>
 */
public interface TestContextAdapter {

    /**
     * Returns the unique identifier for the test.
     *
     * @return the unique test ID
     */
    String getUniqueId();

    /**
     * Returns the display name of the test.
     *
     * @return the test display name
     */
    String getDisplayName();

    /**
     * Returns the tags associated with the test.
     *
     * @return set of tag strings
     */
    Set<String> getTags();

    /**
     * Returns the source location of the test (typically the class name).
     *
     * @return optional source location string, or empty if not available
     */
    Optional<String> getSourceLocation();
}

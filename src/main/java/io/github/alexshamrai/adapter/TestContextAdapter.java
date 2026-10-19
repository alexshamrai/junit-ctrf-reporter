package io.github.alexshamrai.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter interface for accessing test context information from different JUnit sources.
 * <p>
 * Implemented by {@link TestIdentifierAdapter}, which wraps a JUnit Platform {@code TestIdentifier}.
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

    /**
     * Returns the display names of the containers above this one, outermost first, without the engine.
     *
     * @return the container names; empty by default, and when the engine is the only container above
     */
    default List<String> getContainerNames() {
        return List.of();
    }

    /**
     * Returns the display name of the engine this belongs to: the topmost container, or this one if nothing is above it.
     *
     * @return the engine's display name; empty by default
     */
    default Optional<String> getEngineName() {
        return Optional.empty();
    }

    /**
     * Returns the CTRF {@code suite}: the container names, or the engine's name when there are none.
     *
     * @return the suite, outermost first; empty when neither containers nor the engine are known
     */
    default List<String> getSuite() {
        List<String> containers = getContainerNames();
        if (!containers.isEmpty()) {
            return containers;
        }
        return getEngineName().map(List::of).orElse(List.of());
    }

    /**
     * Returns the suite of what this container holds: its container names followed by its own display name. A class's
     * "initializationError" or "teardownError" entry gets it, so that it's grouped with the class's tests.
     *
     * @return the suite, outermost first; it never falls back to the engine's name
     */
    default List<String> getSuiteOfChildren() {
        List<String> suite = new ArrayList<>(getContainerNames());
        suite.add(getDisplayName());
        return suite;
    }
}

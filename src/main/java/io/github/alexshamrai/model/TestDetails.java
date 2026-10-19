package io.github.alexshamrai.model;

import io.github.alexshamrai.adapter.TestContextAdapter;

import java.util.List;
import java.util.Set;

/**
 * Contains metadata about a test during execution.
 * <p>
 * This record holds information extracted from a JUnit Platform {@code TestIdentifier} and is used internally for
 * tracking tests during execution.
 * <p>
 * As a record, this class is immutable - all fields are set during construction.
 *
 * @param startTime   when the test started, in milliseconds since the epoch
 * @param tags        the test's tags
 * @param filePath    the test's class, or {@code null}
 * @param uniqueId    the JUnit unique ID, which becomes the report's {@code testId}
 * @param displayName the test's display name, which becomes the report's {@code name}
 * @param suite       the display names of the test's containers, outermost first; empty if there are none
 */
public record TestDetails(
    long startTime,
    Set<String> tags,
    String filePath,
    String uniqueId,
    String displayName,
    List<String> suite
) {

    /**
     * Creates TestDetails. The suite is copied; {@code null} means none, as an empty list does.
     */
    public TestDetails {
        suite = suite == null ? List.of() : List.copyOf(suite);
    }

    /**
     * Creates TestDetails without a suite.
     *
     * @param startTime   when the test started
     * @param tags        the test's tags
     * @param filePath    the test's class, or {@code null}
     * @param uniqueId    the JUnit unique ID
     * @param displayName the test's display name
     */
    public TestDetails(long startTime, Set<String> tags, String filePath, String uniqueId, String displayName) {
        this(startTime, tags, filePath, uniqueId, displayName, List.of());
    }

    /**
     * Creates TestDetails from a test context adapter.
     * <p>
     * This static factory method extracts all relevant test metadata (unique ID, display name,
     * tags, source location, suite) from the adapter and constructs a TestDetails record with the
     * current timestamp as the start time.
     *
     * @param adapter the test context adapter
     * @return the constructed TestDetails
     */
    public static TestDetails fromAdapter(TestContextAdapter adapter) {
        return new TestDetails(
            System.currentTimeMillis(),
            adapter.getTags(),
            adapter.getSourceLocation().orElse(null),
            adapter.getUniqueId(),
            adapter.getDisplayName(),
            adapter.getSuite()
        );
    }
}

package io.github.alexshamrai.model;

import io.github.alexshamrai.adapter.TestContextAdapter;

import java.util.Set;

/**
 * Contains metadata about a test during execution.
 * <p>
 * This record holds information extracted from test contexts (JUnit Jupiter ExtensionContext
 * or JUnit Platform TestIdentifier) and is used internally for tracking tests during execution.
 * <p>
 * As a record, this class is immutable - all fields are set during construction.
 */
public record TestDetails(
    long startTime,
    Set<String> tags,
    String filePath,
    String uniqueId,
    String displayName
) {

    /**
     * Creates TestDetails from a test context adapter.
     * <p>
     * This static factory method extracts all relevant test metadata (unique ID, display name,
     * tags, source location) from the adapter and constructs a TestDetails record with the
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
            adapter.getDisplayName()
        );
    }
}

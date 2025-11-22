package io.github.alexshamrai.adapter;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter that wraps JUnit Platform {@link TestIdentifier} to provide test metadata.
 * <p>
 * This adapter extracts test information from the JUnit Platform launcher model,
 * making it available through the {@link TestContextAdapter} interface.
 * It handles conversion of TestTag objects to strings and extracts source location
 * from various TestSource types.
 */
public final class TestIdentifierAdapter implements TestContextAdapter {

    private final TestIdentifier identifier;

    /**
     * Creates a new adapter wrapping the given test identifier.
     *
     * @param identifier the JUnit Platform test identifier
     */
    public TestIdentifierAdapter(TestIdentifier identifier) {
        this.identifier = identifier;
    }

    @Override
    public String getUniqueId() {
        return identifier.getUniqueId();
    }

    @Override
    public String getDisplayName() {
        return identifier.getDisplayName();
    }

    @Override
    public Set<String> getTags() {
        return identifier.getTags().stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    @Override
    public Optional<String> getSourceLocation() {
        return identifier.getSource().map(this::extractSourceLocation);
    }

    private String extractSourceLocation(TestSource source) {
        if (source instanceof MethodSource) {
            return ((MethodSource) source).getClassName();
        } else if (source instanceof ClassSource) {
            return ((ClassSource) source).getClassName();
        }
        return source.toString();
    }
}

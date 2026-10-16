package io.github.alexshamrai.adapter;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.ArrayList;
import java.util.List;
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
    private final TestPlan plan;

    /**
     * Creates a new adapter wrapping the given test identifier. Without its plan, it has no suite.
     *
     * @param identifier the JUnit Platform test identifier
     */
    public TestIdentifierAdapter(TestIdentifier identifier) {
        this(identifier, null);
    }

    /**
     * Creates a new adapter wrapping the given test identifier, with the plan it belongs to, which gives its suite.
     *
     * @param identifier the JUnit Platform test identifier
     * @param plan       the test plan the identifier belongs to, or {@code null}
     */
    public TestIdentifierAdapter(TestIdentifier identifier, TestPlan plan) {
        this.identifier = identifier;
        this.plan = plan;
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

    @Override
    public List<String> getContainerNames() {
        if (plan == null) {
            return List.of();
        }
        try {
            List<String> names = new ArrayList<>();
            Optional<TestIdentifier> parent = plan.getParent(identifier);
            while (parent.isPresent()) {
                if (!isEngine(parent.get())) {
                    names.add(0, parent.get().getDisplayName());
                }
                parent = plan.getParent(parent.get());
            }
            return names;
        } catch (RuntimeException e) {
            // An identifier whose ancestors the plan can't give has no suite; the test itself is still reported
            return List.of();
        }
    }

    @Override
    public Optional<String> getEngineName() {
        if (plan == null) {
            return Optional.empty();
        }
        try {
            TestIdentifier current = identifier;
            while (!isEngine(current)) {
                Optional<TestIdentifier> parent = plan.getParent(current);
                if (parent.isEmpty()) {
                    return Optional.empty();
                }
                current = parent.get();
            }
            return Optional.ofNullable(current.getDisplayName());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    // Every engine is left out of a suite, also one that the suite engine runs inside a @Suite class
    private static boolean isEngine(TestIdentifier identifier) {
        return "engine".equals(identifier.getUniqueIdObject().getLastSegment().getType());
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

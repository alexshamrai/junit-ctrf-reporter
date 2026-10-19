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
 * The listener's view of a test: what the report needs from a JUnit Platform {@link TestIdentifier}, and, with the
 * {@link TestPlan} it belongs to, its {@code suite}.
 */
public final class TestIdentifierAdapter {

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

    /**
     * Returns the test's unique ID.
     *
     * @return the JUnit unique ID
     */
    public String getUniqueId() {
        return identifier.getUniqueId();
    }

    /**
     * Returns the test's display name.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return identifier.getDisplayName();
    }

    /**
     * Returns the test's tags.
     *
     * @return the tag names
     */
    public Set<String> getTags() {
        return identifier.getTags().stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    /**
     * Returns where the test is: the class of a class or method source, or the source's text otherwise.
     *
     * @return the location, or empty if the test has no source
     */
    public Optional<String> getSourceLocation() {
        return identifier.getSource().map(this::extractSourceLocation);
    }

    /**
     * Returns the display names of the containers above, outermost first, without any engine.
     *
     * @return the container names; empty without a plan, or when only engines are above
     */
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

    /**
     * Returns the display name of the nearest engine, this identifier's own if it is one.
     *
     * @return the engine's display name; empty without a plan
     */
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

    /**
     * Returns the CTRF {@code suite}: the container names, or the engine's name when there are none.
     *
     * @return the suite, outermost first; empty when neither containers nor the engine are known
     */
    public List<String> getSuite() {
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
    public List<String> getSuiteOfChildren() {
        List<String> suite = new ArrayList<>(getContainerNames());
        suite.add(getDisplayName());
        return suite;
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

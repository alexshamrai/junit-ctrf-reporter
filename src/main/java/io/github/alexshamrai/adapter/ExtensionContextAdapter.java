package io.github.alexshamrai.adapter;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;
import java.util.Set;

/**
 * Adapter that wraps JUnit Jupiter {@link ExtensionContext} to provide test metadata.
 * <p>
 * This adapter extracts test information from the JUnit Jupiter extension model,
 * making it available through the {@link TestContextAdapter} interface.
 */
public final class ExtensionContextAdapter implements TestContextAdapter {

    private final ExtensionContext context;

    /**
     * Creates a new adapter wrapping the given extension context.
     *
     * @param context the JUnit Jupiter extension context
     */
    public ExtensionContextAdapter(ExtensionContext context) {
        this.context = context;
    }

    @Override
    public String getUniqueId() {
        return context.getUniqueId();
    }

    @Override
    public String getDisplayName() {
        return context.getDisplayName();
    }

    @Override
    public Set<String> getTags() {
        return context.getTags();
    }

    @Override
    public Optional<String> getSourceLocation() {
        return context.getTestClass().map(Class::getName);
    }
}

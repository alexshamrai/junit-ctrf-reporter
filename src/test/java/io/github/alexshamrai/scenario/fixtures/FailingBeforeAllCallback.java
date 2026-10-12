package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * An extension whose class setup fails, as a container-starting extension might.
 */
public class FailingBeforeAllCallback implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        throw new IllegalStateException("callback failed");
    }
}

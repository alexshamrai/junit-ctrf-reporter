package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * An extension whose per-test setup fails, before CtrfExtension's own beforeEach runs.
 */
public class FailingBeforeEachCallback implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        throw new IllegalStateException("fixture setup failed");
    }
}

package io.github.alexshamrai.scenario.fixtures;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * An extension whose per-test setup aborts the test with a failed assumption, before CtrfExtension's own
 * beforeEach runs.
 */
public class AbortingBeforeEachCallback implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        assumeTrue(false, "service not available for setup");
    }
}

package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A class using CtrfExtension whose setup is failed by another extension, not by a lifecycle method.
 */
@Tag("scenario-fixture")
@ExtendWith({CtrfExtension.class, FailingBeforeAllCallback.class})
public class ExtThirdCallbackFailureFixture {

    @Test
    void neverRuns() {
    }
}

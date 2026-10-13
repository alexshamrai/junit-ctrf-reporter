package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A test that an extension registered before CtrfExtension aborts, so CtrfExtension's beforeEach never runs.
 */
@Tag("scenario-fixture")
@ExtendWith({AbortingBeforeEachCallback.class, CtrfExtension.class})
public class ExtAbortedSetupFixture {

    @Test
    @Tag("payments")
    void importantBusinessTest() {
    }
}

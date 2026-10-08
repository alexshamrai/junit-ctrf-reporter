package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A test whose setup fails in an extension registered before CtrfExtension, so CtrfExtension's beforeEach never runs.
 */
@Tag("scenario-fixture")
@ExtendWith({FailingBeforeEachCallback.class, CtrfExtension.class})
public class ExtFailingSetupFixture {

    @Test
    @Tag("payments")
    void importantBusinessTest() {
    }
}

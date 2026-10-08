package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A passing class that sorts before the other extension fixtures, so their failures happen in a later class.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
public class ExtFirstFixture {

    @Test
    void first() {
    }
}

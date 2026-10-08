package compat;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(CtrfExtension.class)
class CompatTest {

    @Test
    void passes() throws Exception {
        VersionProbe.write();
    }

    @Test
    void fails() {
        fail("intentional failure: the compatibility check expects one failed test");
    }
}

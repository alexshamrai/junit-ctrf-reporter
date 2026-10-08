package compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

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

package compat;

import org.junit.Test;

import static org.junit.Assert.fail;

public class VintageCompatTest {

    @Test
    public void passes() throws Exception {
        VersionProbe.write();
    }

    @Test
    public void fails() {
        fail("intentional failure: the compatibility check expects one failed test");
    }
}

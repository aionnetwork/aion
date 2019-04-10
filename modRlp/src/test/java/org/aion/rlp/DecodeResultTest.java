package org.aion.rlp;

import org.junit.Test;

public class DecodeResultTest {

    @Test(expected = RuntimeException.class)
    public void testAsString_wRuntimeException() {
        DecodeResult res = new DecodeResult(0, null);
        res.toString();
    }
}

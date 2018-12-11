package org.aion.p2p;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;

/** @author chris */
public class VerTest {

    @Test
    public void testFilter() {

        /*
         * active versions
         */
        short v0 = (byte) ThreadLocalRandom.current().nextInt(0, 2);
        assertEquals(v0, Ver.filter(v0));

        /*
         * inactive versions
         */
        byte b1 = (byte) ThreadLocalRandom.current().nextInt(2, Short.MAX_VALUE);
        assertEquals(Ver.UNKNOWN, Ver.filter(b1));
    }
}

package org.aion.p2p;

import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;

/**
 * @author  chris
 */
public class VersionTest {

    @Test
    public void testFilter() {

        /*
         * active versions
         */
        short v0 = (byte)ThreadLocalRandom.current().nextInt(0, 2);
        assertEquals(v0, Version.filter(v0));

        /*
         * inactive versions
         */
        byte b1 = (byte)ThreadLocalRandom.current().nextInt(2, Short.MAX_VALUE);
        assertEquals(Version.UNKNOWN, Version.filter(b1));

    }
}

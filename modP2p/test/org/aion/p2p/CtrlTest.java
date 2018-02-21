package org.aion.p2p;

import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;

public class CtrlTest {

    @Test
    public void testFilter() {

        /*
         * active ctrls
         */
        byte c0 = (byte)ThreadLocalRandom.current().nextInt(Ctrl.MIN, Ctrl.MAX + 1);
        assertEquals(c0, Ctrl.filter(c0));

        /*
         * inactive ctrls
         */
        byte c1 = (byte)ThreadLocalRandom.current().nextInt(Ctrl.MAX + 1, Byte.MAX_VALUE + 1);
        assertEquals(Ctrl.UNKNOWN, Ctrl.filter(c1));

    }
}

package org.aion.p2p;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;

/** @author chris */
public class CtrlTest {

    @Test
    public void testFilter() {

        /*
         * active ctrls
         */
        byte c0 = (byte) ThreadLocalRandom.current().nextInt(0, 2);
        assertEquals(c0, Ctrl.filter(c0));

        /*
         * inactive ctrls
         */
        byte c1 = (byte) ThreadLocalRandom.current().nextInt(2, Byte.MAX_VALUE + 1);
        assertEquals(Ctrl.UNKNOWN, Ctrl.filter(c1));
    }
}

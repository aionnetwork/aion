package org.aion.p2p.impl.comm;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;

/** @author chris */
public class ActTest {

    @Test
    public void testFilter() {

        /*
         * active versions
         */
        assertEquals(Act.REQ_HANDSHAKE, Act.filter(Act.REQ_HANDSHAKE));
        assertEquals(Act.RES_HANDSHAKE, Act.filter(Act.RES_HANDSHAKE));
        assertEquals(Act.REQ_ACTIVE_NODES, Act.filter(Act.REQ_ACTIVE_NODES));
        assertEquals(Act.RES_ACTIVE_NODES, Act.filter(Act.RES_ACTIVE_NODES));

        /*
         * inactive versions
         */
        assertEquals(Act.UNKNOWN, Act.filter(Act.DISCONNECT));
        assertEquals(Act.UNKNOWN, Act.filter(Act.PING));
        assertEquals(Act.UNKNOWN, Act.filter(Act.PONG));

        byte a1 = (byte) ThreadLocalRandom.current().nextInt(7, Byte.MAX_VALUE + 1);

        System.out.println(a1);
        System.out.println(Act.UNKNOWN);

        assertEquals(Act.UNKNOWN, Act.filter(a1));
    }
}

package org.aion.p2p.v0;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import org.aion.p2p.Version;
import org.junit.Test;
import java.util.concurrent.ThreadLocalRandom;

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

        byte a1 = (byte)ThreadLocalRandom.current().nextInt(7, Byte.MAX_VALUE + 1);
        assertEquals(Act.UNKNOWN, Version.filter(a1));

    }
}
package org.aion.p2p.impl.zero.msg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.concurrent.ThreadLocalRandom;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.junit.Test;

/** @author chris */
public class ResHandshakeTest {

    @Test
    public void testRoute() {
        ResHandshake mh1 = new ResHandshake(true);
        assertEquals(Ver.V0, mh1.getHeader().getVer());
        assertEquals(Ctrl.NET, mh1.getHeader().getCtrl());
        assertEquals(Act.RES_HANDSHAKE, mh1.getHeader().getAction());
    }

    @Test
    public void testEncodeDecode() {
        ResHandshake mh1 = new ResHandshake(ThreadLocalRandom.current().nextBoolean());
        byte[] mhBytes = mh1.encode();
        ResHandshake mh2 = ResHandshake.decode(mhBytes);
        assertEquals(mh1.getSuccess(), mh2.getSuccess());
    }

    @Test
    public void testDecodeNull() {
        assertNull(ResHandshake1.decode(null));
        assertNull(ResHandshake1.decode(new byte[1]));
    }
}

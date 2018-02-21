package org.aion.p2p.v0.msg;

import static org.junit.Assert.*;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Version;
import org.aion.p2p.v0.Act;
import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

public class ResHandshakeTest {

    @Test
    public void testRoute(){
        ResHandshake mh1 = new ResHandshake(true);
        assertEquals(Version.V0, mh1.getHeader().getVer());
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

}
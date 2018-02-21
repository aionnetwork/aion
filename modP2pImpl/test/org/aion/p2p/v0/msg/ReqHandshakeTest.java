package org.aion.p2p.v0.msg;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.UUID;
import org.aion.p2p.v0.ACT;
import org.junit.Test;

public class ReqHandshakeTest {

    byte[] id = UUID.randomUUID().toString().getBytes();

    int version = 0;

    byte[] ip = Helper.ipStrToBytes("0.0.0.0");

    int port = 30303;

    @Test
    public void testAct() {
        ReqHandshake mh1 = new ReqHandshake(id, 0, ip, port);
        assertEquals(ACT.REQ_HANDSHAKE, mh1.getAct());
    }

    @Test
    public void testEncodeDecode() {

        int minVersion = Integer.MIN_VALUE;
        int zeroVersion = 0;
        int maxVersion = Integer.MAX_VALUE;

        ReqHandshake mh1 = new ReqHandshake(id, minVersion, ip, port);
        byte[] mhBytes = mh1.encode();
        ReqHandshake mh2 = ReqHandshake.decode(mhBytes);
        assertEquals(mh1.getVersion(), mh2.getVersion());
        assertTrue(Arrays.equals(mh1.getNodeId(), mh2.getNodeId()));

        mh1 = new ReqHandshake(id, zeroVersion, ip, port);
        mhBytes = mh1.encode();
        mh2 = ReqHandshake.decode(mhBytes);
        assertEquals(mh1.getVersion(), mh2.getVersion());
        assertTrue(Arrays.equals(mh1.getNodeId(), mh2.getNodeId()));

        mh1 = new ReqHandshake(id, maxVersion, ip, port);
        mhBytes = mh1.encode();
        mh2 = ReqHandshake.decode(mhBytes);
        assertEquals(mh1.getVersion(), mh2.getVersion());
        assertTrue(Arrays.equals(mh1.getNodeId(), mh2.getNodeId()));

        mh1 = new ReqHandshake(new byte[0], zeroVersion, ip, port);
        mhBytes = mh1.encode();
        assertNull(mhBytes);

    }

}

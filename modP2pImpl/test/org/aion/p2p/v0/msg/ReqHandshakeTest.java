package org.aion.p2p.v0.msg;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Version;
import org.aion.p2p.v0.Act;
import org.aion.p2p.v0.Node;
import org.junit.Test;

public class ReqHandshakeTest {

    private byte[] validNodeId = UUID.randomUUID().toString().getBytes();

    private int version = ThreadLocalRandom.current().nextInt();

    private byte[] invalidNodeId = UUID.randomUUID().toString().substring(0, 34).getBytes();

    private int port = ThreadLocalRandom.current().nextInt();

    private String randomIp = ThreadLocalRandom.current().nextInt(0,256) + "." +
            ThreadLocalRandom.current().nextInt(0,256) + "." +
            ThreadLocalRandom.current().nextInt(0,256) + "." +
            ThreadLocalRandom.current().nextInt(0,256);

    @Test
    public void testRoute(){

        ReqHandshake req = new ReqHandshake(validNodeId, version, Node.ipStrToBytes(randomIp), port);
        assertEquals(Version.V0, req.getHeader().getVer());
        assertEquals(Ctrl.NET, req.getHeader().getCtrl());
        assertEquals(Act.REQ_HANDSHAKE, req.getHeader().getAction());
    }

    @Test
    public void testValidEncodeDecode() {

        ReqHandshake req1 = new ReqHandshake(validNodeId, version, Node.ipStrToBytes(randomIp), port);
        byte[] bytes = req1.encode();

        ReqHandshake req2 = ReqHandshake.decode(bytes);
        assertArrayEquals(req1.getNodeId(), req2.getNodeId());
        assertArrayEquals(req1.getIp(), req2.getIp());
        assertEquals(req1.getVersion(), req2.getVersion());
        assertEquals(req1.getPort(), req2.getPort());

    }

    @Test
    public void testInvalidEncodeDecode() {

        ReqHandshake req1 = new ReqHandshake(invalidNodeId, version, Node.ipStrToBytes(randomIp), port);
        byte[] bytes = req1.encode();
        assertNull(bytes);
    }

}

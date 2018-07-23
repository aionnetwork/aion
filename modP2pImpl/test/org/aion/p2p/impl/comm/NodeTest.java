package org.aion.p2p.impl.comm;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.aion.p2p.IPeerMetric;
import org.junit.Test;

/**
 * @author chris
 */
public class NodeTest {

    private String validId = UUID.randomUUID().toString();

    private String invalidId = UUID.randomUUID().toString().substring(0, 34);

    private String validIp =
        ThreadLocalRandom.current().nextInt(0, 256)
            + "."
            + ThreadLocalRandom.current().nextInt(0, 256)
            + "."
            + ThreadLocalRandom.current().nextInt(0, 256)
            + "."
            + ThreadLocalRandom.current().nextInt(0, 256);

    private int validPort = 12345;

    @Test
    public void testParseFromNode() {

        int port = 30303;
        Node n = Node.parseP2p("p2p://" + validId + "@" + validIp + ":" + port);

        assert n != null;
        assertEquals(36, n.getId().length);

        assertEquals(validId, new String(n.getId()));
        assertEquals(8, n.getIp().length);
        assertEquals(validIp, n.getIpStr());
        assertEquals(n.getPort(), port);

        n = Node.parseP2p("p2p://" + invalidId + "@" + validIp + ":" + port);
        assertNull(n);

        String invalidIp = "256.0.0.0";
        n = Node.parseP2p("p2p://" + validId + "@" + invalidIp + ":" + port);
        assertNull(n);
    }

    @Test
    public void testIpByteStrConversion() {
        String ipSource, ipVerify;
        byte[] ipBytes;

        ipSource = "253.253.253.253";
        ipBytes = Node.ipStrToBytes(ipSource);
        assertNotNull(ipBytes);
        assertEquals(ipBytes.length, 8);
        ipVerify = Node.ipBytesToStr(ipBytes);
        assertEquals(ipSource, ipVerify);

        ipSource = "000.000.000.000";
        ipBytes = Node.ipStrToBytes(ipSource);
        assertNotNull(ipBytes);
        assertEquals(ipBytes.length, 8);
        ipVerify = Node.ipBytesToStr(ipBytes);
        assertNotEquals(ipSource, ipVerify);
        assertEquals("0.0.0.0", ipVerify);

        ipSource = "256.256.256.256";
        ipBytes = Node.ipStrToBytes(ipSource);
        assertNotNull(ipBytes);
        assertEquals(ipBytes.length, 8);
        ipVerify = Node.ipBytesToStr(ipBytes);
        assertEquals("256.256.256.256", ipVerify);
    }

    @Test
    public void testIpByteStrConversionHandle() {
        String ipSource;
        byte[] ipBytes;

        ipSource = "0.0.0.a";
        ipBytes = Node.ipStrToBytes(ipSource);
        assertEquals(ipBytes.length, 0);
    }

    @Test
    public void testIpByteStrConversionHandle2() {
        byte[] ipBytes = new byte[9];
        assertTrue(Node.ipBytesToStr(null).isEmpty());
        assertTrue(Node.ipBytesToStr(ipBytes).isEmpty());
    }

    @Test
    public void testValidNode() {
        Node validNode = new Node(validIp, validPort);

        assertEquals(validIp, validNode.getIpStr());
        assertEquals(validPort, validNode.getPort());

        String id = UUID.randomUUID().toString();
        byte[] bId = id.getBytes();
        validNode.setId(bId);

        assertEquals(bId, validNode.getId());
        assertEquals(Arrays.hashCode(bId), validNode.getIdHash());

        String idShort = new String(Arrays.copyOfRange(id.getBytes(), 0, 6));
        assertEquals(idShort, validNode.getIdShort());

        long bestBlockNum = ThreadLocalRandom.current().nextLong();
        String hash = "015d1b31cc93e2ca5807d8da52342e1ef6d295e7a27c3620b24ba367db781321";
        byte[] bestBlockHash = hash.getBytes();
        BigInteger td = BigInteger.valueOf(ThreadLocalRandom.current().nextInt());
        validNode.updateStatus(bestBlockNum, bestBlockHash, td);

        assertEquals(bestBlockNum, validNode.getBestBlockNumber());
        assertEquals(bestBlockHash, validNode.getBestBlockHash());
        assertEquals(td, validNode.getTotalDifficulty());

        boolean bootList = ThreadLocalRandom.current().nextBoolean();
        validNode.setFromBootList(bootList);
        assertEquals(bootList, validNode.getIfFromBootList());

        String revision = "9.9.9.abcdefgh";
        validNode.setBinaryVersion(revision);
        assertEquals(revision, validNode.getBinaryVersion());

        validNode.setPort(12345);
        assertEquals(12345, validNode.getPort());

    }

    @Test
    public void testNodeConnection() {
        Node n = Node.parseP2p("p2p://" + validId + "@" + validIp + ":" + validPort);
        assertNotNull(n);
        String output = n.toString();
        assertNotNull(output);
    }

    @Test
    public void testNodeToString() {
        int port = 30303;
        Node n = Node.parseP2p("p2p://" + validId + "@" + validIp + ":" + port);
        assertNotNull(n);
    }

    @Test
    public void testRefreshTimeStamp() {

        Node validNode = new Node(validIp, validPort);
        long curr_time = System.currentTimeMillis();

        //Sleep for few secs
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        validNode.refreshTimestamp();

        assertTrue((curr_time < validNode.getTimestamp()));

    }

    @Test
    public void testGetPeerMetric() {
        Node n = Node.parseP2p("p2p://" + validId + "@" + validIp + ":" + validPort);
        assertNotNull(n);

        IPeerMetric pm = n.getPeerMetric();
        assertNotNull(pm);
        assertTrue(pm.notBan());
        assertFalse(pm.shouldNotConn());
    }

    @Test
    public void testChannel() throws IOException {
        Node n = Node.parseP2p("p2p://" + validId + "@" + validIp + ":" + validPort);
        assertNotNull(n);

        SocketChannel sc = SocketChannel.open();
        n.setChannel(sc);
        assertNotNull(n.getChannel());
        assertEquals(sc, n.getChannel());
    }

    @Test
    public void testConnection() throws IOException {
        Node n = Node.parseP2p("p2p://" + validId + "@" + validIp + ":" + validPort);
        assertNotNull(n);

        String cn = "inbound";
        n.setConnection(cn);
        assertNotNull(n.getConnection());
        assertEquals(cn, n.getConnection());
    }
}

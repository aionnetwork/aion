package org.aion.p2p.impl.comm;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;

/** @author chris */
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

        assertTrue(n.getId().length == 36);

        String targetIdStr = new String(n.getId());
        assertTrue(validId.equals(new String(n.getId())));
        assertTrue(n.getIp().length == 8);
        assertTrue(validIp.equals(n.getIpStr()));
        assertTrue(n.getPort() == port);

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
        assertTrue(ipSource.equals(ipVerify));

        ipSource = "000.000.000.000";
        ipBytes = Node.ipStrToBytes(ipSource);
        assertNotNull(ipBytes);
        assertEquals(ipBytes.length, 8);
        ipVerify = Node.ipBytesToStr(ipBytes);
        assertFalse(ipSource.equals(ipVerify));
        assertTrue("0.0.0.0".equals(ipVerify));

        ipSource = "256.256.256.256";
        ipBytes = Node.ipStrToBytes(ipSource);
        assertNotNull(ipBytes);
        assertEquals(ipBytes.length, 8);
        ipVerify = Node.ipBytesToStr(ipBytes);
        assertTrue("256.256.256.256".equals(ipVerify));
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
    public void testInValidNode() {
        //TODO
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
}

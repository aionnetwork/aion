package org.aion.p2p.impl;

import static org.junit.Assert.*;

import org.aion.p2p.impl.comm.Node;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author chris
 *
 */
public class NodeTest {

    private String validId = UUID.randomUUID().toString();

    private String invalidId = UUID.randomUUID().toString().substring(0, 34);

    private String validIp =
            ThreadLocalRandom.current().nextInt(0,256) + "." +
                    ThreadLocalRandom.current().nextInt(0,256) + "." +
                    ThreadLocalRandom.current().nextInt(0,256) + "." +
                    ThreadLocalRandom.current().nextInt(0,256);

    @Test
    public void testParseFromEnode() {

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
        System.out.println(ipVerify);
        assertTrue("256.256.256.256".equals(ipVerify));
    }

}
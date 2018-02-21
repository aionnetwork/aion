package org.aion.p2p.v0;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class P2pMgrTest {

    private String nodeId1 = UUID.randomUUID().toString();
    private String nodeId2 = UUID.randomUUID().toString();
    private String ip1 = "127.0.0.1";
    private String ip2 = "192.168.0.11";
    private int port1 = 30303;
    private int port2 = 30304;

    @Test
    public void testIgnoreSameNodeIdAsSelf() {

        String[] nodes = new String[]{
                "p2p://" + nodeId1 + "@" + ip2+ ":" + port2
        };

        P2pMgr p2p = new P2pMgr(nodeId1, ip1, port1, nodes, false, false, false);
        assertEquals(p2p.getTempNodesCount(), 0);

    }

    @Test
    public void testIgnoreSameIpAndPortAsSelf(){

        String[] nodes = new String[]{
                "p2p://" + nodeId2 + "@" + ip1+ ":" + port1
        };

        P2pMgr p2p = new P2pMgr(nodeId1, ip1, port1, nodes, false, false, false);
        assertEquals(p2p.getTempNodesCount(), 0);

    }

    @Test
    public void testTempNodes(){

        String[] nodes = new String[]{
                "p2p://" + nodeId2 + "@" + ip1+ ":" + port2,
                "p2p://" + nodeId2 + "@" + ip2+ ":" + port1,
                "p2p://" + nodeId2 + "@" + ip2+ ":" + port2,
        };

        P2pMgr p2p = new P2pMgr(nodeId1, ip1, port1, nodes, false, false, false);
        assertEquals(p2p.getTempNodesCount(), 3);

    }



}

package org.aion.p2p.a0.msg;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.aion.p2p.a0.Helper;
import org.aion.p2p.a0.Node;
import org.aion.p2p.a0.msg.ACT;
import org.aion.p2p.a0.msg.ResActiveNodes;
import org.junit.Test;

public class ResActiveNodesTest {

    byte[] id = UUID.randomUUID().toString().getBytes();
    String ipStr = "192.168.2.2";
    int port = 30303;
    
    @Test 
    public void testAct() {
        List<Node> nodes = new ArrayList<Node>();
        ResActiveNodes mh1 = new ResActiveNodes(nodes);
        assertEquals(mh1.getAct(), ACT.RES_ACTIVE_NODES.ordinal());
    }
    
    @Test
    public void testEncodeDecode() {
        List<Node> nodes = new ArrayList<Node>();
        ResActiveNodes ran = new ResActiveNodes(nodes);
        byte[] ranBytes = ran.encode();
        assertEquals(1, ranBytes.length);
        assertEquals(0, ranBytes[0]);
        
        byte[] ip = Helper.ipStrToBytes(ipStr);
        Node n0 = new Node(false, id, ip, port);
        nodes.add(n0);
        ran = new ResActiveNodes(nodes);
        ranBytes = ran.encode();
        ran = ResActiveNodes.decode(ranBytes);
        assertTrue(ranBytes.length > 1);
        assertEquals(1, ran.getNodes().size());
        assertEquals(ipStr, Helper.ipBytesToStr(ran.getNodes().get(0).getIp()));
        assertEquals(port, ran.getNodes().get(0).getPort());
        
    }
}


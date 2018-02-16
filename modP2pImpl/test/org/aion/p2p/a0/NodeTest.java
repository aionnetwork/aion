package org.aion.p2p.a0;

import static org.junit.Assert.*;

import org.aion.p2p.a0.Helper;
import org.aion.p2p.a0.Node;
import org.junit.Test;

/**
 * 
 * @author chris
 * TODO: not completed 
 * 
 */

public class NodeTest {
    
    String validId   = "b9b28261-7d9c-47ed-9851-07703496a89c";
    String invalidId = "b9b28261-7d9c-47ed-9851-07703496a89";
    String ip1 = "255.255.255.255";
    String ip2 = "0.0.0.0";
    String port = "30303";
    
    @Test
    public void testParseFromEnode() {
        
        Node n = Node.parseEnode(false,"p2p://" + validId + "@" + ip1 + ":" + port);
        assertEquals(n.getId().length, 36);
        assertEquals(n.getIp().length, 8);
        assertTrue(n.getPort() <= Short.MAX_VALUE);
        System.out.println("id " + new String(n.getId()));
        assertTrue(validId.equals(new String(n.getId())));
        System.out.println("ip " + Helper.ipBytesToStr(n.getIp()));
        assertTrue(ip1.equals(Helper.ipBytesToStr(n.getIp())));
        System.out.println("port " + n.getPort());
        assertEquals(Short.parseShort(port), n.getPort());
        
        
        
        n = Node.parseEnode(false,"p2p://" + validId + "@" + ip2 + ":" + port);
        assertEquals(n.getId().length, 36);
        assertEquals(n.getIp().length, 8);
        assertTrue(n.getPort() <= Short.MAX_VALUE);
        System.out.println("id " + new String(n.getId()));
        assertTrue(validId.equals(new String(n.getId())));
        System.out.println("ip " + Helper.ipBytesToStr(n.getIp()));
        assertTrue(ip2.equals(Helper.ipBytesToStr(n.getIp())));
        System.out.println("port " + n.getPort());
        assertEquals(Short.parseShort(port), n.getPort());
        
        
        n = Node.parseEnode(false,"p2p://" + invalidId + "@" + ip1 + ":" + port);
        assertNull(n);

    }

}

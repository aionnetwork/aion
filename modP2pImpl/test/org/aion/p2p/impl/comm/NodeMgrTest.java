package org.aion.p2p.impl.comm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.UUID;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.impl1.P2pMgr;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * @author sridevi
 */
public class NodeMgrTest {

    private final int MAX_TEMP_NODES = 128;
    private final int MAX_ACTIVE_NODES = 128;

    private NodeMgr nMgr = new NodeMgr(MAX_ACTIVE_NODES, MAX_TEMP_NODES);

    private String nodeId1 = UUID.randomUUID().toString();
    private String nodeId2 = UUID.randomUUID().toString();
    private String ip1 = "127.0.0.1";
    private String ip2 = "192.168.0.11";
    private int port1 = 30304;
    private int port2 = 30305;
    ServerSocketChannel tcpServer;
    SocketChannel channel;

    private String[] nodes = new String[]{
        "p2p://" + nodeId1 + "@" + ip1 + ":" + port2,
        "p2p://" + nodeId2 + "@" + ip2 + ":" + port1,
    };

    private final P2pMgr p2p = new P2pMgr(0,
        "",
        nodeId1,
        ip1,
        port1,
        nodes,
        false,
        128,
        128,
        false,
        false,
        false,
        50);

    @Before
    public void mock_connection() throws IOException {

        tcpServer = ServerSocketChannel.open();
        Selector selector = Selector.open();
        tcpServer = ServerSocketChannel.open();
        tcpServer.configureBlocking(false);
        tcpServer.socket().setReuseAddress(true);
        tcpServer.socket().bind(new InetSocketAddress(ip1, port1));
        tcpServer.register(selector, SelectionKey.OP_ACCEPT);


        channel = SocketChannel.open();
        channel.socket().connect(new InetSocketAddress(ip1, port1), 10000);
        channel.configureBlocking(false);
        channel.socket().setSoTimeout(10000);

        // set buffer to 256k.
        channel.socket().setReceiveBufferSize(P2pConstant.RECV_BUFFER_SIZE);
        channel.socket().setSendBufferSize(P2pConstant.SEND_BUFFER_SIZE);

    }

    @After
    public void close() throws IOException {

        tcpServer.close();
        channel.socket().close();
    }

    @Test
    public void test_tempNode() {

        for (String nodeL : nodes) {
            Node node = Node.parseP2p(nodeL);
            nMgr.addTempNode(node);
            nMgr.seedIpAdd(node.getIpStr());
        }
        assertEquals(2, nMgr.tempNodesSize());

    }

    @Test
    public void test_tempNodeMax_128() {

        String[] nodes_max = new String[130];
        int ip = 0;

        int port = 10000;
        for (int i = 0; i < 130; i++) {
            nodes_max[i] =
                "p2p://" + UUID.randomUUID().toString() + "@255.255.255." + ip++ + ":" + port++;
        }

        for (String nodeL : nodes_max) {
            Node node = Node.parseP2p(nodeL);
            nMgr.addTempNode(node);
            nMgr.seedIpAdd(node.getIpStr());
        }
        assertEquals(128, nMgr.tempNodesSize());

    }

    @Test
    public void test_tempNodesTake() {

        String[] nodes = new String[]{
            "p2p://" + nodeId1 + "@" + ip1 + ":" + port2,
            "p2p://" + nodeId2 + "@" + ip1 + ":" + port1,
        };

        NodeMgr mgr = new NodeMgr(MAX_ACTIVE_NODES, MAX_TEMP_NODES);

        for (String nodeL : nodes) {
            Node node = Node.parseP2p(nodeL);
            mgr.addTempNode(node);
            mgr.seedIpAdd(node.getIpStr());
        }
        assertEquals(2, mgr.tempNodesSize());

        INode node = null;
        while (mgr.tempNodesSize() != 0) {
            try {
                node = mgr.tempNodesTake();
                assertEquals(ip1, node.getIpStr());
                assertTrue(node.getIfFromBootList());
                assertTrue(mgr.isSeedIp(ip1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        assertEquals(0, mgr.tempNodesSize());
    }


    @Test
    public void test_tempNodeMax_Any() {

        NodeMgr mgr = new NodeMgr(512, 512);
        String[] nodes_max = new String[512];

        int ip = 0;
        int port = 10000;
        for (int i = 0; i < 256; i++) {
            nodes_max[i] =
                "p2p://" + UUID.randomUUID().toString() + "@255.255.255." + ip++ + ":" + port++;
        }

        ip = 0;
        port = 10000;
        for (int i = 256; i < 512; i++) {
            nodes_max[i] =
                "p2p://" + UUID.randomUUID().toString() + "@255.255.254." + ip++ + ":" + port++;

        }

        for (String nodeL : nodes_max) {
            Node node = Node.parseP2p(nodeL);
            if (node == null) {
                System.out.println("node is null");
            }
            mgr.addTempNode(node);
            mgr.seedIpAdd(node.getIpStr());

        }
        assertEquals(512, mgr.tempNodesSize());

    }

    @Test
    public void test_addInOutBoundNode() {

        INode node = nMgr.allocNode(ip1, 0);
        node.setChannel(channel);
        try {
            node.setId(nodeId1.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        nMgr.addInboundNode(node);
        INode iNode = nMgr.getInboundNode(channel.hashCode());
        assertEquals(ip1, iNode.getIpStr());

        nMgr.addOutboundNode(node);
        INode oNode = nMgr.getOutboundNode(node.getIdHash());
        assertEquals(ip1, oNode.getIpStr());
    }

    @Test
    public void test_moveInboundToActive() {

        INode node = nMgr.allocNode(ip2, 0);
        node.setChannel(channel);

        try {
            node.setId(nodeId2.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        nMgr.addInboundNode(node);
        assertEquals(0, nMgr.activeNodesSize());

        nMgr.moveInboundToActive(channel.hashCode(), p2p);
        assertEquals(1, nMgr.activeNodesSize());
    }

    @Test
    public void test_moveOutboundToActive() {
        INode node = nMgr.allocNode(ip2, 0);

        node.setChannel(channel);
        try {
            node.setId(nodeId2.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        nMgr.addOutboundNode(node);
        assertEquals(0, nMgr.activeNodesSize());

        nMgr.moveOutboundToActive(node.getIdHash(), node.getIdShort(), p2p);
        assertEquals(1, nMgr.activeNodesSize());

    }

    @Test
    public void test_getActiveNodesList() {

        NodeMgr nMgr = new NodeMgr(MAX_ACTIVE_NODES, MAX_TEMP_NODES);
        INode node = nMgr.allocNode(ip2, 0);

        node.setChannel(channel);
        try {
            node.setId(nodeId2.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        nMgr.addInboundNode(node);
        assertEquals(0, nMgr.activeNodesSize());

        nMgr.moveInboundToActive(channel.hashCode(), p2p);

        assertEquals(1, nMgr.activeNodesSize());

        List<INode> active = nMgr.getActiveNodesList();

        for (INode activeN : active) {
            assertEquals(ip2, activeN.getIpStr());
        }

    }

    @Test
    public void test_dropActive() {
        INode node = nMgr.allocNode(ip2, 0);

        node.setChannel(channel);
        try {
            node.setId(nodeId2.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        nMgr.addInboundNode(node);
        assertEquals(0, nMgr.activeNodesSize());

        nMgr.moveInboundToActive(channel.hashCode(), p2p);
        assertEquals(1, nMgr.activeNodesSize());

        nMgr.dropActive(node.getIdHash(), p2p, "close");
        assertEquals(0, nMgr.activeNodesSize());

    }

    @Test
    public void test_ban() {
        INode node = nMgr.allocNode(ip2, 0);

        node.setChannel(channel);
        try {
            node.setId(nodeId2.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        nMgr.addInboundNode(node);
        assertEquals(0, nMgr.activeNodesSize());

        nMgr.moveInboundToActive(channel.hashCode(), p2p);
        assertEquals(1, nMgr.activeNodesSize());

        assertTrue(node.getPeerMetric().notBan());
        nMgr.ban(node.getIdHash());
        assertFalse(node.getPeerMetric().notBan());

    }

    @Test
    public void test_timeoutInbound() {

        INode node = nMgr.allocNode(ip2, 0);

        node.setChannel(channel);
        try {
            node.setId(nodeId2.getBytes("UTF-8"));
            node.refreshTimestamp();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        nMgr.addInboundNode(node);

        //Sleep for MAX_INBOUND_TIMEOUT
        try {
            Thread.sleep(11000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        nMgr.timeoutInbound((IP2pMgr) p2p);
        assertNull(nMgr.getInboundNode(channel.hashCode()));

    }

    @Test
    public void test_timeoutActive() {
        //TODO
    }

}

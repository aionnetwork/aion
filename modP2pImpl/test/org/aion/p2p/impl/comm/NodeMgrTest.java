/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

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
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.p2p.INode;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.impl1.P2pMgr;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;


/**
 * @author sridevi
 */
public class NodeMgrTest {

    private final int MAX_TEMP_NODES = 128;
    private final int MAX_ACTIVE_NODES = 128;

    private String nodeId1 = UUID.randomUUID().toString();
    private String nodeId2 = UUID.randomUUID().toString();
    private String ip1 = "127.0.0.1";
    private String ip2 = "192.168.0.11";
    private int port1 = 30304;
    private int port2 = 30305;
    private ServerSocketChannel tcpServer;
    private SocketChannel channel;

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.P2P.name());

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
        50);

    private NodeMgr nMgr = new NodeMgr(p2p, MAX_ACTIVE_NODES, MAX_TEMP_NODES, LOGGER);


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
            assert node != null;
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

        NodeMgr mgr = new NodeMgr(p2p, MAX_ACTIVE_NODES, MAX_TEMP_NODES, LOGGER);

        for (String nodeL : nodes) {
            Node node = Node.parseP2p(nodeL);
            mgr.addTempNode(node);
            mgr.seedIpAdd(node.getIpStr());
        }
        assertEquals(2, mgr.tempNodesSize());

        INode node;
        while (mgr.tempNodesSize() != 0) {
            node = mgr.tempNodesTake();
            assertEquals(ip1, node.getIpStr());
            assertTrue(node.getIfFromBootList());
            assertTrue(mgr.isSeedIp(ip1));
        }

        assertEquals(0, mgr.tempNodesSize());
    }


    @Test
    public void test_tempNodeMax_Any() {

        NodeMgr mgr = new NodeMgr(p2p, 512, 512, LOGGER);
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
            assert node != null;
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

        nMgr.movePeerToActive(channel.hashCode(), "inbound");
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

        nMgr.movePeerToActive(node.getIdHash(), "outbound");
        assertEquals(1, nMgr.activeNodesSize());

    }

    @Test
    public void test_getActiveNodesList() {

        NodeMgr nMgr = new NodeMgr(p2p, MAX_ACTIVE_NODES, MAX_TEMP_NODES, LOGGER);
        INode node = nMgr.allocNode(ip2, 0);

        node.setChannel(channel);
        try {
            node.setId(nodeId2.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        nMgr.addInboundNode(node);
        assertEquals(0, nMgr.activeNodesSize());

        nMgr.movePeerToActive(channel.hashCode(), "inbound");

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

        nMgr.movePeerToActive(channel.hashCode(), "inbound");
        assertEquals(1, nMgr.activeNodesSize());

        nMgr.dropActive(node.getIdHash(), "close");
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

        nMgr.movePeerToActive(channel.hashCode(), "inbound");

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

        nMgr.timeoutInbound();
        assertNull(nMgr.getInboundNode(channel.hashCode()));

    }

    @Test
    public void test_timeoutActive() {
        //TODO
    }

}

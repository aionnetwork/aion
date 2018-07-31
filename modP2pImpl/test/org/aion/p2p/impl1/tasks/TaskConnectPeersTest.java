/*
 * Copyright (c) 2017-2018 Aion foundation.
 *      This file is part of the aion network project.
 *
 *      The aion network project is free software: you can redistribute it
 *      and/or modify it under the terms of the GNU General Public License
 *      as published by the Free Software Foundation, either version 3 of
 *      the License, or any later version.
 *
 *      The aion network project is distributed in the hope that it will
 *      be useful, but WITHOUT ANY WARRANTY; without even the implied
 *      warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *      See the GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the aion network project source files.
 *      If not, see <https://www.gnu.org/licenses/>.
 *
 *  Contributors:
 *      Aion foundation.
 */

package org.aion.p2p.impl1.tasks;

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevels;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.impl.zero.msg.ReqHandshake1;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskConnectPeersTest {

    @Mock
    INodeMgr nodeMgr;

    @Mock
    IP2pMgr p2pMgr;

    @Mock
    BlockingQueue<MsgOut> sendMsgQue;

    @Mock
    ReqHandshake1 rhs;

    @Mock
    INode node;

    ServerSocketChannel ssc;

    Thread listen;

    Selector selector;

    public class ThreadTCPServer extends Thread {

        SocketChannel sc;
        Selector selector;

        ThreadTCPServer(Selector _selector) {
            selector = _selector;
        }

        @Override
        public void run() {
            while (!this.isInterrupted()) {
                try {
                    if (this.selector.selectNow() == 0) {
                        Thread.sleep(0, 1);
                        continue;
                    }
                } catch (IOException | ClosedSelectorException e) {
                    p2pLOG.debug("inbound-select-exception", e);
                    continue;
                } catch (InterruptedException e) {
                    p2pLOG.error("inbound thread sleep exception ", e);
                    return;
                }

                Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key;
                    try {
                        key = keys.next();
                        if (key.isAcceptable()) {
                            ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                            sc = ssc.accept();
                            if (sc != null) {
                                sc.configureBlocking(false);
                                sc.register(selector, SelectionKey.OP_READ);
                                System.out.println("socket connected!");
                            }
                        }
                    } catch ( IOException e) {
                        e.printStackTrace();
                    }
                }
                keys.remove();
            }
        }
    }

    @Before
    public void Setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        Map<String, String> logMap = new HashMap<>();
        logMap.put(LogEnum.P2P.name(), LogLevels.TRACE.name());
        AionLoggerFactory.init(logMap);

        System.setProperty("java.net.preferIPv4Stack", "true");
        ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        ssc.socket().setReuseAddress(true);
        ssc.socket().bind(new InetSocketAddress(60606));
        // Create the selector
        selector = Selector.open();
        ssc.register(selector, SelectionKey.OP_ACCEPT);

        listen = new ThreadTCPServer(selector);
        listen.start();
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        listen.interrupt();
        Thread.sleep(100);
        ssc.close();
    }


    @Test
    public void testRun() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskConnectPeers tcp = new TaskConnectPeers(p2pMgr, atb, nodeMgr, 128, selector, sendMsgQue,
            rhs);

        Thread t = new Thread(tcp);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(10);
        assertTrue(tcp.isRun());
        atb.set(false);
        Thread.sleep(2000);
        assertFalse(tcp.isRun());
    }

    @Test
    public void testRun1() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskConnectPeers tcp = new TaskConnectPeers(p2pMgr, atb, nodeMgr, 128, selector, sendMsgQue,
            rhs);

        when(nodeMgr.activeNodesSize()).thenReturn(128);
        when(nodeMgr.tempNodesTake()).thenReturn(null);

        when(node.getIdHash()).thenReturn(1);
        when(node.getPort()).thenReturn(60606);
        when(node.getIpStr()).thenReturn("127.0.0.1");
        when(nodeMgr.notAtOutboundList(node.getIdHash())).thenReturn(true);
        when(nodeMgr.notActiveNode(node.getIdHash())).thenReturn(true);

        Thread t = new Thread(tcp);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(10);
        assertTrue(tcp.isRun());

        // should see the loop continue every sec
        Thread.sleep(2000);

        when(nodeMgr.activeNodesSize()).thenReturn(127);
        // should see the loop continue every sec due to null node been taken
        Thread.sleep(2000);

        when(node.getIdShort()).thenReturn("1");
        when(nodeMgr.tempNodesTake()).thenReturn(node);
        when(node.getIfFromBootList()).thenReturn(true);

        Thread.sleep(10000);

        atb.set(false);
        Thread.sleep(3000);
        assertFalse(tcp.isRun());
    }
}

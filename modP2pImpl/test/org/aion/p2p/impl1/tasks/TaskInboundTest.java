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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevels;
import org.aion.p2p.Handler;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;
import org.aion.p2p.impl.zero.msg.ResHandshake1;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


public class TaskInboundTest {

    @Mock
    private
    INodeMgr nodeMgr;

    @Mock
    private IP2pMgr p2pMgr;

    @Mock
    private BlockingQueue<MsgOut> msgOutQue;

    @Mock
    private BlockingQueue<MsgIn> msgInQue;

    @Mock
    private ResHandshake1 rhs1;

    @Mock
    private INode node;

    @Mock
    private Msg msg;

    @Mock
    private ServerSocketChannel ssc;

    @Mock
    private SelectionKey sk;


    @Mock
    private SelectionKey sk2;


    @Mock
    private SelectionKey sk3;

    @Mock
    private Map<Integer, List<Handler>> hldrMap;

    @Mock
    private MockSelector selector;

    public class MockSelector extends Selector {

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public SelectorProvider provider() {
            return null;
        }

        @Override
        public Set<SelectionKey> keys() {
            return null;
        }

        @Override
        public Set<SelectionKey> selectedKeys() {
            return null;
        }

        @Override
        public int selectNow() {
            return 0;
        }

        @Override
        public int select(long timeout) {
            return 0;
        }

        @Override
        public int select() {
            return 0;
        }

        @Override
        public Selector wakeup() {
            return null;
        }

        @Override
        public void close() {

        }
    }

    @Before
    public void Setup() {

        MockitoAnnotations.initMocks(this);
        Map<String, String> logMap = new HashMap<>();
        logMap.put(LogEnum.P2P.name(), LogLevels.TRACE.name());
        AionLoggerFactory.init(logMap);

    }

    @After
    public void tearDown() {
    }


    @Test
    public void testRun() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue, rhs1, msgInQue);

        when(selector.selectNow()).thenReturn(0);

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(100);
        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testRunException() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue, rhs1, msgInQue);

        doThrow(ClosedSelectorException.class).when(selector).selectNow();

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(100);

        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testRunClosedSelectorException() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);

        when(selector.selectNow()).thenReturn(1);
        when(selector.selectedKeys()).thenThrow(ClosedSelectorException.class);

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(100);
        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testRun2() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);

        when(sk.isValid()).thenReturn(false);

        when(sk2.isValid()).thenReturn(true);
        when(sk2.isAcceptable()).thenReturn(true);
        when(sk2.isReadable()).thenReturn(true);
        when(sk2.attachment()).thenReturn(null);

        when(sk3.isValid()).thenReturn(true);
        when(sk3.isAcceptable()).thenReturn(true);
        when(sk3.isReadable()).thenReturn(true);
        ChannelBuffer cb = new ChannelBuffer();
        when(sk3.attachment()).thenReturn(cb);

        when(selector.selectNow()).thenReturn(1);

        Set<SelectionKey> ss = new LinkedHashSet<>();
        ss.add(sk);
        ss.add(sk2);
        ss.add(sk3);
        when(selector.selectedKeys()).thenReturn(ss);

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(200);
        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

//
//    @Test
//    public void testRunMsgOutTimeout() throws InterruptedException {
//        AtomicBoolean atb = new AtomicBoolean(true);
//        TaskSend ts = new TaskSend(p2pMgr, r.nextInt(lane), sendMsgQue, atb, nodeMgr, selector);
//
//        MsgOut mo = new MsgOut(r.nextInt(), "1", msg, Dest.OUTBOUND);
//        when(sendMsgQue.take()).thenReturn(mo);
//        Thread.sleep(5000);
//
//        Thread t = new Thread(ts);
//        t.start();
//        assertTrue(t.isAlive());
//        Thread.sleep(10);
//        atb.set(false);
//        Thread.sleep(100);
//        assertEquals("TERMINATED", t.getState().toString());
//    }
//
//    @Test
//    public void testRunLane() throws InterruptedException {
//        AtomicBoolean atb = new AtomicBoolean(true);
//        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
//
//        MsgOut mo = new MsgOut(1, "1", msg, Dest.OUTBOUND);
//        when(sendMsgQue.take()).thenReturn(mo);
//
//        Thread t = new Thread(ts);
//        t.start();
//        assertTrue(t.isAlive());
//        Thread.sleep(10);
//        atb.set(false);
//        Thread.sleep(100);
//        assertEquals("TERMINATED", t.getState().toString());
//    }
//
//    @Test
//    public void testRun2() throws InterruptedException, IOException {
//        AtomicBoolean atb = new AtomicBoolean(true);
//        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
//
//        MsgOut mo = new MsgOut(0, "1", msg, Dest.OUTBOUND);
//        when(sendMsgQue.take()).thenReturn(mo);
//        when(nodeMgr.getOutboundNode(0)).thenReturn(node);
//
//        ChannelBuffer cb = new ChannelBuffer();
//        SocketChannel ch = SocketChannel.open();
//        ch.configureBlocking(false);
//        ch.register(selector, SelectionKey.OP_WRITE, cb);
//
//        when(node.getChannel()).thenReturn(ch);
//
//        Thread t = new Thread(ts);
//        t.start();
//
//        assertTrue(t.isAlive());
//        Thread.sleep(10);
//        atb.set(false);
//        Thread.sleep(100);
//        assertEquals("TERMINATED", t.getState().toString());
//    }
//
//    @Test
//    public void testRun3() throws InterruptedException, IOException {
//        AtomicBoolean atb = new AtomicBoolean(true);
//        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
//
//        MsgOut mo = new MsgOut(0, "1", msg, Dest.ACTIVE);
//        when(sendMsgQue.take()).thenReturn(mo);
//        when(nodeMgr.getActiveNode(0)).thenReturn(node);
//
//        ChannelBuffer cb = new ChannelBuffer();
//        SocketChannel ch = SocketChannel.open();
//        ch.configureBlocking(false);
//        ch.register(selector, SelectionKey.OP_WRITE, cb);
//
//        when(node.getChannel()).thenReturn(ch);
//
//        Thread t = new Thread(ts);
//        t.start();
//
//        assertTrue(t.isAlive());
//        Thread.sleep(10);
//        atb.set(false);
//        Thread.sleep(100);
//        assertEquals("TERMINATED", t.getState().toString());
//    }
//
//    @Test
//    public void testRun4() throws InterruptedException, IOException {
//        AtomicBoolean atb = new AtomicBoolean(true);
//        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
//
//        MsgOut mo = new MsgOut(0, "1", msg, Dest.INBOUND);
//        when(sendMsgQue.take()).thenReturn(mo);
//        when(nodeMgr.getInboundNode(0)).thenReturn(node);
//
//        ChannelBuffer cb = new ChannelBuffer();
//        SocketChannel ch = SocketChannel.open();
//        ch.configureBlocking(false);
//        ch.register(selector, SelectionKey.OP_WRITE, cb);
//
//        when(node.getChannel()).thenReturn(ch);
//
//        Thread t = new Thread(ts);
//        t.start();
//
//        assertTrue(t.isAlive());
//        Thread.sleep(10);
//        atb.set(false);
//        Thread.sleep(100);
//        assertEquals("TERMINATED", t.getState().toString());
//    }
//
//    @Test
//    public void testRunNullNode() throws InterruptedException {
//        AtomicBoolean atb = new AtomicBoolean(true);
//        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
//
//        MsgOut mo = new MsgOut(0, "1", msg, Dest.INBOUND);
//        when(sendMsgQue.take()).thenReturn(mo);
//        when(nodeMgr.getInboundNode(0)).thenReturn(null);
//
//        Thread t = new Thread(ts);
//        t.start();
//
//        assertTrue(t.isAlive());
//        Thread.sleep(10);
//        atb.set(false);
//        Thread.sleep(100);
//        assertEquals("TERMINATED", t.getState().toString());
//    }

}

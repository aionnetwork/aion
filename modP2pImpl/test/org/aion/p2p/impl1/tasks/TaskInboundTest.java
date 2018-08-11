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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.p2p.Handler;
import org.aion.p2p.Header;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.impl.zero.msg.ResHandshake1;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


public class TaskInboundTest {

    @Mock
    private INodeMgr nodeMgr;

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
    private ChannelBuffer cb;

    @Mock
    private Header hdr;

    @Mock
    private ServerSocketChannel ssc;

    @Mock
    private SocketChannel sc;

    @Mock
    private Socket s;

    @Mock
    private InetAddress ia;

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

    private Random r = new Random();

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
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Map<String, String> logMap = new HashMap<>();
        logMap.put(LogEnum.P2P.name(), LogLevel.TRACE.name());
        AionLoggerFactory.init(logMap);
    }

    @Test
    public void testRun() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue, rhs1, msgInQue);
        assertNotNull(ti);

        when(selector.selectNow()).thenReturn(0);

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(50);
        atb.set(false);
        Thread.sleep(50);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testRunException() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue, rhs1, msgInQue);
        assertNotNull(ti);

        doThrow(ClosedSelectorException.class).when(selector).selectNow();

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(50);

        atb.set(false);
        Thread.sleep(50);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testRunClosedSelectorException() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);
        assertNotNull(ti);

        when(selector.selectNow()).thenReturn(1);
        when(selector.selectedKeys()).thenThrow(ClosedSelectorException.class);

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(50);
        atb.set(false);
        Thread.sleep(50);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testRun2() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);
        assertNotNull(ti);

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
        Thread.sleep(100);
        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testAccept() throws InterruptedException, IOException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);
        assertNotNull(ti);

        when(sk2.isValid()).thenReturn(true);
        when(sk2.isAcceptable()).thenReturn(true);
        when(nodeMgr.activeNodesSize()).thenReturn(1);
        when(p2pMgr.getMaxActiveNodes()).thenReturn(2);
        when(ssc.accept()).thenReturn(sc);
        when(sc.socket()).thenReturn(s);
        when(s.getInetAddress()).thenReturn(ia);
        when(ia.getHostAddress()).thenReturn("0.0.0.0");
        when(p2pMgr.isSyncSeedsOnly()).thenReturn(true);
        when(nodeMgr.isSeedIp(anyString())).thenReturn(true);

        when(selector.selectNow()).thenReturn(1);

        Set<SelectionKey> ss = new LinkedHashSet<>();
        ss.add(sk2);
        when(selector.selectedKeys()).thenReturn(ss);

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(100);
        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testAccept2() throws InterruptedException, IOException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);
        assertNotNull(ti);

        when(sk2.isValid()).thenReturn(true);
        when(sk2.isAcceptable()).thenReturn(true);
        when(nodeMgr.activeNodesSize()).thenReturn(1);
        when(p2pMgr.getMaxActiveNodes()).thenReturn(2);
        when(ssc.accept()).thenReturn(sc);
        when(sc.socket()).thenReturn(s);
        when(s.getInetAddress()).thenReturn(ia);
        when(ia.getHostAddress()).thenReturn("0.0.0.0");
        when(p2pMgr.isSyncSeedsOnly()).thenReturn(true);
        when(nodeMgr.isSeedIp(anyString())).thenReturn(false);
        when(p2pMgr.getOutGoingIP()).thenReturn("0.0.0.0");

        when(selector.selectNow()).thenReturn(1);

        Set<SelectionKey> ss = new LinkedHashSet<>();
        ss.add(sk2);
        when(selector.selectedKeys()).thenReturn(ss);

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(100);
        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testAccept3() throws InterruptedException, IOException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);
        assertNotNull(ti);

        when(sk.isValid()).thenReturn(true);
        when(sk.isAcceptable()).thenReturn(true);
        when(nodeMgr.activeNodesSize()).thenReturn(1);
        when(p2pMgr.getMaxActiveNodes()).thenReturn(2);
        when(ssc.accept()).thenReturn(sc);
        when(sc.socket()).thenReturn(s);
        when(s.getInetAddress()).thenReturn(ia);

        when(ia.getHostAddress()).thenReturn("0.0.0.0");
        when(p2pMgr.isSyncSeedsOnly()).thenReturn(true);
        when(nodeMgr.isSeedIp(anyString())).thenReturn(false);
        when(p2pMgr.getOutGoingIP()).thenReturn("1.1.1.1");
        when(s.getPort()).thenReturn(0);
        when(nodeMgr.allocNode(anyString(), anyInt())).thenReturn(node);

        when(sc.register(any(), anyInt())).thenReturn(sk);


        when(selector.selectNow()).thenReturn(1);

        Set<SelectionKey> ss = new LinkedHashSet<>();
        ss.add(sk);
        when(selector.selectedKeys()).thenReturn(ss);

        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(100);
        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testReadBuffer() throws InterruptedException, IOException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);
        assertNotNull(ti);

        // settings for readBuffer
        when(sk.channel()).thenReturn(sc);
        when(sc.read(any(ByteBuffer.class))).thenReturn(0);

        // settings for run
        when(sk.isValid()).thenReturn(true);
        when(sk.isReadable()).thenReturn(true);
        when(sk.attachment()).thenReturn(cb);
        when(selector.selectNow()).thenReturn(1);

        Set<SelectionKey> ss = new LinkedHashSet<>();
        ss.add(sk);
        when(selector.selectedKeys()).thenReturn(ss);

        // execute the task
        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(100);
        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testReadBuffer2() throws InterruptedException, IOException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);
        assertNotNull(ti);

        // settings for readBuffer
        when(sk.channel()).thenReturn(sc);
        when(sc.read(any(ByteBuffer.class))).thenReturn(1).thenReturn(0);

        //settings for calBuffer
        //when(cb.buffRemain).thenReturn(1);

        // settings for run
        when(sk.isValid()).thenReturn(true);
        when(sk.isReadable()).thenReturn(true);
        when(sk.attachment()).thenReturn(cb);
        when(selector.selectNow()).thenReturn(1);

        Set<SelectionKey> ss = new LinkedHashSet<>();
        ss.add(sk);
        when(selector.selectedKeys()).thenReturn(ss);

        // execute the task
        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(100);


        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testReadBuffer3() throws InterruptedException, IOException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskInbound ti = new TaskInbound(p2pMgr, selector, atb, nodeMgr, ssc, hldrMap, msgOutQue,
            rhs1, msgInQue);
        assertNotNull(ti);

        // settings for readBuffer
        when(sk.channel()).thenReturn(sc);
        int read = r.nextInt(10000);
        int remain = r.nextInt(10000);
        when(sc.read(any(ByteBuffer.class))).thenReturn(read).thenReturn(0);

        //settings for calBuffer
        when(cb.getBuffRemain()).thenReturn(remain);
        when(cb.getRemainBuffer()).thenReturn(new byte[remain]);

        //settings for readMsg
        when(cb.isHeaderNotCompleted()).thenReturn(true);
        when(cb.isBodyNotCompleted()).thenReturn(true);

        //settings for readBody
        when(cb.getHeader()).thenReturn(hdr);
        //when(hdr.getLen()).thenReturn(Header.LEN);

        // settings for run
        when(sk.isValid()).thenReturn(true);
        when(sk.isReadable()).thenReturn(true);
        when(sk.attachment()).thenReturn(cb);
        when(selector.selectNow()).thenReturn(1);

        Set<SelectionKey> ss = new LinkedHashSet<>();
        ss.add(sk);
        when(selector.selectedKeys()).thenReturn(ss);

        // execute the task
        Thread t = new Thread(ti);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(100);

        atb.set(false);
        Thread.sleep(100);
        assertEquals("TERMINATED", t.getState().toString());
    }
}

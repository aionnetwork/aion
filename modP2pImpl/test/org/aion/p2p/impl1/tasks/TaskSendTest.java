package org.aion.p2p.impl1.tasks;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;
import org.aion.p2p.impl1.P2pMgr.Dest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskSendTest {

    @Mock private INodeMgr nodeMgr;

    @Mock private IP2pMgr p2pMgr;

    @Mock private BlockingQueue<MsgOut> sendMsgQue;

    @Mock private INode node;

    @Mock private Msg msg;

    private Selector selector;

    private int lane;

    private Random r = new Random();

    @Before
    public void setup() throws IOException {
        lane = Math.min(Runtime.getRuntime().availableProcessors() << 1, 32);
        MockitoAnnotations.initMocks(this);
        Map<String, String> logMap = new HashMap<>();
        logMap.put(LogEnum.P2P.name(), LogLevel.INFO.name());
        AionLoggerFactory.init(logMap);

        selector = Selector.open();
        assertNotNull(selector);
    }

    @Test(timeout = 10_000)
    public void testRun() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskSend ts = new TaskSend(p2pMgr, r.nextInt(lane), sendMsgQue, atb, nodeMgr, selector);
        assertNotNull(ts);

        Thread t = new Thread(ts);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(10);
        atb.set(false);
        while (!t.getState().toString().contains("TERMINATED")) {
            Thread.sleep(10);
        }
    }

    @Test(timeout = 10_000)
    public void testRunMsgOutTimeout() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskSend ts = new TaskSend(p2pMgr, r.nextInt(lane), sendMsgQue, atb, nodeMgr, selector);
        assertNotNull(ts);

        MsgOut mo = new MsgOut(r.nextInt(), "1", msg, Dest.OUTBOUND);
        assertNotNull(mo);
        when(sendMsgQue.take()).thenReturn(mo);
        Thread.sleep(5000);

        Thread t = new Thread(ts);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(10);
        atb.set(false);
        while (!t.getState().toString().contains("TERMINATED")) {
            Thread.sleep(10);
        }
    }

    @Test(timeout = 10_000)
    public void testRunLane() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
        assertNotNull(ts);

        MsgOut mo = new MsgOut(1, "1", msg, Dest.OUTBOUND);
        assertNotNull(mo);

        when(sendMsgQue.take()).thenReturn(mo);

        Thread t = new Thread(ts);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(10);
        atb.set(false);
        while (!t.getState().toString().contains("TERMINATED")) {
            Thread.sleep(10);
        }
    }

    @Test(timeout = 10_000)
    public void testRun2() throws InterruptedException, IOException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
        assertNotNull(ts);

        MsgOut mo = new MsgOut(0, "1", msg, Dest.OUTBOUND);
        assertNotNull(mo);

        when(sendMsgQue.take()).thenReturn(mo);
        when(nodeMgr.getOutboundNode(0)).thenReturn(node);

        ChannelBuffer cb = new ChannelBuffer();
        SocketChannel ch = SocketChannel.open();
        assertNotNull(ch);

        ch.configureBlocking(false);
        ch.register(selector, SelectionKey.OP_WRITE, cb);

        when(node.getChannel()).thenReturn(ch);

        Thread t = new Thread(ts);
        t.start();

        assertTrue(t.isAlive());
        Thread.sleep(10);
        atb.set(false);
        while (!t.getState().toString().contains("TERMINATED")) {
            Thread.sleep(10);
        }
    }

    @Test(timeout = 10_000)
    public void testRun3() throws InterruptedException, IOException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
        assertNotNull(ts);

        MsgOut mo = new MsgOut(0, "1", msg, Dest.ACTIVE);
        assertNotNull(mo);

        when(sendMsgQue.take()).thenReturn(mo);
        when(nodeMgr.getActiveNode(0)).thenReturn(node);

        ChannelBuffer cb = new ChannelBuffer();
        SocketChannel ch = SocketChannel.open();
        assertNotNull(ch);

        ch.configureBlocking(false);
        ch.register(selector, SelectionKey.OP_WRITE, cb);

        when(node.getChannel()).thenReturn(ch);

        Thread t = new Thread(ts);
        t.start();

        assertTrue(t.isAlive());
        Thread.sleep(10);
        atb.set(false);
        while (!t.getState().toString().contains("TERMINATED")) {
            Thread.sleep(10);
        }
    }

    @Test(timeout = 10_000)
    public void testRun4() throws InterruptedException, IOException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
        assertNotNull(ts);

        MsgOut mo = new MsgOut(0, "1", msg, Dest.INBOUND);
        assertNotNull(mo);

        when(sendMsgQue.take()).thenReturn(mo);
        when(nodeMgr.getInboundNode(0)).thenReturn(node);

        ChannelBuffer cb = new ChannelBuffer();
        SocketChannel ch = SocketChannel.open();
        assertNotNull(ch);

        ch.configureBlocking(false);
        ch.register(selector, SelectionKey.OP_WRITE, cb);

        when(node.getChannel()).thenReturn(ch);

        Thread t = new Thread(ts);
        t.start();

        assertTrue(t.isAlive());
        Thread.sleep(10);
        atb.set(false);
        while (!t.getState().toString().contains("TERMINATED")) {
            Thread.sleep(10);
        }
    }

    @Test(timeout = 10_000)
    public void testRunNullNode() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskSend ts = new TaskSend(p2pMgr, 0, sendMsgQue, atb, nodeMgr, selector);
        assertNotNull(ts);

        MsgOut mo = new MsgOut(0, "1", msg, Dest.INBOUND);
        assertNotNull(mo);

        when(sendMsgQue.take()).thenReturn(mo);
        when(nodeMgr.getInboundNode(0)).thenReturn(null);

        Thread t = new Thread(ts);
        t.start();

        assertTrue(t.isAlive());
        Thread.sleep(10);
        atb.set(false);
        while (!t.getState().toString().contains("TERMINATED")) {
            Thread.sleep(10);
        }
    }
}

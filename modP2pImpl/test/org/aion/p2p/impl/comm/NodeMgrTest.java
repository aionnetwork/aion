package org.aion.p2p.impl.comm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.aion.p2p.INode;
import org.aion.p2p.impl1.P2pMgr;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

/** @author sridevi */
public class NodeMgrTest {

    private final int MAX_TEMP_NODES = 128;
    private final int MAX_ACTIVE_NODES = 128;

    private String nodeId1 = UUID.randomUUID().toString();
    private String nodeId2 = UUID.randomUUID().toString();
    private String ip1 = "127.0.0.1";
    private String ip2 = "192.168.0.11";
    private int port1 = 30304;

    @Mock private Logger LOGGER;

    @Mock private P2pMgr p2p;

    @Mock private Node node;

    @Mock private SocketChannel channel;

    private NodeMgr nMgr;

    private Random r;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        nMgr = new NodeMgr(p2p, MAX_ACTIVE_NODES, MAX_TEMP_NODES, LOGGER);
        r = new Random();
    }

    private byte[] randomIP() {
        byte[] ip = new byte[8];
        for (int i = 1; i < 8; i += 2) {
            ip[i] = (byte) r.nextInt(256);
        }
        return ip;
    }

    private void addActiveNode() {
        INode node = nMgr.allocNode(ip1, 1);

        byte[] rHash = new byte[32];
        r.nextBytes(rHash);

        node.updateStatus(
                r.nextLong(),
                rHash,
                BigInteger.valueOf(r.nextLong()),
                (byte) r.nextInt(),
                (short) r.nextInt(),
                r.nextInt(),
                r.nextInt());
        addNodetoOutbound(node, UUID.randomUUID());
        nMgr.movePeerToActive(node.getIdHash(), "outbound");
    }

    private void addNodetoOutbound(INode node, UUID _uuid) {
        node.setChannel(channel);
        node.setId(_uuid.toString().getBytes(StandardCharsets.UTF_8));
        node.refreshTimestamp();
        nMgr.addOutboundNode(node);
        assertNotNull(nMgr.getOutboundNode(node.getIdHash()));
    }

    private void addNodetoInbound(INode node, UUID _uuid) {
        node.setChannel(channel);
        node.setId(_uuid.toString().getBytes(StandardCharsets.UTF_8));
        node.refreshTimestamp();
        nMgr.addInboundNode(node);
        assertNotNull(nMgr.getInboundNode(channel.hashCode()));
    }

    @Test(timeout = 10_000)
    public void testTempNode() {
        nMgr.addTempNode(node);
        assertEquals(1, nMgr.tempNodesSize());

        nMgr.addTempNode(node);
        assertEquals(1, nMgr.tempNodesSize());

        String nl = "p2p://" + nodeId1 + "@" + ip1 + ":" + port1;

        INode node = Node.parseP2p(nl);

        nMgr.addTempNode(node);
        assertEquals(2, nMgr.tempNodesSize());
    }

    @Test(timeout = 30_000)
    public void testTempNodeMax_128() {

        String[] nodes_max = new String[130];
        int ip = 0;

        int port = 10000;
        for (int i = 0; i < 130; i++) {
            nodes_max[i] =
                    "p2p://" + UUID.randomUUID().toString() + "@255.255.255." + ip++ + ":" + port++;
        }

        for (String nodeL : nodes_max) {
            INode node = Node.parseP2p(nodeL);
            assertNotNull(node);
            nMgr.addTempNode(node);
            nMgr.seedIpAdd(node.getIpStr());
        }
        assertEquals(128, nMgr.tempNodesSize());
    }

    @Test(timeout = 30_000)
    public void testTempNodesTake() {

        int port2 = 30305;
        String[] nodes =
                new String[] {
                    "p2p://" + nodeId1 + "@" + ip1 + ":" + port2,
                    "p2p://" + nodeId2 + "@" + ip1 + ":" + port1,
                };

        NodeMgr mgr = new NodeMgr(p2p, MAX_ACTIVE_NODES, MAX_TEMP_NODES, LOGGER);

        for (String nodeL : nodes) {
            Node node = Node.parseP2p(nodeL);
            mgr.addTempNode(node);
            assert node != null;
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

    @Test(timeout = 30_000)
    public void testTempNodeMax_Any() {

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

    @Test(timeout = 30_000)
    public void testAddInOutBoundNode() {

        INode node = nMgr.allocNode(ip1, 1);
        node.setChannel(channel);
        node.setId(nodeId1.getBytes(StandardCharsets.UTF_8));

        nMgr.addInboundNode(node);
        INode iNode = nMgr.getInboundNode(channel.hashCode());
        assertEquals(ip1, iNode.getIpStr());

        nMgr.addOutboundNode(node);
        INode oNode = nMgr.getOutboundNode(node.getIdHash());
        assertEquals(ip1, oNode.getIpStr());
    }

    @Test(timeout = 30_000)
    public void testGetActiveNodesList() {

        NodeMgr nMgr = new NodeMgr(p2p, MAX_ACTIVE_NODES, MAX_TEMP_NODES, LOGGER);
        INode node = nMgr.allocNode(ip2, 1);

        node.setChannel(channel);
        node.setId(nodeId2.getBytes(StandardCharsets.UTF_8));

        nMgr.addInboundNode(node);
        assertEquals(0, nMgr.activeNodesSize());

        nMgr.movePeerToActive(channel.hashCode(), "inbound");

        assertEquals(1, nMgr.activeNodesSize());

        List<INode> active = nMgr.getActiveNodesList();

        for (INode activeN : active) {
            assertEquals(ip2, activeN.getIpStr());
        }
    }

    @Test(timeout = 30_000)
    public void testDropActive() {
        INode node = nMgr.allocNode(ip2, 1);

        node.setChannel(channel);
        node.setId(nodeId2.getBytes(StandardCharsets.UTF_8));

        nMgr.addInboundNode(node);
        assertEquals(0, nMgr.activeNodesSize());

        nMgr.movePeerToActive(channel.hashCode(), "inbound");
        assertEquals(1, nMgr.activeNodesSize());

        // will not drop
        nMgr.dropActive(node.getIdHash() - 1, "close");
        assertEquals(1, nMgr.activeNodesSize());

        // will drop
        nMgr.dropActive(node.getIdHash(), "close");
        assertEquals(0, nMgr.activeNodesSize());
    }

    @Test(timeout = 30_000)
    public void testBan() {
        INode node = nMgr.allocNode(ip2, 1);

        node.setChannel(channel);
        node.setId(nodeId2.getBytes(StandardCharsets.UTF_8));

        nMgr.addInboundNode(node);
        assertEquals(0, nMgr.activeNodesSize());

        nMgr.movePeerToActive(channel.hashCode(), "inbound");

        assertEquals(1, nMgr.activeNodesSize());

        assertTrue(node.getPeerMetric().notBan());
        nMgr.ban(node.getIdHash());
        assertFalse(node.getPeerMetric().notBan());
    }

    @Test
    public void testTimeoutInbound() {
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoInbound(node, UUID.fromString(nodeId1));

        nMgr.timeoutCheck(System.currentTimeMillis() + NodeMgr.TIMEOUT_INBOUND_NODES + 1);
        assertNull(nMgr.getInboundNode(channel.hashCode()));
    }

    @Test
    public void testTimeoutOutbound() {
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoOutbound(node, UUID.fromString(nodeId1));

        nMgr.timeoutCheck(System.currentTimeMillis() + NodeMgr.TIMEOUT_OUTBOUND_NODES + 1);
        assertNull(nMgr.getOutboundNode(node.getIdHash()));
    }

    @Test(timeout = 30_000)
    public void testAllocate() {
        INode node = nMgr.allocNode(ip2, 1);
        assertNotNull(node);
        assertFalse(node.getIfFromBootList());

        nMgr.seedIpAdd(ip2);
        node = nMgr.allocNode(ip2, 1);
        assertNotNull(node);
        assertTrue(node.getIfFromBootList());
    }

    @Test(timeout = 30_000)
    public void testMoveOutboundToActive() {
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoOutbound(node, UUID.fromString(nodeId1));

        nMgr.movePeerToActive(node.getIdHash(), "outbound");
        assertNull(nMgr.getOutboundNode(node.getIdHash()));

        INode activeNode = nMgr.getActiveNode(node.getIdHash());
        assertNotNull(activeNode);
        assertEquals(node, activeNode);
    }

    @Test(timeout = 30_000)
    public void testMoveInboundToActive() {
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoInbound(node, UUID.fromString(nodeId1));

        nMgr.movePeerToActive(node.getChannel().hashCode(), "inbound");
        assertNull(nMgr.getInboundNode(node.getChannel().hashCode()));

        INode activeNode = nMgr.getActiveNode(node.getIdHash());
        assertNotNull(activeNode);
        assertEquals(node, activeNode);
    }

    @Test
    public void testTimeoutActive() {
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoInbound(node, UUID.fromString(nodeId1));

        nMgr.movePeerToActive(node.getChannel().hashCode(), "inbound");
        INode activeNode = nMgr.getActiveNode(node.getIdHash());
        assertNotNull(activeNode);
        assertEquals(node, activeNode);

        nMgr.timeoutCheck(System.currentTimeMillis() + NodeMgr.MIN_TIMEOUT_ACTIVE_NODES + 1);
        assertNull(nMgr.getActiveNode(node.getIdHash()));
    }

    @Test(timeout = 30_000)
    public void testGetActiveNodesMap() {
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoInbound(node, UUID.fromString(nodeId1));

        nMgr.movePeerToActive(node.getChannel().hashCode(), "inbound");

        Map activeMap = nMgr.getActiveNodesMap();
        assertNotNull(activeMap);
        assertEquals(1, activeMap.size());
    }

    @Test(timeout = 30_000)
    public void testNotAtOutBoundList() {
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoOutbound(node, UUID.fromString(nodeId1));
        assertFalse(nMgr.notAtOutboundList(node.getIdHash()));

        node = nMgr.allocNode(ip1, 1);
        assertTrue(nMgr.notAtOutboundList(node.getIdHash()));
    }

    @Test(timeout = 30_000)
    public void testGetRandom() {
        assertNull(nMgr.getRandom());

        INode node = nMgr.allocNode(ip2, 1);
        addNodetoInbound(node, UUID.fromString(nodeId1));
        nMgr.movePeerToActive(node.getChannel().hashCode(), "inbound");

        INode nodeRandom = nMgr.getRandom();
        assertNotNull(node);
        assertEquals(node, nodeRandom);
    }

    @Test(timeout = 30_000)
    public void testMovePeerToActive() {
        INode node = nMgr.allocNode(ip2, 1);
        node.setChannel(channel);
        nMgr.movePeerToActive(node.getChannel().hashCode(), "inbound");
        assertTrue(nMgr.getActiveNodesMap().isEmpty());
    }

    @Test(timeout = 30_000)
    public void testMovePeerToActive2() {
        nMgr = new NodeMgr(p2p, 2, 2, LOGGER);
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoOutbound(node, UUID.fromString(nodeId1));
        nMgr.movePeerToActive(node.getIdHash(), "outbound");

        node = nMgr.allocNode(ip1, 1);
        addNodetoOutbound(node, UUID.fromString(nodeId2));
        nMgr.movePeerToActive(node.getIdHash(), "outbound");

        assertEquals(2, nMgr.getActiveNodesMap().size());

        node = nMgr.allocNode(ip1, port1);
        addNodetoOutbound(node, UUID.randomUUID());
        nMgr.movePeerToActive(node.getIdHash(), "outbound");
        assertEquals(2, nMgr.getActiveNodesMap().size());
    }

    @Test(timeout = 30_000)
    public void testMovePeerToActive3() {
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoOutbound(node, UUID.fromString(nodeId1));

        when(p2p.isSelf(node)).thenReturn(true);

        nMgr.movePeerToActive(node.getIdHash(), "outbound");
        assertTrue(nMgr.getActiveNodesMap().isEmpty());
    }

    @Test(timeout = 30_000)
    public void testShutDown() {
        INode node = nMgr.allocNode(ip2, 1);
        addNodetoOutbound(node, UUID.randomUUID());

        node = nMgr.allocNode(ip1, 1);
        addNodetoOutbound(node, UUID.randomUUID());

        nMgr.movePeerToActive(node.getIdHash(), "outbound");

        node = nMgr.allocNode("1.1.1.1", 1);
        addNodetoInbound(node, UUID.randomUUID());

        assertEquals(1, nMgr.activeNodesSize());

        nMgr.shutdown();
        assertTrue(nMgr.getActiveNodesMap().isEmpty());
    }

    @Test(timeout = 30_000)
    public void testDumpNodeInfo() {
        String dump = nMgr.dumpNodeInfo("testId", false);
        assertNotNull(dump);

        INode node = nMgr.allocNode(ip1, 1);
        addNodetoOutbound(node, UUID.randomUUID());
        nMgr.movePeerToActive(node.getIdHash(), "outbound");

        String dump2 = nMgr.dumpNodeInfo("testId", false);
        assertEquals(dump.length(), dump2.length());

        String dump3 = nMgr.dumpNodeInfo("testId", true);
        assertTrue(dump3.length() > dump2.length());
    }

    @Test(timeout = 30_000)
    public void testDumpNodeInfo2() {
        String dump = nMgr.dumpNodeInfo("testId", false);
        assertNotNull(dump);

        nMgr.seedIpAdd(ip1);
        INode node = nMgr.allocNode(ip1, 1);
        byte[] rHash = new byte[32];
        r.nextBytes(rHash);
        node.updateStatus(
                r.nextLong(),
                rHash,
                BigInteger.ONE,
                (byte) r.nextInt(),
                (short) r.nextInt(),
                r.nextInt(),
                r.nextInt());
        addNodetoOutbound(node, UUID.randomUUID());
        nMgr.movePeerToActive(node.getIdHash(), "outbound");

        String dump2 = nMgr.dumpNodeInfo("testId", false);
        assertTrue(dump2.length() > dump.length());

        String dump3 = nMgr.dumpNodeInfo("testId", true);
        assertEquals(dump3.length(), dump2.length());
    }

    @Test(timeout = 30_000)
    public void testDumpNodeInfo3() {
        String dump = nMgr.dumpNodeInfo("testId", false);
        assertNotNull(dump);
        addActiveNode();
        addActiveNode();
        addActiveNode();

        String dump2 = nMgr.dumpNodeInfo("testId", true);
        System.out.println(dump2);
        assertTrue(dump2.length() > dump.length());
    }

    public static final int TIME_OUT = 10; // seconds

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        Deque<INode> inbound = new ConcurrentLinkedDeque<>();
        Deque<INode> outbound = new ConcurrentLinkedDeque<>();

        List<Runnable> threads = new ArrayList<>();
        // due to the maximum number of threads used, active nodes will not be rejected here
        for (int i = 0; i < MAX_ACTIVE_NODES; i++) {
            threads.add(generateTempNode());
            threads.add(moveTempNodeToOutbound(outbound));
            threads.add(moveTempNodeToInbound(inbound));
            threads.add(movePeerToActive(inbound, outbound));
        }

        assertConcurrent("Testing concurrent use of NodeMgr with additions to temp, inbound, outbound and active.", threads, TIME_OUT);

        // print the resulting set of active peers
        System.out.println(nMgr.dumpNodeInfo("self", true));

        assertTrue(nMgr.activeNodesSize() <= MAX_ACTIVE_NODES);
        // the following assert can fail, but under normal circumstances the odds are extremely low
        // if it fails consistently there is very likely a bug in the node management
        assertTrue(nMgr.activeNodesSize() > 0);

        // also remove active nodes
        for (int i = 0; i < MAX_ACTIVE_NODES; i++) {
            threads.add(dropActive());
        }

        assertConcurrent("Testing concurrent use of NodeMgr use with added deletions.", threads, TIME_OUT);

        // print the resulting set of active peers
        System.out.println(nMgr.dumpNodeInfo("self", true));

        assertTrue(nMgr.activeNodesSize() <= MAX_ACTIVE_NODES);
        // the following assert can fail, but under normal circumstances the odds are extremely low
        // if it fails consistently there is very likely a bug in the node management
        assertTrue(nMgr.activeNodesSize() > 0);
    }

    private Runnable generateTempNode() {
        return () -> {
            INode newNode =
                    new Node(
                            false,
                            UUID.randomUUID().toString().getBytes(),
                            randomIP(),
                            r.nextInt(65535) + 1);

            SocketChannel ch = mock(SocketChannel.class);
            newNode.setChannel(ch);
            byte[] rHash = new byte[32];
            r.nextBytes(rHash);
            newNode.updateStatus(
                    r.nextLong(),
                    rHash,
                    BigInteger.valueOf(r.nextLong()),
                    (byte) r.nextInt(),
                    (short) r.nextInt(),
                    r.nextInt(),
                    r.nextInt());

            nMgr.addTempNode(newNode);
        };
    }

    private Runnable moveTempNodeToOutbound(Deque<INode> outbound) {
        return () -> {
            INode tempNode = nMgr.tempNodesTake();
            if (tempNode != null) {
                nMgr.addOutboundNode(tempNode);
                outbound.addLast(tempNode);
            }
        };
    }

    private Runnable moveTempNodeToInbound(Deque<INode> inbound) {
        return () -> {
            INode tempNode = nMgr.tempNodesTake();
            if (tempNode != null) {
                nMgr.addInboundNode(tempNode);
                inbound.addLast(tempNode);
            }
        };
    }

    private Runnable movePeerToActive(Deque<INode> inbound, Deque<INode> outbound) {
        return () -> {
            INode newNode;

            boolean fromInbound = r.nextBoolean();
            if (fromInbound) {
                newNode = inbound.pollFirst();
                if (newNode != null) {
                    nMgr.movePeerToActive(newNode.getChannel().hashCode(), "inbound");
                }
            } else {
                newNode = outbound.pollFirst();
                if (newNode != null) {
                    nMgr.movePeerToActive(newNode.getIdHash(), "outbound");
                }
            }
        };
    }

    private Runnable dropActive() {
        return () -> {
            Map<Integer, INode> activeMap = nMgr.getActiveNodesMap();
            if (!activeMap.isEmpty()) {
                int peerId = activeMap.keySet().iterator().next();
                nMgr.dropActive(peerId, "test");
            }
        };
    }

    /**
     * From <a
     * href="https://github.com/junit-team/junit4/wiki/multithreaded-code-and-concurrency">JUnit
     * Wiki on multithreaded code and concurrency</a>
     */
    public static void assertConcurrent(
            final String message,
            final List<? extends Runnable> runnables,
            final int maxTimeoutSeconds)
            throws InterruptedException {
        final int numThreads = runnables.size();
        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        final ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
        try {
            final CountDownLatch allExecutorThreadsReady = new CountDownLatch(numThreads);
            final CountDownLatch afterInitBlocker = new CountDownLatch(1);
            final CountDownLatch allDone = new CountDownLatch(numThreads);
            for (final Runnable submittedTestRunnable : runnables) {
                threadPool.submit(
                        () -> {
                            allExecutorThreadsReady.countDown();
                            try {
                                afterInitBlocker.await();
                                submittedTestRunnable.run();
                            } catch (final Exception e) {
                                exceptions.add(e);
                            } finally {
                                allDone.countDown();
                            }
                        });
            }
            // wait until all threads are ready
            assertTrue(
                    "Timeout initializing threads! Perform long lasting initializations before passing runnables to assertConcurrent",
                    allExecutorThreadsReady.await(runnables.size() * 10, TimeUnit.MILLISECONDS));
            // start all test runners
            afterInitBlocker.countDown();
            assertTrue(
                    message + " timeout! More than " + maxTimeoutSeconds + " seconds",
                    allDone.await(maxTimeoutSeconds, TimeUnit.SECONDS));
        } finally {
            threadPool.shutdownNow();
        }
        if (!exceptions.isEmpty()) {
            for (Exception e : exceptions) {
                e.printStackTrace();
            }
        }
        assertTrue(
                message + "failed with " + exceptions.size() + " exception(s):" + exceptions,
                exceptions.isEmpty());
    }
}

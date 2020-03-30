package org.aion.p2p.impl1;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.Header;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.TaskUPnPManager;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl.zero.msg.ReqActiveNodes;
import org.aion.p2p.impl.zero.msg.ReqHandshake;
import org.aion.p2p.impl.zero.msg.ReqHandshake1;
import org.aion.p2p.impl.zero.msg.ResActiveNodes;
import org.aion.p2p.impl.zero.msg.ResHandshake;
import org.aion.p2p.impl.zero.msg.ResHandshake1;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

/** @author Chris p2p://{uuid}@{ip}:{port} */
public final class P2pMgr implements IP2pMgr {
    private static final int DELAY_SHOW_P2P_STATUS = 10; // in seconds
    private static final int DELAY_CLEAR_PEERS = 10; // in seconds
    private static final int DELAY_REQUEST_ACTIVE_NODES = 1; // in seconds
    private static final int PERIOD_UPNP_PORT_MAPPING = 3600000;
    private static final int DELAY_CONNECT_OUTBOUND = 1; // in seconds
    private static final int TIMEOUT_OUTBOUND_CONNECT = 10000; // in milliseconds
    private static final int TIMEOUT_MSG_READ = 10000;

    // timeout for messages to be sent
    private static final long WRITE_MSG_TIMEOUT = TimeUnit.SECONDS.toNanos(5);
    private static final long MAX_BUFFER_WRITE_TIME = 1_000_000_000L;
    private static final long MIN_TRACE_BUFFER_WRITE_TIME = 10_000_000L;

    public final Logger p2pLOG, surveyLog;

    // IO-bounded threads get max-gain from the double of the availableProcessor number
    private static final int WORKER = Math.min(Runtime.getRuntime().availableProcessors() * 2, 32);
    private final int SOCKET_RECV_BUFFER = 1024 * 128;
    private final int SOCKET_BACKLOG = 1024;

    private final int maxTempNodes, maxActiveNodes, selfNodeIdHash, selfPort;
    private final int selfChainId;
    private boolean syncSeedsOnly, upnpEnable;
    private String selfRevision, selfShortId;
    private final byte[] selfNodeId, selfIp;
    private INodeMgr nodeMgr;
    private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();
    private final Set<Short> versions = new HashSet<>();
    private final Map<Integer, Integer> errCnt = Collections.synchronizedMap(new LRUMap<>(128));
    private final AtomicBoolean start = new AtomicBoolean(true);

    private ServerSocketChannel tcpServer;
    private Selector selector;
    private ScheduledExecutorService scheduledWorkers;
    private int errTolerance;
    /*
     * The size limit was chosen taking into account that:
     * - in a 2G OOM heap dump the size of this queue reached close to 700_000;
     * - in normal execution heap dumps the size is close to 0.
     * The size should be increased if we notice many warning logs that
     * the queue has reached capacity during execution.
     */
    private BlockingQueue<MsgIn> receiveMsgQue = new LinkedBlockingQueue<>(50_000);

    private static ReqHandshake1 cachedReqHandshake1;
    private static ResHandshake1 cachedResHandshake1;
    private static final ReqActiveNodes cachedReqActiveNodesMsg = new ReqActiveNodes();

    public enum Dest {
        INBOUND,
        OUTBOUND,
        ACTIVE
    }

    /**
     * @param chainId identifier assigned to the current chain read from the blockchain
     *     configuration. Peer connections are allowed only for equal network identifiers.
     * @param _nodeId byte[36]
     * @param _ip String
     * @param _port int
     * @param _bootNodes String[]
     * @param _upnpEnable boolean
     * @param _maxTempNodes int
     * @param _maxActiveNodes int
     */
    public P2pMgr(
            final Logger _p2pLog,
            final Logger surveyLog,
            final int chainId,
            final String _revision,
            final String _nodeId,
            final String _ip,
            final int _port,
            final String[] _bootNodes,
            final boolean _upnpEnable,
            final int _maxTempNodes,
            final int _maxActiveNodes,
            final boolean _bootlistSyncOnly,
            final int _errorTolerance) {

        if (_p2pLog == null) {
            throw new NullPointerException("A non-null logger must be provided in the constructor.");
        }
        this.p2pLOG = _p2pLog;
        this.surveyLog = surveyLog;
        this.selfChainId = chainId;
        this.selfRevision = _revision;
        this.selfNodeId = _nodeId.getBytes();
        this.selfNodeIdHash = Arrays.hashCode(selfNodeId);
        this.selfShortId = new String(Arrays.copyOfRange(_nodeId.getBytes(), 0, 6));
        this.selfIp = Node.ipStrToBytes(_ip);
        this.selfPort = _port;
        this.upnpEnable = _upnpEnable;
        this.maxTempNodes = _maxTempNodes;
        this.maxActiveNodes = _maxActiveNodes;
        this.syncSeedsOnly = _bootlistSyncOnly;
        this.errTolerance = _errorTolerance;

        INode myNode = new Node(false, selfNodeId, selfIp, selfPort);
        myNode.setBinaryVersion(selfRevision);
        myNode.setConnection("self");

        nodeMgr = new NodeMgr(this, _maxActiveNodes, _maxTempNodes, p2pLOG, myNode);

        for (String _bootNode : _bootNodes) {
            Node node = Node.parseP2p(_bootNode);
            if (validateNode(node)) {
                nodeMgr.addTempNode(node);
                nodeMgr.seedIpAdd(node.getIpStr());
            }
        }

        // rem out for bug:
        // nodeMgr.loadPersistedNodes();
        cachedResHandshake1 = new ResHandshake1(p2pLOG, true, this.selfRevision);
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();

            scheduledWorkers = Executors.newScheduledThreadPool(6);

            tcpServer = ServerSocketChannel.open();
            tcpServer.configureBlocking(false);
            tcpServer.socket().setReuseAddress(true);
            /*
             * Bigger RECV_BUFFER and BACKLOG can have a better socket read/write tolerance, can be a advanced p2p settings in the config file.
             */
            tcpServer.socket().setReceiveBufferSize(SOCKET_RECV_BUFFER);

            try {
                tcpServer
                        .socket()
                        .bind(
                                new InetSocketAddress(Node.ipBytesToStr(selfIp), selfPort),
                                SOCKET_BACKLOG);
            } catch (IOException e) {
                p2pLOG.error(
                        "Failed to connect to Socket Address: "
                                + Node.ipBytesToStr(selfIp)
                                + ":"
                                + selfPort
                                + ", please check your ip and port configration!",
                        e);
            }

            tcpServer.register(selector, SelectionKey.OP_ACCEPT);

            Thread thrdIn = new Thread(getInboundInstance(), "p2p-in");
            thrdIn.setPriority(Thread.NORM_PRIORITY);
            thrdIn.start();

            if (p2pLOG.isDebugEnabled()) {
                this.handlers.forEach(
                        (route, callbacks) -> {
                            Handler handler = callbacks.get(0);
                            Header h = handler.getHeader();
                            p2pLOG.debug(
                                    "handler route={} v-c-a={}-{}-{} name={}",
                                    route,
                                    h.getVer(),
                                    h.getCtrl(),
                                    h.getAction(),
                                    handler.getClass().getSimpleName());
                        });
            }

            for (int i = 0; i < WORKER; i++) {
                Thread t = new Thread(getReceiveInstance(), "p2p-worker-" + i);
                t.setPriority(Thread.NORM_PRIORITY);
                t.start();
            }

            if (upnpEnable) {
                scheduledWorkers.scheduleWithFixedDelay(
                        new TaskUPnPManager(p2pLOG, selfPort),
                        1,
                        PERIOD_UPNP_PORT_MAPPING,
                        TimeUnit.MILLISECONDS);
            }

            if (p2pLOG.isInfoEnabled()) {
                scheduledWorkers.scheduleWithFixedDelay(
                        () -> {
                            Thread.currentThread().setName("p2p-status");
                            p2pLOG.info(nodeMgr.dumpNodeInfo(selfShortId, p2pLOG.isDebugEnabled()));
                            p2pLOG.debug("receive queue[{}]", receiveMsgQue.size());
                        },
                        DELAY_SHOW_P2P_STATUS, DELAY_SHOW_P2P_STATUS, TimeUnit.SECONDS);
            }

            if (!syncSeedsOnly) {
                scheduledWorkers.scheduleWithFixedDelay(() -> {
                            Thread.currentThread().setName("p2p-reqNodes");
                            INode node = getRandom();
                            if (node != null) {
                                p2pLOG.trace("TaskRequestActiveNodes: {}", node.toString());
                                send(node.getIdHash(), node.getIdShort(), cachedReqActiveNodesMsg);
                            }
                        },
                        5 * DELAY_REQUEST_ACTIVE_NODES, DELAY_REQUEST_ACTIVE_NODES, TimeUnit.SECONDS);
            }

            scheduledWorkers.scheduleWithFixedDelay(
                    () -> {
                        Thread.currentThread().setName("p2p-clear");
                        nodeMgr.timeoutCheck(System.currentTimeMillis());
                    },
                    DELAY_CLEAR_PEERS, DELAY_CLEAR_PEERS, TimeUnit.SECONDS);

            scheduledWorkers.scheduleWithFixedDelay(() -> connectPeers(), DELAY_CONNECT_OUTBOUND, DELAY_CONNECT_OUTBOUND, TimeUnit.SECONDS);
        } catch (SocketException e) {
            p2pLOG.error("tcp-server-socket-exception.", e);
        } catch (IOException e) {
            p2pLOG.error("tcp-server-io-exception.", e);
        }
    }

    @Override
    public void register(final List<Handler> _cbs) {
        for (Handler _cb : _cbs) {
            Header h = _cb.getHeader();
            short ver = h.getVer();
            byte ctrl = h.getCtrl();
            if (Ver.filter(ver) != Ver.UNKNOWN && Ctrl.filter(ctrl) != Ctrl.UNKNOWN) {
                versions.add(ver);

                int route = h.getRoute();
                List<Handler> routeHandlers = handlers.get(route);
                if (routeHandlers == null) {
                    routeHandlers = new ArrayList<>();
                    routeHandlers.add(_cb);
                    handlers.put(route, routeHandlers);
                } else {
                    routeHandlers.add(_cb);
                }
            }
        }

        cachedReqHandshake1 = new ReqHandshake1(selfNodeId, selfChainId, selfIp, selfPort, selfRevision.getBytes(), new ArrayList<>(versions));
    }

    @Override
    public void send(int nodeId, String displayId, final Msg message) {
        send(nodeId, displayId, message, Dest.ACTIVE);
    }

    public void send(int nodeId, String displayId, final Msg message, Dest peerList) {
        scheduledWorkers.execute(() -> {
            Thread.currentThread().setName("p2p-out");
            long startTime = System.nanoTime();
            process(nodeId, displayId, message, peerList, System.nanoTime());
            long duration = System.nanoTime() - startTime;
            surveyLog.debug("TaskSend: process message, duration = {} ns.", duration);
        });
    }

    private void process(int nodeId, String nodeDisplayId, final Msg message, Dest peerList, long timestamp) {
        // Discard message after the timeout period has passed.
        long now = System.nanoTime();
        if (now - timestamp > WRITE_MSG_TIMEOUT) {
            p2pLOG.debug("timeout-msg to-node={} timestamp={}", nodeDisplayId, now);
        } else {
            INode node = null;
            switch (peerList) {
                case ACTIVE:
                    node = nodeMgr.getActiveNode(nodeId);
                    break;
                case INBOUND:
                    node = nodeMgr.getInboundNode(nodeId);
                    break;
                case OUTBOUND:
                    node = nodeMgr.getOutboundNode(nodeId);
                    break;
            }

            if (node == null) {
                p2pLOG.debug("msg-{} -> {} node-not-exist", peerList.name(), nodeDisplayId);
            } else {
                SelectionKey sk = node.getChannel().keyFor(selector);
                if (sk != null && sk.attachment() != null) {
                    ChannelBuffer channelBuffer = (ChannelBuffer) sk.attachment();
                    SocketChannel sc = node.getChannel();

                    // reset allocated buffer and clear messages if the channel is closed
                    if (channelBuffer.isClosed()) {
                        channelBuffer.refreshHeader();
                        channelBuffer.refreshBody();
                        this.dropActive(channelBuffer.getNodeIdHash(), "close-already");
                        return;
                    } else {
                        try {
                            channelBuffer.lock.lock();

                            // @warning header set len (body len) before header encode
                            byte[] bodyBytes = message.encode();
                            int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
                            Header h = message.getHeader();
                            h.setLen(bodyLen);
                            byte[] headerBytes = h.encode();

                            p2pLOG.trace("write id:{} {}-{}-{}", nodeDisplayId, h.getVer(), h.getCtrl(), h.getAction());

                            ByteBuffer buf = ByteBuffer.allocate(headerBytes.length + bodyLen);
                            buf.put(headerBytes);
                            if (bodyBytes != null) {
                                buf.put(bodyBytes);
                            }
                            buf.flip();

                            long t1 = System.nanoTime(), t2;
                            int wrote = 0;
                            try {
                                do {
                                    int result = sc.write(buf);
                                    wrote += result;

                                    if (result == 0) {
                                        // @Attention:  very important sleep , otherwise when NIO write buffer full,
                                        // without sleep will hangup this thread.
                                        Thread.sleep(0, 1);
                                    }

                                    t2 = System.nanoTime() - t1;
                                } while (buf.hasRemaining() && (t2 < MAX_BUFFER_WRITE_TIME));

                                if (t2 > MIN_TRACE_BUFFER_WRITE_TIME) {
                                    p2pLOG.trace("msg write: id {} size {} time {} ms length {}", nodeDisplayId, wrote, t2, buf.array().length);
                                }

                            } catch (ClosedChannelException ex1) {
                                p2pLOG.debug("closed-channel-exception node=" + nodeDisplayId, ex1);
                                channelBuffer.setClosed();
                            } catch (IOException ex2) {
                                p2pLOG.debug("write-msg-io-exception node=" + nodeDisplayId + " headerBytes=" + headerBytes.length + " bodyLen=" + bodyLen + " time=" + (System.nanoTime() - t1) + "ns", ex2);

                                if (ex2.getMessage().equals("Broken pipe")) {
                                    channelBuffer.setClosed();
                                }
                            } catch (InterruptedException e) {
                                p2pLOG.error("Interrupted while writing message to node=" + nodeDisplayId + ".", e);
                            }
                        } finally {
                            channelBuffer.lock.unlock();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void shutdown() {
        start.set(false);

        if (scheduledWorkers != null) {
            scheduledWorkers.shutdownNow();
        }

        for (List<Handler> hdrs : handlers.values()) {
            hdrs.forEach(Handler::shutDown);
        }
        nodeMgr.shutdown();
    }

    @Override
    public List<Short> versions() {
        return new ArrayList<>(versions);
    }

    @Override
    public void errCheck(int _nodeIdHash, String _displayId) {
        int cnt = (errCnt.get(_nodeIdHash) == null ? 1 : (errCnt.get(_nodeIdHash) + 1));
        if (cnt > this.errTolerance) {
            ban(_nodeIdHash);
            errCnt.put(_nodeIdHash, 0);

            if (p2pLOG.isDebugEnabled()) {
                p2pLOG.debug(
                        "ban node={} err-count={}",
                        (_displayId == null ? _nodeIdHash : _displayId),
                        cnt);
            }
        } else {
            errCnt.put(_nodeIdHash, cnt);
        }
    }

    /** @param _sc SocketChannel */
    public void closeSocket(final SocketChannel _sc, String _reason) {
        closeSocket(_sc, _reason, null);
    }

    @Override
    public void closeSocket(SocketChannel _sc, String _reason, Exception e) {
        if (p2pLOG.isDebugEnabled()) {
            if (e != null) {
                p2pLOG.debug("close-socket reason=" + _reason, e);
            } else {
                p2pLOG.debug("close-socket reason={}", _reason);
            }
        }

        if (_sc != null) {
            SelectionKey sk = _sc.keyFor(selector);
            if (sk != null) {
                sk.cancel();
                sk.attach(null);
            }

            try {
                _sc.close();
            } catch (IOException ex) {
                p2pLOG.info("close-socket-io-exception.", ex);
            }
        }
    }

    /**
     * Remove an active node if exists.
     *
     * @param _nodeIdHash int
     * @param _reason String
     */
    @Override
    public void dropActive(int _nodeIdHash, String _reason) {
        nodeMgr.dropActive(_nodeIdHash, _reason);
    }

    /**
     * @param _node Node
     * @return boolean
     */
    @Override
    public boolean validateNode(final INode _node) {
        if (_node != null) {
            boolean notSelfId = !Arrays.equals(_node.getId(), this.selfNodeId);
            boolean notSameIpOrPort =
                    !(Arrays.equals(selfIp, _node.getIp()) && selfPort == _node.getPort());
            boolean notActive = nodeMgr.notActiveNode(_node.getPeerId());
            boolean notOutbound = nodeMgr.notAtOutboundList(_node.getPeerId());
            return notSelfId && notSameIpOrPort && notActive && notOutbound;
        } else {
            return false;
        }
    }

    /** @param _channel SocketChannel TODO: check option */
    @Override
    public void configChannel(final SocketChannel _channel) throws IOException {
        _channel.configureBlocking(false);
        _channel.socket().setSoTimeout(TIMEOUT_MSG_READ);
        _channel.socket().setReceiveBufferSize(P2pConstant.RECV_BUFFER_SIZE);
        _channel.socket().setSendBufferSize(P2pConstant.SEND_BUFFER_SIZE);
    }

    private void ban(int nodeIdHashcode) {
        nodeMgr.ban(nodeIdHashcode);
        nodeMgr.dropActive(nodeIdHashcode, "ban");
    }

    // <------------------------ getter methods below --------------------------->

    @Override
    public INode getRandom() {
        return this.nodeMgr.getRandom();
    }

    @Override
    public Map<Integer, INode> getActiveNodes() {
        return this.nodeMgr.getActiveNodesMap();
    }

    public int getTempNodesCount() {
        return this.nodeMgr.tempNodesSize();
    }

    @Override
    public int getMaxActiveNodes() {
        return this.maxActiveNodes;
    }

    @Override
    public int getMaxTempNodes() {
        return this.maxTempNodes;
    }

    @Override
    public boolean isSyncSeedsOnly() {
        return this.syncSeedsOnly;
    }

    @Override
    public int getAvgLatency() {
        return this.nodeMgr.getAvgLatency();
    }

    @Override
    public boolean isCorrectNetwork(int netId){
        return netId == selfChainId;
    }

    /**
     * @implNote Compares the port and id to the given node to allow connections to the same id and
     *     different port. Does not compare IP values since the self IP is often recorded as 0.0.0.0
     *     in the configuration file and cannot be inferred reliably by the node itself.
     */
    @Override
    public boolean isSelf(INode node) {
        return selfNodeIdHash == node.getIdHash()
                && selfPort == node.getPort()
                && Arrays.equals(selfNodeId, node.getId());
    }

    @Override
    public void updateChainInfo(long blockNumber, byte[] blockHash, BigInteger blockTD) {
        nodeMgr.updateChainInfo(blockNumber, blockHash, blockTD);
    }

    private TaskInbound getInboundInstance() {
        return new TaskInbound(
                p2pLOG,
                surveyLog,
                this,
                this.selector,
                this.start);
    }

    private TaskReceive getReceiveInstance() {
        return new TaskReceive(p2pLOG, surveyLog, start, receiveMsgQue, handlers);
    }

    private void connectPeers() {
        Thread.currentThread().setName("p2p-peer");

        INode node;
        try {
            if (nodeMgr.activeNodesSize() >= maxActiveNodes) {
                p2pLOG.warn("tcp-connect-peer pass max-active-nodes.");
                return;
            }

            node = nodeMgr.tempNodesTake();
            if (node == null) {
                p2pLOG.debug("no temp node can take.");
                return;
            }

            if (node.getIfFromBootList()) {
                nodeMgr.addTempNode(node);
            }
        } catch (Exception e) {
            p2pLOG.debug("tcp-Exception.", e);
            return;
        }
        int nodeIdHash = node.getIdHash();
        if (nodeMgr.notAtOutboundList(nodeIdHash) && nodeMgr.notActiveNode(nodeIdHash)) {
            int _port = node.getPort();
            SocketChannel channel = null;
            try {
                channel = SocketChannel.open();
                channel.socket().connect(new InetSocketAddress(node.getIpStr(), _port), TIMEOUT_OUTBOUND_CONNECT);
                configChannel(channel);

                if (channel.isConnected()) {
                    p2pLOG.debug("success-connect node-id={} ip={}", node.getIdShort(), node.getIpStr());

                    channel.configureBlocking(false);
                    SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
                    ChannelBuffer rb = new ChannelBuffer(p2pLOG);
                    rb.setDisplayId(node.getIdShort());
                    rb.setNodeIdHash(nodeIdHash);
                    sk.attach(rb);

                    node.refreshTimestamp();
                    node.setChannel(channel);
                    nodeMgr.addOutboundNode(node);

                    p2pLOG.debug("prepare-request-handshake -> id={} ip={}", node.getIdShort(), node.getIpStr());

                    send(node.getIdHash(), node.getIdShort(), cachedReqHandshake1, Dest.OUTBOUND);
                } else {
                    p2pLOG.debug("fail-connect node-id -> id={} ip={}", node.getIdShort(), node.getIpStr());

                    channel.close();
                    // node.peerMetric.incFailedCount();
                }
            } catch (Exception e) {
                p2pLOG.debug("connect-outbound exception -> id=" + node.getIdShort() + " ip=" + node.getIpStr(), e);
                p2pLOG.trace("close channel {}", node.toString());

                if (channel != null) {
                    try {
                        channel.close();
                    } catch (IOException e1) {
                        p2pLOG.debug("TaskConnectPeers close exception.", e1);
                    }
                }
            }
        }
    }

    void acceptConnection(ServerSocketChannel _channel) throws IOException {
        if (this.nodeMgr.activeNodesSize() >= getMaxActiveNodes()) {
            return;
        }
        SocketChannel channel = _channel.accept();
        if (channel != null) {
            configChannel(channel);

            String ip = channel.socket().getInetAddress().getHostAddress();

            if (isSyncSeedsOnly() && this.nodeMgr.isSeedIp(ip)) {
                channel.close();
                return;
            }

            int port = channel.socket().getPort();
            INode node;
            try {
                node = this.nodeMgr.allocNode(ip, port);
            } catch (IllegalArgumentException e) {
                p2pLOG.error("illegal ip / port : {} {}", ip, port);
                channel.close();
                return;
            }
            p2pLOG.trace("new-node : {}", node.toString());

            node.setChannel(channel);
            SelectionKey sk = channel.register(this.selector, SelectionKey.OP_READ);
            sk.attach(new ChannelBuffer(p2pLOG));
            this.nodeMgr.addInboundNode(node);
            p2pLOG.debug("new-connection {}:{}", ip, port);
        }
    }

    void readBuffer(final SelectionKey sk, final ChannelBuffer cb, final ByteBuffer readBuf) throws IOException {
        readBuf.rewind();
        SocketChannel sc = (SocketChannel) sk.channel();

        int r;
        int cnt = 0;
        do {
            r = sc.read(readBuf);
            cnt += r;
        } while (r > 0);

        if (cnt < 1) {
            return;
        }

        int remainBufAll = cb.getBuffRemain() + cnt;
        ByteBuffer bufferAll = calBuffer(cb, readBuf, cnt);

        do {
            r = readMsg(sk, bufferAll, remainBufAll);
            if (remainBufAll == r) {
                break;
            } else {
                remainBufAll = r;
            }
        } while (r > 0);

        cb.setBuffRemain(r);

        if (r != 0) {
            // there are no perfect cycling buffer in jdk
            // yet.
            // simply just buff move for now.
            // @TODO: looking for more efficient way.

            int currPos = bufferAll.position();
            cb.setRemainBuffer(new byte[r]);
            bufferAll.position(currPos - r);
            bufferAll.get(cb.getRemainBuffer());
        }

        readBuf.rewind();
    }

    private static ByteBuffer calBuffer(ChannelBuffer _cb, ByteBuffer _readBuf, int _cnt) {
        ByteBuffer r;
        if (_cb.getBuffRemain() != 0) {
            byte[] alreadyRead = new byte[_cnt];
            _readBuf.position(0);
            _readBuf.get(alreadyRead);
            r = ByteBuffer.allocate(_cb.getBuffRemain() + _cnt);
            r.put(_cb.getRemainBuffer());
            r.put(alreadyRead);
        } else {
            r = _readBuf;
        }

        return r;
    }

    private int readMsg(SelectionKey sk, ByteBuffer readBuf, int cnt) throws IOException {
        ChannelBuffer cb = (ChannelBuffer) sk.attachment();
        if (cb == null) {
            throw new IOException("attachment is null");
        }

        int readCnt;
        if (cb.isHeaderNotCompleted()) {
            readCnt = readHeader(cb, readBuf, cnt);
        } else {
            readCnt = cnt;
        }

        if (cb.isBodyNotCompleted()) {
            readCnt = readBody(cb, readBuf, readCnt);
        }

        if (cb.isBodyNotCompleted()) {
            return readCnt;
        }

        handleMessage(sk, cb);

        return readCnt;
    }

    private static int readHeader(final ChannelBuffer cb, final ByteBuffer readBuf, int cnt) {
        if (cnt < Header.LEN) {
            return cnt;
        }

        int origPos = readBuf.position();
        int startP = origPos - cnt;
        readBuf.position(startP);
        cb.readHead(readBuf);
        readBuf.position(origPos);
        return cnt - Header.LEN;
    }

    private static int readBody(final ChannelBuffer cb, ByteBuffer readBuf, int cnt) {
        int bodyLen = cb.getHeader().getLen();

        // some msg have nobody.
        if (bodyLen == 0) {
            cb.body = new byte[0];
            return cnt;
        }

        if (cnt < bodyLen) {
            return cnt;
        }

        int origPos = readBuf.position();
        int startP = origPos - cnt;
        readBuf.position(startP);
        cb.readBody(readBuf);
        readBuf.position(origPos);
        return cnt - bodyLen;
    }

    // used to impose a low limit to this type of messages
    private static final int ACT_BROADCAST_BLOCK = 7;
    private static final int CTRL_SYNC = 1;

    private void handleMessage(SelectionKey sk, ChannelBuffer cb) {

        Header h = cb.getHeader();
        byte[] bodyBytes = cb.body;

        cb.refreshHeader();
        cb.refreshBody();

        int maxRequestsPerSecond = 0;

        // TODO: refactor to remove knowledge of sync message types
        if (h.getCtrl() == CTRL_SYNC && h.getAction() == ACT_BROADCAST_BLOCK) {
            maxRequestsPerSecond = P2pConstant.READ_MAX_RATE;
        } else {
            maxRequestsPerSecond = P2pConstant.READ_MAX_RATE_TXBC;
        }

        boolean underRC = cb.shouldRoute(h.getRoute(), maxRequestsPerSecond);

        if (!underRC) {
            p2pLOG.debug("over-called-route={}-{}-{} calls={} node={}", h.getVer(), h.getCtrl(), h.getAction(), cb.getRouteCount(h.getRoute()).count, cb.getDisplayId());
            return;
        }

        switch (h.getVer()) {
            case Ver.V0:
                switch (h.getCtrl()) {
                    case Ctrl.NET:
                        try {
                            handleP2pMessage(sk, h.getAction(), bodyBytes);
                        } catch (Exception ex) {
                            p2pLOG.debug("handle-p2p-msg error.", ex);
                        }
                        break;
                    case Ctrl.SYNC:
                        if (!handlers.containsKey(h.getRoute())) {
                            p2pLOG.debug("unregistered-route={}-{}-{} node={}", h.getVer(), h.getCtrl(), h.getAction(), cb.getDisplayId());
                            return;
                        }

                        handleKernelMessage(cb.getNodeIdHash(), h.getRoute(), bodyBytes);
                        break;
                    default:
                        p2pLOG.debug("invalid-route={}-{}-{} node={}", h.getVer(), h.getCtrl(), h.getAction(), cb.getDisplayId());
                        break;
                }
                break;
            default:
                p2pLOG.debug("unhandled-ver={} node={}", h.getVer(), cb.getDisplayId());
                break;
        }
    }

    private static final int OFFER_TIMEOUT = 100; // in milliseconds

    private void handleKernelMessage(int nodeIdHash, int route, final byte[] msgBytes) {
        INode node = nodeMgr.getActiveNode(nodeIdHash);
        if (node != null) {
            String nodeDisplayId = node.getIdShort();
            node.refreshTimestamp();
            try {
                boolean added = receiveMsgQue.offer(new MsgIn(nodeIdHash, nodeDisplayId, route, msgBytes), OFFER_TIMEOUT, TimeUnit.MILLISECONDS);
                if (!added) {
                    p2pLOG.warn("Message not added to the receive queue due to exceeded capacity: msg={} from node={}", msgBytes, node.getIdShort());
                }
            } catch (InterruptedException e) {
                p2pLOG.error("Interrupted while attempting to add the received message to the processing queue:", e);
            }
        } else {
            p2pLOG.debug("handleKernelMsg can't find hash{}", nodeIdHash);
        }
    }

    private void handleP2pMessage(final SelectionKey sk, byte act, final byte[] msgBytes) {
        ChannelBuffer rb = (ChannelBuffer) sk.attachment();

        switch (act) {
            case Act.REQ_HANDSHAKE:
                if (msgBytes.length > ReqHandshake.LEN) {
                    ReqHandshake1 reqHandshake1 = ReqHandshake1.decode(msgBytes, p2pLOG);
                    if (reqHandshake1 != null) {
                        handleHandshakeRequest(rb, sk.channel().hashCode(), reqHandshake1.getNodeId(), reqHandshake1.getNetId(), reqHandshake1.getPort(), reqHandshake1.getRevision());
                    }
                }
                break;

            case Act.RES_HANDSHAKE:
                if (rb.getNodeIdHash() != 0) {
                    if (msgBytes.length > ResHandshake.LEN) {
                        ResHandshake1 resHandshake1 = ResHandshake1.decode(msgBytes, p2pLOG);
                        if (resHandshake1 != null && resHandshake1.getSuccess()) {
                            handleHandshakeResponse(rb.getNodeIdHash(), resHandshake1.getBinaryVersion());
                        }
                    }
                }
                break;

            case Act.REQ_ACTIVE_NODES:
                if (rb.getNodeIdHash() != 0) {
                    INode node = nodeMgr.getActiveNode(rb.getNodeIdHash());
                    if (node != null) {
                        ResActiveNodes resActiveNodes = new ResActiveNodes(p2pLOG, nodeMgr.getActiveNodesList());
                        send(node.getIdHash(), node.getIdShort(), resActiveNodes);
                    }
                }
                break;

            case Act.RES_ACTIVE_NODES:
                if (isSyncSeedsOnly() || rb.getNodeIdHash() == 0) {
                    break;
                }

                INode node = nodeMgr.getActiveNode(rb.getNodeIdHash());
                if (node != null) {
                    node.refreshTimestamp();
                    ResActiveNodes resActiveNodes = ResActiveNodes.decode(msgBytes, p2pLOG);
                    if (resActiveNodes != null) {
                        List<INode> incomingNodes = resActiveNodes.getNodes();
                        for (INode incomingNode : incomingNodes) {
                            if (nodeMgr.tempNodesSize() >= getMaxTempNodes()) {
                                return;
                            }

                            if (validateNode(incomingNode)) {
                                nodeMgr.addTempNode(incomingNode);
                            }
                        }
                    }
                }
                break;
            default:
                p2pLOG.debug("unknown-route act={}", act);
                break;
        }
    }

    private void handleHandshakeResponse(int nodeIdHash, String binaryVersion) {
        INode node = nodeMgr.getOutboundNode(nodeIdHash);
        if (node != null && node.getPeerMetric().notBan()) {
            node.refreshTimestamp();
            node.setBinaryVersion(binaryVersion);
            nodeMgr.movePeerToActive(node.getIdHash(), "outbound");
        }
    }

    /**
     * Constructs node info after handshake request success.
     */
    private void handleHandshakeRequest(final ChannelBuffer buffer, int channelHash, final byte[] nodeId, int netId, int port, final byte[] revision) {
        INode node = nodeMgr.getInboundNode(channelHash);
        if (node != null && node.getPeerMetric().notBan()) {
            p2pLOG.debug("netId={}, nodeId={} port={} rev={}", netId, new String(nodeId), port, revision);

            p2pLOG.trace("node {}", node.toString());
            if (isCorrectNetwork(netId)) {
                buffer.setNodeIdHash(Arrays.hashCode(nodeId));
                buffer.setDisplayId(new String(Arrays.copyOfRange(nodeId, 0, 6)));
                node.setId(nodeId);
                node.setPort(port);

                // handshake 1
                if (revision != null) {
                    String binaryVersion;
                    binaryVersion = new String(revision, StandardCharsets.UTF_8);
                    node.setBinaryVersion(binaryVersion);
                    nodeMgr.movePeerToActive(channelHash, "inbound");
                    send(node.getIdHash(), node.getIdShort(), cachedResHandshake1);
                }

            } else {
                p2pLOG.debug("handshake-rule-fail");
            }
        }
    }
}

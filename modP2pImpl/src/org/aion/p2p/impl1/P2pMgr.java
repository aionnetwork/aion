/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl1;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.*;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.impl.TaskRequestActiveNodes;
import org.aion.p2p.impl.TaskUPnPManager;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl.zero.msg.*;
import org.aion.p2p.impl1.TaskReceive.MsgIn;
import org.aion.p2p.impl1.TaskSend.MsgOut;
import org.apache.commons.collections4.map.LRUMap;

/** @author Chris p2p://{uuid}@{ip}:{port} */
public final class P2pMgr implements IP2pMgr {
    private static final int PERIOD_SHOW_STATUS = 10000;
    private static final int PERIOD_REQUEST_ACTIVE_NODES = 1000;
    private static final int PERIOD_UPNP_PORT_MAPPING = 3600000;
    private static final int TIMEOUT_MSG_READ = 10000;

    // TODO: need refactor by passing the parameter in the later version.
    private static final int txBroadCastRoute =
        (Ctrl.SYNC << 8) + 6; // ((Ver.V0 << 16) + (Ctrl.SYNC << 8) + 6);

    private final int maxTempNodes;
    private final int maxActiveNodes;
    private final boolean syncSeedsOnly;
    private final boolean showStatus;
    private final boolean showLog;
    private final int selfNetId;
    private final String selfRevision;
    private final byte[] selfNodeId;
    private final int selfNodeIdHash;
    private final String selfShortId;
    private final byte[] selfIp;
    private final int selfPort;
    private final boolean upnpEnable;
    private final NodeMgr nodeMgr;

    private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();
    private final Set<Short> versions = new HashSet<>();
    private final Map<Integer, Integer> errCnt = Collections.synchronizedMap(new LRUMap<>(128));

    private ServerSocketChannel tcpServer;
    private Selector selector;
    private ScheduledThreadPoolExecutor scheduledWorkers;
    private int errTolerance;
    private LinkedBlockingQueue<MsgOut> sendMsgQue = new LinkedBlockingQueue<>();
    private LinkedBlockingQueue<MsgIn> receiveMsgQue = new LinkedBlockingQueue<>();
    private AtomicBoolean start = new AtomicBoolean(true);

    private static ReqHandshake1 cachedReqHandshake1;
    private static ResHandshake1 cachedResHandshake1;

    enum Dest { INBOUND, OUTBOUND, ACTIVE }

    /**
     * @param _nodeId byte[36]
     * @param _ip String
     * @param _port int
     * @param _bootNodes String[]
     * @param _upnpEnable boolean
     * @param _maxTempNodes int
     * @param _maxActiveNodes int
     * @param _showStatus boolean
     * @param _showLog boolean
     */
    public P2pMgr(
        int _netId,
        String _revision,
        String _nodeId,
        String _ip,
        int _port,
        final String[] _bootNodes,
        boolean _upnpEnable,
        int _maxTempNodes,
        int _maxActiveNodes,
        boolean _showStatus,
        boolean _showLog,
        boolean _bootlistSyncOnly,
        int _errorTolerance) {

        this.selfNetId = _netId;
        this.selfRevision = _revision;
        this.selfNodeId = _nodeId.getBytes();
        this.selfNodeIdHash = Arrays.hashCode(selfNodeId);
        this.selfShortId = new String(Arrays.copyOfRange(_nodeId.getBytes(), 0, 6));
        this.selfIp = Node.ipStrToBytes(_ip);
        this.selfPort = _port;
        this.upnpEnable = _upnpEnable;
        this.maxTempNodes = _maxTempNodes;
        this.maxActiveNodes = _maxActiveNodes;
        this.showStatus = _showStatus;
        this.showLog = _showLog;
        this.syncSeedsOnly = _bootlistSyncOnly;
        this.errTolerance = _errorTolerance;

        nodeMgr = new NodeMgr(_maxActiveNodes, _maxTempNodes);

        for (String _bootNode : _bootNodes) {
            Node node = Node.parseP2p(_bootNode);
            if (node != null && validateNode(node)) {
                nodeMgr.addTempNode(node);
                nodeMgr.seedIpAdd(node.getIpStr());
            }
        }

        // rem out for bug:
        // nodeMgr.loadPersistedNodes();
        cachedResHandshake1 = new ResHandshake1(true, this.selfRevision);
    }

    void accept() {
        SocketChannel channel;
        try {

            if (nodeMgr.activeNodesSize() >= this.maxActiveNodes) return;

            channel = tcpServer.accept();
            configChannel(channel);

            SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
            sk.attach(new ChannelBuffer(this.showLog));

            String ip = channel.socket().getInetAddress().getHostAddress();
            int port = channel.socket().getPort();

            if (syncSeedsOnly && nodeMgr.isSeedIp(ip)) {
                channel.close();
                return;
            }

            Node node = nodeMgr.allocNode(ip, 0);
            node.setChannel(channel);
            nodeMgr.addInboundNode(node);

            if (showLog) System.out.println("<p2p new-connection " + ip + ":" + port + ">");

        } catch (IOException e) {
            if (showLog) System.out.println("<p2p inbound-accept-io-exception>");
        }
    }

    /**
     * SocketChannel
     *
     * @throws IOException IOException
     */
    private int readHeader(final ChannelBuffer _cb, ByteBuffer readBuffer, int cnt)
        throws IOException {

        if (cnt < Header.LEN) return cnt;

        int origPos = readBuffer.position();

        int startP = origPos - cnt;

        readBuffer.position(startP);

        _cb.readHead(readBuffer);

        readBuffer.position(origPos);

        return cnt - Header.LEN;
    }

    /**
     * SocketChannel
     *
     * @throws IOException IOException
     */
    private int readBody(final ChannelBuffer _cb, ByteBuffer readBuffer, int cnt)
        throws IOException {

        int bodyLen = _cb.header.getLen();

        // some msg have nobody.
        if (bodyLen == 0) {
            _cb.body = new byte[0];
            return cnt;
        }

        if (cnt < bodyLen) return cnt;

        int origPos = readBuffer.position();
        int startP = origPos - cnt;

        readBuffer.position(startP);

        _cb.readBody(readBuffer);

        readBuffer.position(origPos);

        return cnt - bodyLen;
    }

    /**
     * @param _sk SelectionKey
     * @throws IOException IOException
     */
    int read(final SelectionKey _sk, ByteBuffer _readBuffer, int _cnt) throws IOException {

        int currCnt = 0;

        if (_sk.attachment() == null) {
            throw new P2pException("attachment is null");
        }
        ChannelBuffer rb = (ChannelBuffer) _sk.attachment();

        // read header
        if (!rb.isHeaderCompleted()) {
            currCnt = readHeader(rb, _readBuffer, _cnt);
        } else {
            currCnt = _cnt;
        }

        // read body
        if (rb.isHeaderCompleted() && !rb.isBodyCompleted()) {
            currCnt = readBody(rb, _readBuffer, currCnt);
        }

        if (!rb.isBodyCompleted()) return currCnt;

        Header h = rb.header;

        byte[] bodyBytes = rb.body;
        rb.refreshHeader();
        rb.refreshBody();

        short ver = h.getVer();
        byte ctrl = h.getCtrl();
        byte act = h.getAction();
        int route = h.getRoute();

        boolean underRC =
            rb.shouldRoute(
                route,
                ((route == txBroadCastRoute)
                    ? P2pConstant.READ_MAX_RATE_TXBC
                    : P2pConstant.READ_MAX_RATE));

        if (!underRC) {
            if (showLog)
                System.out.println(
                    "<p2p over-called-route="
                        + ver
                        + "-"
                        + ctrl
                        + "-"
                        + act
                        + " calls="
                        + rb.getRouteCount(route).count
                        + " node="
                        + rb.displayId
                        + ">");
            return currCnt;
        }

        switch (ver) {
            case Ver.V0:
                switch (ctrl) {
                    case Ctrl.NET:
                        try {
                            handleP2pMsg(_sk, act, bodyBytes);
                        } catch (Exception ex) {
                            if (showLog)
                                System.out.println(
                                    "<p2p handle-p2p-msg error=" + ex.getMessage() + ">");
                        }
                        break;
                    case Ctrl.SYNC:
                        if (!handlers.containsKey(route)) {
                            if (showLog)
                                System.out.println(
                                    "<p2p unregistered-route="
                                        + ver
                                        + "-"
                                        + ctrl
                                        + "-"
                                        + act
                                        + " node="
                                        + rb.displayId
                                        + ">");
                            return currCnt;
                        }

                        handleKernelMsg(rb.nodeIdHash, route, bodyBytes);
                        break;
                    default:
                        if (showLog)
                            System.out.println(
                                "<p2p invalid-route="
                                    + ver
                                    + "-"
                                    + ctrl
                                    + "-"
                                    + act
                                    + " node="
                                    + rb.displayId
                                    + ">");
                        break;
                }
                break;
            default:
                if (showLog)
                    System.out.println("<p2p unhandled-ver=" + ver + " node=" + rb.displayId + ">");
                break;
        }

        return currCnt;
    }

    /**
     * @param _node Node
     * @return boolean
     */
    private boolean validateNode(final Node _node) {
        if (_node != null) {
            boolean notSelfId = !Arrays.equals(_node.getId(), this.selfNodeId);
            boolean notSameIpOrPort =
                    !(Arrays.equals(selfIp, _node.getIp()) && selfPort == _node.getPort());
            boolean notActive = !nodeMgr.hasActiveNode(_node.getIdHash());
            boolean notOutbound = !nodeMgr.getOutboundNodes().containsKey(_node.getIdHash());
            return notSelfId && notSameIpOrPort && notActive && notOutbound;
        } else return false;
    }

    /** @param _channel SocketChannel TODO: check option */
    void configChannel(final SocketChannel _channel) throws IOException {
        _channel.configureBlocking(false);
        _channel.socket().setSoTimeout(TIMEOUT_MSG_READ);

        // set buffer to 256k.
        _channel.socket().setReceiveBufferSize(P2pConstant.RECV_BUFFER_SIZE);
        _channel.socket().setSendBufferSize(P2pConstant.SEND_BUFFER_SIZE);
        // _channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        // _channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        // _channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    }

    /** @param _sc SocketChannel */
    public void closeSocket(final SocketChannel _sc, String _reason) {
        if (showLog) System.out.println("<p2p close-socket reason=" + _reason + ">");

        try {
            SelectionKey sk = _sc.keyFor(selector);
            _sc.close();
            if (sk != null) sk.cancel();
        } catch (IOException e) {
            if (showLog) System.out.println("<p2p close-socket-io-exception>");
        }
    }

    /** @return boolean TODO: implementation */
    private boolean handshakeRuleCheck(int netId) {
        // check net id
        if (netId != selfNetId) return false;
        // check supported protocol versions
        return true;
    }

    /**
     * @param _buffer ChannelBuffer
     * @param _channelHash int
     * @param _nodeId byte[]
     * @param _netId int
     * @param _port int
     * @param _revision byte[]
     *     <p>Construct node info after handshake request success
     */
    private void handleReqHandshake(
            final ChannelBuffer _buffer,
            int _channelHash,
            final byte[] _nodeId,
            int _netId,
            int _port,
            final byte[] _revision) {
        Node node = nodeMgr.getInboundNode(_channelHash);
        if (node != null && node.peerMetric.notBan()) {
            if (handshakeRuleCheck(_netId)) {
                _buffer.nodeIdHash = Arrays.hashCode(_nodeId);
                _buffer.displayId = new String(Arrays.copyOfRange(_nodeId, 0, 6));
                node.setId(_nodeId);
                node.setPort(_port);

                // handshake 1
                if (_revision != null) {
                    String binaryVersion;
                    try {
                        binaryVersion = new String(_revision, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        binaryVersion = "decode-fail";
                    }
                    node.setBinaryVersion(binaryVersion);
                    nodeMgr.moveInboundToActive(_channelHash, this);
                    sendMsgQue.offer(
                            new MsgOut(
                                    node.getIdHash(),
                                    node.getIdShort(),
                                    cachedResHandshake1,
                                    Dest.ACTIVE));
                }

            } else {
                if (showLog) System.out.println("<p2p handshake-rule-fail>");
            }
        }
    }

    private void handleResHandshake(int _nodeIdHash, String _binaryVersion) {
        Node node = nodeMgr.getOutboundNodes().get(_nodeIdHash);
        if (node != null && node.peerMetric.notBan()) {
            node.refreshTimestamp();
            node.setBinaryVersion(_binaryVersion);
            nodeMgr.moveOutboundToActive(node.getIdHash(), node.getIdShort(), this);
        }
    }

    /**
     * @param _sk SelectionKey
     * @param _act ACT
     * @param _msgBytes byte[]
     */
    private void handleP2pMsg(final SelectionKey _sk, byte _act, final byte[] _msgBytes) {

        ChannelBuffer rb = (ChannelBuffer) _sk.attachment();

        switch (_act) {
            case Act.REQ_HANDSHAKE:
                if (_msgBytes.length > ReqHandshake.LEN) {
                    ReqHandshake1 reqHandshake1 = ReqHandshake1.decode(_msgBytes);
                    if (reqHandshake1 != null) {
                        handleReqHandshake(
                                rb,
                                _sk.channel().hashCode(),
                                reqHandshake1.getNodeId(),
                                reqHandshake1.getNetId(),
                                reqHandshake1.getPort(),
                                reqHandshake1.getRevision());
                    }
                }
                break;

            case Act.RES_HANDSHAKE:
                if (rb.nodeIdHash == 0) return;

                if (_msgBytes.length > ResHandshake.LEN) {
                    ResHandshake1 resHandshake1 = ResHandshake1.decode(_msgBytes);
                    if (resHandshake1 != null && resHandshake1.getSuccess())
                        handleResHandshake(rb.nodeIdHash, resHandshake1.getBinaryVersion());
                }
                break;

            case Act.REQ_ACTIVE_NODES:
                if (rb.nodeIdHash != 0) {
                    Node node = nodeMgr.getActiveNode(rb.nodeIdHash);
                    if (node != null)
                        sendMsgQue.offer(
                                new MsgOut(
                                        node.getIdHash(),
                                        node.getIdShort(),
                                        new ResActiveNodes(nodeMgr.getActiveNodesList()),
                                        Dest.ACTIVE));
                }
                break;

            case Act.RES_ACTIVE_NODES:
                if (syncSeedsOnly) break;

                if (rb.nodeIdHash != 0) {
                    Node node = nodeMgr.getActiveNode(rb.nodeIdHash);
                    if (node != null) {
                        node.refreshTimestamp();
                        ResActiveNodes resActiveNodes = ResActiveNodes.decode(_msgBytes);
                        if (resActiveNodes != null) {
                            List<Node> incomingNodes = resActiveNodes.getNodes();
                            for (Node incomingNode : incomingNodes) {
                                if (nodeMgr.tempNodesSize() >= this.maxTempNodes) return;
                                if (validateNode(incomingNode)) nodeMgr.addTempNode(incomingNode);
                            }
                        }
                    }
                }
                break;
            default:
                if (showLog) System.out.println("<p2p unknown-route act=" + _act + ">");
                break;
        }
    }

    /**
     * @param _nodeIdHash int
     * @param _route int
     * @param _msgBytes byte[]
     */
    private void handleKernelMsg(int _nodeIdHash, int _route, final byte[] _msgBytes) {
        Node node = nodeMgr.getActiveNode(_nodeIdHash);
        if (node != null) {
            int nodeIdHash = node.getIdHash();
            String nodeDisplayId = node.getIdShort();
            node.refreshTimestamp();
            receiveMsgQue.offer(new MsgIn(nodeIdHash, nodeDisplayId, _route, _msgBytes));
        }
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();

            scheduledWorkers = new ScheduledThreadPoolExecutor(2);

            tcpServer = ServerSocketChannel.open();
            tcpServer.configureBlocking(false);
            tcpServer.socket().setReuseAddress(true);
            tcpServer.socket().bind(new InetSocketAddress(Node.ipBytesToStr(selfIp), selfPort));
            tcpServer.register(selector, SelectionKey.OP_ACCEPT);

            Thread thrdIn =
                    new Thread(
                            new TaskInbound(this, this.selector, this.start),
                            "p2p-in");
            //            Thread thrdIn = new Thread(new TaskInbound(), "p2p-in");
            thrdIn.setPriority(Thread.NORM_PRIORITY);
            thrdIn.start();

            if (showLog)
                this.handlers.forEach(
                        (route, callbacks) -> {
                            Handler handler = callbacks.get(0);
                            Header h = handler.getHeader();
                            System.out.println(
                                    "<p2p-handler route="
                                            + route
                                            + " v-c-a="
                                            + h.getVer()
                                            + "-"
                                            + h.getCtrl()
                                            + "-"
                                            + h.getAction()
                                            + " name="
                                            + handler.getClass().getSimpleName()
                                            + ">");
                        });

            for (int i = 0; i < TaskSend.TOTAL_LANE; i++) {
                Thread thrdOut =
                        new Thread(
                                new TaskSend(
                                        this,
                                        i,
                                        this.sendMsgQue,
                                        this.start,
                                        this.nodeMgr,
                                        this.selector),
                                "p2p-out-" + i);
                thrdOut.setPriority(Thread.NORM_PRIORITY);
                thrdOut.start();
            }

            for (int i = 0, m = Runtime.getRuntime().availableProcessors(); i < m; i++) {
                Thread t =
                        new Thread(
                                new TaskReceive(
                                        this.start,
                                        this.receiveMsgQue,
                                        this.handlers,
                                        this.showLog),
                                "p2p-worker-" + i);
                t.setPriority(Thread.NORM_PRIORITY);
                t.start();
            }

            if (upnpEnable)
                scheduledWorkers.scheduleWithFixedDelay(
                        new TaskUPnPManager(selfPort),
                        1,
                        PERIOD_UPNP_PORT_MAPPING,
                        TimeUnit.MILLISECONDS);

            if (showStatus)
                scheduledWorkers.scheduleWithFixedDelay(
                        new TaskStatus(
                                this.nodeMgr,
                                this.selfShortId,
                                this.sendMsgQue,
                                this.receiveMsgQue),
                        2,
                        PERIOD_SHOW_STATUS,
                        TimeUnit.MILLISECONDS);

            if (!syncSeedsOnly)
                scheduledWorkers.scheduleWithFixedDelay(
                        new TaskRequestActiveNodes(this),
                        5000,
                        PERIOD_REQUEST_ACTIVE_NODES,
                        TimeUnit.MILLISECONDS);

            Thread thrdClear =
                    new Thread(new TaskClear(this, this.nodeMgr, this.start), "p2p-clear");
            thrdClear.setPriority(Thread.NORM_PRIORITY);
            thrdClear.start();

            Thread thrdConn =
                    new Thread(
                            new TaskConnectPeers(
                                    this,
                                    this.start,
                                    this.nodeMgr,
                                    this.maxActiveNodes,
                                    this.selector,
                                    this.sendMsgQue,
                                    cachedReqHandshake1),
                            "p2p-conn");
            thrdConn.setPriority(Thread.NORM_PRIORITY);
            thrdConn.start();

        } catch (IOException e) {
            if (showLog) System.out.println("<p2p tcp-server-io-exception>");
        }
    }

    @Override
    public void register(final List<Handler> _cbs) {
        for (Handler _cb : _cbs) {
            Header h = _cb.getHeader();
            short ver = h.getVer();
            byte ctrl = h.getCtrl();
            if (Ver.filter(ver) != Ver.UNKNOWN && Ctrl.filter(ctrl) != Ctrl.UNKNOWN) {
                if (!versions.contains(ver)) {
                    versions.add(ver);
                }

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

        List<Short> supportedVersions = new ArrayList<>(versions);
        cachedReqHandshake1 =
                new ReqHandshake1(
                        selfNodeId,
                        selfNetId,
                        this.selfIp,
                        this.selfPort,
                        this.selfRevision.getBytes(),
                        supportedVersions);
    }

    @Override
    public void send(int _nodeIdHash, String _nodeIdShort, final Msg _msg) {
        sendMsgQue.add(new MsgOut(_nodeIdHash, _nodeIdShort, _msg, Dest.ACTIVE));
    }

    @Override
    public void shutdown() {
        start.set(false);
        scheduledWorkers.shutdownNow();

        for (List<Handler> hdrs : handlers.values()) {
            hdrs.forEach(Handler::shutDown);
        }

        nodeMgr.shutdown(this);
    }

    @Override
    public List<Short> versions() {
        return new ArrayList<>(versions);
    }

    /**
     * Remove an active node if exists.
     *
     * @param _nodeIdHash int
     * @param _reason String
     */
    void dropActive(int _nodeIdHash, String _reason) {
        nodeMgr.dropActive(_nodeIdHash, this, _reason);
    }

    @Override
    public void errCheck(int _nodeIdHash, String _displayId) {
        int cnt = (errCnt.get(_nodeIdHash) == null ? 1 : (errCnt.get(_nodeIdHash) + 1));
        if (cnt > this.errTolerance) {
            ban(_nodeIdHash);
            errCnt.put(_nodeIdHash, 0);
            if (showLog) {
                System.out.println(
                        "<p2p-ban node="
                                + (_displayId == null ? _nodeIdHash : _displayId)
                                + " err-count="
                                + cnt
                                + ">");
            }
        } else {
            errCnt.put(_nodeIdHash, cnt);
        }
    }

    private void ban(int nodeIdHashcode) {
        nodeMgr.ban(nodeIdHashcode);
        nodeMgr.dropActive(nodeIdHashcode, this, "ban");
    }



    @Override
    public INode getRandom() {
        return this.nodeMgr.getRandom();
    }

    @Override
    public Map<Integer, INode> getActiveNodes() {
        return new HashMap<>(this.nodeMgr.getActiveNodesMap());
    }

    @Override
    public int chainId() {
        return this.selfNetId;
    }

    @Override
    public int getSelfIdHash() {
        return this.selfNodeIdHash;
    }

    @Override
    public boolean isShowLog() {
        return this.showLog;
    }

    public int getTempNodesCount() {
        return this.nodeMgr.tempNodesSize();
    }
}
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
package org.aion.p2p.impl1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.*;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.impl.TaskRequestActiveNodes;
import org.aion.p2p.impl.TaskUPnPManager;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl.zero.msg.*;
import org.aion.p2p.impl1.tasks.MsgIn;
import org.aion.p2p.impl1.tasks.MsgOut;
import org.aion.p2p.impl1.tasks.TaskReceive;
import org.aion.p2p.impl1.tasks.TaskSend;
import org.aion.p2p.impl1.tasks.TaskClear;
import org.aion.p2p.impl1.tasks.TaskConnectPeers;
import org.aion.p2p.impl1.tasks.TaskInbound;
import org.aion.p2p.impl1.tasks.TaskStatus;
import org.apache.commons.collections4.map.LRUMap;

/** @author Chris p2p://{uuid}@{ip}:{port} */
public final class P2pMgr implements IP2pMgr {
    private static final int PERIOD_SHOW_STATUS = 10000;
    private static final int PERIOD_REQUEST_ACTIVE_NODES = 1000;
    private static final int PERIOD_UPNP_PORT_MAPPING = 3600000;
    private static final int TIMEOUT_MSG_READ = 10000;

    private final int maxTempNodes, maxActiveNodes, selfNetId, selfNodeIdHash, selfPort;
    private final boolean syncSeedsOnly, showStatus, showLog, upnpEnable;
    private final String selfRevision, selfShortId;
    private final byte[] selfNodeId, selfIp;
    private final INodeMgr nodeMgr;
    private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();
    private final Set<Short> versions = new HashSet<>();
    private final Map<Integer, Integer> errCnt = Collections.synchronizedMap(new LRUMap<>(128));

    private ServerSocketChannel tcpServer;
    private Selector selector;
    private ScheduledExecutorService scheduledWorkers;
    private int errTolerance;
    private BlockingQueue<MsgOut> sendMsgQue = new LinkedBlockingQueue<>();
    private BlockingQueue<MsgIn> receiveMsgQue = new LinkedBlockingQueue<>();
    private AtomicBoolean start = new AtomicBoolean(true);

    private static ReqHandshake1 cachedReqHandshake1;
    private static ResHandshake1 cachedResHandshake1;

    public enum Dest {
        INBOUND,
        OUTBOUND,
        ACTIVE
    }

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

            Thread thrdIn = new Thread(getInboundInstance(), "p2p-in");
            thrdIn.setPriority(Thread.NORM_PRIORITY);
            thrdIn.start();

            if (showLog) {
                this.handlers.forEach(
                    (route, callbacks) -> {
                        Handler handler = callbacks.get(0);
                        Header h = handler.getHeader();
                        System.out.println(
                            getRouteMsg(route, h.getVer(), h.getCtrl(), h.getAction(),
                                handler.getClass().getSimpleName()));
                    });
            }

            for (int i = 0; i < TaskSend.TOTAL_LANE; i++) {
                Thread thrdOut = new Thread(getSendInstance(i), "p2p-out-" + i);
                thrdOut.setPriority(Thread.NORM_PRIORITY);
                thrdOut.start();
            }

            for (int i = 0, m = Runtime.getRuntime().availableProcessors(); i < m; i++) {
                Thread t = new Thread(getReceiveInstance(), "p2p-worker-" + i);
                t.setPriority(Thread.NORM_PRIORITY);
                t.start();
            }

            if (upnpEnable) {
                scheduledWorkers.scheduleWithFixedDelay(
                    new TaskUPnPManager(selfPort),
                    1,
                    PERIOD_UPNP_PORT_MAPPING,
                    TimeUnit.MILLISECONDS);
            }
            if (showStatus) {
                scheduledWorkers.scheduleWithFixedDelay(
                    getStatusInstance(),
                    2,
                    PERIOD_SHOW_STATUS,
                    TimeUnit.MILLISECONDS);
            }
            if (!syncSeedsOnly) {
                scheduledWorkers.scheduleWithFixedDelay(
                    new TaskRequestActiveNodes(this),
                    5000,
                    PERIOD_REQUEST_ACTIVE_NODES,
                    TimeUnit.MILLISECONDS);
            }
            Thread thrdClear = new Thread(getClearInstance(), "p2p-clear");
            thrdClear.setPriority(Thread.NORM_PRIORITY);
            thrdClear.start();

            Thread thrdConn = new Thread(getConnectPeersInstance(), "p2p-conn");
            thrdConn.setPriority(Thread.NORM_PRIORITY);
            thrdConn.start();
        } catch (SocketException e) {
            if (showLog) { System.out.println("<p2p tcp-server-socket-exception> " + e.getMessage()); }
        } catch (IOException e) {
            if (showLog) { System.out.println("<p2p tcp-server-io-exception>"); }
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

        List<Short> supportedVersions = new ArrayList<>(versions);
        cachedReqHandshake1 = getReqHandshake1Instance(supportedVersions);
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

    @Override
    public void errCheck(int _nodeIdHash, String _displayId) {
        int cnt = (errCnt.get(_nodeIdHash) == null ? 1 : (errCnt.get(_nodeIdHash) + 1));
        if (cnt > this.errTolerance) {
            ban(_nodeIdHash);
            errCnt.put(_nodeIdHash, 0);
            if (showLog) {
                System.out.println(getBanNodeMsg(_displayId, _nodeIdHash, cnt));
            }
        } else {
            errCnt.put(_nodeIdHash, cnt);
        }
    }

    /** @param _sc SocketChannel */
    public void closeSocket(final SocketChannel _sc, String _reason) {
        if (showLog) { System.out.println("<p2p close-socket reason=" + _reason + ">"); }

        try {
            SelectionKey sk = _sc.keyFor(selector);
            _sc.close();
            if (sk != null) { sk.cancel(); }
        } catch (IOException e) {
            if (showLog) { System.out.println("<p2p close-socket-io-exception>"); }
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
        nodeMgr.dropActive(_nodeIdHash, this, _reason);
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
            boolean notActive = !nodeMgr.hasActiveNode(_node.getIdHash());
            boolean notOutbound = !nodeMgr.getOutboundNodes().containsKey(_node.getIdHash());
            return notSelfId && notSameIpOrPort && notActive && notOutbound;
        } else return false;
    }

    /** @param _channel SocketChannel TODO: check option */
    @Override
    public void configChannel(final SocketChannel _channel) throws IOException {
        _channel.configureBlocking(false);
        _channel.socket().setSoTimeout(TIMEOUT_MSG_READ);

        // set buffer to 256k.
        _channel.socket().setReceiveBufferSize(P2pConstant.RECV_BUFFER_SIZE);
        _channel.socket().setSendBufferSize(P2pConstant.SEND_BUFFER_SIZE);
        // _channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        // _channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        // _channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    }

    private void ban(int nodeIdHashcode) {
        nodeMgr.ban(nodeIdHashcode);
        nodeMgr.dropActive(nodeIdHashcode, this, "ban");
    }

    // <------------------------ getter methods below --------------------------->

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

    @Override
    public int getMaxActiveNodes() {
        return this.maxActiveNodes;
    }

    @Override
    public int getMaxTempNodes() {
        return this.maxTempNodes;
    }

    @Override
    public int getSelfNetId() {
        return this.selfNetId;
    }

    @Override
    public boolean isSyncSeedsOnly() {
        return this.syncSeedsOnly;
    }

    // <---------------------- message and Runnable getters below ------------------------->

    private String getRouteMsg(Integer route, short ver, byte ctrl, byte act, String name) {
        return "<p2p-handler route=" + route + " v-c-a=" + ver + "-" + ctrl + "-" + act + " name="
            + name + ">";
    }

    private String getBanNodeMsg(String id, int hash, int cnt) {
        return "<p2p-ban node=" + (id == null ? hash : id) + " err-count=" + cnt + ">";
    }

    private TaskInbound getInboundInstance() {
        return new TaskInbound(
            this,
            this.selector,
            this.start,
            this.nodeMgr,
            this.tcpServer,
            this.handlers,
            this.sendMsgQue,
            cachedResHandshake1,
            this.receiveMsgQue);
    }

    private TaskSend getSendInstance(int i) {
        return new TaskSend(
            this,
            i,
            this.sendMsgQue,
            this.start,
            this.nodeMgr,
            this.selector);
    }

    private TaskReceive getReceiveInstance() {
        return new TaskReceive(
            this.start,
            this.receiveMsgQue,
            this.handlers,
            this.showLog);
    }

    private TaskStatus getStatusInstance() {
        return new TaskStatus(
            this.nodeMgr,
            this.selfShortId,
            this.sendMsgQue,
            this.receiveMsgQue);
    }

    private TaskClear getClearInstance() {
        return new TaskClear(this, this.nodeMgr, this.start);
    }

    private TaskConnectPeers getConnectPeersInstance() {
        return new TaskConnectPeers(
            this,
            this.start,
            this.nodeMgr,
            this.maxActiveNodes,
            this.selector,
            this.sendMsgQue,
            cachedReqHandshake1);
    }

    private ReqHandshake1 getReqHandshake1Instance(List<Short> versions) {
        return new ReqHandshake1(
            selfNodeId,
            selfNetId,
            this.selfIp,
            this.selfPort,
            this.selfRevision.getBytes(),
            versions);
    }
}

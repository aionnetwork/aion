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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.Header;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.TaskRequestActiveNodes;
import org.aion.p2p.impl.TaskUPnPManager;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl.zero.msg.ReqHandshake1;
import org.aion.p2p.impl.zero.msg.ResHandshake1;
import org.aion.p2p.impl1.tasks.MsgIn;
import org.aion.p2p.impl1.tasks.MsgOut;
import org.aion.p2p.impl1.tasks.TaskClear;
import org.aion.p2p.impl1.tasks.TaskConnectPeers;
import org.aion.p2p.impl1.tasks.TaskInbound;
import org.aion.p2p.impl1.tasks.TaskReceive;
import org.aion.p2p.impl1.tasks.TaskSend;
import org.aion.p2p.impl1.tasks.TaskStatus;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

/**
 * @author Chris p2p://{uuid}@{ip}:{port}
 */
public final class P2pMgr implements IP2pMgr {

    private static final int PERIOD_SHOW_STATUS = 10000;
    private static final int PERIOD_REQUEST_ACTIVE_NODES = 1000;
    private static final int PERIOD_UPNP_PORT_MAPPING = 3600000;
    private static final int TIMEOUT_MSG_READ = 10000;

    // TODO: need refactor by passing the parameter in the later version to P2pMgr.
    public static int txBroadCastRoute =
        (Ctrl.SYNC << 8) + 6; // ((Ver.V0 << 16) + (Ctrl.SYNC << 8) + 6);

    public static final Logger p2pLOG = AionLoggerFactory.getLogger(LogEnum.P2P.name());

    private int maxTempNodes, maxActiveNodes, selfNetId, selfNodeIdHash, selfPort;
    private boolean syncSeedsOnly, upnpEnable;
    private String selfRevision, selfShortId;
    private byte[] selfNodeId, selfIp;
    private INodeMgr nodeMgr;
    private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();
    private final Set<Short> versions = new HashSet<>();
    private final Map<Integer, Integer> errCnt = Collections.synchronizedMap(new LRUMap<>(128));
    private final AtomicBoolean start = new AtomicBoolean(true);

    private ServerSocketChannel tcpServer;
    private Selector selector;
    private ScheduledExecutorService scheduledWorkers;
    private int errTolerance;
    private BlockingQueue<MsgOut> sendMsgQue = new LinkedBlockingQueue<>();
    private BlockingQueue<MsgIn> receiveMsgQue = new LinkedBlockingQueue<>();

    private static ReqHandshake1 cachedReqHandshake1;
    private static ResHandshake1 cachedResHandshake1;

    private String outGoingIP = "0.0.0.0";

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
     */
    public P2pMgr(
        final int _netId,
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
        this.syncSeedsOnly = _bootlistSyncOnly;
        this.errTolerance = _errorTolerance;

        nodeMgr = new NodeMgr(this, _maxActiveNodes, _maxTempNodes, p2pLOG);

        outGoingIP = checkOutGoingIP();

        for (String _bootNode : _bootNodes) {
            Node node = Node.parseP2p(_bootNode);
            if (validateNode(node)) {
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

            if (p2pLOG.isDebugEnabled()) {
                this.handlers.forEach(
                    (route, callbacks) -> {
                        Handler handler = callbacks.get(0);
                        Header h = handler.getHeader();
                        p2pLOG.debug("handler route={} v-c-a={}-{}-{} name={}", route, h.getVer(),
                            h.getCtrl(), h.getAction(), handler.getClass().getSimpleName());
                    });
            }

            int pNum = Runtime.getRuntime().availableProcessors();

            for (int i = 0; i < (pNum << 1); i++) {
                Thread thrdOut = new Thread(getSendInstance(i), "p2p-out-" + i);
                thrdOut.setPriority(Thread.NORM_PRIORITY);
                thrdOut.start();
            }

            for (int i = 0; i < pNum; i++) {
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

            if (p2pLOG.isInfoEnabled()) {
                scheduledWorkers.scheduleWithFixedDelay(
                    getStatusInstance(),
                    2,
                    PERIOD_SHOW_STATUS,
                    TimeUnit.MILLISECONDS);
            }

            if (!syncSeedsOnly) {
                scheduledWorkers.scheduleWithFixedDelay(
                    new TaskRequestActiveNodes(this, p2pLOG),
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
            p2pLOG.error("tcp-server-socket-exception {}", e.getMessage());
        } catch (IOException e) {
            p2pLOG.error("tcp-server-io-exception {}", e.getMessage());
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
                p2pLOG.debug("ban node={} err-count={}",
                    (_displayId == null ? _nodeIdHash : _displayId), cnt);
            }
        } else {
            errCnt.put(_nodeIdHash, cnt);
        }
    }

    /**
     * @param _sc SocketChannel
     */
    public void closeSocket(final SocketChannel _sc, String _reason) {

        if (p2pLOG.isDebugEnabled()) {
            p2pLOG.debug("close-socket reason={}", _reason);
        }

        if (_sc != null) {
            SelectionKey sk = _sc.keyFor(selector);
            if (sk != null) {
                sk.cancel();
            }

            try {
                _sc.close();
            } catch (IOException e) {
                p2pLOG.info("close-socket-io-exception, {}", e.getMessage());
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

    /**
     * @param _channel SocketChannel TODO: check option
     */
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

    @Override
    public int chainId() {
        return this.selfNetId;
    }

    @Override
    public int getSelfIdHash() {
        return this.selfNodeIdHash;
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
    public String getOutGoingIP() {
        return outGoingIP;
    }

    @Override
    public boolean isSyncSeedsOnly() {
        return this.syncSeedsOnly;
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
            this.handlers);
    }

    private TaskStatus getStatusInstance() {
        return new TaskStatus(
            this.nodeMgr,
            this.selfShortId,
            this.sendMsgQue,
            this.receiveMsgQue);
    }

    private TaskClear getClearInstance() {
        return new TaskClear(this.nodeMgr, this.start);
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

    private String checkOutGoingIP() {
        StringBuilder output = new StringBuilder();
        Runtime rt = Runtime.getRuntime();
        Process pr;
        try {
            pr = rt.exec("wget -qO- icanhazip.com");
            pr.waitFor();
            BufferedReader reader =
                new BufferedReader(new InputStreamReader(pr.getInputStream()));

            String line;
            while ((line = reader.readLine())!= null) {
                output.append(line);
            }
        } catch (IOException | InterruptedException e) {
            p2pLOG.error("get outGoingIP exception {}", e.toString());
        }

        return output.toString();
    }
}

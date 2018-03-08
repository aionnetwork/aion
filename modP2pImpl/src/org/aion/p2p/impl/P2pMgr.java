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

package org.aion.p2p.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.aion.p2p.*;
import org.aion.p2p.impl.msg.ReqHandshake;
import org.aion.p2p.impl.msg.ResActiveNodes;
import org.aion.p2p.impl.msg.ResHandshake;

/**
 * @author Chris p2p://{uuid}@{ip}:{port}
 * TODO: 1) simplify id bytest to int, ip bytest to str 2) upnp protocal 3) framing
 */
public final class P2pMgr implements IP2pMgr {

    private final static int PERIOD_SHOW_STATUS = 10000;
    private final static int PERIOD_REQUEST_ACTIVE_NODES = 1000;
    private final static int PERIOD_CONNECT_OUTBOUND = 1000;
    private final static int PERIOD_CLEAR = 20000;

    private final static int TIMEOUT_OUTBOUND_CONNECT = 10000;
    private final static int TIMEOUT_OUTBOUND_NODES = 10000;
    private final static int PERIOD_UPNP_PORT_MAPPING = 3600000;

    private final static int TIMEOUT_MSG_READ = 10000;

    private final int maxTempNodes;
    private final int maxActiveNodes;

    private final boolean showStatus;
    final boolean showLog;
    private final int selfNodeIdHash;
    private String selfShortId;
    private final byte[] selfIp;
    private final int selfPort;
    private final boolean upnpEnable;

    private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();

    private NodeMgr nodeMgr = new NodeMgr();
    private ServerSocketChannel tcpServer;
    private Selector selector;
    private Lock selectorLock = new ReentrantLock();

    private ScheduledThreadPoolExecutor scheduledWorkers;
    private ExecutorService workers;
    private AtomicBoolean start = new AtomicBoolean(true);

    private static ReqHandshake cachedReqHandshake;

    private final class TaskInbound implements Runnable {
        @Override
        public void run() {
            while (start.get()) {

                int num;
                try {
                    num = selector.select(1);
                } catch (IOException e) {
                    if (showLog)
                        System.out.println("<p2p inbound-select-io-exception>");
                    continue;
                }

                if (num == 0)
                    continue;

                selectorLock.lock();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {

                    final SelectionKey sk = keys.next();
                    keys.remove();

                    if (!sk.isValid())
                        continue;

                    if (sk.isAcceptable())
                        accept();

                    if (sk.isReadable())
                        try {
                            read(sk);
                        } catch (IOException | NullPointerException e) {
                            if (showLog) {
                                System.out.println("<p2p read-msg-io-exception>");
                            }
                            closeSocket((SocketChannel) sk.channel());
                        }
                }
                selectorLock.unlock();
            }
            if (showLog)
                System.out.println("<p2p-pi shutdown>");
        }
    }

    private final class TaskStatus implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("p2p-ts");
            nodeMgr.dumpNodeInfo(selfShortId);
            nodeMgr.dumpAllNodeInfo();
        }
    }

    private final class TaskConnectPeers implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("p2p-tcp");
            while (start.get()) {
                try {
                    Thread.sleep(PERIOD_CONNECT_OUTBOUND);
                } catch (InterruptedException e) {
                    if (showLog)
                        System.out.println("<p2p-tcp interrupted>");
                }

                if (nodeMgr.activeNodesSize() >= maxActiveNodes) {
                    if (showLog)
                        System.out.println("<p2p-tcp-connect-peer pass max-active-nodes>");
                    return;
                }

                Node node;
                try {
                    node = nodeMgr.tempNodesTake();
                    if (node.getIfFromBootList())
                        nodeMgr.tempNodesAdd(node);
                    if (node.peerMetric.shouldNotConn()) {
                        continue;
                    }
                } catch (InterruptedException e) {
                    if (showLog)
                        System.out.println("<p2p outbound-connect-io-exception>");
                    return;
                }
                int nodeIdHash = node.getIdHash();
                if (!nodeMgr.getOutboundNodes().containsKey(nodeIdHash) && !nodeMgr.hasActiveNode(nodeIdHash)) {
                    int _port = node.getPort();
                    try {
                        SocketChannel channel = SocketChannel.open();
                        if (showLog)
                            System.out.println("<p2p try-connect-" + node.getIpStr() + ">");
                        channel.socket().connect(
                                new InetSocketAddress(node.getIpStr(), _port),
                                TIMEOUT_OUTBOUND_CONNECT
                        );
                        configChannel(channel);

                        if (channel.finishConnect() && channel.isConnected()) {
                            selectorLock.lock();
                            SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
                            ChannelBuffer rb = new ChannelBuffer();
                            rb.nodeIdHash = nodeIdHash;
                            sk.attach(rb);

                            node.setChannel(channel);
                            node.setPortConnected(channel.socket().getLocalPort());

                            addOutboundNode(node);
                            selectorLock.unlock();
                            workers.submit(new TaskWrite(node.getIdShort(), channel, cachedReqHandshake, rb));

                            if (showLog)
                                System.out.println("<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port
                                        + " result=success>");

                            node.peerMetric.decFailedCount();

                        } else {
                            channel.close();
                            node.peerMetric.incFailedCount();
                        }
                    } catch (IOException e) {
                        if (showLog)
                            System.out.println("<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port
                                    + " result=failed>");
                        node.peerMetric.incFailedCount();
                    }
                }
            }
        }
    }

    private final class TaskClear implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("p2p-clr");
            while (start.get()) {
                try {
                    Thread.sleep(PERIOD_CLEAR);

                    selectorLock.lock();
                    nodeMgr.rmTimeOutInbound(P2pMgr.this);
                    selectorLock.unlock();

                    // clean up temp nodes list if metric failed.
                    nodeMgr.rmMetricFailedNodes();

                    Iterator outboundIt = nodeMgr.getOutboundNodes().keySet().iterator();
                    while (outboundIt.hasNext()) {

                        Object obj = outboundIt.next();

                        if (obj == null)
                            continue;

                        int nodeIdHash = (int) obj;
                        Node node = nodeMgr.getOutboundNodes().get(nodeIdHash);

                        if (node == null)
                            continue;

                        if (System.currentTimeMillis() - node.getTimestamp() > TIMEOUT_OUTBOUND_NODES) {
                            selectorLock.lock();
                            closeSocket(node.getChannel());
                            selectorLock.unlock();
                            outboundIt.remove();

                            if (showLog)
                                System.out.println("<p2p-clear outbound-timeout>");
                        }
                    }

                    selectorLock.lock();
                    nodeMgr.rmTimeOutActives(P2pMgr.this);
                    selectorLock.unlock();

                } catch (Exception e) {
                }
            }
        }
    }

    private final class TaskWrite implements Runnable {

        private String nodeShortId;
        private SocketChannel sc;
        private Msg msg;
        private ChannelBuffer channelBuffer;

        TaskWrite(String _nodeShortId, final SocketChannel _sc, final Msg _msg, final ChannelBuffer _cb) {
            this.nodeShortId = _nodeShortId;
            this.sc = _sc;
            this.msg = _msg;
            this.channelBuffer = _cb;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("p2p-write");

            // NOTE: the following logic may cause message loss
            if (this.channelBuffer.onWrite.compareAndSet(false, true)) {
                /*
                 * @warning header set len (body len) before header encode
                 */
                byte[] bodyBytes = msg.encode();
                int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
                Header h = msg.getHeader();
                h.setLen(bodyLen);
                byte[] headerBytes = h.encode();

                //System.out.println("write " + h.getCtrl() + "-" + h.getAction());
                ByteBuffer buf = ByteBuffer.allocate(headerBytes.length + bodyLen);
                buf.put(headerBytes);
                if (bodyBytes != null)
                    buf.put(bodyBytes);
                buf.flip();

                try {
                    while (buf.hasRemaining()) {
                        sc.write(buf);
                    }
                } catch (IOException e) {
                    if (showLog) {
                        System.out.println("<p2p write-msg-io-exception node=" + this.nodeShortId + ">");
                    }
                } finally {
                    this.channelBuffer.onWrite.set(false);
                    try {

                        Msg msg = this.channelBuffer.msgs.poll(1, TimeUnit.MILLISECONDS);

                        if (msg != null) {
                            //System.out.println("write " + h.getCtrl() + "-" + h.getAction());
                            workers.submit(new TaskWrite(nodeShortId, sc, msg, channelBuffer));
                        }
                    } catch (InterruptedException e) {
                        if(showLog)
                            e.printStackTrace();
                    }
                }
            } else {
                try {
                    this.channelBuffer.msgs.put(msg);
                } catch (InterruptedException e) {
                    if(showLog)
                        e.printStackTrace();
                }
            }

        }
    }

    /**
     * @param _nodeId         byte[36]
     * @param _ip             String
     * @param _port           int
     * @param _bootNodes      String[]
     * @param _upnpEnable     boolean
     * @param _maxTempNodes   int
     * @param _maxActiveNodes int
     * @param _showStatus     boolean
     * @param _showLog        boolean
     */
    public P2pMgr(String _nodeId, String _ip, int _port, final String[] _bootNodes, boolean _upnpEnable,
                  int _maxTempNodes, int _maxActiveNodes, boolean _showStatus, boolean _showLog) {
        byte[] selfNodeId = _nodeId.getBytes();
        this.selfNodeIdHash = Arrays.hashCode(selfNodeId);
        this.selfShortId = new String(Arrays.copyOfRange(selfNodeId, 0, 6));
        int selfNetId = 0;
        this.selfIp = Node.ipStrToBytes(_ip);
        this.selfPort = _port;
        this.upnpEnable = _upnpEnable;
        this.maxTempNodes = _maxTempNodes;
        this.maxActiveNodes = _maxActiveNodes;
        this.showStatus = _showStatus;
        this.showLog = _showLog;

        for (String _bootNode : _bootNodes) {
            Node node = Node.parseP2p(_bootNode);
            if (node != null && validateNode(node)) {
                nodeMgr.tempNodesAdd(node);
                nodeMgr.seedIpAdd(node.getIpStr());
            }
        }

        cachedReqHandshake = new ReqHandshake(selfNodeId, selfNetId, this.selfIp, this.selfPort);
    }

    /**
     * @param _node Node
     * @return boolean
     */
    private boolean validateNode(final Node _node) {
        boolean notNull = _node != null;
        boolean notSelfId = _node.getIdHash() != this.selfNodeIdHash;
        boolean notSameIpOrPort = !(Arrays.equals(selfIp, _node.getIp()) && selfPort == _node.getPort());
        boolean notActive = !nodeMgr.hasActiveNode(_node.getIdHash());
        boolean notOutbound = !nodeMgr.getOutboundNodes().containsKey(_node.getIdHash());
        return notNull && notSelfId && notSameIpOrPort && notActive && notOutbound;
    }

    /**
     * @param _channel SocketChannel TODO: check option
     */
    private void configChannel(final SocketChannel _channel) throws IOException {
        _channel.configureBlocking(false);
        _channel.socket().setSoTimeout(TIMEOUT_MSG_READ);
        _channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        _channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        _channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    }

    /**
     * @param _sc SocketChannel
     */
    void closeSocket(final SocketChannel _sc) {
        try {
            SelectionKey sk = _sc.keyFor(selector);
            _sc.close();
            if (sk != null)
                sk.cancel();
        } catch (IOException e) {
            if (showLog)
                System.out.println("<p2p close-socket-io-exception>");
        }
    }

    /**
     * @param _node Node 1) leave outbound timestamp check to outbound connections
     *              process 2) add if no such connection or drop new if connection
     *              to target exists
     */
    private void addOutboundNode(final Node _node) {
        Node previous = nodeMgr.getOutboundNodes().putIfAbsent(_node.getIdHash(), _node);
        if (previous != null)
            closeSocket(_node.getChannel());
    }

    private void accept() {
        SocketChannel channel;
        try {
            channel = tcpServer.accept();
            configChannel(channel);

            SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
            sk.attach(new ChannelBuffer());

            String ip = channel.socket().getInetAddress().getHostAddress();
            int port = channel.socket().getPort();

            // Node node = new Node(false, ip);
            Node node = nodeMgr.allocNode(ip, 0, port);

            node.setChannel(channel);
            nodeMgr.inboundNodeAdd(node);
        } catch (IOException e) {
            if (showLog)
                System.out.println("<p2p inbound-accept-io-exception>");
            return;
        }

        if (showLog)
            System.out.println("<p2p new-connection>");
    }

    /**
     * @param _sk SelectionKey
     * @throws IOException IOException
     */
    private void read(final SelectionKey _sk) throws IOException {

        if (_sk.attachment() == null) {
            throw new IOException("attachment is null");
        }
        ChannelBuffer rb = (ChannelBuffer) _sk.attachment();

        // read header
        if (!rb.isHeaderCompleted()) {
            readHeader((SocketChannel) _sk.channel(), rb);
        }

//        if(rb.isHeaderCompleted() && !handlers.containsKey(rb.header.getRoute())){
//            // TODO: Test
//            return;
//        }

        // read body
        if (rb.isHeaderCompleted() && !rb.isBodyCompleted()) {
            readBody((SocketChannel) _sk.channel(), rb);
        }

        if (!rb.isBodyCompleted())
            return;

        Header h = rb.header;

        byte[] bodyBytes = Arrays.copyOf(rb.body, rb.body.length);

        rb.refreshHeader();
        rb.refreshBody();

        byte ctrl = h.getCtrl();
        byte act = h.getAction();

        //System.out.println("read " + ctrl + "-" + act);

        switch (ctrl) {
            case Ctrl.NET:
                handleP2pMsg(_sk, act, bodyBytes);
                break;
            default:
                int route = h.getRoute();
                if (rb.nodeIdHash != 0 || handlers.containsKey(route))
                    handleKernelMsg(rb.nodeIdHash, route, bodyBytes);
                break;
        }
    }

    /**
     * @param _sc SocketChannel
     * @throws IOException IOException
     */
    private void readHeader(final SocketChannel _sc, final ChannelBuffer _cb) throws IOException {

        int ret;
        while ((ret = _sc.read(_cb.headerBuf)) > 0) {
        }

        if (!_cb.headerBuf.hasRemaining()) {
            _cb.header = Header.decode(_cb.headerBuf.array());
        } else {
            if (ret == -1) {
                throw new IOException("read-header-eof");
            }
        }
    }

    /**
     * @param _sc SocketChannel
     * @throws IOException IOException
     */
    private void readBody(final SocketChannel _sc, final ChannelBuffer _cb) throws IOException {

        if (_cb.bodyBuf == null)
            _cb.bodyBuf = ByteBuffer.allocate(_cb.header.getLen());

        int ret;
        while ((ret = _sc.read(_cb.bodyBuf)) > 0) {
        }

        if (!_cb.bodyBuf.hasRemaining()) {
            _cb.body = _cb.bodyBuf.array();
        } else {
            if (ret == -1) {
                throw new IOException("read-body-eof");
            }
        }
    }

    /**
     * @param _sk       SelectionKey
     * @param _act      ACT
     * @param _msgBytes byte[]
     */
    private void handleP2pMsg(final SelectionKey _sk, byte _act, final byte[] _msgBytes) {
        ChannelBuffer rb = (ChannelBuffer) _sk.attachment();
        //System.out.println("I am handle p2p msg !!!!!");

        switch (_act) {

            case Act.REQ_HANDSHAKE:
                ReqHandshake reqHandshake = ReqHandshake.decode(_msgBytes);
                if (reqHandshake != null) {
                    Node node = nodeMgr.getInboundNode(_sk.channel().hashCode());
                    if (node != null) {
                        rb.nodeIdHash = Arrays.hashCode(reqHandshake.getNodeId());
                        node.setId(reqHandshake.getNodeId());
                        node.setNetId(reqHandshake.getNetId());
                        node.setPort(reqHandshake.getPort());

                        nodeMgr.moveInboundToActive(node.getChannel().hashCode(), this);

                        workers.submit(new TaskWrite(node.getIdShort(), node.getChannel(), new ResHandshake(true), rb));
                    }
                }
                break;

            case Act.RES_HANDSHAKE:
                ResHandshake resHandshake = ResHandshake.decode(_msgBytes);
                if (resHandshake != null && rb.nodeIdHash != 0 && resHandshake.getSuccess()) {
                    Node node = nodeMgr.getOutboundNodes().get(rb.nodeIdHash);
                    if (node != null) {
                        node.refreshTimestamp();
                        nodeMgr.moveOutboundToActive(node.getIdHash(), node.getIdShort(), this);
                    }
                }
                break;

            case Act.REQ_ACTIVE_NODES:
                if (rb.nodeIdHash != 0) {
                    Node node = nodeMgr.getActiveNode(rb.nodeIdHash);
                    if (node != null)
                        workers.submit(new TaskWrite(node.getIdShort(), node.getChannel(),
                                new ResActiveNodes(nodeMgr.getActiveNodesList()), rb));
                }
                break;

            case Act.RES_ACTIVE_NODES:
                if (rb.nodeIdHash != 0) {
                    Node node = nodeMgr.getActiveNode(rb.nodeIdHash);
                    if (node != null) {
                        node.refreshTimestamp();
                        ResActiveNodes resActiveNodes = ResActiveNodes.decode(_msgBytes);
                        if (resActiveNodes != null) {
                            List<Node> incomingNodes = resActiveNodes.getNodes();
                            for (Node incomingNode : incomingNodes) {
                                if (nodeMgr.tempNodesSize() >= this.maxTempNodes)
                                    return;
                                if (validateNode(incomingNode))
                                    nodeMgr.tempNodesAdd(incomingNode);
                            }
                        }
                    }
                }
                break;
            default:
                if (showLog)
                    System.out.println("<p2p unknown-route act=" + _act + ">");
                break;
        }
    }

    /**
     * @param _nodeIdHash int
     * @param _route      int
     * @param _msgBytes   byte[]
     */
    private void handleKernelMsg(int _nodeIdHash, int _route, final byte[] _msgBytes) {
        Node node = nodeMgr.getActiveNode(_nodeIdHash);
        if (node != null) {
            List<Handler> hs = handlers.get(_route);
            if (hs == null)
                return;
            for (Handler hlr : hs) {
                if (hlr == null)
                    continue;
                node.refreshTimestamp();
                //System.out.println("I am handle kernel msg !!!!! " + hlr.getHeader().getCtrl() + "-" + hlr.getHeader().getAction() + "-" + hlr.getHeader().getLen());
                workers.submit(() -> hlr.receive(node.getIdHash(), node.getIdShort(), _msgBytes));
            }
        }
    }


    public NodeMgr getNodeMgr() {
        return this.nodeMgr;
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();

            scheduledWorkers = new ScheduledThreadPoolExecutor(1);
            workers = Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors() * 2, 16));

            tcpServer = ServerSocketChannel.open();
            tcpServer.configureBlocking(false);
            tcpServer.socket().setReuseAddress(true);
            tcpServer.socket().bind(new InetSocketAddress(Node.ipBytesToStr(selfIp), selfPort));
            tcpServer.register(selector, SelectionKey.OP_ACCEPT);

            Thread boss = new Thread(new TaskInbound(), "p2p-pi");
            boss.setPriority(Thread.MAX_PRIORITY);
            boss.start();

            if (upnpEnable)
                scheduledWorkers.scheduleWithFixedDelay(new TaskUPnPManager(selfPort), 1, PERIOD_UPNP_PORT_MAPPING,
                        TimeUnit.MILLISECONDS);

            if (showStatus)
                scheduledWorkers.scheduleWithFixedDelay(new TaskStatus(), 2, PERIOD_SHOW_STATUS, TimeUnit.MILLISECONDS);
            scheduledWorkers.scheduleWithFixedDelay(new TaskRequestActiveNodes(this), 5000, PERIOD_REQUEST_ACTIVE_NODES,
                    TimeUnit.MILLISECONDS);

            workers.submit(new TaskClear());
            workers.submit(new TaskConnectPeers());

        } catch (IOException e) {
            if (showLog)
                System.out.println("<p2p tcp-server-io-exception>");
        }
    }

    @Override
    public INode getRandom(){
        return nodeMgr.getRandom();
    }

    public INode getRandom(NodeRandPolicy nrp, long bbn) {
        switch (nrp) {
            case RND:
                break;
            case REALTIME:

                // only fetch node with blocknumber > ( highest -128 )
                return nodeMgr.getRandomRealtime(bbn);

            case SYNC:
                break;
        }

        return nodeMgr.getRandom();
    }

    @Override
    public Map<Integer, INode> getActiveNodes() {
        return this.nodeMgr.getActiveNodesMap();
    }

    /**
     * for test
     */
    void clearTempNodes() {
        this.nodeMgr.clearTempNodes();
    }

    int getTempNodesCount() {
        return nodeMgr.tempNodesSize();
    }

    @Override
    public void register(final List<Handler> _cbs) {
        for (Handler _cb : _cbs) {
            Header h = _cb.getHeader();
            short ver = h.getVer();
            byte ctrl = h.getCtrl();
            if (Ver.filter(ver) != Ver.UNKNOWN && Ctrl.filter(ctrl) != Ctrl.UNKNOWN) {
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
    }

    @Override
    public void send(int _nodeIdHashcode, final Msg _msg) {
        Node node = this.nodeMgr.getActiveNode(_nodeIdHashcode);
        if (node != null) {
            SelectionKey sk = node.getChannel().keyFor(selector);

            if (sk != null) {
                Object attachment = sk.attachment();
                if (attachment != null)
                    workers.submit(
                            new TaskWrite(node.getIdShort(), node.getChannel(), _msg, (ChannelBuffer) attachment));
            }
        }
    }

    @Override
    public void shutdown() {
        start.set(false);
        scheduledWorkers.shutdownNow();
        nodeMgr.shutdown(this);
        workers.shutdownNow();
    }

    @Override
    public String version() {
        return "0.1.0";
    }

}

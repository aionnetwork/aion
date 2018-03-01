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
import java.net.Socket;
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
import java.util.stream.Collectors;

import org.aion.p2p.*;
import org.aion.p2p.impl.msg.ReqHandshake;
import org.aion.p2p.impl.msg.ResActiveNodes;
import org.aion.p2p.impl.msg.ResHandshake;

/**
 * @author Chris
 * aion0 p2p implementation p2p://{uuid}@{ip}:{port} eg:
 * p2p://3e2cab6a-09dd-4771-b28d-6aa674009796@127.0.0.1:30303 TODO: 1)
 * simplify id bytest to int, ip bytest to str 2) upnp protocal 3)
 * framing
 */
public final class P2pMgr implements IP2pMgr {

    private final static int PERIOD_SHOW_STATUS = 10000;
    private final static int PERIOD_REQUEST_ACTIVE_NODES = 10000;
    private final static int PERIOD_CONNECT_OUTBOUND = 2000;
    private final static int PERIOD_CLEAR = 20000;

    private final static int TIMEOUT_OUTBOUND_CONNECT = 10000;
    private final static int TIMEOUT_OUTBOUND_NODES = 10000;
    private final static int TIMEOUT_INBOUND_NODES = 10000;
    private final static int TIMEOUT_ACTIVE_NODES = 30000;
    private final static int TIMEOUT_MSG_READ = 10000;

    private final static int MAX_BODY_BYTES = 1024 * 1024 * 50;
    private final int maxTempNodes;
    private final int maxActiveNodes;

    private final boolean showStatus;
    private final boolean showLog;
    private final int selfNodeIdHash;
    private String selfShortId;
    private final byte[] selfIp;
    private final int selfPort;
    private final boolean upnpEnable;

    private final BlockingQueue<Node> tempNodes = new LinkedBlockingQueue<>();
    private final Map<Integer, Node> outboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> inboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> activeNodes = new ConcurrentHashMap<>();
    private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();

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
                            e.printStackTrace();
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
            System.out.println("[p2p-status " + selfShortId + "]");
            System.out.println("[temp-nodes-size=" + tempNodes.size() + "]");
            //System.out.println("[inbound-nodes-size=" + inboundNodes.size() + "]");
            //System.out.println("[outbound-nodes-size=" + outboundNodes.size() + "]");
            System.out.println("[active-nodes(nodeIdHash)=[" + activeNodes.entrySet().stream()
                    .map((entry) -> "\n" + entry.getValue().getBestBlockNumber() + "-" + entry.getValue().getIdShort()
                            + "-" + entry.getValue().getIpStr() + (entry.getValue().getIfFromBootList() ? "-seed" : ""))
                    .collect(Collectors.joining(",")) + "]]");
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

                if(P2pMgr.this.activeNodes.size() >= P2pMgr.this.maxActiveNodes){
                    if (showLog)
                        System.out.println("<p2p-tcp-connect-peer pass max-active-nodes>");
                    return;
                }

                Node node;
                try {
                    node = tempNodes.take();
                    if (node.getIfFromBootList())
                        tempNodes.add(node);
                } catch (InterruptedException e) {
                    if (showLog)
                        System.out.println("<p2p outbound-connect-io-exception>");
                    return;
                }
                int nodeIdHash = node.getIdHash();
                if (!outboundNodes.containsKey(nodeIdHash) && !activeNodes.containsKey(nodeIdHash)) {

                    int _port = node.getPort();
                    try {
                        SocketChannel channel = SocketChannel.open();
                        if (showLog)
                            System.out.println("<p2p try-connect-" + node.getIpStr() + ">");
                        channel.socket().connect(new InetSocketAddress(node.getIpStr(), _port), TIMEOUT_OUTBOUND_CONNECT);
                        configChannel(channel);

                        if (channel.finishConnect() && channel.isConnected()) {
                            selectorLock.lock();
                            SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
                            ChannelBuffer rb = new ChannelBuffer();
                            rb.nodeIdHash = nodeIdHash;
                            sk.attach(rb);
                            node.setChannel(channel);
                            addOutboundNode(node);
                            selectorLock.unlock();
                            write(nodeIdHash, node.getIdShort(), channel, cachedReqHandshake);
                            if (showLog)
                                System.out.println("<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port
                                        + " result=success>");
                        } else {
                            channel.close();
                        }
                    } catch (IOException e) {
                        if (showLog)
                            System.out.println(
                                    "<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port + " result=failed>");
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

                    Iterator inboundIt = P2pMgr.this.inboundNodes.keySet().iterator();
                    while (inboundIt.hasNext()) {
                        int key = (int) inboundIt.next();
                        Node node = P2pMgr.this.inboundNodes.get(key);
                        if (System.currentTimeMillis() - node.getTimestamp() > TIMEOUT_INBOUND_NODES) {
                            selectorLock.lock();
                            closeSocket(node.getChannel());
                            selectorLock.unlock();
                            inboundIt.remove();

                            if (showLog)
                                System.out.println("<p2p-clear inbound-timeout>");
                        }
                    }

                    Iterator outboundIt = P2pMgr.this.outboundNodes.keySet().iterator();
                    while (outboundIt.hasNext()) {

                        Object obj = outboundIt.next();

                        if (obj == null)
                            continue;

                        int nodeIdHash = (int)obj;
                        Node node = P2pMgr.this.outboundNodes.get(nodeIdHash);

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

                    Iterator activeIt = P2pMgr.this.activeNodes.keySet().iterator();
                    while (activeIt.hasNext()) {
                        int key = (int) activeIt.next();
                        Node node = P2pMgr.this.activeNodes.get(key);
                        if (System.currentTimeMillis() - node.getTimestamp() > TIMEOUT_ACTIVE_NODES) {
                            selectorLock.lock();
                            closeSocket(node.getChannel());
                            selectorLock.unlock();
                            activeIt.remove();

                            if (showLog)
                                System.out.println("<p2p-clear active-timeout>");
                        }
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                }
            }
        }
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
     *
     */
    public P2pMgr(
            String _nodeId,
            String _ip,
            int _port,
            final String[] _bootNodes,
            boolean _upnpEnable,
            int _maxTempNodes,
            int _maxActiveNodes,
            boolean _showStatus,
            boolean _showLog
    ) {
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
            if (validateNode(node)) {
                tempNodes.add(node);
            }
        }

        cachedReqHandshake = new ReqHandshake(selfNodeId, selfNetId, this.selfIp, this.selfPort);
    }

    /**
     * @param _node Node
     * @return boolean
     */
    private boolean validateNode(final Node _node) {
        return _node != null && _node.getIdHash() != this.selfNodeIdHash
                && !(Arrays.equals(selfIp, _node.getIp()) && selfPort == _node.getPort())
                && !activeNodes.containsKey(_node.getIdHash()) && !outboundNodes.containsKey(_node.getIdHash());
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
    private void closeSocket(final SocketChannel _sc) {
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
     * @param _node Node self-check inbound timestamp and close if expired
     */
    private void addInboundNode(final Node _node) {
        SocketChannel sc = _node.getChannel();
        Node previous = inboundNodes.putIfAbsent(sc.hashCode(), _node);
        if (previous != null)
            closeSocket(_node.getChannel());
    }

    /**
     * @param _node Node
     * 1) leave outbound timestamp check to outbound connections process
     * 2) add if no such connection or drop new if connection to target exists
     */
    private void addOutboundNode(final Node _node) {
        Node previous = outboundNodes.putIfAbsent(_node.getIdHash(), _node);
        if (previous != null)
            closeSocket(_node.getChannel());
    }

    /**
     * @param _channelHashCode int
     */
    private void moveInboundToActive(int _channelHashCode) {
        Node node = this.inboundNodes.remove(_channelHashCode);
        if (node != null) {
            INode previous = this.activeNodes.putIfAbsent(node.getIdHash(), node);
            if (previous != null)
                closeSocket(node.getChannel());
            else {
                if (showLog)
                    System.out.println("<p2p action=move-inbound-to-active channel-id=" + _channelHashCode + ">");
            }
        }
    }

    /**
     * @param _nodeIdHash int
     */
    private void moveOutboundToActive(int _nodeIdHash, String _shortId) {
        Node node = this.outboundNodes.remove(_nodeIdHash);
        if (node != null) {
            INode previous = this.activeNodes.putIfAbsent(_nodeIdHash, node);
            if (previous != null)
                closeSocket(node.getChannel());
            else {
                if (showLog)
                    System.out.println("<p2p action=move-outbound-to-active node-id=" + _shortId + ">");
            }
        }
    }

    /**
     * @param _nodeIdHash int
     * @param _reason String
     */
    private void dropActive(int _nodeIdHash, String _reason, String _shortId) {
        Node node = activeNodes.remove(_nodeIdHash);
        if (node != null) {
            selectorLock.lock();
            closeSocket(node.getChannel());
            selectorLock.unlock();
            if (showLog)
                System.out.println("<p2p drop-active reason=" + _reason + " node=" + _shortId +">");
        }
    }

    private void accept() {
        SocketChannel channel;
        try {
            channel = tcpServer.accept();
            configChannel(channel);

            SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
            sk.attach(new ChannelBuffer());

            String ip = channel.socket().getInetAddress().getHostAddress();
            Node node = new Node(false, ip);
            node.setChannel(channel);
            addInboundNode(node);
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
            readHeader((SocketChannel)_sk.channel(), rb);
        }

        // read body
        if (rb.isHeaderCompleted() && !rb.isBodyCompleted()) {
            readBody((SocketChannel)_sk.channel(), rb);
        }

        if (!rb.isBodyCompleted())
            return;

        Header h = rb.header;
        byte[] bodyBytes = Arrays.copyOf(rb.body, rb.body.length);

        rb.refreshHeader();
        rb.refreshBody();

        byte ctrl = h.getCtrl();
        byte act = h.getAction();
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

        while (_sc.read(_cb.headerBuf) > 0) {
            // continue read if has data, break on timeout
        }
        if (_cb.headerBuf.hasRemaining())
            return;
        _cb.header = Header.decode(_cb.headerBuf.array());

        if (_cb.header.getLen() > MAX_BODY_BYTES) {
            throw new IOException("over-max-body-bytes");
        }
    }

    /**
     * @param _sc SocketChannel
     * @throws IOException IOException
     */
    private void readBody(final SocketChannel _sc, final ChannelBuffer _cb) throws IOException {

        int read;
        if (_cb.bodyBuf == null)
            _cb.bodyBuf = ByteBuffer.allocate(_cb.header.getLen());

        while (true) {
            read = _sc.read(_cb.bodyBuf);

            // 0 | -1 means all done or body in multi-selector
            if (read <= 0) {
                break;
            }
        }

        if (!_cb.bodyBuf.hasRemaining()) {
            _cb.body = _cb.bodyBuf.array();
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
                ReqHandshake reqHandshake = ReqHandshake.decode(_msgBytes);
                if (reqHandshake != null) {
                    Node node = inboundNodes.get(_sk.channel().hashCode());
                    if (node != null) {
                        rb.nodeIdHash = Arrays.hashCode(reqHandshake.getNodeId());
                        node.setId(reqHandshake.getNodeId());
                        node.setVersion(reqHandshake.getVersion());
                        node.setPort(reqHandshake.getPort());
                        moveInboundToActive(node.getChannel().hashCode());
                        write(rb.nodeIdHash, node.getIdShort(), (SocketChannel) _sk.channel(), new ResHandshake(true));
                    }
                }
                break;

            case Act.RES_HANDSHAKE:
                ResHandshake resHandshake = ResHandshake.decode(_msgBytes);
                if (resHandshake != null && rb.nodeIdHash != 0 && resHandshake.getSuccess()) {
                    Node node = outboundNodes.get(rb.nodeIdHash);
                    if (node != null) {
                        node.refreshTimestamp();
                        moveOutboundToActive(node.getIdHash(), node.getIdShort());
                    }
                }
                break;

            case Act.REQ_ACTIVE_NODES:
                if (rb.nodeIdHash != 0) {
                    Node node = activeNodes.get(rb.nodeIdHash);
                    if (node != null)
                        write(
                                rb.nodeIdHash,
                                node.getIdShort(),
                                (SocketChannel) _sk.channel(),
                                new ResActiveNodes(new ArrayList(activeNodes.values()))
                        );
                }
                break;

            case Act.RES_ACTIVE_NODES:
                if (rb.nodeIdHash != 0) {
                    Node node = activeNodes.get(rb.nodeIdHash);
                    if (node != null) {
                        node.refreshTimestamp();
                        ResActiveNodes resActiveNodes = ResActiveNodes.decode(_msgBytes);
                        if (resActiveNodes != null) {
                            List<Node> incomingNodes = resActiveNodes.getNodes();
                            for (Node incomingNode : incomingNodes) {
                                if (tempNodes.size() >= this.maxTempNodes)
                                    return;
                                if (validateNode(incomingNode))
                                    tempNodes.add(incomingNode);
                            }
                        }
                    }
                }
                break;
            default:
                if(showLog)
                    System.out.println("<p2p unknown-route act=" + _act + ">");
                break;
        }
    }

    /**
     * @param _nodeIdHash int
     * @param _route int
     * @param _msgBytes byte[]
     */
    private void handleKernelMsg(int _nodeIdHash, int _route, final byte[] _msgBytes) {
        Node node = activeNodes.get(_nodeIdHash);
        if (node != null) {
            List<Handler> hs = handlers.get(_route);
            if (hs == null)
                return;
            for (Handler h : hs) {
                if (h == null)
                    continue;
                node.refreshTimestamp();
                //System.out.println("in1 " + h.getHeader().getVer() + "-" + h.getHeader().getCtrl() + "-" + h.getHeader().getAction());
                workers.submit(() -> h.receive(node.getIdHash(), node.getIdShort(), _msgBytes));
            }
        }
    }

    /**
     * @param _nodeIdHash int
     * @param _sc SocketChannel
     * @param _msg IMsg
     * @throws IOException IOException
     */
    private void write(int _nodeIdHash, String _nodeShortId, final SocketChannel _sc, final Msg _msg) {

        workers.submit(()-> {
            synchronized (_sc) {
                /*
                 * @warning header set len (body len) before header encode
                 */
                byte[] bodyBytes = _msg.encode();
                int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
                Header h = _msg.getHeader();
                h.setLen(bodyLen);
                byte[] headerBytes = h.encode();

                //System.out.println("out " + h.getVer() + "-" + h.getCtrl() + "-" + h.getAction());

                ByteBuffer buf = ByteBuffer.allocate(headerBytes.length + bodyLen);
                buf.put(headerBytes);
                if (bodyBytes != null)
                    buf.put(bodyBytes);
                buf.flip();

                try {
                    while (buf.hasRemaining()) {
                        _sc.write(buf);
                    }
                } catch (IOException e) {
                    if (showLog) {
                        System.out.println("<p2p write-msg-io-exception>");
                        e.printStackTrace();
                    }
                }
//            finally {
//                if (buf.hasRemaining())
//                    dropActive(_nodeIdHash, _nodeShortId, "timeout-write-msg");
//            }
        }
        });
    }

    private void runUpnp() {
        // TODO: implement
    }

    @Override
    public void run() {
        try {
            this.selector = Selector.open();

            scheduledWorkers = new ScheduledThreadPoolExecutor(1);

            workers = Executors.newFixedThreadPool(Math.max(Runtime.getRuntime().availableProcessors() * 2, 8));

            tcpServer = ServerSocketChannel.open();
            tcpServer.configureBlocking(false);
            tcpServer.socket().setReuseAddress(true);
            tcpServer.socket().bind(new InetSocketAddress(Node.ipBytesToStr(selfIp), selfPort));
            tcpServer.register(selector, SelectionKey.OP_ACCEPT);

            if (this.upnpEnable)
                runUpnp();

            Thread boss = new Thread(new TaskInbound(), "p2p-pi");
            boss.setPriority(Thread.MAX_PRIORITY);
            boss.start();

            if (showStatus)
                scheduledWorkers.scheduleWithFixedDelay(new TaskStatus(), 2, PERIOD_SHOW_STATUS, TimeUnit.MILLISECONDS);
            scheduledWorkers.scheduleWithFixedDelay(new TaskRequestActiveNodes(this), 10, PERIOD_REQUEST_ACTIVE_NODES,
                    TimeUnit.MILLISECONDS);

            workers.submit(new TaskClear());
            workers.submit(new TaskConnectPeers());

        } catch (IOException e) {
            if (showLog)
                System.out.println("<p2p tcp-server-io-exception>");
        }
    }

    @Override
    public INode getRandom() {
        int nodesCount = this.activeNodes.size();
        if (nodesCount > 0) {
            Random r = new Random(System.currentTimeMillis());
            List<Integer> keysArr = new ArrayList<>(this.activeNodes.keySet());
            try {
                int randomNodeKeyIndex = r.nextInt(keysArr.size());
                int randomNodeKey = keysArr.get(randomNodeKeyIndex);
                return this.activeNodes.get(randomNodeKey);
            } catch (IllegalArgumentException e) {
                if (showLog)
                    System.out.println("<p2p get-random-exception>");
                return null;
            }
        } else
            return null;
    }

    @Override
    public Map getActiveNodes() {
        return this.activeNodes;
    }

    /**
     * @return int
     */
    int getTempNodesCount() {
        return this.tempNodes.size();
    }

    @Override
    public void register(final List<Handler> _cbs) {
        for (Handler _cb : _cbs) {
            Header h = _cb.getHeader();
            short ver = h.getVer();
            byte ctrl = h.getCtrl();
            if(Ver.filter(ver) != Ver.UNKNOWN && Ctrl.filter(ctrl) != Ctrl.UNKNOWN){
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
        Node node = this.activeNodes.get(_nodeIdHashcode);
        if (node != null)
            write(_nodeIdHashcode, node.getIdShort(), node.getChannel(), _msg);
    }

    @Override
    public void shutdown() {
        start.set(false);
        scheduledWorkers.shutdownNow();
        activeNodes.forEach((k,n)->{
            closeSocket(n.getChannel());
        });
        activeNodes.clear();
        outboundNodes.forEach((k,n)->{
            closeSocket(n.getChannel());
        });
        outboundNodes.clear();
        inboundNodes.forEach((k,n)->{
            closeSocket(n.getChannel());
        });
        inboundNodes.clear();
        workers.shutdownNow();
    }

    @Override
    public String version() {
        return "0.1.0";
    }

}

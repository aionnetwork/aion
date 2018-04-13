///*
// * Copyright (c) 2017-2018 Aion foundation.
// *
// * This file is part of the aion network project.
// *
// * The aion network project is free software: you can redistribute it
// * and/or modify it under the terms of the GNU General Public License
// * as published by the Free Software Foundation, either version 3 of
// * the License, or any later version.
// *
// * The aion network project is distributed in the hope that it will
// * be useful, but WITHOUT ANY WARRANTY; without even the implied
// * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
// * See the GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with the aion network project source files.
// * If not, see <https://www.gnu.org/licenses/>.
// *
// * Contributors to the aion source files in decreasing order of code volume:
// *
// * Aion foundation.
// *
// */
//
//package org.aion.p2p.impl3;
//
//import org.aion.p2p.*;
//import org.aion.p2p.impl.TaskRequestActiveNodes;
//import org.aion.p2p.impl.TaskUPnPManager;
//import org.aion.p2p.impl.zero.msg.*;
//import org.apache.commons.collections4.map.LRUMap;
//
//import java.io.IOException;
//import java.io.UnsupportedEncodingException;
//import java.net.InetSocketAddress;
//import java.nio.ByteBuffer;
//import java.nio.channels.*;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.stream.Collectors;
//
///**
// * @author Chris p2p://{uuid}@{ip}:{port} TODO: 1) simplify id bytest to int, ip
// *         bytest to str 2) upnp protocal 3) framing
// */
//public final class P2pMgr implements IP2pMgr {
//
//	private final static int PERIOD_SHOW_STATUS = 10000;
//	private final static int PERIOD_REQUEST_ACTIVE_NODES = 1000;
//	private final static int PERIOD_CONNECT_OUTBOUND = 1000;
//	private final static int PERIOD_CLEAR = 20000;
//    private final static int PERIOD_UPNP_PORT_MAPPING = 3600000;
//
//	private final static int TIMEOUT_OUTBOUND_CONNECT = 10000;
//	private final static int TIMEOUT_MSG_READ = 10000;
//
//	private final int maxTempNodes;
//	private final int maxConnections;
//	private final int errTolerance;
//
//	private final boolean syncSeedsOnly;
//	private final boolean showStatus;
//	private final boolean showLog;
//	private final int selfNetId;
//	private final String selfRevision;
//	private final byte[] selfNodeId;
//	private final int selfNodeIdHash;
//	private final String selfShortId;
//	private final byte[] selfIp;
//	private final int selfPort;
//	private final boolean upnpEnable;
//
//	private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();
//	private final Set<Short> versions = new HashSet<>();
//
//	private ServerSocketChannel tcpServer;
//	private Selector selector;
//
//	private final ScheduledThreadPoolExecutor scheduledWorkers;
//    private final ExecutorService workers;
//	private final Map<Integer, Integer> errCnt = Collections.synchronizedMap(new LRUMap<>(128));
//
//	private final Set<String> seedIps = new HashSet<>();
//	private final Map<Integer, Node> allNodes = new ConcurrentHashMap<>();
//	private final BlockingQueue<Node> tempNodes = new LinkedBlockingQueue<>();
//	private final Map<Integer, Node> outboundNodes = new ConcurrentHashMap<>();
//	private final Map<Integer, Node> inboundNodes = new ConcurrentHashMap<>();
//	private final Map<Integer, Node> activeNodes = new ConcurrentHashMap<>();
//
//	private AtomicBoolean start = new AtomicBoolean(true);
//
//	// initialed after handlers registration completed
//	private static ReqHandshake cachedReqHandshake;
//	private static ResHandshake cachedResHandshake;
//
//    private final class TaskInbound implements Runnable {
//        @Override
//        public void run() {
//            while (start.get()) {
//
//                int num;
//                try {
//                    num = selector.select(1);
//                } catch (IOException e) {
//                    if (showLog)
//                        System.out.println("<p2p inbound-select-io-exception>");
//                    continue;
//                }
//
//                if (num == 0)
//                    continue;
//
//                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
//
//                while (keys.hasNext()) {
//
//                    final SelectionKey sk = keys.next();
//                    keys.remove();
//
//                    if (!sk.isValid())
//                        continue;
//
//                    if (sk.isAcceptable())
//                        accept();
//
//                    if (sk.isReadable())
//                        try {
//                            read(sk);
//                        } catch (IOException | NullPointerException e) {
//                            if (showLog) {
//                                System.out.println("<p2p read-msg-io-exception>");
//                            }
//                            closeSocket((SocketChannel) sk.channel());
//                        }
//                }
//            }
//            if (showLog)
//                System.out.println("<p2p-pi shutdown>");
//        }
//	}
//
//	private final class TaskStatus implements Runnable {
//		@Override
//		public void run() {
//			Thread.currentThread().setName("p2p-ts");
//            StringBuilder sb = new StringBuilder();
//            sb.append("\n");
//            sb.append(String.format(
//                    "================================================================== p2p-status-%6s ==================================================================\n",
//                    selfShortId));
//            //		sb.append(String.format(
//            //				"temp[%3d] inbound[%3d] outbound[%3d] active[%3d]                            s - seed node, td - total difficulty, # - block number, bv - binary version\n",
//            //				tempNodesSize(), 0, 0, activeNodes.size()));
//            Collection<INode> sorted = getActiveNodes().values();
//            if (sorted.size() > 0) {
//                sb.append("\n          s"); // id & seed
//                sb.append("               td");
//                sb.append("          #");
//                sb.append("                                                             hash");
//                sb.append("              ip");
//                sb.append("  port");
//                sb.append("     conn");
//                sb.append("              bv\n");
//                sb.append(
//                        "-------------------------------------------------------------------------------------------------------------------------------------------------------\n");
//                sorted.sort((n1, n2) -> {
//                    int tdCompare = n2.getTotalDifficulty().compareTo(n1.getTotalDifficulty());
//                    if (tdCompare == 0) {
//                        Long n2Bn = n2.getBestBlockNumber();
//                        Long n1Bn = n1.getBestBlockNumber();
//                        return n2Bn.compareTo(n1Bn);
//                    } else
//                        return tdCompare;
//                });
//                for (Node n : sorted) {
//                    try {
//                        sb.append(
//                            String.format("id:%6s %c %16s %10d %64s %15s %5d %8s %15s\n",
//                                    n.getIdShort(),
//                                    n.getIfFromBootList() ? 'y' : ' ', n.getTotalDifficulty().toString(10),
//                                    n.getBestBlockNumber(),
//                                    n.getBestBlockHash() == null ? "" : Utility.bytesToHex(n.getBestBlockHash()), n.getIpStr(),
//                                    n.getPort(),
//                                    n.getConnection(),
//                                    n.getBinaryVersion()
//                            )
//                        );
//                    } catch (Exception ex) {
//                        ex.printStackTrace();
//                    }
//                }
//            }
//            sb.append("\n");
//		}
//	}
//
//    private final class TaskConnect implements Runnable {
//        @Override
//        public void run() {
//            Thread.currentThread().setName("p2p-tcp");
//            while (start.get()) {
//                try {
//                    Thread.sleep(PERIOD_CONNECT_OUTBOUND);
//                } catch (InterruptedException e) {
//                    if (showLog)
//                        System.out.println("<p2p-tcp interrupted>");
//                }
//
//                if (connections.size() >= connections.size()) {
//                    if (showLog)
//                        System.out.println("<p2p-tcp-connect-peer pass max-active-nodes>");
//                    return;
//                }
//
//                Node node;
//                try {
//                    node = tempNodes.take();
//                    if (node.getIfFromBootList())
//                        tempNodes.offer(node);
//                    //                    if (node.nodeMetric.shouldNotConn()) {
//                    //                        continue;
//                    //                    }
//                } catch (InterruptedException e) {
//                    if (showLog)
//                        System.out.println("<p2p outbound-connect-io-exception>");
//                    return;
//                }
//                int nodeIdHash = node.getIdHash();
//
//                if (!nodeMgr.getOutboundNodes().containsKey(nodeIdHash) && !nodeMgr.hasActiveNode(nodeIdHash)) {
//                    int _port = node.getPort();
//                    try {
//                        SocketChannel channel = SocketChannel.open();
//                        if (showLog)
//                            System.out.println("<p2p try-connect-" + node.getIpStr() + ">");
//                        channel.socket().connect(
//                                new InetSocketAddress(node.getIpStr(), _port),
//                                TIMEOUT_OUTBOUND_CONNECT
//                        );
//                        configChannel(channel);
//
//                        if (channel.finishConnect() && channel.isConnected()) {
//                            SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
//                            ChannelBuffer rb = new ChannelBuffer();
//                            rb.nodeIdHash = nodeIdHash;
//                            sk.attach(rb);
//
//                            node.setChannel(channel);
//
//                            addOutboundNode(node);
//                            //selectorLock.unlock();
//
//                            // fire extended handshake request first
//                            workers.submit(new TaskWrite(workers, showLog, node.getIdShort(), channel, cachedReqHandshake, rb));
//
//                            if (showLog)
//                                System.out.println("<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port
//                                        + " result=success>");
//
//                            node.nodeMetric.decFailedCount();
//
//                        } else {
//                            channel.close();
//                            node.nodeMetric.incFailedCount();
//                        }
//                    } catch (IOException e) {
//                        if (showLog)
//                            System.out.println("<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port
//                                    + " result=failed>");
//                        node.nodeMetric.incFailedCount();
//                    }
//                }
//            }
//        }
//    }
//
//	private final class TaskClear implements Runnable {
//		@Override
//		public void run() {
//			Thread.currentThread().setName("p2p-clr");
//			while (start.get()) {
//				try {
//					Thread.sleep(PERIOD_CLEAR);
//
//					// reconnect node stuck during handshake.
//					List<Node> ns = nodeMgr.getStmNodeHS();
//					for (Node n : ns) {
//						P2pMgr.this.sendMsgQue.add(new MsgOut(n.getChannelId(), cachedReqHandshake, Dest.OUTBOUND));
//					}
//
//					// nodeMgr.rmTimeOutInbound(P2pMgr.this);
//					//
//					// // clean up temp nodes list if metric failed.
//					// nodeMgr.rmMetricFailedNodes();
//					//
//					// Iterator outboundIt = nodeMgr.getOutboundNodes().keySet().iterator();
//					// while (outboundIt.hasNext()) {
//					//
//					// Object obj = outboundIt.next();
//					//
//					// if (obj == null)
//					// continue;
//					//
//					// int nodeIdHash = (int) obj;
//					// Node node = nodeMgr.getOutboundNodes().get(nodeIdHash);
//					//
//					// if (node == null)
//					// continue;
//					//
//					// if (System.currentTimeMillis() - node.getTimestamp() >
//					// TIMEOUT_OUTBOUND_NODES) {
//					// closeSocket(node.getChannel());
//					// outboundIt.remove();
//					//
//					// if (showLog)
//					// System.out.println("<p2p-clear outbound-timeout>");
//					// }
//					// }
//					//
//					// nodeMgr.rmTimeOutActives(P2pMgr.this);
//
//				} catch (Exception e) {
//				}
//			}
//		}
//	}
//
//	/**
//	 * @param _nodeId
//	 *            byte[36]
//	 * @param _ip
//	 *            String
//	 * @param _port
//	 *            int
//	 * @param _bootNodes
//	 *            String[]
//	 * @param _upnpEnable
//	 *            boolean
//	 * @param _maxTempNodes
//	 *            int
//	 * @param _maxActiveNodes
//	 *            int
//	 * @param _showStatus
//	 *            boolean
//	 * @param _showLog
//	 *            boolean
//	 */
//	public P2pMgr(int _netId, String _revision, String _nodeId, String _ip, int _port, final String[] _bootNodes,
//			boolean _upnpEnable, int _maxTempNodes, int _maxConnections, boolean _showStatus, boolean _showLog,
//			boolean _bootlistSyncOnly, int _errorTolerance) {
//		this.selfNetId = _netId;
//		this.selfRevision = _revision;
//		this.selfNodeId = _nodeId.getBytes();
//		this.selfNodeIdHash = Arrays.hashCode(selfNodeId);
//		this.selfShortId = new String(Arrays.copyOfRange(_nodeId.getBytes(), 0, 6));
//		this.selfIp = Node.ipStrToBytes(_ip);
//		this.selfPort = _port;
//		this.upnpEnable = _upnpEnable;
//		this.maxTempNodes = _maxTempNodes;
//		this.maxConnections = Math.max(_maxConnections, 128);
//		this.showStatus = _showStatus;
//		this.showLog = _showLog;
//		this.syncSeedsOnly = _bootlistSyncOnly;
//		this.errTolerance = _errorTolerance;
//
//		for (String _bootNode : _bootNodes) {
//			Node node = Node.parseP2p(_bootNode);
//			if (node != null && validateNode(node)) {
//				tempNodes.add(node);
//				seedIps.add(node.getIpStr());
//			}
//		}
//
//		cachedResHandshake = new ResHandshake(true, this.selfRevision);
//	}
//
//	/**
//	 * @param _node
//	 *            Node
//	 * @return boolean
//	 */
//	private boolean validateNode(final Node _node) {
//		boolean notNull = _node != null;
//		// filter self
//		boolean notSelfId = _node.getIdHash() != this.selfNodeIdHash;
//		boolean notSameIpOrPort = !(Arrays.equals(selfIp, _node.getIp()) && selfPort == _node.getPort());
//
//		// filter already active.
//		// boolean notActive = !nodeMgr.hasActiveNode(_node.getIdHash());
//
//		boolean notActive = !nodeMgr.hasActiveNode(_node.getChannelId());
//
//		// filter out conntected.
//		boolean notOutbound = !(_node.st.stat == NodeState.CONNECTTED);
//
//		for (Node n : this.nodeMgr.allStmNodes) {
//			if (n.getIdHash() == _node.getIdHash()) {
//				return false;
//			}
//		}
//
//		return notNull && notSelfId && notSameIpOrPort && notActive && notOutbound;
//	}
//
//	/**
//	 * @param _channel
//	 *            SocketChannel TODO: check option
//	 */
//	private void configChannel(final SocketChannel _channel) throws IOException {
//		_channel.configureBlocking(false);
//		_channel.socket().setSoTimeout(TIMEOUT_MSG_READ);
//		// _channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
//		// _channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
//		// _channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
//	}
//
//	/**
//	 * @param _sc
//	 *            SocketChannel
//	 */
//	public void closeSocket(final SocketChannel _sc) {
//		if (showLog)
//			System.out.println("<p2p close-socket->");
//
//		try {
//			SelectionKey sk = _sc.keyFor(selector);
//			_sc.close();
//			if (sk != null)
//				sk.cancel();
//		} catch (IOException e) {
//			if (showLog)
//				System.out.println("<p2p close-socket-io-exception>");
//		}
//	}
//
//	private void accept() {
//		SocketChannel channel;
//		try {
//			channel = tcpServer.accept();
//			configChannel(channel);
//
//			SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
//
//			String ip = channel.socket().getInetAddress().getHostAddress();
//			int port = channel.socket().getPort();
//
//			if (syncSeedsOnly && nodeMgr.isSeedIp(ip)) {
//				// close the channel and return.
//				channel.close();
//				return;
//			}
//
//			// Node node = new Node(false, ip);
//			Node node = nodeMgr.allocNode(ip, 0, port);
//
//			node.setChannel(channel);
//			node.st.setStat(NodeState.ACCEPTED);
//			node.st.setStatOr(NodeState.HS);
//
//			ChannelBuffer cb = new ChannelBuffer();
//			cb.set
//			sk.attach(cb);
//
//			if (showLog)
//				System.out.println("<p2p new-connection " + ip + ":" + port + ">");
//
//		} catch (IOException e) {
//			if (showLog)
//				System.out.println("<p2p inbound-accept-io-exception>");
//			return;
//		}
//	}
//
//    /**
//     * @param _sc SocketChannel
//     * @throws IOException IOException
//     */
//    private void readHeader(final SocketChannel _sc, final ChannelBuffer _cb) throws IOException {
//
//        int ret;
//        while ((ret = _sc.read(_cb.headerBuf)) > 0) {
//        }
//
//        if (!_cb.headerBuf.hasRemaining()) {
//            _cb.header = Header.decode(_cb.headerBuf.array());
//        } else {
//            if (ret == -1) {
//                throw new IOException("read-header-eof");
//            }
//        }
//    }
//
//    /**
//     * @param _sc SocketChannel
//     * @throws IOException IOException
//     */
//    private void readBody(final SocketChannel _sc, final ChannelBuffer _cb) throws IOException {
//
//        if (_cb.bodyBuf == null)
//            _cb.bodyBuf = ByteBuffer.allocate(_cb.header.getLen());
//
//        int ret;
//        while ((ret = _sc.read(_cb.bodyBuf)) > 0) {
//        }
//
//        if (!_cb.bodyBuf.hasRemaining()) {
//            _cb.body = _cb.bodyBuf.array();
//        } else {
//            if (ret == -1) {
//                throw new IOException("read-body-eof");
//            }
//        }
//    }
//
//    /**
//     * @param _sk SelectionKey
//     * @throws IOException IOException
//     */
//    private void read(final SelectionKey _sk) throws IOException {
//
//        if (_sk.attachment() == null) {
//            throw new IOException("attachment is null");
//        }
//        ChannelBuffer rb = (ChannelBuffer) _sk.attachment();
//
//        // read header
//        if (!rb.isHeaderCompleted()) {
//            readHeader((SocketChannel) _sk.channel(), rb);
//        }
//
//        // read body
//        if (rb.isHeaderCompleted() && !rb.isBodyCompleted()) {
//            readBody((SocketChannel) _sk.channel(), rb);
//        }
//
//        if (!rb.isBodyCompleted())
//            return;
//
//        Header h = rb.header;
//        byte[] bodyBytes = rb.body;
//        rb.refreshHeader();
//        rb.refreshBody();
//
//        short ver = h.getVer();
//        byte ctrl = h.getCtrl();
//        byte act = h.getAction();
//
//        // print route
//        // System.out.println("read " + ver + "-" + ctrl + "-" + act);
//        switch(ver){
//            case Ver.V0:
//                switch (ctrl) {
//                    case Ctrl.NET:
//                        handleP2pMsg(_sk, act, bodyBytes);
//                        break;
//                    default:
//                        int route = h.getRoute();
//                        if (handlers.containsKey(route))
//                            handleKernelMsg(_sk.channel().hashCode(), route, bodyBytes);
//                        break;
//                }
//                break;
//        }
//    }
//
//	/**
//	 * @return boolean
//     * TODO: supported protocol versions
//	 */
//	private boolean handshakeRuleCheck(int netId) {
//        return netId == selfNetId;
//    }
//
//	/**
//	 * @param _buffer ChannelBuffer
//	 * @param _channelHash int
//	 * @param _nodeId byte[]
//	 * @param _netId int
//	 * @param _port int
//	 * @param _revision byte[]
//	 * Construct node info after handshake request success
//	 */
//	private void handleReqHandshake(
//        final ChannelBuffer _buffer,
//        int _channelId,
//        final byte[] _nodeId,
//        int _netId,
//		int _port,
//        final byte[] _revision
//    ) {
//		Node node = connections.get(_channelId);
//		if (node != null && ) {
//			if (handshakeRuleCheck(_netId)) {
//
//				node.setId(_nodeId);
//				node.setPort(_port);
//
//                String binaryVersion;
//                try {
//                    binaryVersion = new String(_revision, "UTF-8");
//                } catch (UnsupportedEncodingException e) {
//                    binaryVersion = "decode-fail";
//                }
//                node.setBinaryVersion(binaryVersion);
//                // workers.submit(new TaskWrite(workers, showLog,
//                // node.getIdShort(), node.getChannel(),
//                // cachedResHandshake, _buffer, this));
//                sendMsgQue.offer(new MsgOut(node.getChannelId(), cachedResHandshake, Dest.INBOUND));
//
//				node.st.setStat(NodeState.HS_DONE);
//
//				node.st.setStat(NodeState.ACTIVE);
//
//			} else {
//				if (showLog)
//					System.out.println("incompatible netId ours=" + this.selfNetId + " theirs=" + _netId);
//			}
//		}
//	}
//
//	private void handleResHandshake(int channelIdHash, String _binaryVersion) {
//
//		Node node = nodeMgr.getStmNode(channelIdHash, NodeState.CONNECTTED);
//
//		if (node != null) {
//
//			node.refreshTimestamp();
//			node.setBinaryVersion(_binaryVersion);
//
//			node.st.setStat(NodeState.HS_DONE);
//
//			node.st.setStat(NodeState.ACTIVE);
//
//		}
//	}
//
//	/**
//	 * @param _sk
//	 *            SelectionKey
//	 * @param _act
//	 *            ACT
//	 * @param _msgBytes
//	 *            byte[]
//	 */
//	private void handleP2pMsg(final SelectionKey _sk, byte _act, final byte[] _msgBytes) {
//
//		ChannelBuffer rb = (ChannelBuffer) _sk.attachment();
//
//		switch (_act) {
//
//		case Act.REQ_HANDSHAKE:
//            ReqHandshake reqHandshake = ReqHandshake.decode(_msgBytes);
//
//            if (reqHandshake != null) {
//                handleReqHandshake(rb, _sk.channel().hashCode(), reqHandshake.getNodeId(),
//                        reqHandshake.getNetId(), reqHandshake.getPort(), reqHandshake.getRevision());
//            }
//			break;
//
//		case Act.RES_HANDSHAKE:
//            ResHandshake resHandshake = ResHandshake.decode(_msgBytes);
//            if (resHandshake != null && resHandshake.getSuccess())
//                handleResHandshake(rb.cid, resHandshake.getBinaryVersion());
//			break;
//
//		case Act.REQ_ACTIVE_NODES:
//			if (rb.cid <= 0)
//				return;
//
//			// Node node = nodeMgr.getActiveNode(rb.cid);
//			// if (node != null)
//			// workers.submit(new TaskWrite(workers, showLog,
//			// node.getIdShort(), node.getChannel(),
//			// new ResActiveNodes(nodeMgr.getActiveNodesList()), rb,
//			// this));
//			sendMsgQue.offer(new MsgOut(rb.cid, new ResActiveNodes(nodeMgr.getActiveNodesList()), Dest.ACTIVE));
//
//			break;
//
//		case Act.RES_ACTIVE_NODES:
//			if (syncSeedsOnly)
//				break;
//
//			if (rb.cid > 0) {
//				Node node = nodeMgr.getActiveNode(rb.cid);
//				if (node != null) {
//					node.refreshTimestamp();
//					ResActiveNodes resActiveNodes = ResActiveNodes.decode(_msgBytes);
//					if (resActiveNodes != null) {
//						List<Node> incomingNodes = resActiveNodes.getNodes();
//						for (Node incomingNode : incomingNodes) {
//							if (nodeMgr.tempNodesSize() >= this.maxTempNodes)
//								return;
//							if (validateNode(incomingNode))
//								nodeMgr.tempNodesAdd(incomingNode);
//						}
//					}
//				}
//			}
//			break;
//		default:
//			if (showLog)
//				System.out.println("<p2p unknown-route act=" + _act + ">");
//			break;
//		}
//	}
//
//	/**
//	 * @param _channelId int
//	 * @param _route int
//	 * @param _msgBytes byte[]
//	 */
//	private void handleKernelMsg(int _channelId, int _route, final byte[] _msgBytes) {
//		Optional<Map.Entry<Integer, Node>> entry = connections.entrySet().stream().filter(e -> e.getValue().state.hasStat(NodeState.ACTIVE)).findAny();
//		if(entry != null && entry.isPresent()){
//		    Node node = entry.get().getValue();
//            List<Handler> hs = handlers.get(_route);
//            if (hs == null)
//                return;
//            for (Handler hlr : hs) {
//                if (hlr == null)
//                    continue;
//                node.refreshTimestamp();
//                //System.out.println("I am handle kernel msg !!!!! " + hlr.getHeader().getCtrl() + "-" + hlr.getHeader().getAction() + "-" + hlr.getHeader().getLen());
//                workers.submit(() -> hlr.receive(node.getIdHash(), node.getIdShort(), _msgBytes));
//            }
//		}
//        else {
//		    if(showLog)
//                System.out.println("<p2p-handle-kernel-msg-failed channel-id=" + _channelId + ">");
//
//        }
//	}
//
//	@Override
//	public void run() {
//		try {
//			selector = Selector.open();
//			scheduledWorkers = new ScheduledThreadPoolExecutor(1);
//            workers = Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors() * 2, 16), new ThreadFactory() {
//                private AtomicInteger count = new AtomicInteger();
//                @Override
//                public Thread newThread(Runnable r) {
//                    return new Thread(r,"p2p-worker-" + count.incrementAndGet());
//                }
//            });
//
//			tcpServer = ServerSocketChannel.open();
//			tcpServer.configureBlocking(false);
//			tcpServer.socket().setReuseAddress(true);
//			tcpServer.socket().bind(new InetSocketAddress(Node.ipBytesToStr(selfIp), selfPort));
//			tcpServer.register(selector, SelectionKey.OP_ACCEPT);
//
//			Thread tInbound = new Thread(new TaskInbound(), "p2p-in");
//            tInbound.setPriority(Thread.NORM_PRIORITY);
//            tInbound.start();
//
//            Thread tConn = new Thread(new TaskConnect(), "p2p-conn");
//            tConn.setPriority(Thread.NORM_PRIORITY);
//            tConn.start();
//
//			Thread tGuard = new Thread(new TaskGuard(), "p2p-guard");
//            tGuard.setPriority(Thread.NORM_PRIORITY);
//            tGuard.start();
//
//            if (upnpEnable)
//                scheduledWorkers.scheduleWithFixedDelay(new TaskUPnPManager(selfPort), 1, PERIOD_UPNP_PORT_MAPPING,
//                        TimeUnit.MILLISECONDS);
//
//            if (showStatus)
//                scheduledWorkers.scheduleWithFixedDelay(new TaskStatus(), 2, PERIOD_SHOW_STATUS, TimeUnit.MILLISECONDS);
//
//            if (!syncSeedsOnly)
//                scheduledWorkers.scheduleWithFixedDelay(new TaskRequestActiveNodes(this), 5000,
//                        PERIOD_REQUEST_ACTIVE_NODES, TimeUnit.MILLISECONDS);
//
//		} catch (IOException e) {
//			if (showLog)
//				System.out.println("<p2p tcp-server-io-exception>");
//		}
//	}
//
//	@Override
//	public INode getRandom() {
//		return nodeMgr.getRandom();
//	}
//
//	@Override
//	public Map<Integer, INode> getActiveNodes() {
//	    long start = System.currentTimeMillis();
//	    Map<Integer,INode> activeNodes = new HashMap<>(
//            connections
//            .entrySet().stream()
//            .filter(e -> e.getValue().state.hasStat(NodeState.ACTIVE))
//            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()))
//        );
//        System.out.println("get-active-nodes costs " + (System.currentTimeMillis() - start));
//		return activeNodes;
//	}
//
//	int getTempNodesCount() {
//		return tempNodes.size();
//	}
//
//	@Override
//	public void register(final List<Handler> _cbs) {
//		for (Handler _cb : _cbs) {
//			Header h = _cb.getHeader();
//			short ver = h.getVer();
//			byte ctrl = h.getCtrl();
//			if (Ver.filter(ver) != Ver.UNKNOWN && Ctrl.filter(ctrl) != Ctrl.UNKNOWN) {
//				if (!versions.contains(ver)) {
//					versions.add(ver);
//				}
//
//				int route = h.getRoute();
//				List<Handler> routeHandlers = handlers.get(route);
//				if (routeHandlers == null) {
//					routeHandlers = new ArrayList<>();
//					routeHandlers.add(_cb);
//					handlers.put(route, routeHandlers);
//				} else {
//					routeHandlers.add(_cb);
//				}
//			}
//		}
//
//		List<Short> supportedVersions = new ArrayList<>(versions);
//		cachedReqHandshake = new ReqHandshake(selfNodeId, selfNetId, this.selfIp, this.selfPort,
//				this.selfRevision.getBytes(), supportedVersions);
//	}
//
//	@Override
//	public void send(int cid, final Msg _msg) {
//        Node node = this.nodeMgr.getActiveNode(_nodeIdHashcode);
//        if (node != null) {
//            SelectionKey sk = node.getChannel().keyFor(selector);
//
//            if (sk != null) {
//                Object attachment = sk.attachment();
//                if (attachment != null)
//                    workers.submit(
//                            new TaskWrite(workers, showLog, node.getIdShort(), node.getChannel(), _msg, (ChannelBuffer) attachment));
//            }
//        }
//	}
//
//	@Override
//	public void shutdown() {
//		start.set(false);
//		scheduledWorkers.shutdownNow();
//
//		for (List<Handler> hdrs : handlers.values()) {
//			hdrs.forEach(hdr -> hdr.shutDown());
//		}
//	}
//
//	@Override
//	public List<Short> versions() {
//		return new ArrayList<>(versions);
//	}
//
//	@Override
//	public int chainId() {
//		return selfNetId;
//	}
//
//	@Override
//	public void errCheck(int nodeIdHashcode, String _displayId) {
//		int cnt = (errCnt.get(nodeIdHashcode) == null ? 1 : (errCnt.get(nodeIdHashcode).intValue() + 1));
//
//		if (cnt > this.errTolerance) {
//			ban(nodeIdHashcode);
//			errCnt.put(nodeIdHashcode, 0);
//
//			if (showLog) {
//				System.out.println("<ban node: " + (_displayId == null ? nodeIdHashcode : _displayId) + ">");
//			}
//		} else {
//			errCnt.put(nodeIdHashcode, cnt);
//		}
//	}
//
//	private void ban(int nodeIdHashcode) {
//		//nodeMgr.ban(nodeIdHashcode);
//		//nodeMgr.dropActive(nodeIdHashcode, this);
//	}
//}

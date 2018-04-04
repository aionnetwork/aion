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

package org.aion.p2p.impl2;

import org.aion.p2p.*;
import org.aion.p2p.impl.TaskRequestActiveNodes;
import org.aion.p2p.impl.TaskUPnPManager;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.comm.ChannelBuffer;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl.zero.msg.*;
import org.aion.p2p.impl2.selector.MainIOLoop;
import org.aion.p2p.impl2.selector.Task;
import org.apache.commons.collections4.map.LRUMap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// import org.aion.p2p.impl.one.msg.Hello;

/**
 * @author Chris p2p://{uuid}@{ip}:{port} TODO: 1) simplify id bytest to int, ip
 *         bytest to str 2) upnp protocal 3) framing
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
	// initialed after handlers registration completed
	private ReqHandshake1 cachedReqHandshake1;
	private ReqHandshake cachedReqHandshake;
	private ResHandshake1 cachedResHandshake1;
	private ResHandshake cachedResHandshake;

	private final int maxTempNodes;
	private final int maxActiveNodes;

	private final boolean syncSeedsOnly;
	private final boolean showStatus;
	public final boolean showLog;
	private final int selfNetId;
	private final String selfRevision;
	private final byte[] selfNodeId;
	private final int selfNodeIdHash;
	private final String selfShortId;
	private final byte[] selfIp;
	private final int selfPort;
	private final boolean upnpEnable;

	private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();
	private final Set<Short> versions = new HashSet<>();

	private NodeMgr nodeMgr = new NodeMgr();
	private ServerSocketChannel tcpServer;
	private Selector selector;

	private ScheduledThreadPoolExecutor scheduledWorkers;
	private ExecutorService workers;
	private AtomicBoolean start = new AtomicBoolean(true);

	private final MainIOLoop ioLoop;

	private final HandleChannel handleRead = new HandleChannel();

	private final HandleInboundEvent handleInbound = new HandleInboundEvent();

	private final Map<Integer, Integer> errCnt = Collections.synchronizedMap(new LRUMap<>(128));


	/**
	 * @param _nodeId
	 *            byte[36]
	 * @param _ip
	 *            String
	 * @param _port
	 *            int
	 * @param _bootNodes
	 *            String[]
	 * @param _upnpEnable
	 *            boolean
	 * @param _maxTempNodes
	 *            int
	 * @param _maxActiveNodes
	 *            int
	 * @param _showStatus
	 *            boolean
	 * @param _showLog
	 *            boolean
	 */
	public P2pMgr(int _netId, String _revision, String _nodeId, String _ip, int _port, final String[] _bootNodes,
			boolean _upnpEnable, int _maxTempNodes, int _maxActiveNodes, boolean _showStatus, boolean _showLog,
			boolean _bootlistSyncOnly) {
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

		for (String _bootNode : _bootNodes) {
			Node node = Node.parseP2p(_bootNode);
			if (node != null && validateNode(node)) {
				nodeMgr.tempNodesAdd(node);
				nodeMgr.seedIpAdd(node.getIpStr());
			}
		}

		// rem out for bug:
		// nodeMgr.loadPersistedNodes();
		cachedReqHandshake = new ReqHandshake(_nodeId.getBytes(), selfNetId, this.selfIp, this.selfPort);
		cachedResHandshake = new ResHandshake(true);
		cachedResHandshake1 = new ResHandshake1(true, this.selfRevision);
		this.ioLoop = new MainIOLoop(SelectorProvider.provider());
	}

	public int getSelfNodeIdHash() {
		return this.selfNodeIdHash;
	}

	/**
	 * @param _node
	 *            Node
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
	 * @param _channel
	 *            SocketChannel TODO: check option
	 */
	private static void configChannel(final SocketChannel _channel) throws IOException {
		_channel.configureBlocking(false);
		_channel.socket().setSoTimeout(TIMEOUT_MSG_READ);
		_channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
		_channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
	}

	/**
	 * @param _sc
	 *            SocketChannel
	 */
	public void closeSocket(final SocketChannel _sc) {
		this.ioLoop.cancelChannel(_sc);
	}

	/**
	 * Will drop an active node (if possible) TODO: verify that this indeed does not
	 * have to be synchronized
	 *
	 * @param _nodeIdHash
	 *            hash of the active node to be dropped
	 */
	@Override
	public void dropActive(Integer _nodeIdHash) {
		this.nodeMgr.dropActive(_nodeIdHash, this);
	}

	/**
	 * @param _node
	 *            Node 1) leave outbound timestamp check to outbound connections
	 *            process 2) add if no such connection or drop new if connection to
	 *            target exists
	 */
	private void addOutboundNode(final Node _node) {
		Node previous = nodeMgr.getOutboundNodes().putIfAbsent(_node.getIdHash(), _node);
		if (previous != null)
			closeSocket(_node.getChannel());
	}

	/**
	 * Customized code for registering a channel, taken from Java NIO reference
	 *
	 * @param loop
	 *            event loop
	 * @param channel
	 *            inbound channel produced by {@link #tcpServer}
	 * @param ops
	 *            the items/events we're interested in
	 */
	private static void registerChannel(MainIOLoop loop, SocketChannel channel, int ops, NodeMgr nodeMgr, Task task)
			throws IOException {
		if (channel == null)
			return;

		configChannel(channel);

		String ip = channel.socket().getInetAddress().getHostAddress();
		int port = channel.socket().getPort();
		int localPort = channel.socket().getLocalPort();

		ChannelBuffer buffer = new ChannelBuffer();
		buffer.ip = ip;
		buffer.port = port;
		buffer.task = task;

		loop.attachChannel(channel, ops, buffer, buffer.task);

		// attach to NodeMgr
		Node node = nodeMgr.allocNode(ip, localPort, port);
		node.setChannel(channel);
		nodeMgr.inboundNodeAdd(node);
	}

	/**
	 * @param _sc
	 *            SocketChannel
	 * @throws IOException
	 *             IOException
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
	 * @param _sc
	 *            SocketChannel
	 * @throws IOException
	 *             IOException
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
	 * @param _sk
	 *            SelectionKey
	 * @throws IOException
	 *             IOException
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

		// if(rb.isHeaderCompleted() && !handlers.containsKey(rb.header.getRoute())){
		// // TODO: Test
		// return;
		// }

		// read body
		if (rb.isHeaderCompleted() && !rb.isBodyCompleted()) {
			readBody((SocketChannel) _sk.channel(), rb);
		}

		if (!rb.isBodyCompleted())
			return;

		Header h = rb.header;

		// byte[] bodyBytes = Arrays.copyOf(rb.body, rb.body.length);

		byte[] bodyBytes = rb.body;
		rb.refreshHeader();
		rb.refreshBody();

		short ver = h.getVer();
		byte ctrl = h.getCtrl();
		byte act = h.getAction();

		// print route
		// System.out.println("read " + ver + "-" + ctrl + "-" + act);
		switch (ver) {
		case Ver.V0:
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
			break;

		// testing versioning
		// case Ver.V1:
		// if(ctrl == 0 && act == 0){
		// Hello hello = Hello.decode(bodyBytes);
		// if(hello != null)
		// System.out.println("v1 hello msg " + hello.getMsg());
		// }
		//
		// break;
		}

	}

	/**
	 * @return boolean TODO: implementation
	 */
	private boolean handshakeRuleCheck(int netId) {

		// check net id
		if (netId != selfNetId)
			return false;

		// check supported protocol versions
		return true;
	}

	/**
	 * @param _buffer
	 *            ChannelBuffer
	 * @param _channelHash
	 *            int
	 * @param _nodeId
	 *            byte[]
	 * @param _netId
	 *            int
	 * @param _port
	 *            int
	 * @param _revision
	 *            byte[]
	 *
	 *            Construct node info after handshake request success
	 */
	private void handleReqHandshake(final ChannelBuffer _buffer, int _channelHash, final byte[] _nodeId, int _netId,
			int _port, final byte[] _revision) {
		Node node = nodeMgr.getInboundNode(_channelHash);
		if (node != null) {
			if (handshakeRuleCheck(_netId)) {
				_buffer.nodeIdHash = Arrays.hashCode(_nodeId);
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
					workers.submit(new TaskWrite(ioLoop, workers, showLog, node.getIdShort(), node.getChannel(),
							cachedResHandshake1));
				}
				// handshake 0
				else {
					workers.submit(new TaskWrite(ioLoop, workers, showLog, node.getIdShort(), node.getChannel(),
							cachedResHandshake));
				}
				nodeMgr.moveInboundToActive(_channelHash, this);
			}
		}
	}

	private void handleResHandshake(int _nodeIdHash, String _binaryVersion) {
		Node node = nodeMgr.getOutboundNodes().get(_nodeIdHash);
		if (node != null) {
			node.refreshTimestamp();
			node.setBinaryVersion(_binaryVersion);
			nodeMgr.moveOutboundToActive(node.getIdHash(), node.getIdShort(), this);
		}
	}

	/**
	 * @param _sk
	 *            SelectionKey
	 * @param _act
	 *            ACT
	 * @param _msgBytes
	 *            byte[]
	 */
	private void handleP2pMsg(final SelectionKey _sk, byte _act, final byte[] _msgBytes) {

		ChannelBuffer rb = (ChannelBuffer) _sk.attachment();

		switch (_act) {

		case Act.REQ_HANDSHAKE:
			if (_msgBytes.length > ReqHandshake.LEN) {
				ReqHandshake1 reqHandshake1 = ReqHandshake1.decode(_msgBytes);
				if (reqHandshake1 != null) {
					handleReqHandshake(rb, _sk.channel().hashCode(), reqHandshake1.getNodeId(),
							reqHandshake1.getNetId(), reqHandshake1.getPort(), reqHandshake1.getRevision());
				}
			} else {
				ReqHandshake reqHandshake = ReqHandshake.decode(_msgBytes);
				if (reqHandshake != null)
					handleReqHandshake(rb, _sk.channel().hashCode(), reqHandshake.getNodeId(), reqHandshake.getNetId(),
							reqHandshake.getPort(), null);
			}

			break;

		case Act.RES_HANDSHAKE:
			if (rb.nodeIdHash == 0)
				return;

			if (_msgBytes.length > ResHandshake.LEN) {
				ResHandshake1 resHandshake1 = ResHandshake1.decode(_msgBytes);
				if (resHandshake1 != null && resHandshake1.getSuccess())
					handleResHandshake(rb.nodeIdHash, resHandshake1.getBinaryVersion());

			} else {
				ResHandshake resHandshake = ResHandshake.decode(_msgBytes);
				if (resHandshake != null && resHandshake.getSuccess())
					handleResHandshake(rb.nodeIdHash, "unknown");
			}
			break;

		case Act.REQ_ACTIVE_NODES:
			if (rb.nodeIdHash != 0) {
				Node node = nodeMgr.getActiveNode(rb.nodeIdHash);
				if (node != null)
					workers.submit(new TaskWrite(ioLoop, workers, showLog, node.getIdShort(), node.getChannel(),
							new ResActiveNodes(nodeMgr.getActiveNodesList())));
			}
			break;

		case Act.RES_ACTIVE_NODES:
			if (syncSeedsOnly)
				break;

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
	 * @param _nodeIdHash
	 *            int
	 * @param _route
	 *            int
	 * @param _msgBytes
	 *            byte[]
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
				workers.submit(() -> hlr.receive(node.getIdHash(), node.getIdShort(), _msgBytes));
			}
		}
	}

	/**
	 * @return NodeMgr
	 */
	public NodeMgr getNodeMgr() {
		return this.nodeMgr;
	}

	@Override
	public void run() {
		try {
			selector = Selector.open();

			scheduledWorkers = new ScheduledThreadPoolExecutor(1);
			workers = Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors() * 2, 16),
					new ThreadFactory() {
						private AtomicInteger cnt = new AtomicInteger();

						public Thread newThread(Runnable r) {
							return new Thread(r, "p2p-worker-" + cnt.incrementAndGet());
						}
					});

			tcpServer = ServerSocketChannel.open();
			tcpServer.configureBlocking(false);
			tcpServer.socket().setReuseAddress(true);
			tcpServer.socket().bind(new InetSocketAddress(Node.ipBytesToStr(selfIp), selfPort));

			ChannelBuffer ibChannelBuffer = new ChannelBuffer();
			ibChannelBuffer.task = handleInbound;

			this.ioLoop.attachChannel(tcpServer, SelectionKey.OP_ACCEPT, ibChannelBuffer, ibChannelBuffer.task);

			// this is the main selector thread, this thread handles the following tasks:
			// 1) All selector registration is done through this channel, selector
			// registration
			// may be done by {@link #TaskConnectPeers}
			// 2) All selector removal is done through this channel
			Thread boss = new Thread(this.ioLoop, "p2p-selector-main");
			boss.setPriority(Thread.MAX_PRIORITY);
			boss.start();

			if (upnpEnable)
				scheduledWorkers.scheduleWithFixedDelay(new TaskUPnPManager(selfPort), 1, PERIOD_UPNP_PORT_MAPPING,
						TimeUnit.MILLISECONDS);

			if (showStatus)
				scheduledWorkers.scheduleWithFixedDelay(new TaskStatus(), 2, PERIOD_SHOW_STATUS, TimeUnit.MILLISECONDS);

			if (!syncSeedsOnly)
				scheduledWorkers.scheduleWithFixedDelay(new TaskRequestActiveNodes(this), 5000,
						PERIOD_REQUEST_ACTIVE_NODES, TimeUnit.MILLISECONDS);

			// rem out for bug: https://github.com/aionnetwork/aion/issues/136
			// scheduledWorkers.scheduleWithFixedDelay(new TaskPersistNodes(nodeMgr), 30000,
			// PERIOD_PERSIST_NODES, TimeUnit.MILLISECONDS);

			workers.submit(new TaskClear());
			workers.submit(new TaskConnectPeers());

		} catch (IOException e) {
			if (showLog)
				System.out.println("<p2p tcp-server-io-exception>");
		} catch (Throwable e) {
			if (showLog)
				System.out.println("<p2p tcp-server-error>" + e.toString());
		}
	}

	@Override
	public INode getRandom() {
		return nodeMgr.getRandom();
	}

	@Override
	public Map<Integer, INode> getActiveNodes() {
		return new HashMap<>(this.nodeMgr.getActiveNodesMap());
	}

	/**
	 * for test
	 */
	// void clearTempNodes() {
	// this.nodeMgr.clearTempNodes();
	// }

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
		cachedReqHandshake1 = new ReqHandshake1(selfNodeId, selfNetId, this.selfIp, this.selfPort,
				this.selfRevision.getBytes(), supportedVersions);
	}

	@Override
	public void send(int _nodeIdHashcode, final Msg _msg) {
		Node node = this.nodeMgr.getActiveNode(_nodeIdHashcode);
		if (node != null) {
			SocketChannel channel = node.getChannel();
			if (channel.isOpen()) {
				if (showLog)
					System.out.println("sending: " + _msg.getClass().getSimpleName() + " to " + channel);
				this.workers
						.submit(new TaskWrite(ioLoop, workers, showLog, node.getIdShort(), node.getChannel(), _msg));
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
	public List<Short> versions() {
		return new ArrayList<Short>(versions);
	}

	@Override
	public int chainId() {
		return this.selfNetId;
	}

	@Override
	public void errCheck(int nodeIdHashcode, String _displayId) {
		int cnt = (errCnt.get(nodeIdHashcode) == null ? 1 : (errCnt.get(nodeIdHashcode).intValue() + 1)) ;

		if (cnt > 2) {
			dropActive(nodeIdHashcode);
			errCnt.put(nodeIdHashcode, 0);

			System.out.println("<drop node: " + (_displayId == null ? nodeIdHashcode : _displayId) + ">");
		} else {
			errCnt.put(nodeIdHashcode, cnt);
		}
	}

	private final class HandleInboundEvent implements Task {

		// this is triggered everytime a new event comes in from channel
		@Override
		public void channelReady(SelectableChannel channel, SelectionKey key) {
			try {
				ServerSocketChannel serverChannel = (ServerSocketChannel) channel;
				SocketChannel socketChan = serverChannel.accept();

				registerChannel(P2pMgr.this.ioLoop, socketChan, SelectionKey.OP_READ, P2pMgr.this.nodeMgr,
						new HandleChannel());

			} catch (IOException e) {
				if (P2pMgr.this.showLog) {
					System.out.println("failed to register channel");
					e.printStackTrace();
				}
			} catch (Throwable e) {
				if (P2pMgr.this.showLog) {
					System.out.println("register channel error");
					e.printStackTrace();
				}
			}
		}

		@Override
		public void acceptMessage(SelectableChannel channel, SelectionKey key, ByteBuffer buffer) {
			// do nothing, dont accept outbound messages from this task
		}

		@Override
		public void channelUnregistered(SelectableChannel channel, Throwable cause) {
			try {
				channel.close();
			} catch (IOException e) {
				if (P2pMgr.this.showLog) {
					System.out.println("failed to close channel");
				}
			}
		}
	}

	private final class HandleChannel implements Task {

		private final static int MAX_MESSAGE_QUEUE_SIZE = 128;

		private final Queue<ByteBuffer> byteBuffers = new LinkedBlockingQueue<>(MAX_MESSAGE_QUEUE_SIZE);

		private long lastMessageQueueOverflow = 0;

		@Override
		public void channelReady(SelectableChannel channel, SelectionKey key) {
			try {

				if (key.isAcceptable())
					throw new UnsupportedOperationException("HandleChannel does not support acceptable keys");

				if (key.isReadable()) {
					P2pMgr.this.read(key);
				}

				if (key.isWritable()) {
					write(channel, key);
				}
			} catch (IOException e) {
				if (showLog) {
					System.out.println("<p2p-io-exception ip=" + ((ChannelBuffer) key.attachment()).ip + " cause=\""
							+ e.toString() + "\">");
				}

				// on any IO exception, cancel the channel, no need to close it should be
				// closed when channelUnregistered is triggered
				P2pMgr.this.ioLoop.cancel(key);
				byteBuffers.clear();
			} catch (Throwable e) {
				// print stack trace for all throwable except IO exception
				if (showLog) {
					e.printStackTrace();
				}
			}
		}

		private void write(SelectableChannel channel, SelectionKey key) throws IOException {
			SocketChannel chan = (SocketChannel) channel;
			try {
				LOOP: while (!byteBuffers.isEmpty()) {
					ByteBuffer buf = byteBuffers.peek();
					// try to write as much as we can
					int ret;
					while (buf != null && buf.hasRemaining()) {
						ret = chan.write(buf);
						if (ret <= 0) {
							break LOOP;
						}
					}

					// if we finish processing simply remove
					if (!buf.hasRemaining()) {
						byteBuffers.remove(buf);
					}
				}
			} finally {
				if (byteBuffers.isEmpty())
					key.interestOps(SelectionKey.OP_READ);
			}
		}

		@Override
		public void acceptMessage(SelectableChannel channel, SelectionKey key, ByteBuffer buffer) {
			if (byteBuffers.offer(buffer)) {
				key.interestOps(SelectionKey.OP_WRITE);
			} else {
				if (P2pMgr.this.showLog) {
					long now = System.currentTimeMillis();
					if (now - lastMessageQueueOverflow > 5000) {
						System.out.println("message queue is full");
						lastMessageQueueOverflow = now;
					}
				}
			}
		}

		@Override
		public void channelUnregistered(SelectableChannel channel, Throwable cause) {
			try {
				channel.close();
			} catch (IOException e) {
				if (P2pMgr.this.showLog) {
					System.out.println("failed to close channel");
				}
			}
		}
	}

	private final class TaskStatus implements Runnable {
		@Override
		public void run() {
			Thread.currentThread().setName("p2p-ts");
			String status = nodeMgr.dumpNodeInfo(selfShortId);
			System.out.println(status);
			// TODO: reenable this
			// if (printReport) {
			// try {
			// Files.write(Paths.get(reportFolder, System.currentTimeMillis() +
			// "-p2p-report.out"),
			// status.getBytes());
			// } catch (IOException e) {
			// e.printStackTrace();
			// }
			// }
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
					break;
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
				byte[] nodeIp = node.getIp();
				int nodePort = node.getPort();

				Map<Integer, INode> map = new HashMap<>();
				map.putAll(nodeMgr.getOutboundNodes());
				map.putAll(nodeMgr.getActiveNodesMap());
				boolean connected = (selfNodeIdHash == nodeIdHash
						|| Arrays.equals(selfIp, nodeIp) && selfPort == nodePort)
						|| (map.values().stream().anyMatch(n -> n.getIdHash() == nodeIdHash
								|| Arrays.equals(n.getIp(), nodeIp) && n.getPort() == nodePort));

				if (!connected) {
					int _port = node.getPort();
					try {
						SocketChannel channel = SocketChannel.open();
						if (showLog)
							System.out.println("<p2p try-connect-" + node.getIpStr() + ">");
						channel.socket().connect(new InetSocketAddress(node.getIpStr(), _port),
								TIMEOUT_OUTBOUND_CONNECT);
						configChannel(channel);

						if (channel.finishConnect() && channel.isConnected()) {
							ChannelBuffer rb = new ChannelBuffer();
							rb.nodeIdHash = nodeIdHash;
							rb.ip = node.getIpStr();
							rb.port = node.getPort();
							rb.task = P2pMgr.this.handleRead;
							P2pMgr.this.ioLoop.attachChannel(channel, SelectionKey.OP_READ, rb, rb.task);

							node.setChannel(channel);
							node.setPortConnected(channel.socket().getLocalPort());
							addOutboundNode(node);

							// fire extended handshake request first
							workers.submit(new TaskWrite(ioLoop, workers, showLog, node.getIdShort(), channel,
									cachedReqHandshake1));
							workers.submit(new TaskWrite(ioLoop, workers, showLog, node.getIdShort(), channel,
									cachedReqHandshake));

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
					} catch (Throwable e) {
						if (showLog)
							System.out.println("<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port
									+ " result=error>" + e.toString());
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

					// selectorLock.lock();
					nodeMgr.rmTimeOutInbound(P2pMgr.this);
					// selectorLock.unlock();

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
							// selectorLock.lock();
							closeSocket(node.getChannel());
							// selectorLock.unlock();
							outboundIt.remove();

							if (showLog)
								System.out.println("<p2p-clear-outbound ip=" + node.getIpStr() + " node="
										+ node.getIdShort() + ">");
						}
					}

					// selectorLock.lock();
					nodeMgr.rmTimeOutActives(P2pMgr.this);
					// selectorLock.unlock();

				} catch (InterruptedException interrupted) {
					if (showLog)
						System.out.println("<p2p-clear interrupted>");
				} catch (Throwable e) {
					if (showLog)
						System.out.println("<p2p-clear-throw>" + e.toString());
				}
			}
		}
	}

	public boolean isShowLog() {
		return showLog;
	};
}

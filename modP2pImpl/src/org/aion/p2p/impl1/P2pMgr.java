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

import org.aion.p2p.*;
import org.aion.p2p.impl.TaskRequestActiveNodes;
import org.aion.p2p.impl.TaskUPnPManager;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl.zero.msg.*;
import org.apache.commons.collections4.map.LRUMap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

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

	// private final static int TIMEOUT_OUTBOUND_NODES = 10000;
	private final static int TIMEOUT_OUTBOUND_NODES = 10000;

	private final static int PERIOD_UPNP_PORT_MAPPING = 3600000;

	private final static int TIMEOUT_MSG_READ = 10000;

	private final int maxTempNodes;
	private final int maxActiveNodes;

	private final boolean syncSeedsOnly;
	private final boolean showStatus;
	final boolean showLog;
	private final boolean printReport;
	private final String reportFolder;
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
	private Lock selectorLock = new ReentrantLock();

	private ScheduledThreadPoolExecutor scheduledWorkers;

	private Map<Integer, Node> allNid = new HashMap<>();

	private final Map<Integer, Integer> errCnt = Collections.synchronizedMap(new LRUMap<>(128));

	private int errTolerance;


	enum Dest {
		INBOUND, OUTBOUND, ACTIVE;
	}

	private static class MsgOut {
		public MsgOut(int _nid, Msg _msg, Dest _dst) {
			nid = _nid;
			msg = _msg;
			dest = _dst;
		}

		int nid;
		Msg msg;
		Dest dest;
	}

	private static class MsgIn {
		public MsgIn(int nid, String nsid, int route, byte[] msg) {
			this.nid = nid;
			this.nsid = nsid;
			this.route = route;
			this.msg = msg;
		}

		int nid;
		String nsid;
		int route;
		byte[] msg;
	}

	private LinkedBlockingQueue<MsgOut> sendMsgQue = new LinkedBlockingQueue<>();

	private LinkedBlockingQueue<MsgIn> receiveMsgQue = new LinkedBlockingQueue<>();

	private AtomicBoolean start = new AtomicBoolean(true);

	// initialed after handlers registration completed
	private static ReqHandshake1 cachedReqHandshake1;
	private static ReqHandshake cachedReqHandshake;
	private static ResHandshake1 cachedResHandshake1;
	private static ResHandshake cachedResHandshake;

	private final class TaskInbound implements Runnable {
		@Override
		public void run() {

			// read buffer pre-alloc. 1M max.

			while (start.get()) {

				int num;
				try {
					// num = selector.select(1);
					num = selector.selectNow();
				} catch (IOException e) {
					if (showLog)
						System.out.println("<p2p inbound-select-io-exception>");
					continue;
				}

				if (num == 0) {
					try {
						Thread.sleep(0, 10);
					} catch (Exception e) {
					}
					continue;
				}

				// selectorLock.lock();
				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

				while (keys.hasNext() && (num-- > 0)) {

					final SelectionKey sk = keys.next();
					keys.remove();

					if (!sk.isValid())
						continue;

					if (sk.isAcceptable())
						accept();

					if (sk.isReadable()) {

						ChannelBuffer chanBuf = (ChannelBuffer) (sk.attachment());
						try {

							int ret;
							int cnt = 0;

							while ((ret = ((SocketChannel) sk.channel()).read(chanBuf.readBuf)) > 0) {
								cnt += ret;
							}

							// read empty select key, continue.
							if (cnt <= 0) {
								continue;
							}

							int assertBufRemain = chanBuf.buffRemain;
							int assertTotalCnt = cnt;

							int prevCnt = cnt + chanBuf.buffRemain;

							do {
								cnt = read(sk, chanBuf.readBuf, prevCnt);

								if (prevCnt == cnt) {
									break;
								} else
									prevCnt = cnt;

							} while (cnt > 0);

							// check if really read data.
							if (cnt > prevCnt) {
								chanBuf.buffRemain = 0;
								throw new P2pException(
										"IO read overflow!  suppose read:" + prevCnt + " real left:" + cnt);
							}

							chanBuf.buffRemain = cnt;

							if (cnt == 0) {
								chanBuf.readBuf.rewind();
							} else {
								// cycline buffer
								int remain = chanBuf.readBuf.remaining();
								if (remain < 128 * 1024) {

//									if (showLog)
//										System.out.println(" NIO new buffer! , size = " + cnt + " originTotal_read:"
//												+ assertTotalCnt + " orig_remain:" + assertBufRemain
//												+ " __________ buf remain:" + chanBuf.readBuf.remaining() + " limit:"
//												+ chanBuf.readBuf.limit());

									// if still not read buffer , then copy
									// it
									// and move it to front. later , this
									// can
									// change to ring buffer.
									int currPos = chanBuf.readBuf.position();
									if (cnt != 0) {
										byte[] tmp = new byte[cnt];
										chanBuf.readBuf.position(currPos - cnt);
										chanBuf.readBuf.get(tmp);
										chanBuf.readBuf.rewind();
										chanBuf.readBuf.put(tmp);
									}

								}
							}

						} catch (NullPointerException e) {

							if (showLog) {
								System.out.println("<p2p read-msg-null-exception>");
							}

							closeSocket((SocketChannel) sk.channel());

							chanBuf.isClosed.set(true);
							chanBuf.readBuf.position(0);
						} catch (P2pException e) {

							if (showLog) {
								System.out.println("<p2p read-msg-P2p-exception>");
							}

							// e.printStackTrace();

							// continue;

							closeSocket((SocketChannel) sk.channel());
							chanBuf.isClosed.set(true);
							chanBuf.readBuf.rewind();

						} catch (ClosedChannelException e) {
							if (showLog) {
								System.out.println("<p2p readfail-closechannel>");
							}
							closeSocket((SocketChannel) sk.channel());

						} catch (IOException e) {

							if (showLog) {
								System.out.println("<p2p read-msg-io-exception: " + e.getMessage() + ">");
							}

							e.printStackTrace();

							closeSocket((SocketChannel) sk.channel());
							chanBuf.isClosed.set(true);
							chanBuf.readBuf.position(0);

						} catch (CancelledKeyException e) {
							if (showLog) {
								System.out.println("<p2p key-cancelled-exception>");
							}

							chanBuf.isClosed.set(true);
							closeSocket((SocketChannel) sk.channel());
						}
					}
				}
				// selectorLock.unlock();
			}
			if (showLog)
				System.out.println("<p2p-pi shutdown>");
		}
	}

	private final class TaskSend implements Runnable {
		@Override
		public void run() {

			while (true) {
				try {
					MsgOut mo = sendMsgQue.take();

					Node node = null;
					switch (mo.dest) {
					case ACTIVE:
						node = nodeMgr.getActiveNode(mo.nid);
						break;
					case INBOUND:
						node = nodeMgr.getInboundNode(mo.nid);
						break;
					case OUTBOUND:
						node = nodeMgr.getOutboundNode(mo.nid);
						break;
					}

					// if still not found , let's try all nodes.
					if (node == null) {
						node = allNid.get(mo.nid);
					}
					if (node != null) {
						SelectionKey sk = node.getChannel().keyFor(selector);

						if (sk != null) {
							Object attachment = sk.attachment();
							if (attachment != null) {
								TaskWrite tw = new TaskWrite(showLog, node.getIdShort(), node.getChannel(), mo.msg,
										(ChannelBuffer) attachment, P2pMgr.this);
								tw.run();
							}
						}
					} else {
						System.out.println("send msg, failed to find node! M:" + mo.msg + " D:" + mo.dest);
					}
				} catch (InterruptedException e) {
					System.out.println("Task send interrupted");
					break;
				}
			}
			// Node node = this.nodeMgr.getActiveNode(_nodeIdHashcode);
			// if (node != null) {
			// SelectionKey sk = node.getChannel().keyFor(selector);
			//
			// if (sk != null) {
			// Object attachment = sk.attachment();
			// if (attachment != null)
			// workers.submit(new TaskWrite(workers, showLog, node.getIdShort(),
			// node.getChannel(), _msg,
			// (ChannelBuffer) attachment, this));
			// }
			// }
		}
	}

	private final class TaskReceive implements Runnable {
		@Override
		public void run() {

			while (true) {
				try {
					MsgIn mi = receiveMsgQue.take();

					List<Handler> hs = handlers.get(mi.route);
					if (hs == null)
						return;
					for (Handler hlr : hs) {
						if (hlr == null)
							continue;

						try {
							hlr.receive(mi.nid, mi.nsid, mi.msg);
						} catch (Exception e) {
							System.out.println("Exception during kernel message handling:");
							e.printStackTrace();
						}
					}
				} catch (InterruptedException e) {
					System.out.println("Task receive interrupted");
					break;
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
			if (printReport) {
				try {
					Files.write(Paths.get(reportFolder, System.currentTimeMillis() + "-p2p-report.out"),
							status.getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			// nodeMgr.dumpAllNodeInfo();
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

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						if (showLog)
							System.out.println("<p2p-tcp-interrupted>");
						return;
					}
					continue;
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
						System.out.println("<p2p-tcp-interrupted>");
					return;
				}
				int nodeIdHash = node.getIdHash();
				if (!nodeMgr.getOutboundNodes().containsKey(nodeIdHash) && !nodeMgr.hasActiveNode(nodeIdHash)) {
					int _port = node.getPort();
					try {
						SocketChannel channel = SocketChannel.open();
						if (showLog)
							System.out.println("<p2p try-connect-" + node.getIpStr() + ">");
						channel.socket().connect(new InetSocketAddress(node.getIpStr(), _port),
								TIMEOUT_OUTBOUND_CONNECT);
						configChannel(channel);

						if (channel.finishConnect() && channel.isConnected()) {
							// selectorLock.lock();
							SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
							ChannelBuffer rb = new ChannelBuffer();
							rb.nodeIdHash = nodeIdHash;
							sk.attach(rb);

							node.setChannel(channel);
							node.setPortConnected(channel.socket().getLocalPort());

							nodeMgr.addOutboundNode(node);
							allNid.put(nodeIdHash, node);
							// selectorLock.unlock();

							// fire extended handshake request first
							// workers.submit(new TaskWrite(workers, showLog,
							// node.getIdShort(), channel, cachedReqHandshake1,
							// rb, P2pMgr.this));

							// if don't sleep a while and direct send handshake, sometime it just failed by
							// without response.
							try {
								Thread.sleep(1000);
							} catch (Exception e) {
							}
							sendMsgQue.offer(new MsgOut(node.getIdHash(), cachedReqHandshake1, Dest.OUTBOUND));

							sendMsgQue.offer(new MsgOut(node.getIdHash(), cachedReqHandshake, Dest.OUTBOUND));

							// workers.submit(new TaskWrite(workers, showLog,
							// node.getIdShort(), channel,
							// cachedReqHandshake, rb, P2pMgr.this));

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

					// selectorLock.lock();
					nodeMgr.rmTimeOutInbound(P2pMgr.this);
					// selectorLock.unlock();

					// clean up temp nodes list if metric failed.
					nodeMgr.rmMetricFailedNodes();

					Iterator outboundIt = nodeMgr.getOutboundNodes().keySet().iterator();
					// Create a list with capacity up to all nodes to remove msgs in queue
					Set<Integer> msgClear = new HashSet<>(nodeMgr.getOutboundNodes().keySet().size());
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

							//Add hashcode to remove from msg buffer
							msgClear.add(node.getChannel().hashCode());

							if (showLog)
								System.out.println("<p2p-clear outbound-timeout>");
						}
					}

					// selectorLock.lock();
					nodeMgr.rmTimeOutActives(P2pMgr.this);
					// selectorLock.unlock();

					Predicate<MsgOut> removePredicate = p->msgClear.contains(p.nid);

					//Remove all nodes which have timed out (Single sweep)
					boolean hasRemoved = sendMsgQue.removeIf(removePredicate);

					if(hasRemoved)
					    System.out.println("Removed msgs from " + msgClear.size() + " nodes");

				} catch (Exception e) {
				}
			}
		}
	}

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
			boolean _bootlistSyncOnly, boolean _printReport, String _reportFolder, int _errorTolerance) {
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
		this.printReport = _printReport;
		this.reportFolder = _reportFolder;
		this.errTolerance = _errorTolerance;

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
	private void configChannel(final SocketChannel _channel) throws IOException {
		_channel.configureBlocking(false);
		_channel.socket().setSoTimeout(TIMEOUT_MSG_READ);
		// _channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
		// _channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
		// _channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
	}

	/**
	 * @param _sc
	 *            SocketChannel
	 */
	public void closeSocket(final SocketChannel _sc) {
		if (showLog)
			System.out.println("<p2p close-socket->");

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

	private void accept() {
		SocketChannel channel;
		try {
			channel = tcpServer.accept();
			configChannel(channel);

			SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
			sk.attach(new ChannelBuffer());

			String ip = channel.socket().getInetAddress().getHostAddress();
			int port = channel.socket().getPort();

			if (syncSeedsOnly && nodeMgr.isSeedIp(ip)) {
				// close the channel and return.
				channel.close();
				return;
			}

			// Node node = new Node(false, ip);
			Node node = nodeMgr.allocNode(ip, 0, port);

			node.setChannel(channel);
			nodeMgr.inboundNodeAdd(node);

			allNid.put(node.getChannel().hashCode(), node);

			if (showLog)
				System.out.println("<p2p new-connection " + ip + ":" + port + ">");

		} catch (IOException e) {
			if (showLog)
				System.out.println("<p2p inbound-accept-io-exception>");
			return;
		}
	}

	/**
	 *            SocketChannel
	 * @throws IOException
	 *             IOException
	 */
	private int readHeader(final ChannelBuffer _cb, ByteBuffer readBuffer, int cnt) throws IOException {

		// int ret;
		// while ((ret = _sc.read(_cb.headerBuf)) > 0) {
		// }

		if (cnt < Header.LEN)
			return cnt;

		int origPos = readBuffer.position();

		int startP = origPos - cnt;

		readBuffer.position(startP);

		_cb.readHead(readBuffer);

		readBuffer.position(origPos);

		return cnt - Header.LEN;

	}

	/**
	 *            SocketChannel
	 * @throws IOException
	 *             IOException
	 */
	private int readBody(final ChannelBuffer _cb, ByteBuffer readBuffer, int cnt) throws IOException {

		int bodyLen = _cb.header.getLen();

		// some msg have nobody.
		if (bodyLen == 0) {
			_cb.body = new byte[0];
			return cnt;
		}

		if (cnt < bodyLen)
			return cnt;

		int origPos = readBuffer.position();
		int startP = origPos - cnt;

		readBuffer.position(startP);

		_cb.readBody(readBuffer);

		readBuffer.position(origPos);

		return cnt - bodyLen;
	}

	/**
	 * @param _sk
	 *            SelectionKey
	 * @throws IOException
	 *             IOException
	 */
	private int read(final SelectionKey _sk, ByteBuffer readBuffer, int cnt) throws IOException {

		int currCnt = 0;

		if (_sk.attachment() == null) {
			throw new P2pException("attachment is null");
		}
		ChannelBuffer rb = (ChannelBuffer) _sk.attachment();

		// read header
		if (!rb.isHeaderCompleted()) {
			currCnt = readHeader(rb, readBuffer, cnt);
		} else {
			currCnt = cnt;
		}

		// if(rb.isHeaderCompleted() &&
		// !handlers.containsKey(rb.header.getRoute())){
		// // TODO: Test
		// return;
		// }

		// read body
		if (rb.isHeaderCompleted() && !rb.isBodyCompleted()) {
			currCnt = readBody(rb, readBuffer, currCnt);
		}

		if (!rb.isBodyCompleted())
			return currCnt;

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

		return currCnt;

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
		if (node != null && node.peerMetric.notBan()) {
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
					// workers.submit(new TaskWrite(workers, showLog,
					// node.getIdShort(), node.getChannel(),
					// cachedResHandshake1, _buffer, this));
					sendMsgQue.offer(new MsgOut(node.getChannel().hashCode(), cachedResHandshake1, Dest.INBOUND));
				}
				// handshake 0
				else {
					// workers.submit(new TaskWrite(workers, showLog,
					// node.getIdShort(), node.getChannel(),
					// cachedResHandshake, _buffer, this));

					sendMsgQue.offer(new MsgOut(node.getChannel().hashCode(), cachedResHandshake, Dest.INBOUND));
				}
				nodeMgr.moveInboundToActive(_channelHash, this);
			} else {
				if (isShowLog())
					System.out.println("incompatible netId ours=" + this.selfNetId + " theirs=" + _netId);
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
					// workers.submit(new TaskWrite(workers, showLog,
					// node.getIdShort(), node.getChannel(),
					// new ResActiveNodes(nodeMgr.getActiveNodesList()), rb,
					// this));
					sendMsgQue.offer(new MsgOut(node.getIdHash(), new ResActiveNodes(nodeMgr.getActiveNodesList()),
							Dest.ACTIVE));
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

		// fail back to inbound
		if (node == null) {
			node = nodeMgr.getInboundNode(_nodeIdHash);
		}

		// fail back to outbound
		if (node == null) {
			node = nodeMgr.getOutboundNode(_nodeIdHash);
		}

		if (node != null) {
			int nid = node.getIdHash();
			String nsid = node.getIdShort();

			node.refreshTimestamp();
			receiveMsgQue.offer(new MsgIn(nid, nsid, _route, _msgBytes));
		} else {
			System.out.println(" handle kernel msg failed. can't find node:" + _nodeIdHash);
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

			tcpServer = ServerSocketChannel.open();
			tcpServer.configureBlocking(false);
			tcpServer.socket().setReuseAddress(true);
			tcpServer.socket().bind(new InetSocketAddress(Node.ipBytesToStr(selfIp), selfPort));
			tcpServer.register(selector, SelectionKey.OP_ACCEPT);

			Thread thrdIn = new Thread(new TaskInbound(), "p2p-in");
			thrdIn.setPriority(Thread.NORM_PRIORITY);
			thrdIn.start();

			for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
				Thread thrdOut = new Thread(new TaskSend(), "p2p-out-" + i);
				thrdOut.setPriority(Thread.NORM_PRIORITY);
				thrdOut.start();
			}

			for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
				Thread t = new Thread(new TaskReceive(), "p2p-worker-" + i);
				t.setPriority(Thread.NORM_PRIORITY);
				t.start();
			}

			if (upnpEnable)
				scheduledWorkers.scheduleWithFixedDelay(new TaskUPnPManager(selfPort), 1, PERIOD_UPNP_PORT_MAPPING,
						TimeUnit.MILLISECONDS);

			if (showStatus)
				scheduledWorkers.scheduleWithFixedDelay(new TaskStatus(), 2, PERIOD_SHOW_STATUS, TimeUnit.MILLISECONDS);

			if (!syncSeedsOnly)
				scheduledWorkers.scheduleWithFixedDelay(new TaskRequestActiveNodes(this), 5000,
						PERIOD_REQUEST_ACTIVE_NODES, TimeUnit.MILLISECONDS);

			// test versioning
			// List<Hello> hello = new ArrayList<>();
			// hello.add(new Hello("HELLO"));
			// hello.add(new Hello("BONJOUR"));
			// hello.add(new Hello("HOLA"));
			// hello.add(new Hello("CIAO"));
			// hello.add(new Hello("OLÀ"));
			// hello.add(new Hello("ZDRAS-TVUY-TE"));
			// scheduledWorkers.scheduleWithFixedDelay(()->{
			// int ran = ThreadLocalRandom.current().nextInt(0,6 );
			// INode node = getRandom(NodeRandPolicy.RND, 0);
			// if (node != null)
			// send(node.getIdHash(), hello.get(ran));
			// }, 3000, 3000, TimeUnit.MILLISECONDS);

			// rem out for bug: https://github.com/aionnetwork/aion/issues/136
			// scheduledWorkers.scheduleWithFixedDelay(new
			// TaskPersistNodes(nodeMgr), 30000, PERIOD_PERSIST_NODES,
			// TimeUnit.MILLISECONDS);

			Thread thrdClear = new Thread(new TaskClear(), "p2p-clear");
			thrdClear.setPriority(Thread.NORM_PRIORITY);
			thrdClear.start();

			Thread thrdConn = new Thread(new TaskConnectPeers(), "p2p-conn");
			thrdConn.setPriority(Thread.NORM_PRIORITY);
			thrdConn.start();

		} catch (IOException e) {
			if (showLog)
				System.out.println("<p2p tcp-server-io-exception>");
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

	public int getTempNodesCount() {
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
		// Node node = this.nodeMgr.getActiveNode(_nodeIdHashcode);
		// if (node != null) {
		// SelectionKey sk = node.getChannel().keyFor(selector);
		//
		// if (sk != null) {
		// Object attachment = sk.attachment();
		// if (attachment != null)
		// workers.submit(new TaskWrite(workers, showLog, node.getIdShort(),
		// node.getChannel(), _msg,
		// (ChannelBuffer) attachment, this));
		// }
		// }

		sendMsgQue.offer(new MsgOut(_nodeIdHashcode, _msg, Dest.ACTIVE));
	}

	@Override
	public void shutdown() {
		start.set(false);
		scheduledWorkers.shutdownNow();
		nodeMgr.shutdown(this);

		for (List<Handler> hdrs : handlers.values()) {
			hdrs.forEach(hdr -> hdr.shutDown());
		}
	}

	@Override
	public List<Short> versions() {
		return new ArrayList<Short>(versions);
	}

	@Override
	public int chainId() {
		return selfNetId;
	}

	/**
	 * Remove an active node if exists.
	 *
	 * @param nodeIdHash
	 */
	public void removeActive(int nodeIdHash) {
		nodeMgr.removeActive(nodeIdHash, this);
	}

	@Override
	public void dropActive(Integer _nodeIdHash) {
		nodeMgr.dropActive(_nodeIdHash, this);
	}

	public boolean isShowLog() {
		return showLog;
	}

	@Override
	public void errCheck(int nodeIdHashcode, String _displayId) {
		int cnt = (errCnt.get(nodeIdHashcode) == null ? 1 : (errCnt.get(nodeIdHashcode).intValue() + 1)) ;

		if (cnt > this.errTolerance) {
			ban(nodeIdHashcode);
			errCnt.put(nodeIdHashcode, 0);

			if (isShowLog()) {
				System.out.println("<ban node: " + (_displayId == null ? nodeIdHashcode : _displayId) + ">");
			}
		} else {
			errCnt.put(nodeIdHashcode, cnt);
		}
	}

	private void ban(int nodeIdHashcode) {
		nodeMgr.ban(nodeIdHashcode);
		nodeMgr.dropActive(nodeIdHashcode, this);
	}
}

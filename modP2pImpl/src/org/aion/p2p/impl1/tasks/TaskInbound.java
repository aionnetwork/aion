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
package org.aion.p2p.impl1.tasks;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.Header;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.zero.msg.ReqHandshake;
import org.aion.p2p.impl.zero.msg.ReqHandshake1;
import org.aion.p2p.impl.zero.msg.ResActiveNodes;
import org.aion.p2p.impl.zero.msg.ResHandshake;
import org.aion.p2p.impl.zero.msg.ResHandshake1;
import org.aion.p2p.impl1.P2pException;
import org.aion.p2p.impl1.P2pMgr.Dest;

public class TaskInbound implements Runnable {
    private final IP2pMgr mgr;
    private final Selector selector;
    private final INodeMgr nodeMgr;
    private final Map<Integer, List<Handler>> handlers;
    private AtomicBoolean start;
    private ServerSocketChannel tcpServer;
    private BlockingQueue<MsgOut> sendMsgQue;
    private ResHandshake1 cachedResHandshake1;
    private BlockingQueue<MsgIn> receiveMsgQue;

    public TaskInbound(
            IP2pMgr _mgr,
            Selector _selector,
            AtomicBoolean _start,
            INodeMgr _nodeMgr,
            ServerSocketChannel _tcpServer,
            Map<Integer, List<Handler>> _handlers,
            BlockingQueue<MsgOut> _sendMsgQue,
            ResHandshake1 _cachedResHandshake1,
            BlockingQueue<MsgIn> _receiveMsgQue) {

        this.mgr = _mgr;
        this.selector = _selector;
        this.start = _start;
        this.nodeMgr = _nodeMgr;
        this.tcpServer = _tcpServer;
        this.handlers = _handlers;
        this.sendMsgQue = _sendMsgQue;
        this.cachedResHandshake1 = _cachedResHandshake1;
        this.receiveMsgQue = _receiveMsgQue;
    }

    @Override
    public void run() {

        // read buffer pre-alloc. @ max_body_size
        ByteBuffer readBuf = ByteBuffer.allocate(P2pConstant.MAX_BODY_SIZE);

        while (start.get()) {

            try {
                Thread.sleep(0, 1);
            } catch (Exception e) {
            }

            int num;
            try {
                num = this.selector.selectNow();
            } catch (IOException e) {
                if (this.mgr.isShowLog()) System.out.println("<p2p inbound-select-io-exception>");
                continue;
            }

            if (num == 0) {
                continue;
            }

            Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();

            while (keys.hasNext() && (num-- > 0)) {

                final SelectionKey sk = keys.next();
                keys.remove();

                try {

                    if (!sk.isValid()) continue;

                    if (sk.isAcceptable()) accept();

                    if (sk.isReadable()) {

                        readBuf.rewind();

                        ChannelBuffer chanBuf = (ChannelBuffer) (sk.attachment());
                        try {

                            int ret;
                            int cnt = 0;

                            while ((ret = ((SocketChannel) sk.channel()).read(readBuf)) > 0) {
                                cnt += ret;
                            }

                            // read empty select key, continue.
                            if (cnt <= 0) {
                                continue;
                            }

                            int prevCnt = cnt + chanBuf.buffRemain;
                            ByteBuffer forRead;

                            if (chanBuf.buffRemain != 0) {
                                byte[] alreadyRead = new byte[cnt];

                                readBuf.position(0);
                                readBuf.get(alreadyRead);
                                forRead = ByteBuffer.allocate(prevCnt);
                                forRead.put(chanBuf.remainBuffer);
                                forRead.put(alreadyRead);
                            } else {
                                forRead = readBuf;
                            }

                            do {
                                cnt = read(sk, forRead, prevCnt);

                                if (prevCnt == cnt) {
                                    break;
                                } else prevCnt = cnt;

                            } while (cnt > 0);

                            // check if really read data.
                            if (cnt > prevCnt) {
                                chanBuf.buffRemain = 0;
                                throw new P2pException(getReadOverflowMsg(prevCnt, cnt));
                            }

                            chanBuf.buffRemain = cnt;

                            if (cnt == 0) {
                                readBuf.rewind();
                            } else {
                                // there are no perfect cycling buffer in jdk
                                // yet.
                                // simply just buff move for now.
                                // @TODO: looking for more efficient way.

                                int currPos = forRead.position();
                                chanBuf.remainBuffer = new byte[cnt];
                                forRead.position(currPos - cnt);
                                forRead.get(chanBuf.remainBuffer);
                                readBuf.rewind();
                            }

                        } catch (NullPointerException e) {
                            this.mgr.closeSocket(
                                    (SocketChannel) sk.channel(),
                                    chanBuf.displayId + "-read-msg-null-exception");
                            chanBuf.isClosed.set(true);
                        } catch (P2pException e) {
                            this.mgr.closeSocket(
                                    (SocketChannel) sk.channel(),
                                    chanBuf.displayId + "-read-msg-p2p-exception");
                            chanBuf.isClosed.set(true);

                        } catch (ClosedChannelException e) {
                            this.mgr.closeSocket(
                                    (SocketChannel) sk.channel(),
                                    chanBuf.displayId + "-read-msg-closed-channel-exception");

                        } catch (IOException e) {
                            this.mgr.closeSocket(
                                    (SocketChannel) sk.channel(),
                                    chanBuf.displayId
                                            + "-read-msg-io-exception: "
                                            + e.getMessage());
                            chanBuf.isClosed.set(true);

                        } catch (CancelledKeyException e) {
                            chanBuf.isClosed.set(true);
                            this.mgr.closeSocket(
                                    (SocketChannel) sk.channel(),
                                    chanBuf.displayId + "-read-msg-key-cancelled-exception");
                        } catch (Exception e) {
                            if (this.mgr.isShowLog())
                                System.out.println("<p2p-pi global exception>");
                        }
                    }
                } catch (Exception ex) {
                    if (this.mgr.isShowLog()) {
                        System.out.println("<p2p-pi on-sk-exception=" + ex.getMessage() + ">");
                        ex.printStackTrace();
                    }
                }
            }
        }
        if (this.mgr.isShowLog()) System.out.println("<p2p-pi shutdown>");
    }

    private void accept() {
        SocketChannel channel;
        try {

            if (this.nodeMgr.activeNodesSize() >= this.mgr.getMaxActiveNodes()) return;

            channel = this.tcpServer.accept();
            this.mgr.configChannel(channel);

            SelectionKey sk = channel.register(this.selector, SelectionKey.OP_READ);
            sk.attach(new ChannelBuffer(this.mgr.isShowLog()));

            String ip = channel.socket().getInetAddress().getHostAddress();
            int port = channel.socket().getPort();

            if (this.mgr.isSyncSeedsOnly() && this.nodeMgr.isSeedIp(ip)) {
                channel.close();
                return;
            }

            INode node = this.nodeMgr.allocNode(ip, 0);
            node.setChannel(channel);
            this.nodeMgr.addInboundNode(node);

            if (this.mgr.isShowLog())
                System.out.println("<p2p new-connection " + ip + ":" + port + ">");

        } catch (IOException e) {
            if (this.mgr.isShowLog()) System.out.println("<p2p inbound-accept-io-exception>");
        }
    }

    /**
     * SocketChannel
     */
    private int readHeader(final ChannelBuffer _cb, ByteBuffer readBuffer, int cnt) {

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
     */
    private int readBody(final ChannelBuffer _cb, ByteBuffer readBuffer, int cnt) {

        int bodyLen = _cb.header.getLen();

        // some msg have nobody.
        if (bodyLen == 0) {
            _cb.body = new byte[0];
            return cnt;
        }

        if (cnt < bodyLen) { return cnt; }

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
    private int read(final SelectionKey _sk, ByteBuffer _readBuffer, int _cnt) throws IOException {

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

        if (!rb.isBodyCompleted()) { return currCnt; }

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
                        ((route == this.mgr.txBroadCastRoute)
                                ? P2pConstant.READ_MAX_RATE_TXBC
                                : P2pConstant.READ_MAX_RATE));

        if (!underRC) {
            if (this.mgr.isShowLog()) {
                System.out.println(
                    getRouteMsg(ver, ctrl, act, rb.getRouteCount(route).count, rb.displayId));
            }
            return currCnt;
        }

        switch (ver) {
            case Ver.V0:
                switch (ctrl) {
                    case Ctrl.NET:
                        try {
                            handleP2pMsg(_sk, act, bodyBytes);
                        } catch (Exception ex) {
                            if (this.mgr.isShowLog()) {
                                System.out.println(
                                    "<p2p handle-p2p-msg error=" + ex.getMessage() + ">");
                            }
                        }
                        break;
                    case Ctrl.SYNC:
                        if (!this.handlers.containsKey(route)) {
                            if (this.mgr.isShowLog()) {
                                System.out.println(getUnregRouteMsg(ver, ctrl, act, rb.displayId));
                            }
                            return currCnt;
                        }

                        this.handleKernelMsg(rb.nodeIdHash, route, bodyBytes);
                        break;
                    default:
                        if (this.mgr.isShowLog()) {
                            System.out.println(getInvalRouteMsg(ver, ctrl, act, rb.displayId));
                        }
                        break;
                }
                break;
            default:
                if (this.mgr.isShowLog()) {
                    System.out.println("<p2p unhandled-ver=" + ver + " node=" + rb.displayId + ">");
                }
                break;
        }

        return currCnt;
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
                if (rb.nodeIdHash == 0) { return; }

                if (_msgBytes.length > ResHandshake.LEN) {
                    ResHandshake1 resHandshake1 = ResHandshake1.decode(_msgBytes);
                    if (resHandshake1 != null && resHandshake1.getSuccess()) {
                        handleResHandshake(rb.nodeIdHash, resHandshake1.getBinaryVersion());
                    }
                }
                break;

            case Act.REQ_ACTIVE_NODES:
                if (rb.nodeIdHash != 0) {
                    INode node = nodeMgr.getActiveNode(rb.nodeIdHash);
                    if (node != null) {
                        this.sendMsgQue.offer(
                            new MsgOut(
                                node.getIdHash(),
                                node.getIdShort(),
                                new ResActiveNodes(nodeMgr.getActiveNodesList()),
                                Dest.ACTIVE));
                    }
                }
                break;

            case Act.RES_ACTIVE_NODES:
                if (this.mgr.isSyncSeedsOnly()) { break; }

                if (rb.nodeIdHash != 0) {
                    INode node = nodeMgr.getActiveNode(rb.nodeIdHash);
                    if (node != null) {
                        node.refreshTimestamp();
                        ResActiveNodes resActiveNodes = ResActiveNodes.decode(_msgBytes);
                        if (resActiveNodes != null) {
                            List<INode> incomingNodes = resActiveNodes.getNodes();
                            for (INode incomingNode : incomingNodes) {
                                if (nodeMgr.tempNodesSize() >= this.mgr.getMaxTempNodes()) { return; }
                                if (this.mgr.validateNode(incomingNode)) {
                                    nodeMgr.addTempNode(incomingNode);
                                }
                            }
                        }
                    }
                }
                break;
            default:
                if (this.mgr.isShowLog()) {
                    System.out.println("<p2p unknown-route act=" + _act + ">");
                }
                break;
        }
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
        INode node = nodeMgr.getInboundNode(_channelHash);
        if (node != null && node.getPeerMetric().notBan()) {
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
                    nodeMgr.moveInboundToActive(_channelHash, this.mgr);
                    this.sendMsgQue.offer(
                            new MsgOut(
                                    node.getIdHash(),
                                    node.getIdShort(),
                                    this.cachedResHandshake1,
                                    Dest.ACTIVE));
                }

            } else {
                if (this.mgr.isShowLog()) { System.out.println("<p2p handshake-rule-fail>"); }
            }
        }
    }

    private void handleResHandshake(int _nodeIdHash, String _binaryVersion) {
        INode node = nodeMgr.getOutboundNodes().get(_nodeIdHash);
        if (node != null && node.getPeerMetric().notBan()) {
            node.refreshTimestamp();
            node.setBinaryVersion(_binaryVersion);
            nodeMgr.moveOutboundToActive(node.getIdHash(), node.getIdShort(), this.mgr);
        }
    }

    /**
     * @param _nodeIdHash int
     * @param _route int
     * @param _msgBytes byte[]
     */
    private void handleKernelMsg(int _nodeIdHash, int _route, final byte[] _msgBytes) {
        INode node = nodeMgr.getActiveNode(_nodeIdHash);
        if (node != null) {
            int nodeIdHash = node.getIdHash();
            String nodeDisplayId = node.getIdShort();
            node.refreshTimestamp();
            this.receiveMsgQue.offer(new MsgIn(nodeIdHash, nodeDisplayId, _route, _msgBytes));
        }
    }

    /** @return boolean TODO: implementation */
    private boolean handshakeRuleCheck(int netId) {
        // check net id
        if (netId != this.mgr.getSelfNetId()) { return false; }
        // check supported protocol versions
        return true;
    }

    private String getReadOverflowMsg(int prevCnt, int cnt) {
        return "IO read overflow!  suppose read:" + prevCnt + " real left:" + cnt;
    }

    private String getRouteMsg(short ver, byte ctrl, byte act, int count, String idStr) {
        return "<p2p over-called-route=" + ver + "-" + ctrl + "-" + act + " calls=" + count
            + " node=" + idStr + ">";
    }

    private String getUnregRouteMsg(short ver, byte ctrl, byte act, String idStr) {
        return "<p2p unregistered-route=" + ver + "-" + ctrl + "-" + act + " node=" + idStr + ">";
    }

    private String getInvalRouteMsg(short ver, byte ctrl, byte act, String idStr) {
        return "<p2p invalid-route=" + ver + "-" + ctrl + "-" + act + " node=" + idStr + ">";
    }
}

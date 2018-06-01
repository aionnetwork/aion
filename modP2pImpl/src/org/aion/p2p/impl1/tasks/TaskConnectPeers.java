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
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.impl.zero.msg.ReqHandshake1;
import org.aion.p2p.impl1.P2pMgr.Dest;

public class TaskConnectPeers implements Runnable {
    private static final int PERIOD_CONNECT_OUTBOUND = 1000;
    private static final int TIMEOUT_OUTBOUND_CONNECT = 10000;

    private final INodeMgr nodeMgr;
    private final int maxActiveNodes;
    private final IP2pMgr mgr;
    private AtomicBoolean start;
    private BlockingQueue<MsgOut> sendMsgQue;
    private Selector selector;
    private ReqHandshake1 cachedReqHandshake1;

    public TaskConnectPeers(
            IP2pMgr _mgr,
            AtomicBoolean _start,
            INodeMgr _nodeMgr,
            int _maxActiveNodes,
            Selector _selector,
            BlockingQueue<MsgOut> _sendMsgQue,
            ReqHandshake1 _cachedReqHandshake1) {

        this.start = _start;
        this.nodeMgr = _nodeMgr;
        this.maxActiveNodes = _maxActiveNodes;
        this.mgr = _mgr;
        this.selector = _selector;
        this.sendMsgQue = _sendMsgQue;
        this.cachedReqHandshake1 = _cachedReqHandshake1;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("p2p-tcp");
        while (this.start.get()) {
            try {
                Thread.sleep(PERIOD_CONNECT_OUTBOUND);
            } catch (InterruptedException e) {
                if (this.mgr.isShowLog()) { System.out.println(getTcpInterruptedMsg()); }
            }

            if (this.nodeMgr.activeNodesSize() >= this.maxActiveNodes) {
                if (this.mgr.isShowLog()) {
                    System.out.println(getTcpPassMaxNodesMsg());
                }
                continue;
            }

            INode node;
            try {
                node = this.nodeMgr.tempNodesTake();
                if (this.nodeMgr.isSeedIp(node.getIpStr())) { node.setFromBootList(true); }
                if (node.getIfFromBootList()) { this.nodeMgr.addTempNode(node); }
                // if (node.peerMetric.shouldNotConn()) {
                // continue;
                // }
            } catch (InterruptedException e) {
                if (this.mgr.isShowLog()) { System.out.println(getTcpInterruptedMsg()); }
                return;
            } catch (Exception e) {
                if (this.mgr.isShowLog()) { e.printStackTrace(); }
                continue;
            }
            int nodeIdHash = node.getIdHash();
            if (!this.nodeMgr.getOutboundNodes().containsKey(nodeIdHash)
                    && !this.nodeMgr.hasActiveNode(nodeIdHash)) {
                int _port = node.getPort();
                try {
                    SocketChannel channel = SocketChannel.open();

                    channel.socket()
                            .connect(
                                    new InetSocketAddress(node.getIpStr(), _port),
                                    TIMEOUT_OUTBOUND_CONNECT);
                    this.mgr.configChannel(channel);

                    if (channel.finishConnect() && channel.isConnected()) {

                        if (this.mgr.isShowLog()) {
                            System.out.println(getSucesCnctMsg(node.getIdShort(), node.getIpStr()));
                        }
                        SelectionKey sk = channel.register(this.selector, SelectionKey.OP_READ);
                        ChannelBuffer rb = new ChannelBuffer(this.mgr.isShowLog());
                        rb.displayId = node.getIdShort();
                        rb.nodeIdHash = nodeIdHash;
                        sk.attach(rb);

                        node.refreshTimestamp();
                        node.setChannel(channel);
                        this.nodeMgr.addOutboundNode(node);

                        if (this.mgr.isShowLog()) {
                            System.out.println(getPrepRqstMsg(node.getIdShort(), node.getIpStr()));
                        }
                        this.sendMsgQue.offer(
                                new MsgOut(
                                        node.getIdHash(),
                                        node.getIdShort(),
                                        this.cachedReqHandshake1,
                                        Dest.OUTBOUND));
                        // node.peerMetric.decFailedCount();

                    } else {
                        if (this.mgr.isShowLog()) {
                            System.out.println(getFailCnctMsg(node.getIdShort(), node.getIpStr()));
                        }
                        channel.close();
                        // node.peerMetric.incFailedCount();
                    }
                } catch (IOException e) {
                    if (this.mgr.isShowLog()) {
                        System.out.println(getOutboundConnectMsg(node.getIpStr(), _port));
                    }
                    // node.peerMetric.incFailedCount();
                } catch (Exception e) {
                    if (this.mgr.isShowLog()) e.printStackTrace();
                }
            }
        }
    }

    private String getTcpInterruptedMsg() {
        return "<p2p-tcp- interrupted>";
    }

    private String getTcpPassMaxNodesMsg() {
        return "<p2p-tcp-connect-peer pass max-active-nodes>";
    }

    private String getSucesCnctMsg(String idStr, String ipStr) {
        return "<p2p success-connect node-id=" + idStr + " ip=" + ipStr + ">";
    }

    private String getOutboundConnectMsg(String ipStr, int port) {
        return "<p2p connect-outbound io-exception addr=" + ipStr + ":" + port + " result=failed>";
    }

    private String getFailCnctMsg(String idStr, String ipStr) {
        return "<p2p fail-connect node-id=" + idStr + " ip=" + ipStr + ">";
    }

    private String getPrepRqstMsg(String idStr, String ipStr) {
        return "<p2p prepare-request-handshake -> id=" + idStr + " ip=" + ipStr + ">";
    }
}

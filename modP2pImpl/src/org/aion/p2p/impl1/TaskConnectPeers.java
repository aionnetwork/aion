package org.aion.p2p.impl1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.impl.zero.msg.ReqHandshake1;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.p2p.impl1.P2pMgr.Dest;
import org.slf4j.Logger;

public class TaskConnectPeers implements Runnable {

    private static final int PERIOD_CONNECT_OUTBOUND = 1000;
    private static final int TIMEOUT_OUTBOUND_CONNECT = 10000;

    private final Logger p2pLOG;
    private final INodeMgr nodeMgr;
    private final int maxActiveNodes;
    private final P2pMgr mgr;
    private final AtomicBoolean start;
    private final Selector selector;
    private final ReqHandshake1 cachedReqHS;

    public TaskConnectPeers(
            final Logger p2pLOG,
            final P2pMgr _mgr,
            final AtomicBoolean _start,
            final INodeMgr _nodeMgr,
            final int _maxActiveNodes,
            final Selector _selector,
            final ReqHandshake1 _cachedReqHS) {

        this.p2pLOG = p2pLOG;
        this.start = _start;
        this.nodeMgr = _nodeMgr;
        this.maxActiveNodes = _maxActiveNodes;
        this.mgr = _mgr;
        this.selector = _selector;
        this.cachedReqHS = _cachedReqHS;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("p2p-tcp");
        while (this.start.get()) {
            INode node;
            try {
                Thread.sleep(PERIOD_CONNECT_OUTBOUND);
                if (this.nodeMgr.activeNodesSize() >= this.maxActiveNodes) {
                    p2pLOG.warn("tcp-connect-peer pass max-active-nodes.");
                    continue;
                }

                node = this.nodeMgr.tempNodesTake();
                if (node == null) {
                    p2pLOG.debug("no temp node can take.");
                    continue;
                }

                if (node.getIfFromBootList()) {
                    this.nodeMgr.addTempNode(node);
                }
                // if (node.peerMetric.shouldNotConn()) {
                // continue;
                // }
            } catch (Exception e) {
                p2pLOG.debug("tcp-Exception.", e);
                continue;
            }
            int nodeIdHash = node.getIdHash();
            if (this.nodeMgr.notAtOutboundList(nodeIdHash)
                    && this.nodeMgr.notActiveNode(nodeIdHash)) {
                int _port = node.getPort();
                SocketChannel channel = null;
                try {
                    channel = SocketChannel.open();
                    channel.socket()
                            .connect(
                                    new InetSocketAddress(node.getIpStr(), _port),
                                    TIMEOUT_OUTBOUND_CONNECT);
                    this.mgr.configChannel(channel);

                    if (channel.isConnected()) {
                        if (p2pLOG.isDebugEnabled()) {
                            p2pLOG.debug(
                                    "success-connect node-id={} ip={}",
                                    node.getIdShort(),
                                    node.getIpStr());
                        }

                        channel.configureBlocking(false);
                        SelectionKey sk = channel.register(this.selector, SelectionKey.OP_READ);
                        ChannelBuffer rb = new ChannelBuffer(p2pLOG);
                        rb.setDisplayId(node.getIdShort());
                        rb.setNodeIdHash(nodeIdHash);
                        sk.attach(rb);

                        node.refreshTimestamp();
                        node.setChannel(channel);
                        this.nodeMgr.addOutboundNode(node);

                        if (p2pLOG.isDebugEnabled()) {
                            p2pLOG.debug(
                                    "prepare-request-handshake -> id={} ip={}",
                                    node.getIdShort(),
                                    node.getIpStr());
                        }

                        mgr.send(node.getIdHash(), node.getIdShort(), this.cachedReqHS, Dest.OUTBOUND);
                    } else {
                        if (p2pLOG.isDebugEnabled()) {
                            p2pLOG.debug(
                                    "fail-connect node-id -> id={} ip={}",
                                    node.getIdShort(),
                                    node.getIpStr());
                        }

                        channel.close();
                        // node.peerMetric.incFailedCount();
                    }
                } catch (Exception e) {
                    if (p2pLOG.isDebugEnabled()) {
                        p2pLOG.debug(
                                "connect-outbound exception -> id="
                                        + node.getIdShort()
                                        + " ip="
                                        + node.getIpStr(),
                                e);
                    }

                    if (p2pLOG.isTraceEnabled()) {
                        p2pLOG.trace("close channel {}", node.toString());
                    }

                    if (channel != null) {
                        try {
                            channel.close();
                        } catch (IOException e1) {
                            p2pLOG.debug("TaskConnectPeers close exception.", e1);
                        }
                    }
                }
            }
        }
    }
}

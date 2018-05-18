package org.aion.p2p.impl1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl.zero.msg.ReqHandshake1;
import org.aion.p2p.impl1.P2pMgr.Dest;
import org.aion.p2p.impl1.P2pMgr.MsgOut;

public class TaskConnectPeers2 implements Runnable {
    private static final int PERIOD_CONNECT_OUTBOUND = 1000;
    private static final int TIMEOUT_OUTBOUND_CONNECT = 10000;

    private AtomicBoolean start;
    private final boolean showLog;
    private final NodeMgr nodeMgr;
    private final int maxActiveNodes;
    private final P2pMgr mgr;
    private LinkedBlockingQueue<MsgOut> sendMsgQue;
    private Selector selector;
    private ReqHandshake1 cachedReqHandshake1;

    public TaskConnectPeers2(
            P2pMgr _mgr,
            AtomicBoolean _start,
            boolean _showLog,
            NodeMgr _nodeMgr,
            int _maxActiveNodes,
            Selector _selector,
            LinkedBlockingQueue<MsgOut> _sendMsgQue,
            ReqHandshake1 _cachedReqHandshake1) {

        this.start = _start;
        this.showLog = _showLog;
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
                if (this.showLog) System.out.println("<p2p-tcp interrupted>");
            }

            if (this.nodeMgr.activeNodesSize() >= this.maxActiveNodes) {
                if (this.showLog)
                    System.out.println("<p2p-tcp-connect-peer pass max-active-nodes>");
                continue;
            }

            Node node;
            try {
                node = this.nodeMgr.tempNodesTake();
                if (this.nodeMgr.isSeedIp(node.getIpStr())) node.setFromBootList(true);
                if (node.getIfFromBootList()) this.nodeMgr.addTempNode(node);
                // if (node.peerMetric.shouldNotConn()) {
                // continue;
                // }
            } catch (InterruptedException e) {
                if (this.showLog) System.out.println("<p2p-tcp-interrupted>");
                return;
            } catch (Exception e) {
                if (this.showLog) e.printStackTrace();
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

                        if (this.showLog)
                            System.out.println(
                                    "<p2p success-connect node-id="
                                            + node.getIdShort()
                                            + " ip="
                                            + node.getIpStr()
                                            + ">");

                        SelectionKey sk = channel.register(this.selector, SelectionKey.OP_READ);
                        ChannelBuffer rb = new ChannelBuffer(this.showLog);
                        rb.displayId = node.getIdShort();
                        rb.nodeIdHash = nodeIdHash;
                        sk.attach(rb);

                        node.refreshTimestamp();
                        node.setChannel(channel);
                        this.nodeMgr.addOutboundNode(node);

                        if (this.showLog)
                            System.out.println(
                                    "<p2p prepare-request-handshake -> id="
                                            + node.getIdShort()
                                            + " ip="
                                            + node.getIpStr()
                                            + ">");
                        this.sendMsgQue.offer(
                                new MsgOut(
                                        node.getIdHash(),
                                        node.getIdShort(),
                                        this.cachedReqHandshake1,
                                        Dest.OUTBOUND));
                        // node.peerMetric.decFailedCount();

                    } else {
                        if (this.showLog)
                            System.out.println(
                                    "<p2p fail-connect node-id="
                                            + node.getIdShort()
                                            + " ip="
                                            + node.getIpStr()
                                            + ">");
                        channel.close();
                        // node.peerMetric.incFailedCount();
                    }
                } catch (IOException e) {
                    if (this.showLog)
                        System.out.println(
                                "<p2p connect-outbound io-exception addr="
                                        + node.getIpStr()
                                        + ":"
                                        + _port
                                        + " result=failed>");
                    // node.peerMetric.incFailedCount();
                } catch (Exception e) {
                    if (this.showLog) e.printStackTrace();
                }
            }
        }
    }
}

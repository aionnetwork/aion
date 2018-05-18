package org.aion.p2p.impl1;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl1.P2pMgr.MsgOut;

public class TaskSend2 implements Runnable {
    static final int TOTAL_LANE = (1 << 5) - 1;

    private final P2pMgr mgr;
    private final AtomicBoolean start;
    private final LinkedBlockingQueue<MsgOut> sendMsgQue;
    private final NodeMgr nodeMgr;
    private final Selector selector;
    private boolean showLog;
    private int lane;

    public TaskSend2(
            P2pMgr _mgr,
            int _lane,
            LinkedBlockingQueue<MsgOut> _sendMsgQue,
            AtomicBoolean _start,
            boolean _showLog,
            NodeMgr _nodeMgr,
            Selector _selector) {

        this.mgr = _mgr;
        this.lane = _lane;
        this.sendMsgQue = _sendMsgQue;
        this.start = _start;
        this.showLog = _showLog;
        this.nodeMgr = _nodeMgr;
        this.selector = _selector;
    }

    @Override
    public void run() {
        while (start.get()) {
            try {
                MsgOut mo = sendMsgQue.take();
                // if timeout , throw away this msg.
                long now = System.currentTimeMillis();
                if (now - mo.timestamp > P2pConstant.WRITE_MSG_TIMEOUT) {
                    if (showLog)
                        System.out.println(
                                "<p2p timeout-msg to-node="
                                        + mo.displayId
                                        + " timestamp="
                                        + now
                                        + ">");
                    continue;
                }

                // if not belong to current lane, put it back.
                int targetLane = this.mgr.hash2Lane(mo.nodeId);
                if (targetLane != lane) {
                    sendMsgQue.offer(mo);
                    continue;
                }

                Node node = null;
                switch (mo.dest) {
                    case ACTIVE:
                        node = nodeMgr.getActiveNode(mo.nodeId);
                        break;
                    case INBOUND:
                        node = nodeMgr.getInboundNode(mo.nodeId);
                        break;
                    case OUTBOUND:
                        node = nodeMgr.getOutboundNode(mo.nodeId);
                        break;
                }

                if (node != null) {
                    SelectionKey sk = node.getChannel().keyFor(selector);
                    if (sk != null) {
                        Object attachment = sk.attachment();
                        if (attachment != null) {
                            TaskWrite tw =
                                    new TaskWrite(
                                            showLog,
                                            node.getIdShort(),
                                            node.getChannel(),
                                            mo.msg,
                                            (ChannelBuffer) attachment,
                                            this.mgr);
                            tw.run();
                        }
                    }
                } else {
                    if (showLog)
                        System.out.println(
                                "<p2p msg-"
                                        + mo.dest.name()
                                        + "->"
                                        + mo.displayId
                                        + " node-not-exit");
                }
            } catch (InterruptedException e) {
                if (showLog) System.out.println("<p2p task-send-interrupted>");
                return;
            } catch (Exception e) {
                if (showLog) e.printStackTrace();
            }
        }
    }
}

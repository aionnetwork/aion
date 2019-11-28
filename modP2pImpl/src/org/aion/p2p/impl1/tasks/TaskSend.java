package org.aion.p2p.impl1.tasks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.Header;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.impl.comm.Node;
import org.slf4j.Logger;

public class TaskSend implements Runnable {

    private final Logger p2pLOG, surveyLog;
    private final IP2pMgr mgr;
    private final AtomicBoolean start;
    private final BlockingQueue<MsgOut> sendMsgQue;
    private final INodeMgr nodeMgr;
    private final Selector selector;

    /**
     * Map used for temporarily storing requests to ensure that messages transmissions to peers are
     * spread across peers, avoiding sending consecutive messages to the same peer if possible.
     */
    private final Map<Integer, Deque<MsgOut>> pendingRequests = new HashMap<>();

    private INode lastNode = new Node(false, new byte[36], new byte[8], 1); // fake node

    public TaskSend(
            final Logger p2pLOG,
            final Logger surveyLog,
            final IP2pMgr _mgr,
            final BlockingQueue<MsgOut> _sendMsgQue,
            final AtomicBoolean _start,
            final INodeMgr _nodeMgr,
            final Selector _selector) {

        this.p2pLOG = p2pLOG;
        this.surveyLog = surveyLog;
        this.mgr = _mgr;
        this.sendMsgQue = _sendMsgQue;
        this.start = _start;
        this.nodeMgr = _nodeMgr;
        this.selector = _selector;
    }

    @Override
    public void run() {
        // for runtime survey information
        long startTime, duration;

        while (start.get()) {
            try {
                // process pending requests
                int countPendingPeers = 0;
                do {
                    for (Entry<Integer, Deque<MsgOut>> entry : pendingRequests.entrySet()) {
                        Deque<MsgOut> messages = entry.getValue();
                        if (!messages.isEmpty()) {
                            process(messages.pollFirst(), false);
                            if (!messages.isEmpty()) {
                                countPendingPeers++;
                            }
                        }
                    }
                } while (countPendingPeers > 1);

                boolean wasProcessed = false;
                // wait for a messages from the queue to be processed before going to the pending
                // requests to increases the odds of spreading out peer requests
                // otherwise the pending requests check will only process the same request again
                while (!wasProcessed && start.get()) {
                    startTime = System.nanoTime();
                    MsgOut mo = sendMsgQue.take();
                    duration = System.nanoTime() - startTime;
                    surveyLog.info("TaskSend: wait for message, duration = {} ns.", duration);
                    wasProcessed = process(mo, true);
                }
            } catch (InterruptedException e) {
                p2pLOG.error("task-send-interrupted", e);
                return;
            } catch (RejectedExecutionException e) {
                p2pLOG.warn("task-send-reached thread queue limit", e);
            } catch (Exception e) {
                p2pLOG.debug("TaskSend exception.", e);
            }
        }
    }

    /**
     * Returns {@code true} if the message was processed, {@code false} otherwise.
     *
     * @return {@code true} if the message was processed, {@code false} otherwise
     */
    private boolean process(MsgOut mo, boolean prioritizeQueue) {
        // shouldn't happen; but just in case
        if (mo == null) return false;

        // for runtime survey information
        long startTime, duration;

        startTime = System.nanoTime();
        // if timeout , throw away this msg.
        long now = System.currentTimeMillis();
        if (now - mo.getTimestamp() > P2pConstant.WRITE_MSG_TIMEOUT) {
            p2pLOG.debug("timeout-msg to-node={} timestamp={}", mo.getDisplayId(), now);
            duration = System.nanoTime() - startTime;
            surveyLog.info("TaskSend: timeout, duration = {} ns.", duration);
            return false;
        }

        INode node = null;
        switch (mo.getDest()) {
            case ACTIVE:
                node = nodeMgr.getActiveNode(mo.getNodeId());
                break;
            case INBOUND:
                node = nodeMgr.getInboundNode(mo.getNodeId());
                break;
            case OUTBOUND:
                node = nodeMgr.getOutboundNode(mo.getNodeId());
                break;
        }

        if (node != null) {
            // delay sending the request if we have multiple peers and the last sent message was to
            // the current peer by placing the request in the pendingRequests that will be processed
            // during the next iteration
            if (mgr.getActiveNodes().size() > 1 && node.getIdHash() == lastNode.getIdHash()) {
                if (pendingRequests.containsKey(node.getIdHash())) {
                    if (prioritizeQueue) {
                        // the message comes from the sendMsgQue, i.e. it was created after the ones in the current queue
                        pendingRequests.get(node.getIdHash()).addLast(mo);
                    } else {
                        // the message comes from the pendingRequests, i.e. it was removed from the beginning of the queue
                        pendingRequests.get(node.getIdHash()).addFirst(mo);
                    }
                } else {
                    Deque<MsgOut> dq = new ArrayDeque<>();
                    dq.addLast(mo);
                    pendingRequests.put(node.getIdHash(), dq);
                }
                duration = System.nanoTime() - startTime;
                surveyLog.info("TaskSend: put back, duration = {} ns.", duration);
                return false;
            }

            if (prioritizeQueue && pendingRequests.containsKey(node.getIdHash())) {
                Deque<MsgOut> dq = pendingRequests.get(node.getIdHash());
                if (dq != null && !dq.isEmpty()) {
                    dq.addLast(mo);
                    duration = System.nanoTime() - startTime;
                    surveyLog.info("TaskSend: put back, duration = {} ns.", duration);
                    return false;
                }
            }

            SelectionKey sk = node.getChannel().keyFor(selector);
            if (sk != null && sk.attachment() != null) {
                ChannelBuffer attachment = (ChannelBuffer) sk.attachment();
                write(node.getIdShort(), node.getChannel(), mo.getMsg(), attachment);
                lastNode = node;
            }
        } else {
            p2pLOG.debug("msg-{} ->{} node-not-exist", mo.getDest().name(), mo.getDisplayId());
        }
        duration = System.nanoTime() - startTime;
        surveyLog.info("TaskSend: process message, duration = {} ns.", duration);
        return true;
    }

    private static final long MAX_BUFFER_WRITE_TIME = 1_000_000_000L;
    private static final long MIN_TRACE_BUFFER_WRITE_TIME = 10_000_000L;

    private void write(
            final String nodeShortId,
            final SocketChannel sc,
            final Msg msg,
            final ChannelBuffer channelBuffer) {
        // for runtime survey information
        long startTime, duration;

        startTime = System.nanoTime();
        // reset allocated buffer and clear messages if the channel is closed
        if (channelBuffer.isClosed()) {
            channelBuffer.refreshHeader();
            channelBuffer.refreshBody();
            mgr.dropActive(channelBuffer.getNodeIdHash(), "close-already");
            duration = System.nanoTime() - startTime;
            surveyLog.info("TaskSend: check for closed channel, duration = {} ns.", duration);
            return;
        }
        duration = System.nanoTime() - startTime;
        surveyLog.info("TaskSend: check for closed channel, duration = {} ns.", duration);

        long startTime2 = System.nanoTime();
        try {
            startTime = System.nanoTime();
            channelBuffer.lock.lock();

            /*
             * @warning header set len (body len) before header encode
             */
            byte[] bodyBytes = msg.encode();
            int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
            Header h = msg.getHeader();
            h.setLen(bodyLen);
            byte[] headerBytes = h.encode();

            p2pLOG.trace("write id:{} {}-{}-{}", nodeShortId, h.getVer(), h.getCtrl(), h.getAction());

            ByteBuffer buf = ByteBuffer.allocate(headerBytes.length + bodyLen);
            buf.put(headerBytes);
            if (bodyBytes != null) {
                buf.put(bodyBytes);
            }
            buf.flip();
            duration = System.nanoTime() - startTime;
            surveyLog.info("TaskSend: setup for write, duration = {} ns.", duration);

            long t1 = System.nanoTime(), t2;
            int wrote = 0;
            try {
                startTime = System.nanoTime();
                do {
                    int result = sc.write(buf);
                    wrote += result;

                    if (result == 0) {
                        // @Attention:  very important sleep , otherwise when NIO write buffer full,
                        // without sleep will hangup this thread.
                        Thread.sleep(0, 1);
                    }

                    t2 = System.nanoTime() - t1;
                } while (buf.hasRemaining() && (t2 < MAX_BUFFER_WRITE_TIME));
                duration = System.nanoTime() - startTime;
                surveyLog.info("TaskSend: write message {} node={}, duration = {} ns.", msg, nodeShortId, duration);

                if (t2 > MIN_TRACE_BUFFER_WRITE_TIME) {
                    p2pLOG.trace(
                        "msg write: id {} size {} time {} ms length {}",
                        nodeShortId,
                        wrote,
                        t2,
                        buf.array().length);
                }

            } catch (ClosedChannelException ex1) {
                p2pLOG.debug("closed-channel-exception node=" + nodeShortId, ex1);
                channelBuffer.setClosed();
            } catch (IOException ex2) {
                p2pLOG.debug(
                        "write-msg-io-exception node="
                            + nodeShortId
                            + " headerBytes="
                            + headerBytes.length
                            + " bodyLen="
                            + bodyLen
                            + " time="
                            + (System.nanoTime() - t1)
                            + "ns",
                        ex2);

                if (ex2.getMessage().equals("Broken pipe")) {
                    channelBuffer.setClosed();
                }
            }
        } catch (Exception e) {
            p2pLOG.error("TaskSend exception.", e);
        } finally {
            duration = System.nanoTime() - startTime2;
            surveyLog.info("TaskSend: start to end of write try, duration = {} ns.", duration);
            channelBuffer.lock.unlock();
        }
    }
}

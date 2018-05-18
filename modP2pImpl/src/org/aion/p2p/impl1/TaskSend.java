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

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.Msg;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl1.P2pMgr.Dest;

public class TaskSend implements Runnable {
    static final int TOTAL_LANE = (1 << 5) - 1;

    private final P2pMgr mgr;
    private final AtomicBoolean start;
    private final LinkedBlockingQueue<MsgOut> sendMsgQue;
    private final NodeMgr nodeMgr;
    private final Selector selector;
    private final int lane;

    TaskSend(
            P2pMgr _mgr,
            int _lane,
            LinkedBlockingQueue<MsgOut> _sendMsgQue,
            AtomicBoolean _start,
            NodeMgr _nodeMgr,
            Selector _selector) {

        this.mgr = _mgr;
        this.lane = _lane;
        this.sendMsgQue = _sendMsgQue;
        this.start = _start;
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
                    if (this.mgr.isShowLog())
                        System.out.println(
                                "<p2p timeout-msg to-node="
                                        + mo.displayId
                                        + " timestamp="
                                        + now
                                        + ">");
                    continue;
                }

                // if not belong to current lane, put it back.
                int targetLane = hash2Lane(mo.nodeId);
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
                                            this.mgr.isShowLog(),
                                            node.getIdShort(),
                                            node.getChannel(),
                                            mo.msg,
                                            (ChannelBuffer) attachment,
                                            this.mgr);
                            tw.run();
                        }
                    }
                } else {
                    if (this.mgr.isShowLog())
                        System.out.println(
                                "<p2p msg-"
                                        + mo.dest.name()
                                        + "->"
                                        + mo.displayId
                                        + " node-not-exit");
                }
            } catch (InterruptedException e) {
                if (this.mgr.isShowLog()) System.out.println("<p2p task-send-interrupted>");
                return;
            } catch (Exception e) {
                if (this.mgr.isShowLog()) e.printStackTrace();
            }
        }
    }

    // hash mapping channel id to write thread.
    private int hash2Lane(int in) {
        in ^= in >> (32 - 5);
        in ^= in >> (32 - 10);
        in ^= in >> (32 - 15);
        in ^= in >> (32 - 20);
        in ^= in >> (32 - 25);
        return in & 0b11111;
    }

    static class MsgOut {
        private final int nodeId;
        private final String displayId;
        private final Msg msg;
        private final Dest dest;
        private final long timestamp;

        /**
         * Constructs an outgoing message.
         *
         * @param _nodeId
         * @param _displayId
         * @param _msg
         * @param _dest
         */
        MsgOut(int _nodeId, String _displayId, Msg _msg, Dest _dest) {
            nodeId = _nodeId;
            displayId = _displayId;
            msg = _msg;
            dest = _dest;
            timestamp = System.currentTimeMillis();
        }
    }
}

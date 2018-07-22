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

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.P2pConstant;

public class TaskSend implements Runnable {

    private static final int TOTAL_LANE = Math
        .min(Runtime.getRuntime().availableProcessors() << 1, 32);
    private static final int THREAD_Q_LIMIT = 20000;

    private final IP2pMgr mgr;
    private final AtomicBoolean start;
    private final BlockingQueue<MsgOut> sendMsgQue;
    private final INodeMgr nodeMgr;
    private final Selector selector;
    private final int lane;

    private static ThreadPoolExecutor tpe;

    public TaskSend(
        final IP2pMgr _mgr,
        final int _lane,
        final BlockingQueue<MsgOut> _sendMsgQue,
        final AtomicBoolean _start,
        final INodeMgr _nodeMgr,
        final Selector _selector) {

        this.mgr = _mgr;
        this.lane = _lane;
        this.sendMsgQue = _sendMsgQue;
        this.start = _start;
        this.nodeMgr = _nodeMgr;
        this.selector = _selector;

        if (tpe == null) {
            tpe = new ThreadPoolExecutor(TOTAL_LANE
                , TOTAL_LANE
                , 0
                , TimeUnit.MILLISECONDS
                , new LinkedBlockingQueue<>(THREAD_Q_LIMIT)
                , Executors.defaultThreadFactory());
        }
    }

    @Override
    public void run() {
        while (start.get()) {
            try {
                MsgOut mo = sendMsgQue.take();

                // if timeout , throw away this msg.
                long now = System.currentTimeMillis();
                if (now - mo.getTimestamp() > P2pConstant.WRITE_MSG_TIMEOUT) {
                    if (p2pLOG.isDebugEnabled()) {
                        p2pLOG.debug("timeout-msg to-node={} timestamp={}", mo.getDisplayId(), now);
                    }
                    continue;
                }

                // if not belong to current lane, put it back.
                if (mo.getLane() != lane) {
                    sendMsgQue.offer(mo);
                    continue;
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
                    SelectionKey sk = node.getChannel().keyFor(selector);
                    if (sk != null) {
                        Object attachment = sk.attachment();
                        if (attachment != null) {
                            tpe.execute(new TaskWrite(
                                node.getIdShort(),
                                node.getChannel(),
                                mo.getMsg(),
                                (ChannelBuffer) attachment,
                                this.mgr));
                        }
                    }
                } else {
                    if (p2pLOG.isDebugEnabled()) {
                        p2pLOG.debug("msg-{} ->{} node-not-exit", mo.getDest().name(), mo.getDisplayId());
                    }
                }
            } catch (InterruptedException e) {
                p2pLOG.error("task-send-interrupted", e);
                return;
            } catch (RejectedExecutionException e) {
                p2pLOG.warn("task-send-reached thread queue limit", e);
            } catch (Exception e) {
                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug("TaskSend exception {}", e.getMessage());
                }
            }
        }
    }

    // hash mapping channel id to write thread.
    static int hash2Lane(int in) {
        in ^= in >> (32 - 5);
        in ^= in >> (32 - 10);
        in ^= in >> (32 - 15);
        in ^= in >> (32 - 20);
        in ^= in >> (32 - 25);
        return (in & 0b11111) * TOTAL_LANE / 32;
    }
}

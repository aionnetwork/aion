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

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;

public class TaskClear implements Runnable {
    private static final int PERIOD_CLEAR = 20000;
    private static final int TIMEOUT_OUTBOUND_NODES = 20000;

    private final IP2pMgr mgr;
    private final INodeMgr nodeMgr;
    private AtomicBoolean start;

    public TaskClear(IP2pMgr _mgr, INodeMgr _nodeMgr, AtomicBoolean _start) {
        this.mgr = _mgr;
        this.nodeMgr = _nodeMgr;
        this.start = _start;
    }

    @Override
    public void run() {
        while (start.get()) {
            try {
                Thread.sleep(PERIOD_CLEAR);

                nodeMgr.timeoutInbound(this.mgr);

                Iterator outboundIt = nodeMgr.getOutboundNodes().keySet().iterator();
                while (outboundIt.hasNext()) {

                    Object obj = outboundIt.next();

                    if (obj == null) { continue; }

                    int nodeIdHash = (int) obj;
                    INode node = nodeMgr.getOutboundNodes().get(nodeIdHash);

                    if (node == null) { continue; }

                    if (System.currentTimeMillis() - node.getTimestamp() > TIMEOUT_OUTBOUND_NODES) {
                        this.mgr.closeSocket(
                                node.getChannel(), "outbound-timeout node=" + node.getIdShort());
                        outboundIt.remove();
                    }
                }

                nodeMgr.timeoutActive(this.mgr);

            } catch (Exception e) {
            }
        }
    }
}

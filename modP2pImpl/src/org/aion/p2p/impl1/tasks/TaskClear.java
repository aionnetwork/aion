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

import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INodeMgr;

public class TaskClear implements Runnable {

    private static final int PERIOD_CLEAR = 10000;

    private final INodeMgr nodeMgr;
    private final AtomicBoolean start;

    public TaskClear(final INodeMgr _nodeMgr, final AtomicBoolean _start) {
        this.nodeMgr = _nodeMgr;
        this.start = _start;
    }

    @Override
    public void run() {
        while (start.get()) {
            try {
                Thread.sleep(PERIOD_CLEAR);
                nodeMgr.timeoutCheck();
            } catch (Exception e) {
                p2pLOG.error("TaskClear exception {}", e.toString());
            }
        }
    }
}

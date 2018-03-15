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
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 */

package org.aion.zero.impl.sync;

import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.msg.ReqStatus;
import org.slf4j.Logger;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author chris
 * long run
 */
final class TaskGetStatus implements Runnable {

    // single instance req status
    private final static ReqStatus reqStatus = new ReqStatus();

    private final AtomicBoolean run;

    private final int interval;

    private final IP2pMgr p2p;

    private final Logger log;

    /**
     *
     * @param _run AtomicBoolean
     * @param _interval int
     * @param _p2p IP2pMgr
     * @param _log Logger
     */
    TaskGetStatus(final AtomicBoolean _run, int _interval, final IP2pMgr _p2p, final Logger _log){
        this.run = _run;
        this.interval = _interval;
        this.p2p = _p2p;
        this.log = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        while(this.run.get()){
            Set<Integer> ids = new HashSet<>(p2p.getActiveNodes().keySet());
            for (int id : ids) {
                p2p.send(id, reqStatus);
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                this.log.info("<sync-gs shutdown>");
                return;
            }
        }
        this.log.info("<sync-gs shutdown>");
    }
}
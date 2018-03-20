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
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.types.A0BlockHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author chris
 * long run
 */
final class TaskGetBodies implements Runnable {

    // timeout sent headers
    private final static int SENT_HEADERS_TIMEOUT = 10000;

    private final IP2pMgr p2p;

    private final AtomicBoolean run;

    private final BlockingQueue<HeadersWrapper> headersImported;

    private final ConcurrentHashMap<Integer, HeadersWrapper> headersSent;

    /**
     *
     * @param _p2p IP2pMgr
     * @param _run AtomicBoolean
     * @param _headersImported BlockingQueue
     * @param _headersSent ConcurrentHashMap
     */
    TaskGetBodies(
            final IP2pMgr _p2p,
            final AtomicBoolean _run,
            final BlockingQueue<HeadersWrapper> _headersImported,
            final ConcurrentHashMap<Integer, HeadersWrapper> _headersSent){
        this.p2p = _p2p;
        this.run = _run;
        this.headersImported = _headersImported;
        this.headersSent = _headersSent;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("sync-gb");
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        while (run.get()) {
            HeadersWrapper hw;
            try {
                hw = headersImported.take();
            } catch (InterruptedException e) {
                continue;
            }

            int idHash = hw.getNodeIdHash();
            List<A0BlockHeader> headers = hw.getHeaders();
            synchronized (headersSent) {
                HeadersWrapper hwPrevious = headersSent.get(idHash);
                // already sent, check timeout and add it back if
                // not timeout yet
                if (hwPrevious == null || (System.currentTimeMillis() - hwPrevious.getTimestamp()) > SENT_HEADERS_TIMEOUT) {
                    this.headersSent.put(idHash, hw);
                    List<byte[]> headerHashes = new ArrayList<>();
                    for (A0BlockHeader h : headers) {
                        headerHashes.add(h.getHash());
                    }

                    this.p2p.send(idHash, new ReqBlocksBodies(headerHashes));
                }
            }
        }
    }
}

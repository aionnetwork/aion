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
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author chris
 * long run
 */
final class TaskGetBodies implements Runnable {

    private final IP2pMgr p2p;

    private final AtomicBoolean run;

    private final BlockingQueue<HeadersWrapper> downloadedHeaders;

    private final ConcurrentHashMap<Integer, HeadersWrapper> headersWithBodiesRequested;

    private final Map<Integer, PeerState> peerStates;

    private final Logger log;

    /**
     * @param _p2p             IP2pMgr
     * @param _run             AtomicBoolean
     * @param _downloadedHeaders BlockingQueue
     * @param _headersWithBodiesRequested     ConcurrentHashMap
     */
    TaskGetBodies(
            final IP2pMgr _p2p,
            final AtomicBoolean _run,
            final BlockingQueue<HeadersWrapper> _downloadedHeaders,
            final ConcurrentHashMap<Integer, HeadersWrapper> _headersWithBodiesRequested,
            final Map<Integer, PeerState> peerStates,
            final Logger log) {
        this.p2p = _p2p;
        this.run = _run;
        this.downloadedHeaders = _downloadedHeaders;
        this.headersWithBodiesRequested = _headersWithBodiesRequested;
        this.peerStates = peerStates;
        this.log = log;
    }

    @Override
    public void run() {
        while (run.get()) {
            HeadersWrapper hw;
            try {
                hw = downloadedHeaders.take();
            } catch (InterruptedException e) {
                continue;
            }

            int idHash = hw.getNodeIdHash();
            List<A0BlockHeader> headers = hw.getHeaders();
            if (headers.isEmpty()) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("<get-bodies from-num={} to-num={} node={}>",
                        headers.get(0).getNumber(),
                        headers.get(headers.size() - 1).getNumber(),
                        hw.getDisplayId());
            }

            p2p.send(idHash, new ReqBlocksBodies(headers.stream().map(k -> k.getHash()).collect(Collectors.toList())));
            headersWithBodiesRequested.put(idHash, hw);
        }
    }
}

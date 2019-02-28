package org.aion.zero.impl.sync;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.PeerState.State;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

/**
 * long run
 *
 * @author chris
 */
final class TaskGetBodies implements Runnable {

    private final IP2pMgr p2p;

    private final AtomicBoolean run;

    private final BlockingQueue<HeadersWrapper> downloadedHeaders;

    private final ConcurrentHashMap<Integer, HeadersWrapper> headersWithBodiesRequested;

    private final Map<Integer, PeerState> peerStates;

    private final Logger log;

    private final SyncStats stats;

    /**
     * @param _p2p IP2pMgr
     * @param _run AtomicBoolean
     * @param _downloadedHeaders BlockingQueue
     * @param _headersWithBodiesRequested ConcurrentHashMap
     */
    TaskGetBodies(
            final IP2pMgr _p2p,
            final AtomicBoolean _run,
            final BlockingQueue<HeadersWrapper> _downloadedHeaders,
            final ConcurrentHashMap<Integer, HeadersWrapper> _headersWithBodiesRequested,
            final Map<Integer, PeerState> peerStates,
            final SyncStats _stats,
            final Logger log) {
        this.p2p = _p2p;
        this.run = _run;
        this.downloadedHeaders = _downloadedHeaders;
        this.headersWithBodiesRequested = _headersWithBodiesRequested;
        this.peerStates = peerStates;
        this.stats = _stats;
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
            String displayId = hw.getDisplayId();
            List<A0BlockHeader> headers = hw.getHeaders();
            if (headers.isEmpty()) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug(
                        "<get-bodies from-num={} to-num={} node={}>",
                        headers.get(0).getNumber(),
                        headers.get(headers.size() - 1).getNumber(),
                        hw.getDisplayId());
            }

            p2p.send(
                    idHash,
                    displayId,
                    new ReqBlocksBodies(
                            headers.stream().map(k -> k.getHash()).collect(Collectors.toList())));
            stats.updateTotalRequestsToPeer(displayId, RequestType.BODIES);
            stats.updateRequestTime(displayId, System.nanoTime(), RequestType.BODIES);

            headersWithBodiesRequested.put(idHash, hw);

            PeerState peerState = peerStates.get(hw.getNodeIdHash());
            if (peerState != null) {
                peerState.setState(State.BODIES_REQUESTED);
            } else {
                log.warn("Peer {} sent blocks that were not requested.", hw.getDisplayId());
            }
        }
    }
}

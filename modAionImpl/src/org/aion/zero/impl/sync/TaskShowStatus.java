package org.aion.zero.impl.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.base.util.Hex;
import org.aion.mcf.config.StatsType;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

/**
 * The thread print out sync status
 *
 * @author chris
 */
final class TaskShowStatus implements Runnable {

    private final AtomicBoolean start;

    private final int interval;

    private final AionBlockchainImpl chain;

    private final NetworkStatus networkStatus;

    private final SyncStats stats;

    private final Logger p2pLOG;

    private final IP2pMgr p2p;

    private final Map<Integer, PeerState> peerStates;
    private final Set<StatsType> showStatistics;

    TaskShowStatus(
            final AtomicBoolean _start,
            int _interval,
            final AionBlockchainImpl _chain,
            final NetworkStatus _networkStatus,
            final SyncStats _stats,
            final IP2pMgr _p2p,
            final Map<Integer, PeerState> _peerStates,
            final Set<StatsType> showStatistics,
            final Logger _log) {
        this.start = _start;
        this.interval = _interval;
        this.chain = _chain;
        this.networkStatus = _networkStatus;
        this.stats = _stats;
        this.p2p = _p2p;
        this.peerStates = _peerStates;
        this.p2pLOG = _log;
        this.showStatistics = Collections.unmodifiableSet(new HashSet<>(showStatistics));
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        String requestedInfo;

        while (this.start.get()) {

            String status = getStatus();
            p2pLOG.info(status);

            if (showStatistics.contains(StatsType.PEER_STATES)) {
                requestedInfo = dumpPeerStateInfo(p2p.getActiveNodes().values());
                if (!requestedInfo.isEmpty()) {
                    p2pLOG.info(requestedInfo);
                }
            }

            if (showStatistics.contains(StatsType.REQUESTS)) {
                requestedInfo = dumpRequestsInfo();
                if (!requestedInfo.isEmpty()) {
                    p2pLOG.info(requestedInfo);
                }
            }

            if (showStatistics.contains(StatsType.SEEDS)) {
                requestedInfo = dumpTopSeedsInfo();
                if (!requestedInfo.isEmpty()) {
                    p2pLOG.info(requestedInfo);
                }
            }

            if (showStatistics.contains(StatsType.LEECHES)) {
                requestedInfo = dumpTopLeechesInfo();
                if (!requestedInfo.isEmpty()) {
                    p2pLOG.info(requestedInfo);
                }
            }

            if (showStatistics.contains(StatsType.RESPONSES)) {
                requestedInfo = dumpResponseInfo();
                if (!requestedInfo.isEmpty()) {
                    p2pLOG.info(requestedInfo);
                }
            }

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                // without requested shutdown
                if (start.get() && p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug("sync-ss shutdown.", e);
                }
            }
        }

        // print all the gathered information before shutdown
        if (p2pLOG.isDebugEnabled()) {
            String status = getStatus();
            p2pLOG.debug(status);

            requestedInfo = dumpPeerStateInfo(p2p.getActiveNodes().values());
            if (!requestedInfo.isEmpty()) {
                p2pLOG.debug(requestedInfo);
            }
            requestedInfo = dumpRequestsInfo();
            if (!requestedInfo.isEmpty()) {
                p2pLOG.debug(requestedInfo);
            }
            requestedInfo = dumpTopSeedsInfo();
            if (!requestedInfo.isEmpty()) {
                p2pLOG.debug(requestedInfo);
            }
            requestedInfo = dumpTopLeechesInfo();
            if (!requestedInfo.isEmpty()) {
                p2pLOG.debug(requestedInfo);
            }
            requestedInfo = dumpResponseInfo();
            if (!requestedInfo.isEmpty()) {
                p2pLOG.debug(requestedInfo);
            }

            p2pLOG.debug("sync-ss shutdown");
        }
    }

    private String getStatus() {
        AionBlock selfBest = this.chain.getBestBlock();
        String selfTd = selfBest.getCumulativeDifficulty().toString(10);

        return "sync-status avg-import="
                + String.format("%.2f", this.stats.getAvgBlocksPerSec())
                //
                + " b/s" //
                + " td="
                + selfTd
                + "/"
                + networkStatus.getTargetTotalDiff().toString(10) //
                + " b-num="
                + selfBest.getNumber()
                + "/"
                + this.networkStatus.getTargetBestBlockNumber() //
                + " b-hash="
                + Hex.toHexString(this.chain.getBestBlockHash()) //
                + "/"
                + this.networkStatus.getTargetBestBlockHash()
                + "";
    }

    /**
     * Returns a log stream containing statistics about the percentage of requests made to each peer
     * with respect to the total number of requests made.
     *
     * @return log stream with requests statistical data
     */
    private String dumpRequestsInfo() {
        Map<String, Float> reqToPeers = this.stats.getPercentageOfRequestsToPeers();

        StringBuilder sb = new StringBuilder();

        if (!reqToPeers.isEmpty()) {

            sb.append("\n====== sync-requests-to-peers ======\n");
            sb.append(String.format("   %9s %20s\n", "peer", "% requests"));
            sb.append("------------------------------------\n");

            reqToPeers.forEach(
                    (nodeId, percReq) ->
                            sb.append(
                                    String.format(
                                            "   id:%6s %20s\n",
                                            nodeId, String.format("%.2f", percReq * 100) + " %")));
        }

        return sb.toString();
    }

    /**
     * Returns a log stream containing a list of peers ordered by the total number of blocks
     * received from each peer used to determine who is providing the majority of blocks, i.e. top
     * seeds.
     *
     * @return log stream with peers statistical data on seeds
     */
    private String dumpTopSeedsInfo() {
        Map<String, Long> totalBlocksByPeer = this.stats.getTotalBlocksByPeer();

        StringBuilder sb = new StringBuilder();

        if (!totalBlocksByPeer.isEmpty()) {

            sb.append(
                    "\n============================= sync-top-seeds ==============================\n");
            sb.append(
                    String.format(
                            "   %9s %20s %19s %19s\n",
                            "peer", "total blocks", "imported blocks", "stored blocks"));
            sb.append(
                    "---------------------------------------------------------------------------\n");
            totalBlocksByPeer.forEach(
                    (nodeId, totalBlocks) ->
                            sb.append(
                                    String.format(
                                            "   id:%6s %20s %19s %19s\n",
                                            nodeId,
                                            totalBlocks,
                                            this.stats.getImportedBlocksByPeer(nodeId),
                                            this.stats.getStoredBlocksByPeer(nodeId))));
        }

        return sb.toString();
    }

    /**
     * Obtain log stream containing a list of peers ordered by the total number of blocks requested
     * by each peer used to determine who is requesting the majority of blocks, i.e. top leeches.
     *
     * @return log stream with peers statistical data on leeches
     */
    private String dumpTopLeechesInfo() {
        Map<String, Long> totalBlockReqByPeer = this.stats.getTotalBlockRequestsByPeer();

        StringBuilder sb = new StringBuilder();

        if (!totalBlockReqByPeer.isEmpty()) {

            sb.append("\n========= sync-top-leeches =========\n");
            sb.append(String.format("   %9s %20s\n", "peer", "total blocks"));
            sb.append("------------------------------------\n");

            totalBlockReqByPeer.forEach(
                    (nodeId, totalBlocks) ->
                            sb.append(String.format("   id:%6s %20s\n", nodeId, totalBlocks)));
        }

        return sb.toString();
    }

    /**
     * Obtain log stream containing statistics about the average response time between sending
     * status requests out and that peer responding shown for each peer and averaged for all peers.
     *
     * @return log stream with requests statistical data
     */
    private String dumpResponseInfo() {

        Map<String, Map<String, Pair<Double, Integer>>> responseStats =
                this.stats.getResponseStats();
        StringBuilder sb = new StringBuilder();

        if (!responseStats.isEmpty()) {

            sb.append(
                    "\n========================== sync-responses-by-peer ==========================\n");
            sb.append(
                    String.format(
                            "   %9s %20s %19s %19s \n",
                            "peer", "request type", "avg. response", "number of pairs"));
            sb.append(
                    "----------------------------------------------------------------------------\n");

            Map<String, Pair<Double, Integer>> peerStats = responseStats.get("overall");
            for (String type : peerStats.keySet()) {
                sb.append(
                        String.format(
                                "   «overall» %20s %16s ms %19d\n",
                                "«" + type + "»",
                                String.format("%.0f", peerStats.get(type).getLeft() / 1_000_000),
                                peerStats.get(type).getRight()));
            }
            for (String nodeId : responseStats.keySet()) {
                if (nodeId != "overall") {
                   peerStats = responseStats.get(nodeId);
                    for (String type : peerStats.keySet()) {
                        sb.append(
                                String.format(
                                        "   id:%6s %20s %16s ms %19d\n",
                                        nodeId,
                                        "«" + type + "»",
                                        String.format(
                                                "%.0f", peerStats.get(type).getLeft() / 1_000_000),
                                        peerStats.get(type).getRight()));
                    }
                }
            }
        }
        return sb.toString();
    }

    private String dumpPeerStateInfo(Collection<INode> filtered) {
        List<NodeState> sorted = new ArrayList<>();
        for (INode n : filtered) {
            PeerState s = peerStates.get(n.getIdHash());
            if (s != null) {
                sorted.add(new NodeState(n, s));
            }
        }

        if (!sorted.isEmpty()) {
            sorted.sort((n1, n2) -> Long.compare(n2.getS().getBase(), n1.getS().getBase()));

            StringBuilder sb = new StringBuilder();
            sb.append("\n");
            sb.append(
                    "====================================== sync-peer-states-status ======================================\n");
            sb.append(
                    String.format(
                            "   %9s %16s %18s %10s %16s %4s %16s\n",
                            "peer", "# best block", "state", "mode", "base", "rp", "last request"));
            sb.append(
                    "-----------------------------------------------------------------------------------------------------\n");

            for (NodeState ns : sorted) {
                INode n = ns.getN();
                PeerState s = ns.getS();

                sb.append(
                        String.format(
                                "   id:%6s %16d %18s %10s %16d %4d %16d\n",
                                n.getIdShort(),
                                n.getBestBlockNumber(),
                                s.getState(),
                                s.getMode(),
                                s.getBase(),
                                s.getRepeated(),
                                s.getLastHeaderRequest()));
            }
            return sb.toString();
        }
        return "";
    }

    private class NodeState {

        INode n;
        PeerState s;

        NodeState(INode _n, PeerState _s) {
            this.n = _n;
            this.s = _s;
        }

        public INode getN() {
            return n;
        }

        public PeerState getS() {
            return s;
        }
    }
}

package org.aion.zero.impl.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.mcf.config.StatsType;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
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
        String requestedStats;

        while (this.start.get()) {

            String status = getStatus();
            p2pLOG.info(status);

            if (showStatistics.contains(StatsType.PEER_STATES)) {
                requestedStats = dumpPeerStateInfo(p2p.getActiveNodes().values());
                if (!requestedStats.isEmpty()) {
                    p2pLOG.info(requestedStats);
                }
            }

            if (showStatistics.contains(StatsType.REQUESTS)) {
                requestedStats = stats.dumpRequestStats();
                if (!requestedStats.isEmpty()) {
                    p2pLOG.info(requestedStats);
                }
            }

            if (showStatistics.contains(StatsType.SEEDS)) {
                requestedStats = stats.dumpTopSeedsStats();
                if (!requestedStats.isEmpty()) {
                    p2pLOG.info(requestedStats);
                }
            }

            if (showStatistics.contains(StatsType.LEECHES)) {
                requestedStats = stats.dumpTopLeechesStats();
                if (!requestedStats.isEmpty()) {
                    p2pLOG.info(requestedStats);
                }
            }

            if (showStatistics.contains(StatsType.RESPONSES)) {
                requestedStats = stats.dumpResponseStats();
                if (!requestedStats.isEmpty()) {
                    p2pLOG.info(requestedStats);
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

            requestedStats = dumpPeerStateInfo(p2p.getActiveNodes().values());
            if (!requestedStats.isEmpty()) {
                p2pLOG.debug(requestedStats);
            }
            requestedStats = stats.dumpRequestStats();
            if (!requestedStats.isEmpty()) {
                p2pLOG.debug(requestedStats);
            }
            requestedStats = stats.dumpTopSeedsStats();
            if (!requestedStats.isEmpty()) {
                p2pLOG.debug(requestedStats);
            }
            requestedStats = stats.dumpTopLeechesStats();
            if (!requestedStats.isEmpty()) {
                p2pLOG.debug(requestedStats);
            }
            requestedStats = stats.dumpResponseStats();
            if (!requestedStats.isEmpty()) {
                p2pLOG.debug(requestedStats);
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

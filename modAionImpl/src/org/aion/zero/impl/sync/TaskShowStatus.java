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

package org.aion.zero.impl.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.base.util.Hex;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
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

    TaskShowStatus(
            final AtomicBoolean _start,
            int _interval,
            final AionBlockchainImpl _chain,
            final NetworkStatus _networkStatus,
            final SyncStats _stats,
            final IP2pMgr _p2p,
            final Map<Integer, PeerState> _peerStates,
            final Logger _log) {
        this.start = _start;
        this.interval = _interval;
        this.chain = _chain;
        this.networkStatus = _networkStatus;
        this.stats = _stats;
        this.p2p = _p2p;
        this.peerStates = _peerStates;
        this.p2pLOG = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        while (this.start.get()) {
            AionBlock selfBest = this.chain.getBestBlock();
            String selfTd = selfBest.getCumulativeDifficulty().toString(10);

            String status =
                    "sync-status avg-import="
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

            p2pLOG.info(status);

            if (p2pLOG.isDebugEnabled()) {
                String s = dumpPeerStateInfo(p2p.getActiveNodes().values());
                s += dumpPeerStatsInfo();
                if (!s.isEmpty()) {
                    p2pLOG.debug(s);
                }
            }

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                // without requested shutdown
                if (start.get() && p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug("sync-ss shutdown.", e);
                }
                return;
            }
        }
        if (p2pLOG.isDebugEnabled()) {
            p2pLOG.debug("sync-ss shutdown");
        }
    }

    /**
     * Obtain log stream containing statistics about requests and blocks processed by/from peer
     * nodes.
     *
     * @return log stream with peers statistical data
     */
    private String dumpPeerStatsInfo() {
        Map<String, Float> reqToPeers = this.stats.getPercentageOfRequestsToPeers();
        Map<String, Long> totalBlockReqByPeer = this.stats.getTotalBlockRequestsByPeer();
        Map<String, Long> totalBlocksByPeer = this.stats.getTotalBlocksByPeer();
        Map<String, Double> avgResponseTimeByPeers = this.stats.getAverageResponseTimeByPeers();

        StringBuilder sb = new StringBuilder();

        if (!reqToPeers.isEmpty()) {

            sb.append(
                    String.format(
                            "=================================================================== sync-requests-to-peers ===================================================================\n"));

            sb.append(String.format("%9s %10s\n", "id", "% requests"));

            reqToPeers.forEach(
                    (nodeId, percReq) -> {
                        sb.append(
                                String.format(
                                        "id:%6s %10s\n",
                                        nodeId, String.format("%.2f", percReq * 100) + " %"));
                    });
        }

        if (!totalBlocksByPeer.isEmpty()) {

            sb.append(
                    String.format(
                            "==================================================================== sync-blocks-by-peer =====================================================================\n"));

            sb.append(String.format("%9s %18s\n", "id", "Total blocks"));

            totalBlocksByPeer.forEach(
                    (nodeId, totalBlocks) -> {
                        sb.append(String.format("id:%6s %18s\n", nodeId, totalBlocks));
                    });
        }

        if (!totalBlockReqByPeer.isEmpty()) {

            sb.append(
                    String.format(
                            "================================================================= sync-block-requests-by-peer ================================================================\n"));

            sb.append(String.format("%9s %18s\n", "id", "Total blocks"));

            totalBlockReqByPeer.forEach(
                    (nodeId, totalBlocks) -> {
                        sb.append(String.format("id:%6s %18s\n", nodeId, totalBlocks));
                    });
        }

        if (!avgResponseTimeByPeers.isEmpty()) {

            Long overallAvgResponse = this.stats.getOverallAveragePeerResponseTime();

            sb.append(
                    String.format(
                            "================================================================= sync-avg-response-by-peer ==================================================================\n"));

            sb.append(String.format("%9s %13s\n", "id", "Avg. Response"));
            sb.append(String.format("==Overall %10s ms\n", overallAvgResponse));

            avgResponseTimeByPeers.forEach(
                    (nodeId, avgResponse) -> {
                        sb.append(
                                String.format(
                                        "id:%6s %10s ms\n",
                                        nodeId, String.format("%.0f", avgResponse)));
                    });
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
                    "==================================================================== sync-peer-states-status ====================================================================\n");
            sb.append(
                    String.format(
                            "%9s %16s %17s %8s %16s %2s %16s\n",
                            "id", "# best block", "state", "mode", "base", "rp", "last request"));
            sb.append(
                    "-----------------------------------------------------------------------------------------------------------------------------------------------------------------\n");

            for (NodeState ns : sorted) {
                INode n = ns.getN();
                PeerState s = ns.getS();

                sb.append(
                        String.format(
                                "id:%6s %16d %17s %8s %16d %2d %16d\n",
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

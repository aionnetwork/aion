package org.aion.zero.impl.sync.statistics;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.zero.impl.sync.RequestType;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Records information on the time delay between requests and subsequent responses for different
 * types of p2p messages.
 *
 * @implNote This resource has its own locking mechanism and is thread safe.
 * @author Alexandra Roatis
 * @author Beidou Zhang
 */
public class ResponseStatsTracker {
    // track status, headers and bodes messages
    private final EnumMap<RequestType, ResponseStats> responseStats =
            new EnumMap<>(RequestType.class);
    private final EnumMap<RequestType, Lock> responseLocks = new EnumMap<>(RequestType.class);

    public ResponseStatsTracker(int maxActivePeers) {
        for (RequestType type : RequestType.values()) {
            // instantiate objects for gathering stats
            this.responseStats.put(type, new ResponseStats(maxActivePeers));
            // instantiate locks
            this.responseLocks.put(type, new ReentrantLock());
        }
    }

    public Map<String, Map<String, Pair<Double, Integer>>> getResponseStats() {
        // acquire lock for all resources
        this.responseLocks.get(RequestType.TRIE_DATA).lock();
        this.responseLocks.get(RequestType.RECEIPTS).lock();
        this.responseLocks.get(RequestType.BLOCKS).lock();
        this.responseLocks.get(RequestType.BODIES).lock();
        this.responseLocks.get(RequestType.HEADERS).lock();
        this.responseLocks.get(RequestType.STATUS).lock();

        try {
            Map<String, Pair<Double, Integer>> statusStats =
                    this.responseStats.get(RequestType.STATUS).getResponseStatsByPeers();
            Map<String, Pair<Double, Integer>> headersStats =
                    this.responseStats.get(RequestType.HEADERS).getResponseStatsByPeers();
            Map<String, Pair<Double, Integer>> bodiesStats =
                    this.responseStats.get(RequestType.BODIES).getResponseStatsByPeers();
            Map<String, Pair<Double, Integer>> blocksStats =
                    this.responseStats.get(RequestType.BLOCKS).getResponseStatsByPeers();
            Map<String, Pair<Double, Integer>> receiptsStats =
                    this.responseStats.get(RequestType.RECEIPTS).getResponseStatsByPeers();
            Map<String, Pair<Double, Integer>> trieDataStats =
                    this.responseStats.get(RequestType.TRIE_DATA).getResponseStatsByPeers();

            // skip if there's nothing to show
            if (statusStats.isEmpty()
                    && headersStats.isEmpty()
                    && bodiesStats.isEmpty()
                    && blocksStats.isEmpty()
                    && receiptsStats.isEmpty()
                    && trieDataStats.isEmpty()) {
                return null;
            }

            Map<String, Map<String, Pair<Double, Integer>>> responseStats = new LinkedHashMap<>();

            Pair<Double, Integer> statusOverall = Pair.of(0d, 0);
            Pair<Double, Integer> headersOverall = Pair.of(0d, 0);
            Pair<Double, Integer> bodiesOverall = Pair.of(0d, 0);
            Pair<Double, Integer> blocksOverall = Pair.of(0d, 0);
            Pair<Double, Integer> receiptsOverall = Pair.of(0d, 0);
            Pair<Double, Integer> trieDataOverall = Pair.of(0d, 0);

            // used in computing averages
            int count;

            // making sure to grab all peers
            Set<String> peers = new HashSet<>(statusStats.keySet());
            peers.addAll(headersStats.keySet());
            peers.addAll(bodiesStats.keySet());
            peers.addAll(blocksStats.keySet());
            peers.addAll(receiptsStats.keySet());
            peers.addAll(trieDataStats.keySet());

            for (String nodeId : peers) {

                Map<String, Pair<Double, Integer>> peerStats = new LinkedHashMap<>();
                Pair<Double, Integer> status = statusStats.getOrDefault(nodeId, Pair.of(0d, 0));
                Pair<Double, Integer> headers = headersStats.getOrDefault(nodeId, Pair.of(0d, 0));
                Pair<Double, Integer> bodies = bodiesStats.getOrDefault(nodeId, Pair.of(0d, 0));
                Pair<Double, Integer> blocks = blocksStats.getOrDefault(nodeId, Pair.of(0d, 0));
                Pair<Double, Integer> receipts = receiptsStats.getOrDefault(nodeId, Pair.of(0d, 0));
                Pair<Double, Integer> trieData = trieDataStats.getOrDefault(nodeId, Pair.of(0d, 0));

                count =
                        status.getRight()
                                + headers.getRight()
                                + bodies.getRight()
                                + blocks.getRight()
                                + receipts.getRight()
                                + trieData.getRight();

                Pair<Double, Integer> avgStats;
                // ensuring there are entries
                if (count > 0) {
                    avgStats =
                            Pair.of(
                                    (status.getLeft() * status.getRight()
                                                    + headers.getLeft() * headers.getRight()
                                                    + bodies.getLeft() * bodies.getRight()
                                                    + blocks.getLeft() * blocks.getRight()
                                                    + receipts.getLeft() * receipts.getRight()
                                                    + trieData.getLeft() * trieData.getRight())
                                            / count,
                                    count);
                } else {
                    avgStats = Pair.of(0d, 0);
                }

                peerStats.put("all", avgStats);
                peerStats.put("status", status);
                peerStats.put("headers", headers);
                peerStats.put("bodies", bodies);
                peerStats.put("blocks", blocks);
                peerStats.put("receipts", receipts);
                peerStats.put("trieData", trieData);
                responseStats.put(nodeId, peerStats);

                // adding to overall status
                count = statusOverall.getRight() + status.getRight();
                // ensuring there are entries
                if (count > 0) {
                    statusOverall =
                            Pair.of(
                                    (statusOverall.getLeft() * statusOverall.getRight()
                                                    + status.getLeft() * status.getRight())
                                            / count,
                                    count);
                } // nothing to do if count == 0

                // adding to overall headers
                count = headersOverall.getRight() + headers.getRight();
                // ensuring there are entries
                if (count > 0) {
                    headersOverall =
                            Pair.of(
                                    (headersOverall.getLeft() * headersOverall.getRight()
                                                    + headers.getLeft() * headers.getRight())
                                            / count,
                                    count);
                } // nothing to do if count == 0

                // adding to overall headers
                count = bodiesOverall.getRight() + bodies.getRight();
                // ensuring there are entries
                if (count > 0) {
                    bodiesOverall =
                            Pair.of(
                                    (bodiesOverall.getLeft() * bodiesOverall.getRight()
                                                    + bodies.getLeft() * bodies.getRight())
                                            / count,
                                    count);
                } // nothing to do if count == 0

                // adding to overall blocks
                count = blocksOverall.getRight() + blocks.getRight();
                // ensuring there are entries
                if (count > 0) {
                    blocksOverall =
                            Pair.of(
                                    (blocksOverall.getLeft() * blocksOverall.getRight()
                                                    + blocks.getLeft() * blocks.getRight())
                                            / count,
                                    count);
                } // nothing to do if count == 0

                // adding to overall receipts
                count = receiptsOverall.getRight() + receipts.getRight();
                // ensuring there are entries
                if (count > 0) {
                    receiptsOverall =
                            Pair.of(
                                    (receiptsOverall.getLeft() * receiptsOverall.getRight()
                                                    + receipts.getLeft() * receipts.getRight())
                                            / count,
                                    count);
                } // nothing to do if count == 0

                // adding to overall trie data
                count = trieDataOverall.getRight() + trieData.getRight();
                // ensuring there are entries
                if (count > 0) {
                    trieDataOverall =
                            Pair.of(
                                    (trieDataOverall.getLeft() * trieDataOverall.getRight()
                                                    + trieData.getLeft() * trieData.getRight())
                                            / count,
                                    count);
                } // nothing to do if count == 0
            }

            count =
                    statusOverall.getRight()
                            + headersOverall.getRight()
                            + bodiesOverall.getRight()
                            + blocksOverall.getRight()
                            + receiptsOverall.getRight()
                            + trieDataOverall.getRight();
            Pair<Double, Integer> avgOverall;
            // ensuring there are entries
            if (count > 0) {
                avgOverall =
                        Pair.of(
                                (statusOverall.getLeft() * statusOverall.getRight()
                                                + headersOverall.getLeft()
                                                        * headersOverall.getRight()
                                                + bodiesOverall.getLeft() * bodiesOverall.getRight()
                                                + blocksOverall.getLeft() * blocksOverall.getRight()
                                                + receiptsOverall.getLeft()
                                                        * receiptsOverall.getRight()
                                                + trieDataOverall.getLeft()
                                                        * trieDataOverall.getRight())
                                        / count,
                                count);
            } else {
                avgOverall = Pair.of(0d, 0);
            }

            Map<String, Pair<Double, Integer>> overallStats = new LinkedHashMap<>();
            overallStats.put("all", avgOverall);
            overallStats.put("status", statusOverall);
            overallStats.put("headers", headersOverall);
            overallStats.put("bodies", bodiesOverall);
            overallStats.put("blocks", blocksOverall);
            overallStats.put("receipts", receiptsOverall);
            overallStats.put("trieData", trieDataOverall);

            responseStats.put("overall", overallStats);

            return responseStats;
        } finally {
            // unlock in reverse order
            this.responseLocks.get(RequestType.STATUS).unlock();
            this.responseLocks.get(RequestType.HEADERS).unlock();
            this.responseLocks.get(RequestType.BODIES).unlock();
            this.responseLocks.get(RequestType.BLOCKS).unlock();
            this.responseLocks.get(RequestType.RECEIPTS).unlock();
            this.responseLocks.get(RequestType.TRIE_DATA).unlock();
        }
    }

    public String dumpResponseStats() {
        Map<String, Map<String, Pair<Double, Integer>>> responseStats = getResponseStats();
        StringBuffer sb = new StringBuffer();

        if (responseStats != null && !responseStats.isEmpty()) {

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

    public void updateRequestTime(String displayId, long requestTime, RequestType requestType) {
        Lock responseLock = responseLocks.get(requestType);
        responseLock.lock();
        try {
            responseStats.get(requestType).updateRequestTime(displayId, requestTime);
        } finally {
            responseLock.unlock();
        }
    }

    public void updateResponseTime(String displayId, long responseTime, RequestType requestType) {
        Lock responseLock = responseLocks.get(requestType);
        responseLock.lock();
        try {
            responseStats.get(requestType).updateResponseStats(displayId, responseTime);
        } finally {
            responseLock.unlock();
        }
    }
}

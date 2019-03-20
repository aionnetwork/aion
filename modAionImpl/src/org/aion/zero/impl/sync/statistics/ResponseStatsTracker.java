package org.aion.zero.impl.sync.statistics;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
    private final EnumMap<RequestType, ResponseStats> stats = new EnumMap<>(RequestType.class);
    private final EnumMap<RequestType, Lock> locks = new EnumMap<>(RequestType.class);

    public ResponseStatsTracker(int maxActivePeers) {
        for (RequestType type : RequestType.values()) {
            // instantiate objects for gathering stats
            this.stats.put(type, new ResponseStats(maxActivePeers));
            // instantiate locks
            this.locks.put(type, new ReentrantLock());
        }
    }

    public void updateRequestTime(String displayId, long requestTime, RequestType requestType) {
        Lock responseLock = locks.get(requestType);
        responseLock.lock();
        try {
            stats.get(requestType).updateRequestTime(displayId, requestTime);
        } finally {
            responseLock.unlock();
        }
    }

    public void updateResponseTime(String displayId, long responseTime, RequestType requestType) {
        Lock responseLock = locks.get(requestType);
        responseLock.lock();
        try {
            stats.get(requestType).updateResponseTime(displayId, responseTime);
        } finally {
            responseLock.unlock();
        }
    }

    public Map<String, Map<String, Pair<Double, Integer>>> getResponseStats() {
        // acquire lock for all resources, unlock in reverse order
        List lockTypes = Arrays.asList(RequestType.values());
        lockTypes.forEach(type -> locks.get(type).lock());

        try {
            boolean empty = true;
            EnumMap<RequestType, Map<String, Pair<Double, Integer>>> responseStats =
                    new EnumMap<>(RequestType.class);
            EnumMap<RequestType, Pair<Double, Integer>> overallStats =
                    new EnumMap<>(RequestType.class);
            Set<String> peers = new HashSet<>();

            for (RequestType type : RequestType.values()) {
                Map<String, Pair<Double, Integer>> stats =
                        this.stats.get(type).getResponseStatsByPeers();
                responseStats.put(type, stats);

                if (!stats.isEmpty()) {
                    peers.addAll(stats.keySet());
                    empty = false;
                }

                // used to calculate overall stats
                overallStats.put(type, Pair.of(0d, 0));
            }

            if (empty) return null;

            Map<String, Map<String, Pair<Double, Integer>>> processedStats = new LinkedHashMap<>();
            Pair<Double, Integer> statOverall;
            int overallCount;

            for (String nodeId : peers) {
                Map<String, Pair<Double, Integer>> peerStats = new LinkedHashMap<>();
                // used in computing averages for each peer
                int count = 0;
                Pair<Double, Integer> avgStats = Pair.of(0d, 0); // average for each peer

                for (RequestType type : RequestType.values()) {
                    Pair<Double, Integer> stat =
                            responseStats.get(type).getOrDefault(nodeId, Pair.of(0d, 0));

                    // add different types of stats to current peer
                    peerStats.put(type.toString().toLowerCase(), stat);
                    // calculate average stats for current peer
                    count += stat.getRight();
                    if (count > 0) {
                        avgStats =
                                Pair.of(
                                        (avgStats.getLeft() * avgStats.getRight()
                                                        + stat.getLeft() * stat.getRight())
                                                / count,
                                        count);
                    } // do nothing if count is 0

                    // update overall stats for current request type
                    statOverall = overallStats.getOrDefault(type, Pair.of(0d, 0));
                    overallCount = statOverall.getRight() + stat.getRight();
                    if (overallCount > 0) {
                        overallStats.put(
                                type,
                                Pair.of(
                                        (statOverall.getLeft() * statOverall.getRight()
                                                        + stat.getLeft() * stat.getRight())
                                                / overallCount,
                                        overallCount));
                    } // do nothing if count is 0
                }
                peerStats.put("all", avgStats);
                processedStats.put(nodeId, peerStats);
            }

            Pair<Double, Integer> avgOverall = Pair.of(0d, 0);
            overallCount = 0;
            Map<String, Pair<Double, Integer>> overall = new LinkedHashMap<>();

            for (RequestType type : RequestType.values()) {
                statOverall = overallStats.get(type);
                overall.put(type.toString().toLowerCase(), statOverall);

                // calculate overall average stats
                overallCount += statOverall.getRight();
                if (overallCount > 0) {
                    avgOverall =
                            Pair.of(
                                    (avgOverall.getLeft() * avgOverall.getRight()
                                                    + statOverall.getLeft()
                                                            * statOverall.getRight())
                                            / overallCount,
                                    overallCount);
                }
            }
            overall.put("all", avgOverall);
            processedStats.put("overall", overall);

            return processedStats;

        } finally {
            // unlock all locks in reverse order
            Collections.reverse(lockTypes);
            lockTypes.forEach(type -> locks.get(type).unlock());
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
            sb.append(
                    String.format(
                            "   «overall» %20s %16s ms %19d\n",
                            "«all»",
                            String.format("%.0f", peerStats.get("all").getLeft() / 1_000_000),
                            peerStats.get("all").getRight()));
            for (String type : peerStats.keySet()) {
                if (type != "all") {
                    sb.append(
                            String.format(
                                    "   «overall» %20s %16s ms %19d\n",
                                    "«" + type + "»",
                                    String.format(
                                            "%.0f", peerStats.get(type).getLeft() / 1_000_000),
                                    peerStats.get(type).getRight()));
                }
            }

            for (String nodeId : responseStats.keySet()) {
                if (nodeId != "overall") {
                    peerStats = responseStats.get(nodeId);
                    sb.append(
                            String.format(
                                    "   id:%6s %20s %16s ms %19d\n",
                                    nodeId,
                                    "«all»",
                                    String.format(
                                            "%.0f", peerStats.get("all").getLeft() / 1_000_000),
                                    peerStats.get("all").getRight()));
                    for (String type : peerStats.keySet()) {
                        if (type != "all") {
                            sb.append(
                                    String.format(
                                            "   id:%6s %20s %16s ms %19d\n",
                                            nodeId,
                                            "«" + type + "»",
                                            String.format(
                                                    "%.0f",
                                                    peerStats.get(type).getLeft() / 1_000_000),
                                            peerStats.get(type).getRight()));
                        }
                    }
                }
            }
        }
        return sb.toString();
    }
}

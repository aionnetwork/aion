package org.aion.zero.impl.sync.statistics;

import java.util.HashSet;
import java.util.LinkedHashMap;
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

    // tracks status messages
    private final ResponseStats status;
    private final Lock lockStatus;

    // track headers messages
    private final ResponseStats headers;
    private final Lock lockHeaders;

    // track bodies messages
    private final ResponseStats bodies;
    private final Lock lockBodies;

    public ResponseStatsTracker() {
        // instantiate objects for gathering stats
        this.status = new ResponseStats();
        this.headers = new ResponseStats();
        this.bodies = new ResponseStats();

        // instantiate locks
        this.lockStatus = new ReentrantLock();
        this.lockHeaders = new ReentrantLock();
        this.lockBodies = new ReentrantLock();
    }

    public Map<String, Map<String, Pair<Double, Integer>>> getResponseStats() {
        // acquire lock for all resources
        lockBodies.lock();
        lockHeaders.lock();
        lockStatus.lock();

        try {
            Map<String, Pair<Double, Integer>> statusStats = this.status.getResponseStatsByPeers();
            Map<String, Pair<Double, Integer>> headersStats =
                    this.headers.getResponseStatsByPeers();
            Map<String, Pair<Double, Integer>> bodiesStats = this.bodies.getResponseStatsByPeers();

            Map<String, Map<String, Pair<Double, Integer>>> responseStats = new LinkedHashMap<>();

            Pair<Double, Integer> statusOverall = Pair.of(0d, 0);
            Pair<Double, Integer> headersOverall = Pair.of(0d, 0);
            Pair<Double, Integer> bodiesOverall = Pair.of(0d, 0);

            // used in computing averages
            int count;

            // making sure to grab all peers
            Set<String> peers = new HashSet<>(statusStats.keySet());
            peers.addAll(headersStats.keySet());
            peers.addAll(bodiesStats.keySet());

            for (String nodeId : peers) {

                Map<String, Pair<Double, Integer>> peerStats = new LinkedHashMap<>();
                Pair<Double, Integer> status = statusStats.getOrDefault(nodeId, Pair.of(0d, 0));
                Pair<Double, Integer> headers = headersStats.getOrDefault(nodeId, Pair.of(0d, 0));
                Pair<Double, Integer> bodies = bodiesStats.getOrDefault(nodeId, Pair.of(0d, 0));

                count = status.getRight() + headers.getRight() + bodies.getRight();
                Pair<Double, Integer> avgStats;
                // ensuring there are entries
                if (count > 0) {
                    avgStats =
                            Pair.of(
                                    (status.getLeft() * status.getRight()
                                                    + headers.getLeft() * headers.getRight()
                                                    + bodies.getLeft() * bodies.getRight())
                                            / count,
                                    count);
                } else {
                    avgStats = Pair.of(0d, 0);
                }

                peerStats.put("all", avgStats);
                peerStats.put("status", status);
                peerStats.put("headers", headers);
                peerStats.put("bodies", bodies);
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
            }

            count = statusOverall.getRight() + headersOverall.getRight() + bodiesOverall.getRight();
            Pair<Double, Integer> avgOverall;
            // ensuring there are entries
            if (count > 0) {
                avgOverall =
                        Pair.of(
                                (statusOverall.getLeft() * statusOverall.getRight()
                                                + headersOverall.getLeft()
                                                        * headersOverall.getRight()
                                                + bodiesOverall.getLeft()
                                                        * bodiesOverall.getRight())
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
            responseStats.put("overall", overallStats);

            return responseStats;
        } finally {
            // unlock in reverse order
            lockStatus.unlock();
            lockHeaders.unlock();
            lockBodies.unlock();
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

    public void updateStatusRequest(String displayId, long requestTime) {
        lockStatus.lock();
        try {
            status.addPeerRequestTime(displayId, requestTime);
        } finally {
            lockStatus.unlock();
        }
    }

    public void updateHeadersRequest(String displayId, long requestTime) {
        lockHeaders.lock();
        try {
            headers.addPeerRequestTime(displayId, requestTime);
        } finally {
            lockHeaders.unlock();
        }
    }

    public void updateBodiesRequest(String displayId, long requestTime) {
        lockBodies.lock();
        try {
            bodies.addPeerRequestTime(displayId, requestTime);
        } finally {
            lockBodies.unlock();
        }
    }

    public void updateStatusResponse(String displayId, long responseTime) {
        lockStatus.lock();
        try {
            status.updatePeerResponseStats(displayId, responseTime);
        } finally {
            lockStatus.unlock();
        }
    }

    public void updateHeadersResponse(String displayId, long responseTime) {
        lockHeaders.lock();
        try {
            headers.updatePeerResponseStats(displayId, responseTime);
        } finally {
            lockHeaders.unlock();
        }
    }

    public void updateBodiesResponse(String displayId, long responseTime) {
        lockBodies.lock();
        try {
            bodies.updatePeerResponseStats(displayId, responseTime);
        } finally {
            lockBodies.unlock();
        }
    }
}

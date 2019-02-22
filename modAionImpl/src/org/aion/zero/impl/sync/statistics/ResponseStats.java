package org.aion.zero.impl.sync.statistics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.tuple.Pair;

public class ResponseStats {

    /** Records time of requests made to peers. */
    private final Map<String, Deque<Long>> requestTimeByPeers = new HashMap<>();

    /** Records average response time of peers and number of aggregates data points. */
    private final Map<String, Pair<Double, Integer>> responseStatsByPeers = new LRUMap<>(128);

    /**
     * Log the time of a request sent to a peer.
     *
     * @param nodeId peer display identifier
     * @param requestTime time when the request was sent in nanoseconds
     */
    public void updateRequestTime(String nodeId, long requestTime) {
        if (requestTimeByPeers.containsKey(nodeId)) {
            requestTimeByPeers.get(nodeId).add(requestTime);
        } else {
            Deque<Long> requestStartTimes = new ArrayDeque<>();
            requestStartTimes.add(requestTime);
            requestTimeByPeers.put(nodeId, requestStartTimes);
        }
    }

    /**
     * Log the time of a response received from a peer and update the computed average time and
     * number of data points.
     *
     * @param nodeId peer display identifier
     * @param responseTime time when the response was received in nanoseconds
     */
    public void updateResponseTime(String nodeId, long responseTime) {
        if (!requestTimeByPeers.containsKey(nodeId) || requestTimeByPeers.get(nodeId).isEmpty()) {
            return;
        }

        Long matchingRequestTime = requestTimeByPeers.get(nodeId).pollFirst();
        // there was a matching entry and it's correct wrt to time expectation
        if (matchingRequestTime != null && responseTime >= matchingRequestTime) {
            if (responseStatsByPeers.containsKey(nodeId)) {
                Pair<Double, Integer> stats = responseStatsByPeers.get(nodeId);
                int newCount = stats.getRight() + 1;
                responseStatsByPeers.put(
                        nodeId,
                        Pair.of(
                                (stats.getLeft() * stats.getRight() // old sum
                                                + (responseTime - matchingRequestTime)) // new data
                                        / newCount, // new count
                                newCount));

            } else {
                responseStatsByPeers.put(
                        nodeId, Pair.of((double) responseTime - matchingRequestTime, 1));
            }
        }
    }

    /**
     * Returns the average response time and the number of recorded request/response pairs for each
     * peer.
     *
     * @return map of pairs containing the average response time in nanoseconds and the number of
     *     recorded request/response pairs for each peer
     */
    public Map<String, Pair<Double, Integer>> getResponseStatsByPeers() {
        return this.responseStatsByPeers;
    }
}

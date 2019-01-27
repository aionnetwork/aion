package org.aion.zero.impl.sync;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class ResponseMgr {

    private final Lock responseLock = new ReentrantLock();

    /**
     * Records time of requests/responses for active peer nodes
     *
     * @implNote Access to this resource is managed by the {@Link #responseLock}.
     */
    private final Map<String, List<Long>> requestTimeByPeers = new HashMap<>();

    private final Map<String, List<Long>> responseTimeByPeers = new HashMap<>();

    private double overallAvgPeerResponseTime;
    private int overallNumberOfPairs;

    ResponseMgr() {
        this.overallAvgPeerResponseTime = 0L;
        this.overallNumberOfPairs = 0;
    }

    /**
     * Log the time of request sent to an active peer node
     *
     * @param _nodeId peer node displya Id
     * @param _requestTime time when the request was sent in nanoseconds
     */
    public void addPeerRequestTime(String _nodeId, long _requestTime) {
        responseLock.lock();
        try {
            List<Long> requestStartTimes =
                    requestTimeByPeers.containsKey(_nodeId)
                            ? requestTimeByPeers.get(_nodeId)
                            : new LinkedList<>();
            requestStartTimes.add(_requestTime);
            requestTimeByPeers.put(_nodeId, requestStartTimes);
        } finally {
            responseLock.unlock();
        }
    }

    /**
     * Log the time of response received from an active peer node
     *
     * @param _nodeId peer node display Id
     * @param _responseTime time when the response was received in nanoseconds
     */
    public void addPeerResponseTime(String _nodeId, long _responseTime) {
        responseLock.lock();
        try {
            List<Long> responseEndTimes =
                    responseTimeByPeers.containsKey(_nodeId)
                            ? responseTimeByPeers.get(_nodeId)
                            : new LinkedList<>();
            responseEndTimes.add(_responseTime);
            responseTimeByPeers.put(_nodeId, responseEndTimes);
        } finally {
            responseLock.unlock();
        }
    }

    /**
     * Obtains the average response time and the number of request/response pairs by each active
     * peer node
     *
     * @return map of pairs containing average reseponse time in nanoseconds and number of
     *     request/response pairs by peer node
     */
    public Map<String, Pair<Double, Integer>> getResponseStatsByPeers() {
        responseLock.lock();
        try {
            Pair<Double, Integer> stats;
            String nodeId;
            List<Long> requests, responses;

            Map<String, Pair<Double, Integer>> avgResponseStatsByPeers = new HashMap<>();
            overallAvgPeerResponseTime = 0d;
            overallNumberOfPairs = 0;

            for (Map.Entry<String, List<Long>> peerData : requestTimeByPeers.entrySet()) {
                nodeId = peerData.getKey();
                requests = peerData.getValue();
                responses = responseTimeByPeers.getOrDefault(nodeId, new LinkedList());

                // calculate the average response time and number of request/response pairs
                stats = calculateStats(requests, responses);

                if (stats != null) {
                    // collect a map of average response times by peer
                    avgResponseStatsByPeers.put(nodeId, stats);
                    overallAvgPeerResponseTime += stats.getLeft();
                    overallNumberOfPairs += stats.getRight();
                }
            }

            overallAvgPeerResponseTime =
                    avgResponseStatsByPeers.isEmpty()
                            ? 0d
                            : overallAvgPeerResponseTime / avgResponseStatsByPeers.size();

            return avgResponseStatsByPeers.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(
                            Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e2,
                                    LinkedHashMap::new));
        } finally {
            responseLock.unlock();
        }
    }

    /**
     * Computes the average response time for a peer given the lists of gathered requests and
     * response times.
     *
     * @param requestTimes list of times for requests made
     * @param responseTimes list of times for responses received
     * @return the average response time for the request-response cycle
     */
    private static Pair<Double, Integer> calculateStats(
            List<Long> requestTimes, List<Long> responseTimes) {
        int entries = 0;
        double sum = 0;
        int size = Math.min(requestTimes.size(), responseTimes.size());

        // only consider requests that had responses
        for (int i = 0; i < size; i++) {
            long request = requestTimes.get(i);
            long response = responseTimes.get(i);

            // ignore data where the requests comes after the response
            if (response >= request) {
                sum += response - request;
                entries++;
            }
        }

        if (entries == 0) {
            // indicates no data
            return null;
        } else {
            return Pair.of(Math.ceil(sum / entries), entries);
        }
    }

    Pair<Double, Integer> getOverallResponseStats() {
        return Pair.of(overallAvgPeerResponseTime, overallNumberOfPairs);
    }
}

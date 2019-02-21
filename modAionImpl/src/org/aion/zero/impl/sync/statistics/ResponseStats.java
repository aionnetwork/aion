package org.aion.zero.impl.sync.statistics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.commons.lang3.tuple.Pair;

public class ResponseStats {

    /**
     * Records time of requests/responses for active peer nodes
     *
     * @implNote Access to this resource is managed by the {@Link #responseLock}.
     */
    private final Map<String, LinkedBlockingQueue<Long>> requestTimeByPeers = new HashMap<>();

    private final Map<String, Pair<Double, Integer>> responseStatsByPeers = new HashMap<>();

    /**
     * Log the time of request sent to an active peer node
     *
     * @param nodeId peer node displya Id
     * @param requestTime time when the request was sent in nanoseconds
     */
    public void addPeerRequestTime(String nodeId, long requestTime) {
        LinkedBlockingQueue<Long> requestStartTimes = new LinkedBlockingQueue<>();
        if (requestTimeByPeers.containsKey(nodeId)) {
            requestTimeByPeers.get(nodeId).add(requestTime);
        } else {
            requestStartTimes.add(requestTime);
            requestTimeByPeers.put(nodeId, requestStartTimes);
        }
    }

    /**
     * update the average response time and the number of request/response pairs by each active peer
     * node
     *
     * @param _nodeId peer node display Id
     * @param _responseTime time when the response was received in nanoseconds
     */
    public void updatePeerResponseStats(String _nodeId, long _responseTime) {
        if (!requestTimeByPeers.containsKey(_nodeId) || requestTimeByPeers.get(_nodeId).isEmpty()) {
            return;
        }

        LinkedBlockingQueue<Long> requestTimeQueue = requestTimeByPeers.get(_nodeId);
        Pair<Double, Integer> stats =
                responseStatsByPeers.containsKey(_nodeId)
                        ? responseStatsByPeers.get(_nodeId)
                        : Pair.of(0d, 0);

        if (_responseTime > requestTimeQueue.element()) {
            double average = stats.getLeft();
            int count = stats.getRight();

            responseStatsByPeers.put(
                    _nodeId,
                    Pair.of(
                            (average * count + _responseTime - requestTimeQueue.peek())
                                    / (count + 1),
                            count + 1));
        }
        requestTimeQueue.remove();
    }

    /**
     * Obtains the average response time and the number of request/response pairs by each active
     * peer node
     *
     * @return map of pairs containing average reseponse time in nanoseconds and number of
     *     request/response pairs by peer node
     */
    public Map<String, Pair<Double, Integer>> getResponseStatsByPeers() {
        return this.responseStatsByPeers;
    }
}

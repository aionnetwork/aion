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
    private final Map<String, LinkedList<Long>> requestTimeByPeers = new HashMap<>();

    private final Map<String, Pair<Double, Integer>> responseStatsByPeers = new HashMap<>();

    /**
     * Log the time of request sent to an active peer node
     *
     * @param _nodeId peer node displya Id
     * @param _requestTime time when the request was sent in nanoseconds
     */
    public void addPeerRequestTime(String _nodeId, long _requestTime) {
        responseLock.lock();
        try {
            LinkedList<Long> requestStartTimes =
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
     * update the average response time and the number of request/response pairs by each active
     * peer node
     *
     * @param _nodeId peer node display Id
     * @param _responseTime time when the response was received in nanoseconds
     */
    public void updatePeerResponseStats(String _nodeId, long _responseTime) {
        responseLock.lock();
        try {
            if (!requestTimeByPeers.containsKey(_nodeId)
                    || requestTimeByPeers.get(_nodeId).isEmpty()) {
                return;
            }

            LinkedList<Long> requestTimeList = requestTimeByPeers.get(_nodeId);
            Pair<Double, Integer> stats =
                    responseStatsByPeers.containsKey(_nodeId)
                            ? responseStatsByPeers.get(_nodeId)
                            : Pair.of(0d, 0);

            if (_responseTime > requestTimeList.getFirst()) {
                double average = stats.getLeft();
                int count = stats.getRight();

                responseStatsByPeers.put(
                        _nodeId,
                        Pair.of(
                                (average * count + _responseTime - requestTimeList.getFirst())
                                        / (count + 1),
                                count + 1));
            }
            requestTimeList.removeFirst();

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
        return this.responseStatsByPeers;
    }


}

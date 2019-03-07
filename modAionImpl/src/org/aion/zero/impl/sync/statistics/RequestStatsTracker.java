package org.aion.zero.impl.sync.statistics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.aion.zero.impl.sync.RequestCounter;
import org.aion.zero.impl.sync.RequestType;
import org.apache.commons.collections4.map.LRUMap;

public class RequestStatsTracker {

    private final Map<String, RequestCounter> requestsToPeers;
    private final Lock requestLock;

    public RequestStatsTracker(int maxActivePeers) {
        this.requestsToPeers = new LRUMap<>(maxActivePeers);
        this.requestLock = new ReentrantLock();
    }

    /**
     * Returns a log stream containing statistics about the percentage of requests made to each peer
     * with respect to the total number of requests made.
     *
     * @return log stream with requests statistical data
     */
    public String dumpRequestStats() {
        Map<String, Float> reqToPeers = this.getPercentageOfRequestsToPeers();

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
     * Updates the total requests made to a peer.
     *
     * @param nodeId peer node display id
     * @param type the type of request added
     */
    public void updateTotalRequestsToPeer(String nodeId, RequestType type) {
        requestLock.lock();
        try {
            RequestCounter current = requestsToPeers.get(nodeId);

            if (current == null) {
                current = new RequestCounter(type);
                requestsToPeers.put(nodeId, current);
            } else {
                switch (type) {
                    case STATUS:
                        current.incStatus();
                        break;
                    case HEADERS:
                        current.incHeaders();
                        break;
                    case BODIES:
                        current.incBodies();
                        break;
                    case BLOCKS:
                        current.incBlocks();
                        break;
                    case RECEIPTS:
                        current.incRecepts();
                        break;
                    case TRIE_DATA:
                        current.incTrieData();
                        break;
                }
            }
        } finally {
            requestLock.unlock();
        }
    }

    /**
     * Calculates the percentage of requests made to each peer with respect to the total number of
     * requests made.
     *
     * @return a hash map in descending order containing peers with underlying percentage of
     *     requests made by the node
     */
    public Map<String, Float> getPercentageOfRequestsToPeers() {
        requestLock.lock();
        try {
            Map<String, Float> percentageReq = new LinkedHashMap<>();
            float totalReq = 0f;

            // if there are any values the total will be != 0 after this
            for (RequestCounter rc : requestsToPeers.values()) {
                totalReq += rc.getTotal();
            }
            // resources are locked so the requestsToPeers map is unchanged
            // if we enter this loop the totalReq is not equal to 0
            for (Map.Entry<String, RequestCounter> entry : requestsToPeers.entrySet()) {
                percentageReq.put(entry.getKey(), entry.getValue().getTotal() / totalReq);
            }

            return percentageReq.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(
                            Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e2,
                                    LinkedHashMap::new));
        } finally {
            requestLock.unlock();
        }
    }
}

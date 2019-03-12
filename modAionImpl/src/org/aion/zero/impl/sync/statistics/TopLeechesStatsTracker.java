package org.aion.zero.impl.sync.statistics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.apache.commons.collections4.map.LRUMap;

public class TopLeechesStatsTracker {
    private final Map<String, Integer> blockRequestsByPeer;
    private final Lock leechesLock;

    public TopLeechesStatsTracker(int maxActivePeers) {
        this.blockRequestsByPeer = new LRUMap<>(maxActivePeers);
        this.leechesLock = new ReentrantLock();
    }

    /**
     * Updates the total block requests made by a peer.
     *
     * @param nodeId peer node display Id
     * @param totalBlocks total number of blocks requested
     */
    public void updateTotalBlockRequestsByPeer(String nodeId, int totalBlocks) {
        leechesLock.lock();
        try {
            if (blockRequestsByPeer.putIfAbsent(nodeId, totalBlocks) != null) {
                blockRequestsByPeer.computeIfPresent(nodeId, (key, value) -> value + totalBlocks);
            }
        } finally {
            leechesLock.unlock();
        }
    }

    /**
     * Obtains a map of peers ordered by the total number of requested blocks to the node
     *
     * @return map of total requested blocks by peer and sorted in descending order
     */
    public Map<String, Integer> getTotalBlockRequestsByPeer() {
        leechesLock.lock();
        try {
            return blockRequestsByPeer.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(
                            Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e2,
                                    LinkedHashMap::new));
        } finally {
            leechesLock.unlock();
        }
    }

    /**
     * Obtain log stream containing a list of peers ordered by the total number of blocks requested
     * by each peer used to determine who is requesting the majority of blocks, i.e. top leeches.
     *
     * @return log stream with peers statistical data on leeches
     */
    public String dumpTopLeechesStats() {
        Map<String, Integer> totalBlockReqByPeer = this.getTotalBlockRequestsByPeer();

        StringBuilder sb = new StringBuilder();

        if (!totalBlockReqByPeer.isEmpty()) {

            sb.append("\n========= sync-top-leeches =========\n");
            sb.append(String.format("   %9s %20s\n", "peer", "total blocks"));
            sb.append("------------------------------------\n");

            totalBlockReqByPeer.forEach(
                (nodeId, totalBlocks) ->
                    sb.append(String.format("   id:%6s %20s\n", nodeId, totalBlocks)));
        }

        return sb.toString();
    }
}

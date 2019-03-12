package org.aion.zero.impl.sync.statistics;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.apache.commons.collections4.map.LRUMap;

public class TopSeedsStatsTracker {

    private final EnumMap<BlockType, Map<String, Integer>> blocksByPeer =
            new EnumMap<>(BlockType.class);
    private final Lock seedsLock;

    public TopSeedsStatsTracker(int maxActivePeers) {
        // instantiate objects for gathering stats
        for (BlockType type : BlockType.values()) {
            blocksByPeer.put(type, new LRUMap<>(maxActivePeers));
        }
        seedsLock = new ReentrantLock();
    }

    /**
     * Updates the total number of blocks received/imported/stored from each seed peer
     *
     * @param nodeId peer node display Id
     * @param blocks total number of blocks
     * @param type type of the block: received, imported, stored
     */
    public void updatePeerBlocksByType(String nodeId, int blocks, BlockType type) {
        seedsLock.lock();
        try {

            if (blocksByPeer.get(type).putIfAbsent(nodeId, blocks) != null) {
                blocksByPeer.get(type).computeIfPresent(nodeId, (key, value) -> value + blocks);
            }

        } finally {
            seedsLock.unlock();
        }
    }

    /**
     * Obtains a map of seed peers ordered by the total number of imported blocks
     *
     * @return map of total blocks receivedby peer and sorted in descending order
     */
    public Map<String, Integer> getReceivedBlocksByPeer() {
        seedsLock.lock();
        try {
            return blocksByPeer.get(BlockType.RECEIVED).entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(
                            Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e2,
                                    LinkedHashMap::new));
        } finally {
            seedsLock.unlock();
        }
    }

    /**
     * Obtains the total number of blocks imported from the given seed peer
     *
     * @return number of total imported blocks by peer
     */
    public long getImportedBlocksByPeer(String _nodeId) {
        seedsLock.lock();
        try {
            return blocksByPeer.get(BlockType.IMPORTED).getOrDefault(_nodeId, 0);
        } finally {
            seedsLock.unlock();
        }
    }

    /**
     * Obtains the total number of blocks stored from the given seed peer
     *
     * @return number of total stored blocks by peer
     */
    public long getStoredBlocksByPeer(String nodeId) {
        seedsLock.lock();
        try {
            return blocksByPeer.get(BlockType.STORED).getOrDefault(nodeId, 0);
        } finally {
            seedsLock.unlock();
        }
    }

    /**
     * Returns a log stream containing a list of peers ordered by the total number of blocks
     * received from each peer used to determine who is providing the majority of blocks, i.e. top
     * seeds.
     *
     * @return log stream with peers statistical data on seeds
     */
    public String dumpTopSeedsStats() {
        Map<String, Integer> receivedBlocksByPeer = this.getReceivedBlocksByPeer();

        StringBuilder sb = new StringBuilder();

        if (!receivedBlocksByPeer.isEmpty()) {

            sb.append(
                    "\n============================= sync-top-seeds ==============================\n");
            sb.append(
                    String.format(
                            "   %9s %20s %19s %19s\n",
                            "peer", "received blocks", "imported blocks", "stored blocks"));
            sb.append(
                    "---------------------------------------------------------------------------\n");
            receivedBlocksByPeer.forEach(
                    (nodeId, receivedBlocks) ->
                            sb.append(
                                    String.format(
                                            "   id:%6s %20s %19s %19s\n",
                                            nodeId,
                                            receivedBlocks,
                                            this.getImportedBlocksByPeer(nodeId),
                                            this.getStoredBlocksByPeer(nodeId))));
        }

        return sb.toString();
    }
}

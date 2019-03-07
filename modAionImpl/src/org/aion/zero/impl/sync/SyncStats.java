package org.aion.zero.impl.sync;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.aion.mcf.config.StatsType;
import org.aion.zero.impl.sync.statistics.RequestStatsTracker;
import org.aion.zero.impl.sync.statistics.ResponseStatsTracker;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.tuple.Pair;

/** @author chris */
public final class SyncStats {

    private final long start;
    private final long startBlock;
    // Access to this resource is managed by the {@link #blockAverageLock}.
    private double avgBlocksPerSec;
    private final Lock blockAverageLock = new ReentrantLock();
    private final boolean averageEnabled;

    private final RequestStatsTracker requestsTracker;
    private final boolean requestsEnabled;

    // Access to these resources is managed by the {@link #seedsLock}.
    private final Map<String, Integer> blocksByPeer;
    private final Map<String, Integer> importedByPeer;
    private final Map<String, Integer> storedByPeer;
    private final Lock seedsLock;
    private final boolean seedEnabled;

    // @implNote Access to this resource is managed by the {@link #leechesLock}.
    private final Map<String, Integer> blockRequestsByPeer;
    private final Lock leechesLock;
    private final boolean leechesEnabled;

    private final ResponseStatsTracker responseTracker;
    private final boolean responsesEnabled;

    /**
     * @param enabled all stats are enabled when {@code true}, all stats are disabled otherwise
     * @implNote Enables all statistics.
     */
    @VisibleForTesting
    SyncStats(long _startBlock, boolean enabled) {
        this(
                _startBlock,
                enabled,
                enabled ? StatsType.getAllSpecificTypes() : Collections.emptyList(),
                128); // using a default value since it's only used for testing
    }

    SyncStats(
            long _startBlock,
            boolean averageEnabled,
            Collection<StatsType> showStatistics,
            int maxActivePeers) {
        this.start = System.currentTimeMillis();
        this.startBlock = _startBlock;
        this.avgBlocksPerSec = 0;
        this.averageEnabled = averageEnabled;

        requestsEnabled = showStatistics.contains(StatsType.REQUESTS);
        if (requestsEnabled) {
            this.requestsTracker = new RequestStatsTracker(maxActivePeers);
        } else {
            this.requestsTracker = null;
        }

        seedEnabled = showStatistics.contains(StatsType.SEEDS);
        if (seedEnabled) {
            blocksByPeer = new LRUMap<>(maxActivePeers);
            importedByPeer = new LRUMap<>(maxActivePeers);
            storedByPeer = new LRUMap<>(maxActivePeers);
            seedsLock = new ReentrantLock();
        } else {
            blocksByPeer = null;
            importedByPeer = null;
            storedByPeer = null;
            seedsLock = null;
        }

        leechesEnabled = showStatistics.contains(StatsType.LEECHES);
        if (leechesEnabled) {
            blockRequestsByPeer = new LRUMap<>(maxActivePeers);
            leechesLock = new ReentrantLock();
        } else {
            blockRequestsByPeer = null;
            leechesLock = null;
        }

        this.responsesEnabled = showStatistics.contains(StatsType.RESPONSES);
        if (this.responsesEnabled) {
            this.responseTracker = new ResponseStatsTracker(maxActivePeers);
        } else {
            this.responseTracker = null;
        }
    }

    /**
     * Update statistics based on peer nodeId, total imported blocks, and best block number
     *
     * @param _blockNumber best block number
     */
    void update(long _blockNumber) {
        if (averageEnabled) {
            blockAverageLock.lock();
            try {
                avgBlocksPerSec =
                        ((double) _blockNumber - startBlock)
                                * 1000
                                / (System.currentTimeMillis() - start);
            } finally {
                blockAverageLock.unlock();
            }
        }
    }

    double getAvgBlocksPerSec() {
        blockAverageLock.lock();
        try {
            return this.avgBlocksPerSec;
        } finally {
            blockAverageLock.unlock();
        }
    }

    /**
     * Updates the total requests made to a peer.
     *
     * @param nodeId peer node display id
     * @param type the type of request added
     */
    public void updateTotalRequestsToPeer(String nodeId, RequestType type) {
        if (requestsEnabled) {
            requestsTracker.updateTotalRequestsToPeer(nodeId, type);
        }
    }

    @VisibleForTesting
    Map<String, Float> getPercentageOfRequestsToPeers() {
        if (requestsEnabled) {
            return requestsTracker.getPercentageOfRequestsToPeers();
        } else {
            return null;
        }
    }

    /**
     * Returns a log stream containing statistics about the percentage of requests made to each peer
     * with respect to the total number of requests made.
     *
     * @return log stream with requests statistical data
     */
    public String dumpRequestStats() {
        if (requestsEnabled) {
            return requestsTracker.dumpRequestStats();
        } else {
            return "";
        }
    }

    /**
     * Updates the total number of blocks received from each seed peer
     *
     * @param nodeId peer node display Id
     * @param totalBlocks total number of blocks received
     */
    public void updatePeerTotalBlocks(String nodeId, int totalBlocks) {
        if (seedEnabled) {
            seedsLock.lock();
            try {
                if (blocksByPeer.putIfAbsent(nodeId, totalBlocks) != null) {
                    blocksByPeer.computeIfPresent(nodeId, (key, value) -> value + totalBlocks);
                }
            } finally {
                seedsLock.unlock();
            }
        }
    }

    /**
     * Obtains a map of seed peers ordered by the total number of imported blocks
     *
     * @return map of total imported blocks by peer and sorted in descending order
     */
    Map<String, Integer> getTotalBlocksByPeer() {
        if (seedEnabled) {
            seedsLock.lock();
            try {
                return blocksByPeer.entrySet().stream()
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
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Updates the total number of blocks imported from each seed peer
     *
     * @param nodeId peer node display Id
     * @param importedBlocks total number of blocks imported
     */
    public void updatePeerImportedBlocks(String nodeId, int importedBlocks) {
        if (seedEnabled) {
            seedsLock.lock();
            try {
                if (importedByPeer.putIfAbsent(nodeId, importedBlocks) != null) {
                    importedByPeer.computeIfPresent(nodeId, (key, value) -> value + importedBlocks);
                }
            } finally {
                seedsLock.unlock();
            }
        }
    }

    /**
     * Obtains the total number of blocks imported from the given seed peer
     *
     * @return number of total imported blocks by peer
     */
    long getImportedBlocksByPeer(String _nodeId) {
        if (seedEnabled) {
            seedsLock.lock();
            try {
                return this.importedByPeer.getOrDefault(_nodeId, 0);
            } finally {
                seedsLock.unlock();
            }
        } else {
            return 0L;
        }
    }

    /**
     * Updates the total number of blocks stored from each seed peer
     *
     * @param nodeId peer node display Id
     * @param storedBlocks total number of blocks stored
     */
    public void updatePeerStoredBlocks(String nodeId, int storedBlocks) {
        if (seedEnabled) {
            seedsLock.lock();
            try {
                if (storedByPeer.putIfAbsent(nodeId, storedBlocks) != null) {
                    storedByPeer.computeIfPresent(nodeId, (key, value) -> value + storedBlocks);
                }
            } finally {
                seedsLock.unlock();
            }
        }
    }

    /**
     * Obtains the total number of blocks stored from the given seed peer
     *
     * @return number of total stored blocks by peer
     */
    long getStoredBlocksByPeer(String _nodeId) {
        if (seedEnabled) {
            seedsLock.lock();
            try {
                return this.storedByPeer.getOrDefault(_nodeId, 0);
            } finally {
                seedsLock.unlock();
            }
        } else {
            return 0L;
        }
    }

    /**
     * Updates the total block requests made by a peer.
     *
     * @param nodeId peer node display Id
     * @param totalBlocks total number of blocks requested
     */
    public void updateTotalBlockRequestsByPeer(String nodeId, int totalBlocks) {
        if (leechesEnabled) {
            leechesLock.lock();
            try {
                if (blockRequestsByPeer.putIfAbsent(nodeId, totalBlocks) != null) {
                    blockRequestsByPeer.computeIfPresent(
                            nodeId, (key, value) -> value + totalBlocks);
                }
            } finally {
                leechesLock.unlock();
            }
        }
    }

    /**
     * Obtains a map of peers ordered by the total number of requested blocks to the node
     *
     * @return map of total requested blocks by peer and sorted in descending order
     */
    Map<String, Integer> getTotalBlockRequestsByPeer() {
        if (leechesEnabled) {
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
        } else {
            return Collections.emptyMap();
        }
    }

    public void updateRequestTime(String displayId, long requestTime, RequestType requestType) {
        if (responsesEnabled) {
            responseTracker.updateRequestTime(displayId, requestTime, requestType);
        }
    }

    public void updateResponseTime(String displayId, long responseTime, RequestType requestType) {
        if (responsesEnabled) {
            responseTracker.updateResponseTime(displayId, responseTime, requestType);
        }
    }

    @VisibleForTesting
    Map<String, Map<String, Pair<Double, Integer>>> getResponseStats() {
        if (responsesEnabled) {
            return responseTracker.getResponseStats();
        } else {
            return null;
        }
    }

    /**
     * Obtain log stream containing statistics about the average response time between sending
     * status requests out and that peer responding shown for each peer and averaged for all peers.
     *
     * @return log stream with requests statistical data
     */
    public String dumpResponseStats() {
        if (responsesEnabled) {
            return responseTracker.dumpResponseStats();
        } else {
            return "";
        }
    }
}

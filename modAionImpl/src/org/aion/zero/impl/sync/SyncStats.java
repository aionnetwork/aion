package org.aion.zero.impl.sync;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.aion.mcf.config.StatsType;
import org.aion.zero.impl.sync.statistics.ResponseStatsTracker;
import org.apache.commons.lang3.tuple.Pair;

/** @author chris */
public final class SyncStats {

    private final long start;
    private final long startBlock;
    /** @implNote Access to this resource is managed by the {@link #blockAverageLock}. */
    private double avgBlocksPerSec;

    private final Lock blockAverageLock = new ReentrantLock();
    private final boolean averageEnabled;

    /** @implNote Access to this resource is managed by the {@link #requestsLock}. */
    private final Map<String, RequestCounter> requestsToPeers = new HashMap<>();

    private final Lock requestsLock = new ReentrantLock();
    private final boolean requestsEnabled;

    /**
     * Records information on top seeds.
     *
     * @implNote Access to this resource is managed by the {@link #seedsLock}.
     */
    private final Map<String, Long> blocksByPeer = new HashMap<>();

    private final Map<String, Long> importedByPeer = new HashMap<>();

    private final Map<String, Long> storedByPeer = new HashMap<>();

    private final Lock seedsLock = new ReentrantLock();
    private final boolean seedEnabled;

    /**
     * Records information on top leeches.
     *
     * @implNote Access to this resource is managed by the {@link #leechesLock}.
     */
    private final Map<String, Long> blockRequestsByPeer = new HashMap<>();

    private final Lock leechesLock = new ReentrantLock();
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
                enabled ? StatsType.getAllSpecificTypes() : Collections.emptyList());
    }

    SyncStats(long _startBlock, boolean averageEnabled, Collection<StatsType> showStatistics) {
        this.start = System.currentTimeMillis();
        this.startBlock = _startBlock;
        this.avgBlocksPerSec = 0;

        this.averageEnabled = averageEnabled;
        requestsEnabled = showStatistics.contains(StatsType.REQUESTS);
        seedEnabled = showStatistics.contains(StatsType.SEEDS);
        leechesEnabled = showStatistics.contains(StatsType.LEECHES);

        this.responsesEnabled = showStatistics.contains(StatsType.RESPONSES);
        if (this.responsesEnabled) {
            this.responseTracker = new ResponseStatsTracker();
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
            requestsLock.lock();
            try {
                RequestCounter current = requestsToPeers.get(nodeId);

                if (current == null) {
                    current = new RequestCounter();
                    requestsToPeers.put(nodeId, current);
                }

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
                }

            } finally {
                requestsLock.unlock();
            }
        }
    }

    /**
     * Calculates the percentage of requests made to each peer with respect to the total number of
     * requests made.
     *
     * @return a hash map in descending order containing peers with underlying percentage of
     *     requests made by the node
     */
    Map<String, Float> getPercentageOfRequestsToPeers() {
        requestsLock.lock();

        try {
            Map<String, Float> percentageReq = new LinkedHashMap<>();

            float totalReq = 0f;

            for (RequestCounter rc : requestsToPeers.values()) {
                totalReq += rc.getTotal();
            }

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
            requestsLock.unlock();
        }
    }

    /**
     * Updates the total number of blocks received from each seed peer
     *
     * @param _nodeId peer node display Id
     * @param _totalBlocks total number of blocks received
     */
    public void updatePeerTotalBlocks(String _nodeId, int _totalBlocks) {
        if (seedEnabled) {
            seedsLock.lock();
            try {
                long blocks = (long) _totalBlocks;
                if (blocksByPeer.putIfAbsent(_nodeId, blocks) != null) {
                    blocksByPeer.computeIfPresent(_nodeId, (key, value) -> value + blocks);
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
    Map<String, Long> getTotalBlocksByPeer() {
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
    }

    /**
     * Updates the total number of blocks imported from each seed peer
     *
     * @param _nodeId peer node display Id
     * @param _importedBlocks total number of blocks imported
     */
    public void updatePeerImportedBlocks(String _nodeId, int _importedBlocks) {
        if (seedEnabled) {
            seedsLock.lock();
            try {
                long blocks = (long) _importedBlocks;
                if (importedByPeer.putIfAbsent(_nodeId, blocks) != null) {
                    importedByPeer.computeIfPresent(_nodeId, (key, value) -> value + blocks);
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
        seedsLock.lock();
        try {
            return this.importedByPeer.getOrDefault(_nodeId, (long) 0);
        } finally {
            seedsLock.unlock();
        }
    }

    /**
     * Updates the total number of blocks stored from each seed peer
     *
     * @param _nodeId peer node display Id
     * @param _storedBlocks total number of blocks stored
     */
    public void updatePeerStoredBlocks(String _nodeId, int _storedBlocks) {
        if (seedEnabled) {
            seedsLock.lock();
            try {
                long blocks = (long) _storedBlocks;
                if (storedByPeer.putIfAbsent(_nodeId, blocks) != null) {
                    storedByPeer.computeIfPresent(_nodeId, (key, value) -> value + blocks);
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
        seedsLock.lock();
        try {
            return this.storedByPeer.getOrDefault(_nodeId, (long) 0);
        } finally {
            seedsLock.unlock();
        }
    }

    /**
     * Updates the total block requests made by a peer.
     *
     * @param _nodeId peer node display Id
     * @param _totalBlocks total number of blocks requested
     */
    public void updateTotalBlockRequestsByPeer(String _nodeId, int _totalBlocks) {
        if (leechesEnabled) {
            leechesLock.lock();
            try {
                long blocks = (long) _totalBlocks;
                if (blockRequestsByPeer.putIfAbsent(_nodeId, blocks) != null) {
                    blockRequestsByPeer.computeIfPresent(_nodeId, (key, value) -> value + blocks);
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
    Map<String, Long> getTotalBlockRequestsByPeer() {
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

    public void updateStatusRequest(String displayId, long requestTime) {
        if (responsesEnabled) {
            responseTracker.updateStatusRequest(displayId, requestTime);
        }
    }

    public void updateHeadersRequest(String displayId, long requestTime) {
        if (responsesEnabled) {
            responseTracker.updateHeadersRequest(displayId, requestTime);
        }
    }

    public void updateBodiesRequest(String displayId, long requestTime) {
        if (responsesEnabled) {
            responseTracker.updateBodiesRequest(displayId, requestTime);
        }
    }

    public void updateStatusResponse(String displayId, long responseTime) {
        if (responsesEnabled) {
            responseTracker.updateStatusResponse(displayId, responseTime);
        }
    }

    public void updateHeadersResponse(String displayId, long responseTime) {
        if (responsesEnabled) {
            responseTracker.updateHeadersResponse(displayId, responseTime);
        }
    }

    public void updateBodiesResponse(String displayId, long responseTime) {
        if (responsesEnabled) {
            responseTracker.updateBodiesResponse(displayId, responseTime);
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

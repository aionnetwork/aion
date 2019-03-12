package org.aion.zero.impl.sync;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.mcf.config.StatsType;
import org.aion.zero.impl.sync.statistics.BlockType;
import org.aion.zero.impl.sync.statistics.RequestStatsTracker;
import org.aion.zero.impl.sync.statistics.RequestType;
import org.aion.zero.impl.sync.statistics.ResponseStatsTracker;
import org.aion.zero.impl.sync.statistics.TopLeechesStatsTracker;
import org.aion.zero.impl.sync.statistics.TopSeedsStatsTracker;
import org.apache.commons.lang3.tuple.Pair;

/** @author chris */
public final class SyncStats {

    private final long start;
    private final long startBlock;
    // Access to this resource is managed by the {@link #blockAverageLock}.
    private double avgBlocksPerSec;
    private final Lock blockAverageLock = new ReentrantLock();
    private final boolean averageEnabled;

    private final RequestStatsTracker requestTracker;
    private final boolean requestEnabled;

    private final TopSeedsStatsTracker seedsTracker;
    private final boolean seedsEnabled;

    private final TopLeechesStatsTracker leechesTracker;
    private final boolean leechesEnabled;

    private final ResponseStatsTracker responseTracker;
    private final boolean responseEnabled;

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
            long startBlock,
            boolean averageEnabled,
            Collection<StatsType> showStatistics,
            int maxActivePeers) {
        this.start = System.currentTimeMillis();
        this.startBlock = startBlock;
        this.avgBlocksPerSec = 0;
        this.averageEnabled = averageEnabled;

        requestEnabled = showStatistics.contains(StatsType.REQUESTS);
        if (requestEnabled) {
            requestTracker = new RequestStatsTracker(maxActivePeers);
        } else {
            requestTracker = null;
        }

        seedsEnabled = showStatistics.contains(StatsType.SEEDS);
        if (seedsEnabled) {
            seedsTracker = new TopSeedsStatsTracker(maxActivePeers);
        } else {
            seedsTracker = null;
        }

        leechesEnabled = showStatistics.contains(StatsType.LEECHES);
        if (leechesEnabled) {
            leechesTracker = new TopLeechesStatsTracker(maxActivePeers);
        } else {
            leechesTracker = null;
        }

        responseEnabled = showStatistics.contains(StatsType.RESPONSES);
        if (responseEnabled) {
            responseTracker = new ResponseStatsTracker(maxActivePeers);
        } else {
            responseTracker = null;
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
            return avgBlocksPerSec;
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
        if (requestEnabled) {
            requestTracker.updateTotalRequestsToPeer(nodeId, type);
        }
    }

    /**
     * Calculates the percentage of requests made to each peer with respect to the total number of
     * requests made.
     *
     * @return a hash map in descending order containing peers with underlying percentage of
     *     requests made by the node
     */
    @VisibleForTesting
    Map<String, Float> getPercentageOfRequestsToPeers() {
        if (requestEnabled) {
            return requestTracker.getPercentageOfRequestsToPeers();
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
        if (requestEnabled) {
            return requestTracker.dumpRequestStats();
        } else {
            return "";
        }
    }

    /**
     * Updates the total number of blocks received/imported/stored from each seed peer
     *
     * @param nodeId peer node display Id
     * @param blocks total number of blocks
     * @param type type of the block: received, imported, stored
     */
    public void updatePeerBlocks(String nodeId, int blocks, BlockType type) {
        if (seedsEnabled) {
            seedsTracker.updatePeerBlocksByType(nodeId, blocks, type);
        }
    }

    /**
     * Obtains a map of seed peers ordered by the total number of imported blocks
     *
     * @return map of total blocks receivedby peer and sorted in descending order
     */
    @VisibleForTesting
    Map<String, Integer> getReceivedBlocksByPeer() {
        if (seedsEnabled) {
            return seedsTracker.getReceivedBlocksByPeer();
        } else {
            return null;
        }
    }

    /**
     * Obtains the total number of blocks imported from the given seed peer
     *
     * @return number of total imported blocks by peer
     */
    @VisibleForTesting
    long getImportedBlocksByPeer(String nodeId) {
        if (seedsEnabled) {
            return seedsTracker.getImportedBlocksByPeer(nodeId);
        } else {
            return 0L;
        }
    }

    /**
     * Obtains the total number of blocks stored from the given seed peer
     *
     * @return number of total stored blocks by peer
     */
    @VisibleForTesting
    long getStoredBlocksByPeer(String nodeId) {
        if (seedsEnabled) {
            return seedsTracker.getStoredBlocksByPeer(nodeId);
        } else {
            return 0L;
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
        if (seedsEnabled) {
            return seedsTracker.dumpTopSeedsStats();
        } else {
            return "";
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
            leechesTracker.updateTotalBlockRequestsByPeer(nodeId, totalBlocks);
        }
    }

    /**
     * Obtains a map of peers ordered by the total number of requested blocks to the node
     *
     * @return map of total requested blocks by peer and sorted in descending order
     */
    @VisibleForTesting
    Map<String, Integer> getTotalBlockRequestsByPeer() {
        if (leechesEnabled) {
            return leechesTracker.getTotalBlockRequestsByPeer();
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Obtain log stream containing a list of peers ordered by the total number of blocks requested
     * by each peer used to determine who is requesting the majority of blocks, i.e. top leeches.
     *
     * @return log stream with peers statistical data on leeches
     */
    public String dumpTopLeechesStats() {
        if (leechesEnabled) {
            return leechesTracker.dumpTopLeechesStats();
        } else {
            return "";
        }
    }

    /**
     * Log the time of a request sent to a peer.
     *
     * @param displayId peer display identifier
     * @param requestTime time when the request was sent in nanoseconds
     * @param requestType type of request
     */
    public void updateRequestTime(String displayId, long requestTime, RequestType requestType) {
        if (responseEnabled) {
            responseTracker.updateRequestTime(displayId, requestTime, requestType);
        }
    }

    /**
     * Log the time of a response received from a peer and update the computed average time and
     * number of data points.
     *
     * @param displayId peer display identifier
     * @param responseTime time when the response was received in nanoseconds * @param requestType
     * @param requestType type of request
     */
    public void updateResponseTime(String displayId, long responseTime, RequestType requestType) {
        if (responseEnabled) {
            responseTracker.updateResponseTime(displayId, responseTime, requestType);
        }
    }

    @VisibleForTesting
    Map<String, Map<String, Pair<Double, Integer>>> getResponseStats() {
        if (responseEnabled) {
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
        if (responseEnabled) {
            return responseTracker.dumpResponseStats();
        } else {
            return "";
        }
    }
}

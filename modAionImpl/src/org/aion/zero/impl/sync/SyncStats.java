/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.zero.impl.sync;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** @author chris */
public final class SyncStats {

    private long start;

    private long startBlock;

    private double avgBlocksPerSec;

    private final ConcurrentHashMap<String, Long> requestsToPeers = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> blocksByPeer = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> blockRequestsByPeer = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, LinkedList> statusRequestTimeByPeers =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, LinkedList> statusResponseTimeByPeers =
            new ConcurrentHashMap<>();

    private Long overallAvgPeerResponseTime;

    SyncStats(long _startBlock) {
        this.start = System.currentTimeMillis();
        this.startBlock = _startBlock;
        this.avgBlocksPerSec = 0;
        this.overallAvgPeerResponseTime = 0L;
    }

    /**
     * Update statistics based on peer nodeId, total imported blocks, and best block number
     *
     * @param _nodeId peer node display Id
     * @param _totalBlocks total imported blocks in batch
     * @param _blockNumber best block number
     */
    synchronized void update(String _nodeId, int _totalBlocks, long _blockNumber) {
        avgBlocksPerSec =
                (double) (_blockNumber - startBlock) * 1000 / (System.currentTimeMillis() - start);
        updateTotalRequestsToPeer(_nodeId);
        updatePeerTotalBlocks(_nodeId, _totalBlocks);
    }

    synchronized double getAvgBlocksPerSec() {
        return this.avgBlocksPerSec;
    }

    /**
     * Updates the total requests made to a pear
     *
     * @param _nodeId peer node display Id
     */
    private void updateTotalRequestsToPeer(String _nodeId) {
        if (requestsToPeers.putIfAbsent(_nodeId, 1L) != null) {
            requestsToPeers.computeIfPresent(_nodeId, (key, value) -> value + 1L);
        }
    }

    /**
     * Calculates the percentage of requests made to each peer with respect to the total number of
     * requests made.
     *
     * @return a hash map in descending order containing peers with underlying percentage of
     * requests made by the node
     */
    synchronized Map<String, Float> getPercentageOfRequestsToPeers() {
        Map<String, Float> percentageReq = new HashMap<>();
        Long totalReq = requestsToPeers.reduceValues(2, (val1, val2) -> val1 + val2);
        requestsToPeers.forEachEntry(
                2,
                entry -> {
                    percentageReq.put(
                            entry.getKey(), entry.getValue().floatValue() / totalReq.floatValue());
                });
        return percentageReq
                .entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e2,
                                LinkedHashMap::new));
    }

    /**
     * Updates the total number of blocks received from each seed peer
     *
     * @param _nodeId peer node display Id
     * @param _totalBlocks total number of blocks received
     */
    private void updatePeerTotalBlocks(String _nodeId, int _totalBlocks) {
        long blocks = (long) _totalBlocks;
        if (blocksByPeer.putIfAbsent(_nodeId, blocks) != null) {
            blocksByPeer.computeIfPresent(_nodeId, (key, value) -> value + blocks);
        }
    }

    /**
     * Obtains a map of seed peers ordered by the total number of imported blocks
     *
     * @return map of total imported blocks by peer and sorted in descending order
     */
    synchronized Map<String, Long> getTotalBlocksByPeer() {
        return blocksByPeer
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e2,
                                LinkedHashMap::new));
    }

    /**
     * Updates the total block requests made by a pear
     *
     * @param _nodeId peer node display Id
     */
    public synchronized void updateTotalBlockRequestsByPeer(String _nodeId, int _totalBlocks) {
        long blocks = (long) _totalBlocks;
        if (blockRequestsByPeer.putIfAbsent(_nodeId, blocks) != null) {
            blockRequestsByPeer.computeIfPresent(_nodeId, (key, value) -> value + blocks);
        }
    }

    /**
     * Obtains a map of peers ordered by the total number of requested blocks to the node
     *
     * @return map of total requested blocks by peer and sorted in descending order
     */
    synchronized Map<String, Long> getTotalBlockRequestsByPeer() {
        return blockRequestsByPeer
                .entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e2,
                                LinkedHashMap::new));
    }

    /**
     * Logs the time of status request to an active peer node
     *
     * @param _nodeId peer node display Id
     */
    public synchronized void addPeerRequestTime(String _nodeId, long _requestTime) {
        LinkedList<Long> requestStartTimes =
                statusRequestTimeByPeers.containsKey(_nodeId)
                        ? statusRequestTimeByPeers.get(_nodeId)
                        : new LinkedList<>();
        requestStartTimes.add(_requestTime);
        statusRequestTimeByPeers.put(_nodeId, requestStartTimes);
    }

    /**
     * Log the time of status response received from an active peer node
     *
     * @param _nodeId peer node display Id
     */
    public synchronized void addPeerResponseTime(String _nodeId, long _requestTime) {
        LinkedList<Long> requestStartTimes =
                statusResponseTimeByPeers.containsKey(_nodeId)
                        ? statusResponseTimeByPeers.get(_nodeId)
                        : new LinkedList<>();
        requestStartTimes.add(_requestTime);
        statusResponseTimeByPeers.put(_nodeId, requestStartTimes);
    }

    /**
     * Obtains the average response time by each active peer node
     *
     * @return map of average response time by peer node
     */
    synchronized Map<String, Double> getAverageResponseTimeByPeers() {
        Map<String, Double> avgResponseTimeByPeers =
                statusRequestTimeByPeers
                    .entrySet()
                    .stream()
                    .collect(
                        Collectors.toMap( // collect a map of average response times by peer
                            Map.Entry::getKey, // node display Id
                            entry -> { // calculate the average response time

                                String _nodeId = entry.getKey();
                                final List requestTimes = entry.getValue();
                                final List responseTimes =
                                        statusResponseTimeByPeers.getOrDefault(
                                                _nodeId, new LinkedList());
                                Double average =
                                    Math.ceil( // truncates average value
                                        IntStream.range( // calculates the status response time
                                            0,
                                            Math.min(requestTimes.size(), responseTimes.size()))
                                                .mapToLong( // subtract (response - request) time
                                                    i ->
                                                        ((Long)responseTimes.get(i)).longValue()
                                                        - ((Long)requestTimes.get(i)).longValue()
                                                )
                                                // averaged over all requests
                                                .average().orElse(0));
                                return average;
                            }));
        
        overallAvgPeerResponseTime =
                statusRequestTimeByPeers.isEmpty()
                        ? overallAvgPeerResponseTime
                        : Double.valueOf(
                                Math.ceil(
                                        avgResponseTimeByPeers
                                                .entrySet()
                                                .stream()
                                                .mapToDouble(entry -> entry.getValue())
                                                .average()
                                                .getAsDouble()))
                                                .longValue();

        return avgResponseTimeByPeers
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e2,
                                LinkedHashMap::new));
    }

    /**
     * Obtains the overall average response time from all active peer nodes
     *
     * @return overall average response time
     */
    synchronized Long getOverallAveragePeerResponseTime() {
        return overallAvgPeerResponseTime;
    }
}

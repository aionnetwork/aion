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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/** @author chris */
public final class SyncStats {

    private final long start;
    private final long startBlock;
    /** @implNote Access to this resource is managed by the {@link #blockAverageLock}. */
    private double avgBlocksPerSec;

    private final Lock blockAverageLock = new ReentrantLock();

    /** @implNote Access to this resource is managed by the {@link #requestsLock}. */
    private final Map<String, RequestCounter> requestsToPeers = new HashMap<>();

    private final Lock requestsLock = new ReentrantLock();

    /**
     * Records information on top seeds.
     *
     * @implNote Access to this resource is managed by the {@link #seedsLock}.
     */
    private final Map<String, Long> blocksByPeer = new HashMap<>();

    private final Lock seedsLock = new ReentrantLock();

    /**
     * Records information on top leeches.
     *
     * @implNote Access to this resource is managed by the {@link #leechesLock}.
     */
    private final Map<String, Long> blockRequestsByPeer = new HashMap<>();

    private final Lock leechesLock = new ReentrantLock();

    /** @implNote Access to this resource is managed by the {@link #responsesLock}. */
    private final Map<String, List<Long>> statusRequestTimeByPeers = new HashMap<>();
    /** @implNote Access to this resource is managed by the {@link #responsesLock}. */
    private final Map<String, List<Long>> statusResponseTimeByPeers = new HashMap<>();
    /** @implNote Access to this resource is managed by the {@link #responsesLock}. */
    private double overallAvgPeerResponseTime;

    private final Lock responsesLock = new ReentrantLock();

    SyncStats(long _startBlock) {
        this.start = System.currentTimeMillis();
        this.startBlock = _startBlock;
        this.avgBlocksPerSec = 0;
        this.overallAvgPeerResponseTime = 0L;
    }

    /**
     * Update statistics based on peer nodeId, total imported blocks, and best block number
     *
     * @param _blockNumber best block number
     */
    void update(long _blockNumber) {
        blockAverageLock.lock();
        try {
            avgBlocksPerSec =
                    (double) (_blockNumber - startBlock)
                            * 1000
                            / (System.currentTimeMillis() - start);
        } finally {
            blockAverageLock.unlock();
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

    /**
     * Obtains a map of seed peers ordered by the total number of imported blocks
     *
     * @return map of total imported blocks by peer and sorted in descending order
     */
    Map<String, Long> getTotalBlocksByPeer() {
        seedsLock.lock();
        try {
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

    /**
     * Obtains a map of peers ordered by the total number of requested blocks to the node
     *
     * @return map of total requested blocks by peer and sorted in descending order
     */
    Map<String, Long> getTotalBlockRequestsByPeer() {
        leechesLock.lock();
        try {
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
        } finally {
            leechesLock.unlock();
        }
    }

    /**
     * Logs the time of status request to an active peer node
     *
     * @param _nodeId peer node display Id
     * @param _requestTime time when the request was sent in nanoseconds
     */
    public void addPeerRequestTime(String _nodeId, long _requestTime) {
        responsesLock.lock();
        try {
            List<Long> requestStartTimes =
                    statusRequestTimeByPeers.containsKey(_nodeId)
                            ? statusRequestTimeByPeers.get(_nodeId)
                            : new LinkedList<>();
            requestStartTimes.add(_requestTime);
            statusRequestTimeByPeers.put(_nodeId, requestStartTimes);
        } finally {
            responsesLock.unlock();
        }
    }

    /**
     * Log the time of status response received from an active peer node
     *
     * @param _nodeId peer node display Id
     * @param _responseTime time when the response was received in nanoseconds
     */
    public void addPeerResponseTime(String _nodeId, long _responseTime) {
        responsesLock.lock();
        try {
            List<Long> responseEndTimes =
                    statusResponseTimeByPeers.containsKey(_nodeId)
                            ? statusResponseTimeByPeers.get(_nodeId)
                            : new LinkedList<>();
            responseEndTimes.add(_responseTime);
            statusResponseTimeByPeers.put(_nodeId, responseEndTimes);
        } finally {
            responsesLock.unlock();
        }
    }

    /**
     * Obtains the average response time by each active peer node
     *
     * @return map of average response time in nanoseconds by peer node
     */
    Map<String, Double> getAverageResponseTimeByPeers() {
        responsesLock.lock();
        try {
            double average;
            String nodeId;
            List<Long> requests, responses;

            Map<String, Double> avgResponseTimeByPeers = new HashMap<>();
            overallAvgPeerResponseTime = 0d;

            for (Map.Entry<String, List<Long>> peerData : statusRequestTimeByPeers.entrySet()) {

                nodeId = peerData.getKey(); // node display Id
                requests = peerData.getValue();
                responses = statusResponseTimeByPeers.getOrDefault(nodeId, new LinkedList<>());

                // calculate the average response time
                average = calculateAverage(requests, responses);

                if (average >= 0) {
                    // collect a map of average response times by peer
                    avgResponseTimeByPeers.put(nodeId, average);
                    overallAvgPeerResponseTime += average;
                }
            }

            overallAvgPeerResponseTime =
                    avgResponseTimeByPeers.isEmpty()
                            ? 0d
                            : overallAvgPeerResponseTime / avgResponseTimeByPeers.size();

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
        } finally {
            responsesLock.unlock();
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
    private static double calculateAverage(List<Long> requestTimes, List<Long> responseTimes) {
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
            return (double) -1;
        } else {
            return Math.ceil(sum / entries);
        }
    }

    /**
     * Obtains the overall average response time from all active peer nodes
     *
     * @return overall average response time
     */
    double getOverallAveragePeerResponseTime() {
        responsesLock.lock();
        try {
            return overallAvgPeerResponseTime;
        } finally {
            responsesLock.unlock();
        }
    }
}

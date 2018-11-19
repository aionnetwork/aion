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

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.BlockchainTestUtils.generateNewBlock;
import static org.aion.zero.impl.BlockchainTestUtils.generateRandomChain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.junit.BeforeClass;
import org.junit.Test;

public class SyncStatsTest {

    private static final List<ECKey> accounts = generateAccounts(10);
    private final StandaloneBlockchain.Bundle bundle =
            new StandaloneBlockchain.Builder()
                    .withValidatorConfiguration("simple")
                    .withDefaultAccounts(accounts)
                    .build();

    private static final List<String> peers = new ArrayList<>();

    @BeforeClass
    public static void setup() {
        // mock some peer Ids
        peers.add(UUID.randomUUID().toString().substring(0, 6));
        peers.add(UUID.randomUUID().toString().substring(0, 6));
        peers.add(UUID.randomUUID().toString().substring(0, 6));
    }

    @Test
    public void testAvgBlocksPerSecStat() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber());

        // ensures correct behaviour on empty stats
        assertThat(stats.getAvgBlocksPerSec() == 0).isTrue();

        for (int totalBlocks = 1; totalBlocks <= 3; totalBlocks++) {
            int count = 0;
            while (count < totalBlocks) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_BEST);
                count++;
            }
            stats.update(peers.get(0), totalBlocks, chain.getBestBlock().getNumber());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
        }

        assertThat(stats.getAvgBlocksPerSec() <= 3.).isTrue();
    }

    @Test
    public void testTotalRequestsToPeersStat() {
        List<String> peers = new ArrayList<>(this.peers);
        while (peers.size() < 4) {
            peers.add(UUID.randomUUID().toString().substring(0, 6));
        }

        // this tests requires at least 3 peers in the list
        assertThat(peers.size()).isAtLeast(4);

        StandaloneBlockchain chain = bundle.bc;
        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber());

        // ensures correct behaviour on empty stats
        Map<String, Float> emptyReqToPeers = stats.getPercentageOfRequestsToPeers();
        assertThat(emptyReqToPeers.size()).isEqualTo(0);

        float processedRequests = 0;

        String firstPeer = peers.get(0);
        String secondPeer = peers.get(1);
        String thirdPeer = peers.get(2);

        for (String peer : peers) {
            // status requests
            stats.updateTotalRequestsToPeer(peer, RequestType.STATUS);
            processedRequests++;

            if (peer == firstPeer || peer == secondPeer) {
                // header requests
                stats.updateTotalRequestsToPeer(peer, RequestType.HEADERS);
                processedRequests++;
            }

            // bodies requests
            if (peer == firstPeer) {
                stats.updateTotalRequestsToPeer(peer, RequestType.BODIES);
                processedRequests++;
            }
        }

        Map<String, Float> reqToPeers = stats.getPercentageOfRequestsToPeers();

        // makes sure no additional peers were created
        assertThat(reqToPeers.size()).isEqualTo(peers.size());

        // by design (the updates above are not symmetrical)
        assertThat(reqToPeers.get(firstPeer)).isGreaterThan(reqToPeers.get(secondPeer));
        assertThat(reqToPeers.get(firstPeer)).isEqualTo(3 / processedRequests);

        assertThat(reqToPeers.get(secondPeer)).isGreaterThan(reqToPeers.get(thirdPeer));
        assertThat(reqToPeers.get(secondPeer)).isEqualTo(2 / processedRequests);

        assertThat(reqToPeers.get(thirdPeer)).isEqualTo(1 / processedRequests);

        for (String otherPeers : peers.subList(3, peers.size())) {
            assertThat(reqToPeers.get(otherPeers)).isEqualTo(reqToPeers.get(thirdPeer));
        }

        int blocks = 3;

        float lastPercentage = (float) 1;
        float diffThreshold = (float) 0.01;

        for (String nodeId : reqToPeers.keySet()) {
            float percentageReq = reqToPeers.get(nodeId);
            // ensures desc order
            assertThat(lastPercentage).isAtLeast(percentageReq);
            lastPercentage = percentageReq;
            assertThat(percentageReq - (1. * blocks / processedRequests) < diffThreshold).isTrue();
        }
    }

    @Test
    public void testTotalBlocksByPeer() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber());

        // ensures correct behaviour on empty stats
        Map<String, Long> emptyTotalBlockReqByPeer = stats.getTotalBlocksByPeer();
        assertThat(emptyTotalBlockReqByPeer.size() == 0).isTrue();

        int peerNo = 0;
        int processedBlocks = 0;

        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            processedBlocks += totalBlocks;
            while (blocks > 0) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_BEST);
                stats.update(peers.get(peerNo), blocks, chain.getBestBlock().getNumber());
                blocks--;
            }
            peerNo++;
        }

        Map<String, Long> totalBlockReqByPeer = stats.getTotalBlocksByPeer();

        assertThat(totalBlockReqByPeer.size() == peers.size()).isTrue();

        int blocks = 3;

        long lastTotalBlocks = processedBlocks;

        for (String nodeId : peers) {
            // ensures desc order
            assertThat(lastTotalBlocks >= totalBlockReqByPeer.get(nodeId)).isTrue();
            lastTotalBlocks = totalBlockReqByPeer.get(nodeId);
            assertThat(totalBlockReqByPeer.get(nodeId).compareTo(Long.valueOf(blocks)) == 0);
            blocks--;
        }
    }

    @Test
    public void testTotalBlockRequestsByPeerStats() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber());

        // ensures correct behaviour on empty stats
        Map<String, Long> emptyTotalBlocksByPeer = stats.getTotalBlockRequestsByPeer();
        assertThat(emptyTotalBlocksByPeer.size() == 0).isTrue();

        int blocks = 3;
        for (String nodeId : peers) {
            int count = 0;
            while (count < blocks) {
                stats.updateTotalBlockRequestsByPeer(nodeId, 1);
                count++;
            }
            blocks--;
        }

        Map<String, Long> totalBlocksByPeer = stats.getTotalBlockRequestsByPeer();
        assertThat(totalBlocksByPeer.size() == peers.size()).isTrue();

        Long lastTotalBlocks = (long) peers.size();
        for (String nodeId : totalBlocksByPeer.keySet()) {
            // ensures desc order
            assertThat(lastTotalBlocks >= totalBlocksByPeer.get(nodeId)).isTrue();
            lastTotalBlocks = totalBlocksByPeer.get(nodeId);
        }
    }

    @Test
    public void testAverageResponseTimeByPeersStats() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber());

        // ensures correct behaviour on empty stats
        Map<String, Double> emptyAvgResponseTimeByPeers = stats.getAverageResponseTimeByPeers();
        // request time is logged but no response is received
        stats.addPeerRequestTime("dummy", System.currentTimeMillis());
        Long overallAveragePeerResponseTime = stats.getOverallAveragePeerResponseTime();
        assertThat(emptyAvgResponseTimeByPeers.size() == 0).isTrue();
        assertThat(overallAveragePeerResponseTime.compareTo(0L) == 0).isTrue();

        stats = new SyncStats(chain.getBestBlock().getNumber());

        int requests = 3;
        for (String nodeId : peers) {
            int count = requests;
            while (count > 0) {
                stats.addPeerRequestTime(nodeId, System.currentTimeMillis());
                try {
                    Thread.sleep(100 * count);
                } catch (InterruptedException e) {
                }
                stats.addPeerResponseTime(nodeId, System.currentTimeMillis());
                count--;
            }
            requests--;
        }

        Map<String, Double> avgResponseTimeByPeers = stats.getAverageResponseTimeByPeers();
        assertThat(avgResponseTimeByPeers.size() == peers.size()).isTrue();

        Double lastAvgResponseTime = Double.MIN_VALUE;
        int i = 0;
        for (String nodeId : avgResponseTimeByPeers.keySet()) {
            // ensures asc order
            if (i++ == 0) {
                // First record correspond to the overall average response time by all peers
                assertThat(
                        ((Long) avgResponseTimeByPeers.get(nodeId).longValue())
                                .compareTo(stats.getOverallAveragePeerResponseTime()));
            } else {
                assertThat(avgResponseTimeByPeers.get(nodeId) > lastAvgResponseTime).isTrue();
                lastAvgResponseTime = avgResponseTimeByPeers.get(nodeId);
            }
        }
    }
}

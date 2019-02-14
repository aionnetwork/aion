package org.aion.zero.impl.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.BlockchainTestUtils.generateNewBlock;
import static org.aion.zero.impl.BlockchainTestUtils.generateRandomChain;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), true);

        // ensures correct behaviour on empty stats
        assertThat(stats.getAvgBlocksPerSec()).isEqualTo(0d);

        for (int totalBlocks = 1; totalBlocks <= 3; totalBlocks++) {
            int count = 0;
            while (count < totalBlocks) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_BEST);
                stats.update(current.getNumber());
                count++;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }

        assertThat(stats.getAvgBlocksPerSec()).isGreaterThan(0d);
        assertThat(stats.getAvgBlocksPerSec()).isAtMost(3d);
    }

    @Test
    public void testAvgBlocksPerSecStatDisabled() {
        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        // disables the stats
        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), false);

        for (int totalBlocks = 1; totalBlocks <= 3; totalBlocks++) {
            int count = 0;
            while (count < totalBlocks) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_BEST);
                stats.update(current.getNumber());
                count++;
            }
        }

        // ensures nothing changed
        assertThat(stats.getAvgBlocksPerSec()).isEqualTo(0d);
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
        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), true);

        // ensures correct behaviour on empty stats
        Map<String, Float> emptyReqToPeers = stats.getPercentageOfRequestsToPeers();
        assertThat(emptyReqToPeers.isEmpty()).isTrue();

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
    public void testTotalRequestsToPeersStatDisabled() {
        List<String> peers = new ArrayList<>(this.peers);
        while (peers.size() < 4) {
            peers.add(UUID.randomUUID().toString().substring(0, 6));
        }

        // this tests requires at least 3 peers in the list
        assertThat(peers.size()).isAtLeast(4);

        StandaloneBlockchain chain = bundle.bc;
        // disables the stats
        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), false);

        String firstPeer = peers.get(0);
        String secondPeer = peers.get(1);

        for (String peer : peers) {
            // status requests
            stats.updateTotalRequestsToPeer(peer, RequestType.STATUS);

            if (peer == firstPeer || peer == secondPeer) {
                // header requests
                stats.updateTotalRequestsToPeer(peer, RequestType.HEADERS);
            }

            // bodies requests
            if (peer == firstPeer) {
                stats.updateTotalRequestsToPeer(peer, RequestType.BODIES);
            }
        }

        // ensures still empty
        assertThat(stats.getPercentageOfRequestsToPeers().isEmpty()).isTrue();
    }

    @Test
    public void testTotalBlocksByPeer() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), true);

        // ensures correct behaviour on empty stats
        Map<String, Long> emptyTotalBlockReqByPeer = stats.getTotalBlocksByPeer();
        assertThat(emptyTotalBlockReqByPeer.isEmpty()).isTrue();

        int peerNo = 0;
        int processedBlocks = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            processedBlocks += totalBlocks;
            while (blocks > 0) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_BEST);
                stats.updatePeerTotalBlocks(peers.get(peerNo), 1);
                blocks--;
            }
            peerNo++;
        }

        Map<String, Long> totalBlockReqByPeer = stats.getTotalBlocksByPeer();
        assertThat(totalBlockReqByPeer.size()).isEqualTo(peers.size());

        int total = 3;
        long lastTotalBlocks = processedBlocks;
        for (String nodeId : peers) {
            // ensures desc order
            assertThat(lastTotalBlocks >= totalBlockReqByPeer.get(nodeId)).isTrue();
            lastTotalBlocks = totalBlockReqByPeer.get(nodeId);
            assertEquals(Long.valueOf(total), totalBlockReqByPeer.get(nodeId));

            total--;
        }
    }

    @Test
    public void testTotalBlocksByPeerDisabled() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        // disables the stats
        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), false);

        int peerNo = 0;

        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_BEST);
                stats.updatePeerTotalBlocks(peers.get(peerNo), 1);
                blocks--;
            }
            peerNo++;
        }

        // ensures still empty
        assertThat(stats.getTotalBlocksByPeer().isEmpty()).isTrue();
    }

    @Test
    public void testImportedBlocksByPeerStats() {
        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), true);

        // ensures correct behaviour on empty stats
        assertEquals(stats.getImportedBlocksByPeer(peers.get(0)), 0);

        int peerNo = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                ImportResult result = chain.tryToConnect(current);
                assertTrue(result.isStored());
                stats.updatePeerImportedBlocks(peers.get(peerNo), 1);
                blocks--;
            }
            peerNo++;
        }

        int imported = 3;
        for (String nodeId : peers) {
            assertEquals((long) imported, stats.getImportedBlocksByPeer(nodeId));
            imported--;
        }
    }

    @Test
    public void testImportedBlocksByPeerDisabled() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        // disables the stats
        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), false);

        int peerNo = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                ImportResult result = chain.tryToConnect(current);
                assertTrue(result.isStored());
                stats.updatePeerImportedBlocks(peers.get(peerNo), 1);
                blocks--;
            }
            peerNo++;
        }

        // ensures still empty
        for (String nodeId : peers) {
            assertEquals(0, stats.getImportedBlocksByPeer(nodeId));
        }
    }

    @Test
    public void testStoredBlocksByPeerStats() {
        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), true);

        // ensures correct behaviour on empty stats
        assertEquals(stats.getStoredBlocksByPeer(peers.get(0)), 0);

        int peerNo = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                boolean result = chain.storePendingStatusBlock(current);
                assertTrue(result);
                stats.updatePeerStoredBlocks(peers.get(peerNo), 1);
                blocks--;
            }
            peerNo++;
        }

        int stored = 3;
        for (String nodeId : peers) {
            assertEquals((long) stored, stats.getStoredBlocksByPeer(nodeId));
            stored--;
        }
    }

    @Test
    public void testStoredBlocksByPeerDisabled() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        // disables the stats
        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), false);

        int peerNo = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                boolean result = chain.storePendingStatusBlock(current);
                assertTrue(result);
                stats.updatePeerStoredBlocks(peers.get(peerNo), 1);
                blocks--;
            }
            peerNo++;
        }

        // ensures still empty
        for (String nodeId : peers) {
            assertEquals(0, stats.getStoredBlocksByPeer(nodeId));
        }
    }

    @Test
    public void testTotalBlockRequestsByPeerStats() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), true);

        // ensures correct behaviour on empty stats
        Map<String, Long> emptyTotalBlocksByPeer = stats.getTotalBlockRequestsByPeer();
        assertThat(emptyTotalBlocksByPeer.isEmpty()).isTrue();

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
        assertThat(totalBlocksByPeer.size()).isEqualTo(peers.size());

        Long lastTotalBlocks = (long) peers.size();
        for (String nodeId : totalBlocksByPeer.keySet()) {
            // ensures desc order
            assertThat(lastTotalBlocks >= totalBlocksByPeer.get(nodeId)).isTrue();
            lastTotalBlocks = totalBlocksByPeer.get(nodeId);
        }
    }

    @Test
    public void testTotalBlockRequestsByPeerStatsDisabled() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        // disables the stats
        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), false);

        int blocks = 3;
        for (String nodeId : peers) {
            int count = 0;
            while (count < blocks) {
                stats.updateTotalBlockRequestsByPeer(nodeId, 1);
                count++;
            }
            blocks--;
        }

        // ensures still empty
        assertThat(stats.getTotalBlockRequestsByPeer().isEmpty()).isTrue();
    }

    @Test
    public void testAverageResponseTimeByPeersStats() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), true);

        // ensures correct behaviour on empty stats
        Map<String, Double> emptyAvgResponseTimeByPeers = stats.getAverageResponseTimeByPeers();
        // request time is logged but no response is received
        stats.addPeerRequestTime("dummy", System.nanoTime());
        double overallAveragePeerResponseTime = stats.getOverallAveragePeerResponseTime();
        assertThat(emptyAvgResponseTimeByPeers.isEmpty()).isTrue();
        assertThat(overallAveragePeerResponseTime).isEqualTo(0d);

        stats = new SyncStats(chain.getBestBlock().getNumber(), true);

        int requests = 3;
        for (String nodeId : peers) {
            int count = requests;
            while (count > 0) {
                stats.addPeerRequestTime(nodeId, System.nanoTime());
                try {
                    Thread.sleep(100 * count);
                } catch (InterruptedException e) {
                }
                stats.addPeerResponseTime(nodeId, System.nanoTime());
                count--;
            }
            requests--;
        }

        Map<String, Double> avgResponseTimeByPeers = stats.getAverageResponseTimeByPeers();
        assertThat(avgResponseTimeByPeers.size()).isEqualTo(peers.size());

        Double lastAvgResponseTime = Double.MIN_VALUE;
        int i = 0;
        for (String nodeId : avgResponseTimeByPeers.keySet()) {
            // ensures asc order
            if (i++ == 0) {
                // First record correspond to the overall average response time by all peers
                assertThat(
                        avgResponseTimeByPeers
                                .get(nodeId)
                                .compareTo(stats.getOverallAveragePeerResponseTime()));
            } else {
                assertThat(avgResponseTimeByPeers.get(nodeId) > lastAvgResponseTime).isTrue();
                lastAvgResponseTime = avgResponseTimeByPeers.get(nodeId);
            }
        }
    }

    @Test
    public void testAverageResponseTimeByPeersStatsDisabled() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        // disables the stats
        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber(), false);

        int requests = 3;
        for (String nodeId : peers) {
            int count = requests;
            while (count > 0) {
                stats.addPeerRequestTime(nodeId, System.nanoTime());
                stats.addPeerResponseTime(nodeId, System.nanoTime());
                count--;
            }
            requests--;
        }

        // ensures still empty
        assertThat(stats.getAverageResponseTimeByPeers().isEmpty()).isTrue();
    }
}

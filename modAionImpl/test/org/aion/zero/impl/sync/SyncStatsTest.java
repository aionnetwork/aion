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
import org.apache.commons.lang3.tuple.Pair;
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
        Map<String, Integer> emptyTotalBlockReqByPeer = stats.getTotalBlocksByPeer();
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

        Map<String, Integer> totalBlockReqByPeer = stats.getTotalBlocksByPeer();
        assertThat(totalBlockReqByPeer.size()).isEqualTo(peers.size());

        int total = 3;
        int lastTotalBlocks = processedBlocks;
        for (String nodeId : peers) {
            // ensures desc order
            assertThat(lastTotalBlocks >= totalBlockReqByPeer.get(nodeId)).isTrue();
            lastTotalBlocks = totalBlockReqByPeer.get(nodeId);
            assertThat(totalBlockReqByPeer.get(nodeId)).isEqualTo(total);
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
        SyncStats stats = new SyncStats(0L, true);

        // ensures correct behaviour on empty stats
        Map<String, Integer> emptyTotalBlocksByPeer = stats.getTotalBlockRequestsByPeer();
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

        Map<String, Integer> totalBlocksByPeer = stats.getTotalBlockRequestsByPeer();
        assertThat(totalBlocksByPeer.size()).isEqualTo(peers.size());

        int lastTotalBlocks = peers.size();
        for (String nodeId : totalBlocksByPeer.keySet()) {
            // ensures desc order
            assertThat(lastTotalBlocks >= totalBlocksByPeer.get(nodeId)).isTrue();
            lastTotalBlocks = totalBlocksByPeer.get(nodeId);
        }
    }

    @Test
    public void testTotalBlockRequestsByPeerStatsDisabled() {
        // disables the stats
        SyncStats stats = new SyncStats(0L, false);

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
    public void testResponseStatsByPeersEmpty() {
        SyncStats stats = new SyncStats(0L, true);

        // ensures correct behaviour on empty stats
        assertThat(stats.getResponseStats()).isNull();
        assertThat(stats.dumpResponseStats()).isEmpty();

        // request time is logged but no response is received
        stats.updateRequestTime("dummy", System.nanoTime(), RequestType.STATUS);
        stats.updateRequestTime("dummy", System.nanoTime(), RequestType.HEADERS);
        stats.updateRequestTime("dummy", System.nanoTime(), RequestType.BODIES);
        assertThat(stats.getResponseStats()).isNull();
        assertThat(stats.dumpResponseStats()).isEmpty();

        stats = new SyncStats(0L, true);

        // response time is logged but no request exists
        stats.updateResponseTime("dummy", System.nanoTime(), RequestType.STATUS);
        stats.updateResponseTime("dummy", System.nanoTime(), RequestType.HEADERS);
        stats.updateResponseTime("dummy", System.nanoTime(), RequestType.BODIES);
        assertThat(stats.getResponseStats()).isNull();
        assertThat(stats.dumpResponseStats()).isEmpty();
    }

    @Test
    public void testResponseStatsByPeers() {
        SyncStats stats = new SyncStats(0L, true);
        long time;
        int entries = 3;
        // should be updated if more message types are added
        int requestTypes = 3;

        for (String nodeId : peers) {
            int count = 1;

            while (count <= entries) {
                time = System.nanoTime();

                // status -> type of request 1
                stats.updateRequestTime(nodeId, time, RequestType.STATUS);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.STATUS);

                // headers -> type of request 2
                stats.updateRequestTime(nodeId, time, RequestType.HEADERS);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.HEADERS);

                // bodies -> type of request 3
                stats.updateRequestTime(nodeId, time, RequestType.BODIES);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.BODIES);

                count++;
            }
        }

        Map<String, Map<String, Pair<Double, Integer>>> responseStats = stats.getResponseStats();
        for (Map.Entry<String, Map<String, Pair<Double, Integer>>> e : responseStats.entrySet()) {
            // for entries for each: «all» «status» «headers» «bodies»
            assertThat(e.getValue().size()).isEqualTo(4);

            if (e.getKey().equals("overall")) {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all")) {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight())
                                .isEqualTo(peers.size() * entries * requestTypes);
                    } else {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(peers.size() * entries);
                    }
                }
            } else {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all")) {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(entries * requestTypes);
                    } else {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(entries);
                    }
                }
            }
        }

        // System.out.println(stats.dumpResponseStats());
    }

    @Test
    public void testResponseStatsByPeersStatusOnly() {
        SyncStats stats = new SyncStats(0L, true);
        long time;
        int entries = 3;

        for (String nodeId : peers) {
            int count = 1;

            while (count <= entries) {
                time = System.nanoTime();

                // status
                stats.updateRequestTime(nodeId, time, RequestType.STATUS);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.STATUS);

                count++;
            }
        }

        Map<String, Map<String, Pair<Double, Integer>>> responseStats = stats.getResponseStats();
        for (Map.Entry<String, Map<String, Pair<Double, Integer>>> e : responseStats.entrySet()) {
            // for entries for each: «all» «status» «headers» «bodies»
            assertThat(e.getValue().size()).isEqualTo(4);

            if (e.getKey().equals("overall")) {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all") || sub.getKey().equals("status")) {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(peers.size() * entries);
                    } else {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(0d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(0);
                    }
                }
            } else {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all") || sub.getKey().equals("status")) {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(entries);
                    } else {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(0d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(0);
                    }
                }
            }
        }

        // System.out.println(stats.dumpResponseStats());
    }

    @Test
    public void testResponseStatsByPeersHeadersOnly() {
        SyncStats stats = new SyncStats(0L, true);
        long time;
        int entries = 3;

        for (String nodeId : peers) {
            int count = 1;

            while (count <= entries) {
                time = System.nanoTime();

                // headers
                stats.updateRequestTime(nodeId, time, RequestType.HEADERS);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.HEADERS);

                count++;
            }
        }

        Map<String, Map<String, Pair<Double, Integer>>> responseStats = stats.getResponseStats();
        for (Map.Entry<String, Map<String, Pair<Double, Integer>>> e : responseStats.entrySet()) {
            // for entries for each: «all» «status» «headers» «bodies»
            assertThat(e.getValue().size()).isEqualTo(4);

            if (e.getKey().equals("overall")) {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all") || sub.getKey().equals("headers")) {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(peers.size() * entries);
                    } else {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(0d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(0);
                    }
                }
            } else {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all") || sub.getKey().equals("headers")) {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(entries);
                    } else {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(0d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(0);
                    }
                }
            }
        }

        // System.out.println(stats.dumpResponseStats());
    }

    @Test
    public void testResponseStatsByPeersBodiesOnly() {
        SyncStats stats = new SyncStats(0L, true);
        long time;
        int entries = 3;

        for (String nodeId : peers) {
            int count = 1;

            while (count <= entries) {
                time = System.nanoTime();

                // bodies
                stats.updateRequestTime(nodeId, time, RequestType.BODIES);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.BODIES);

                count++;
            }
        }

        Map<String, Map<String, Pair<Double, Integer>>> responseStats = stats.getResponseStats();
        for (Map.Entry<String, Map<String, Pair<Double, Integer>>> e : responseStats.entrySet()) {
            // for entries for each: «all» «status» «headers» «bodies»
            assertThat(e.getValue().size()).isEqualTo(4);

            if (e.getKey().equals("overall")) {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all") || sub.getKey().equals("bodies")) {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(peers.size() * entries);
                    } else {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(0d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(0);
                    }
                }
            } else {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all") || sub.getKey().equals("bodies")) {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(1_000_000d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(entries);
                    } else {
                        // check average
                        assertThat(sub.getValue().getLeft()).isEqualTo(0d);
                        // check entries
                        assertThat(sub.getValue().getRight()).isEqualTo(0);
                    }
                }
            }
        }

        // System.out.println(stats.dumpResponseStats());
    }

    @Test
    public void testAverageResponseTimeByPeersStatsDisabled() {
        // disables the stats
        SyncStats stats = new SyncStats(0L, false);

        int requests = 3;
        for (String nodeId : peers) {
            int count = requests;
            while (count > 0) {

                // status updates
                stats.updateRequestTime(nodeId, System.nanoTime(), RequestType.STATUS);
                stats.updateResponseTime(nodeId, System.nanoTime(), RequestType.STATUS);

                // headers updates
                stats.updateRequestTime(nodeId, System.nanoTime(), RequestType.HEADERS);
                stats.updateResponseTime(nodeId, System.nanoTime(), RequestType.HEADERS);

                // bodies updates
                stats.updateRequestTime(nodeId, System.nanoTime(), RequestType.BODIES);
                stats.updateResponseTime(nodeId, System.nanoTime(), RequestType.BODIES);

                count--;
            }
            requests--;
        }

        // ensures still empty
        assertThat(stats.getResponseStats()).isNull();
    }
}

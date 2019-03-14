package org.aion.zero.impl.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aion.zero.impl.sync.statistics.BlockType;
import org.aion.zero.impl.sync.statistics.RequestType;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.BeforeClass;
import org.junit.Test;

public class SyncStatsTest {

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
        SyncStats stats = new SyncStats(0L, true);

        // ensures correct behaviour on empty stats
        assertThat(stats.getAvgBlocksPerSec()).isEqualTo(0d);

        for (int blocks = 1; blocks <= 3; blocks++) {
            stats.update(blocks);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
        assertThat(stats.getAvgBlocksPerSec()).isGreaterThan(0d);
        assertThat(stats.getAvgBlocksPerSec()).isAtMost(2d);
    }

    @Test
    public void testAvgBlocksPerSecStatDisabled() {
        // disables the stats
        SyncStats stats = new SyncStats(0L, false);

        for (int blocks = 1; blocks <= 3; blocks++) {
            stats.update(blocks);
        }
        // ensures nothing changed
        assertThat(stats.getAvgBlocksPerSec()).isEqualTo(0d);
    }

    @Test
    public void testTotalRequestsToPeersStat() {
        List<String> peers = new ArrayList<>(this.peers);
        while (peers.size() < 7) {
            peers.add(UUID.randomUUID().toString().substring(0, 6));
        }

        // this tests requires at least 6 peers in the list
        assertThat(peers.size()).isAtLeast(6);

        SyncStats stats = new SyncStats(0L, true);

        // ensures correct behaviour on empty stats
        Map<String, Float> emptyReqToPeers = stats.getPercentageOfRequestsToPeers();
        assertThat(emptyReqToPeers.isEmpty()).isTrue();

        float processedRequests = 0;

        for (String peer : peers) {
            // status requests
            stats.updateTotalRequestsToPeer(peer, RequestType.STATUS);
            processedRequests++;
            // header requests
            if (peers.subList(0, 5).contains(peer)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.HEADERS);
                processedRequests++;
            }
            // bodies requests
            if (peers.subList(0, 4).contains(peer)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.BODIES);
                processedRequests++;
            }
            // blocks requests
            if (peers.subList(0, 3).contains(peer)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.BLOCKS);
                processedRequests++;
            }
            // receipts requests
            if (peers.subList(0, 2).contains(peer)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.RECEIPTS);
                processedRequests++;
            }
            // trie_data requests
            if (peer == peers.get(0)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.TRIE_DATA);
                processedRequests++;
            }
        }

        Map<String, Float> reqToPeers = stats.getPercentageOfRequestsToPeers();

        // makes sure no additional peers were created
        assertThat(reqToPeers.size()).isEqualTo(peers.size());

        String firstPeer = peers.get(0);
        String secondPeer = peers.get(1);
        String thirdPeer = peers.get(2);
        String fourthPeer = peers.get(3);
        String fifthPeer = peers.get(4);
        String sixthPeer = peers.get(5);

        // by design (the updates above are not symmetrical)
        reqToPeers.get(peers.get(0));
        assertThat(reqToPeers.get(firstPeer)).isGreaterThan(reqToPeers.get(secondPeer));
        assertThat(reqToPeers.get(firstPeer)).isEqualTo(6 / processedRequests);

        assertThat(reqToPeers.get(secondPeer)).isGreaterThan(reqToPeers.get(thirdPeer));
        assertThat(reqToPeers.get(secondPeer)).isEqualTo(5 / processedRequests);

        assertThat(reqToPeers.get(thirdPeer)).isGreaterThan(reqToPeers.get(fourthPeer));
        assertThat(reqToPeers.get(thirdPeer)).isEqualTo(4 / processedRequests);

        assertThat(reqToPeers.get(fourthPeer)).isGreaterThan(reqToPeers.get(fifthPeer));
        assertThat(reqToPeers.get(fourthPeer)).isEqualTo(3 / processedRequests);

        assertThat(reqToPeers.get(fifthPeer)).isGreaterThan(reqToPeers.get(sixthPeer));
        assertThat(reqToPeers.get(fifthPeer)).isEqualTo(2 / processedRequests);

        assertThat(reqToPeers.get(sixthPeer)).isEqualTo(1 / processedRequests);

        for (String otherPeers : peers.subList(6, peers.size())) {
            assertThat(reqToPeers.get(otherPeers)).isEqualTo(reqToPeers.get(sixthPeer));
        }

        int blocks = 6;

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
        while (peers.size() < 7) {
            peers.add(UUID.randomUUID().toString().substring(0, 6));
        }

        // this tests requires at least 3 peers in the list
        assertThat(peers.size()).isAtLeast(6);

        // disables the stats
        SyncStats stats = new SyncStats(0L, false);

        for (String peer : peers) {
            // status requests
            stats.updateTotalRequestsToPeer(peer, RequestType.STATUS);
            // header requests
            if (peers.subList(0, 5).contains(peer)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.HEADERS);
            }
            // bodies requests
            if (peers.subList(0, 4).contains(peer)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.BODIES);
            }
            // blocks requests
            if (peers.subList(0, 3).contains(peer)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.BLOCKS);
            }
            // receipts requests
            if (peers.subList(0, 2).contains(peer)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.RECEIPTS);
            }
            // trie_data requests
            if (peer == peers.get(0)) {
                stats.updateTotalRequestsToPeer(peer, RequestType.TRIE_DATA);
            }
        }

        // ensures still empty
        assertThat(stats.getPercentageOfRequestsToPeers()).isNull();
    }

    @Test
    public void testReceivedBlocksByPeer() {
        SyncStats stats = new SyncStats(0L, true);

        // ensures correct behaviour on empty stats
        Map<String, Integer> emptyReceivedBlockReqByPeer = stats.getReceivedBlocksByPeer();
        assertThat(emptyReceivedBlockReqByPeer.isEmpty()).isTrue();

        int peerNo = 0;
        int processedBlocks = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            processedBlocks += totalBlocks;
            while (blocks > 0) {
                stats.updatePeerBlocks(peers.get(peerNo), 1, BlockType.RECEIVED);
                blocks--;
            }
            peerNo++;
        }

        Map<String, Integer> totalBlockResByPeer = stats.getReceivedBlocksByPeer();
        assertThat(totalBlockResByPeer.size()).isEqualTo(peers.size());

        int total = 3;
        int lastTotalBlocks = processedBlocks;
        for (String nodeId : peers) {
            // ensures desc order
            assertThat(lastTotalBlocks >= totalBlockResByPeer.get(nodeId)).isTrue();
            lastTotalBlocks = totalBlockResByPeer.get(nodeId);
            assertThat(totalBlockResByPeer.get(nodeId)).isEqualTo(total);
            total--;
        }
    }

    @Test
    public void testReceivedBlocksByPeerDisabled() {
        // disables the stats
        SyncStats stats = new SyncStats(0L, false);

        int peerNo = 0;

        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                stats.updatePeerBlocks(peers.get(peerNo), 1, BlockType.RECEIVED);
                blocks--;
            }
            peerNo++;
        }

        // ensures still empty
        assertThat(stats.getReceivedBlocksByPeer()).isNull();
    }

    @Test
    public void testImportedBlocksByPeerStats() {
        SyncStats stats = new SyncStats(0L, true);

        // ensures correct behaviour on empty stats
        assertEquals(stats.getImportedBlocksByPeer(peers.get(0)), 0);

        int peerNo = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                stats.updatePeerBlocks(peers.get(peerNo), 1, BlockType.IMPORTED);
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
        // disables the stats
        SyncStats stats = new SyncStats(0L, false);

        int peerNo = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                stats.updatePeerBlocks(peers.get(peerNo), 1, BlockType.IMPORTED);
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

        SyncStats stats = new SyncStats(0L, true);

        // ensures correct behaviour on empty stats
        assertEquals(stats.getStoredBlocksByPeer(peers.get(0)), 0);

        int peerNo = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                stats.updatePeerBlocks(peers.get(peerNo), 1, BlockType.STORED);
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

        // disables the stats
        SyncStats stats = new SyncStats(0L, false);

        int peerNo = 0;
        for (int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
            int blocks = totalBlocks;
            while (blocks > 0) {
                stats.updatePeerBlocks(peers.get(peerNo), 1, BlockType.STORED);
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
        stats.updateRequestTime("dummy", System.nanoTime(), RequestType.BLOCKS);
        stats.updateRequestTime("dummy", System.nanoTime(), RequestType.RECEIPTS);
        stats.updateRequestTime("dummy", System.nanoTime(), RequestType.TRIE_DATA);

        assertThat(stats.getResponseStats()).isNull();
        assertThat(stats.dumpResponseStats()).isEmpty();

        stats = new SyncStats(0L, true);

        // response time is logged but no request exists
        stats.updateResponseTime("dummy", System.nanoTime(), RequestType.STATUS);
        stats.updateResponseTime("dummy", System.nanoTime(), RequestType.HEADERS);
        stats.updateResponseTime("dummy", System.nanoTime(), RequestType.BODIES);
        stats.updateResponseTime("dummy", System.nanoTime(), RequestType.BLOCKS);
        stats.updateResponseTime("dummy", System.nanoTime(), RequestType.RECEIPTS);
        stats.updateResponseTime("dummy", System.nanoTime(), RequestType.TRIE_DATA);

        assertThat(stats.getResponseStats()).isNull();
        assertThat(stats.dumpResponseStats()).isEmpty();
    }

    @Test
    public void testResponseStatsByPeers() {
        SyncStats stats = new SyncStats(0L, true);
        long time;
        int entries = 3;
        // should be updated if more message types are added
        int requestTypes = 6;

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

                // bodies -> type of request 4
                stats.updateRequestTime(nodeId, time, RequestType.BLOCKS);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.BLOCKS);

                // bodies -> type of request 5
                stats.updateRequestTime(nodeId, time, RequestType.RECEIPTS);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.RECEIPTS);

                // bodies -> type of request 6
                stats.updateRequestTime(nodeId, time, RequestType.TRIE_DATA);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.TRIE_DATA);

                count++;
            }
        }

        Map<String, Map<String, Pair<Double, Integer>>> responseStats = stats.getResponseStats();
        for (Map.Entry<String, Map<String, Pair<Double, Integer>>> e : responseStats.entrySet()) {
            // 7 entries for each: «all» «status» «headers» «bodies» «blocks» «receipts» «trie_data»
            assertThat(e.getValue().size()).isEqualTo(7);

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
            // 7 entries for each: «all» «status» «headers» «bodies» «blocks» «receipts» «trie_data»
            assertThat(e.getValue().size()).isEqualTo(7);

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
            // 7 entries for each: «all» «status» «headers» «bodies» «blocks» «receipts» «trie_data»
            assertThat(e.getValue().size()).isEqualTo(7);

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
            // 7 entries for each: «all» «status» «headers» «bodies» «blocks» «receipts» «trie_data»
            assertThat(e.getValue().size()).isEqualTo(7);

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
    public void testResponseStatsByPeersBlocksOnly() {
        SyncStats stats = new SyncStats(0L, true);
        long time;
        int entries = 3;

        for (String nodeId : peers) {
            int count = 1;

            while (count <= entries) {
                time = System.nanoTime();

                // blocks
                stats.updateRequestTime(nodeId, time, RequestType.BLOCKS);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.BLOCKS);

                count++;
            }
        }

        Map<String, Map<String, Pair<Double, Integer>>> responseStats = stats.getResponseStats();
        for (Map.Entry<String, Map<String, Pair<Double, Integer>>> e : responseStats.entrySet()) {
            // 7 entries for each: «all» «status» «headers» «bodies» «blocks» «receipts» «trie_data»
            assertThat(e.getValue().size()).isEqualTo(7);

            if (e.getKey().equals("overall")) {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all") || sub.getKey().equals("blocks")) {
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
                    if (sub.getKey().equals("all") || sub.getKey().equals("blocks")) {
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
    public void testResponseStatsByPeersReceiptsOnly() {
        SyncStats stats = new SyncStats(0L, true);
        long time;
        int entries = 3;

        for (String nodeId : peers) {
            int count = 1;

            while (count <= entries) {
                time = System.nanoTime();

                // blocks
                stats.updateRequestTime(nodeId, time, RequestType.RECEIPTS);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.RECEIPTS);

                count++;
            }
        }

        Map<String, Map<String, Pair<Double, Integer>>> responseStats = stats.getResponseStats();
        for (Map.Entry<String, Map<String, Pair<Double, Integer>>> e : responseStats.entrySet()) {
            // 7 entries for each: «all» «status» «headers» «bodies» «blocks» «receipts» «trie_data»
            assertThat(e.getValue().size()).isEqualTo(7);

            if (e.getKey().equals("overall")) {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all") || sub.getKey().equals("receipts")) {
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
                    if (sub.getKey().equals("all") || sub.getKey().equals("receipts")) {
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
    public void testResponseStatsByPeersTrieDataOnly() {
        SyncStats stats = new SyncStats(0L, true);
        long time;
        int entries = 3;

        for (String nodeId : peers) {
            int count = 1;

            while (count <= entries) {
                time = System.nanoTime();

                // blocks
                stats.updateRequestTime(nodeId, time, RequestType.TRIE_DATA);
                stats.updateResponseTime(nodeId, time + 1_000_000, RequestType.TRIE_DATA);

                count++;
            }
        }

        Map<String, Map<String, Pair<Double, Integer>>> responseStats = stats.getResponseStats();
        for (Map.Entry<String, Map<String, Pair<Double, Integer>>> e : responseStats.entrySet()) {
            // 7 entries for each: «all» «status» «headers» «bodies» «blocks» «receipts» «trie_data»
            assertThat(e.getValue().size()).isEqualTo(7);

            if (e.getKey().equals("overall")) {
                for (Map.Entry<String, Pair<Double, Integer>> sub : e.getValue().entrySet()) {
                    if (sub.getKey().equals("all") || sub.getKey().equals("trie_data")) {
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
                    if (sub.getKey().equals("all") || sub.getKey().equals("trie_data")) {
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

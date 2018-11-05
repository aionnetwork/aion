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
    private final StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
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

        assertThat(stats.getAvgBlocksPerSec()  <= 3.).isTrue();
    }

    @Test
    public void testTotalRequestsToPeersStat() {

        StandaloneBlockchain chain = bundle.bc;
        generateRandomChain(chain, 1, 1, accounts, 10);

        SyncStats stats = new SyncStats(chain.getBestBlock().getNumber());

        // ensures correct behaviour on empty stats
        Map<String, Float> emptyReqToPeers = stats.getPercentageOfRequestsToPeers();
        Map<String, Long> emptyTotalBlockReqByPeer = stats.getTotalBlocksByPeer();
        assertThat(emptyReqToPeers.size() == 0).isTrue();
        assertThat(emptyTotalBlockReqByPeer.size() == 0).isTrue();

        int peerNo = 0;
        int processedBlocks = 0;

        for(int totalBlocks = peers.size(); totalBlocks > 0; totalBlocks--) {
                int blocks = totalBlocks;
                processedBlocks += totalBlocks;
                while(blocks > 0) {
                        AionBlock current = generateNewBlock(chain, chain.getBestBlock(), accounts, 10);
                        assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_BEST);
                        stats.update(peers.get(peerNo), blocks, chain.getBestBlock().getNumber());
                        blocks--;
                }
                peerNo++;
        }

        Map<String, Float> reqToPeers = stats.getPercentageOfRequestsToPeers();
        Map<String, Long> totalBlockReqByPeer = stats.getTotalBlocksByPeer();

        assertThat(reqToPeers.size() == peers.size()).isTrue();
        assertThat(totalBlockReqByPeer.size() == peers.size()).isTrue();

        int blocks = 3;

        float lastPercentage = (float) 1;
        float diffThreshold = (float) 0.01;

        long lastTotalBlocks = processedBlocks;

        for(String nodeId:reqToPeers.keySet()) {
                float percentageReq = reqToPeers.get(nodeId);
                // ensures desc order
                assertThat(lastPercentage >= percentageReq).isTrue();
                lastPercentage = percentageReq;
                assertThat(percentageReq - (1. * blocks/processedBlocks) < diffThreshold).isTrue();
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
        for(String nodeId:peers) {
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
        for(String nodeId:totalBlocksByPeer.keySet()) {
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
        for(String nodeId:peers) {
            int count = requests;
            while (count > 0) {
                stats.addPeerRequestTime(nodeId, System.currentTimeMillis());
                try {
                    Thread.sleep(100 * count);
                } catch(InterruptedException e) {
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
            if(i == 0) {
                // First record correspond to the overall average response time by all peers
                assertThat(((Long)avgResponseTimeByPeers.get(nodeId).longValue())
                    .compareTo(stats.getOverallAveragePeerResponseTime()));
            } else {
                assertThat(avgResponseTimeByPeers.get(nodeId) > lastAvgResponseTime).isTrue();
                lastAvgResponseTime = avgResponseTimeByPeers.get(nodeId);
            }
        }
    }
}

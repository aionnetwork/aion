package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.types.MiningBlock;
import org.junit.Test;

public class BlockchainConcurrencyTest {

    @Test
    public void testPublishBestBlockSafely() {
        ExecutorService blockCreationService = Executors.newSingleThreadExecutor();
        ExecutorService getBlockService = Executors.newSingleThreadExecutor();

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();
        StandaloneBlockchain bc = bundle.bc;

        final int MAX_COUNT = 100000;
        final CountDownLatch endLatch = new CountDownLatch(2);

        // this will not definitively prove
        try {
            blockCreationService.submit(
                    () -> {
                        int count = 0;

                        List<AionTransaction> txList = Collections.emptyList();
                        MiningBlock block =
                            null;
                        try {
                            block = bc.createNewMiningBlock(bc.genesis, Collections.emptyList(), false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        while (!Thread.currentThread().isInterrupted() && count < MAX_COUNT) {
                            try {
                                block = bc.createNewMiningBlock(block, txList, false);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            count++;
                        }
                        System.out.println("completed block creation");
                        endLatch.countDown();
                    });

            getBlockService.submit(
                    () -> {
                        int count = 0;
                        long prevNumber = bc.getBestBlock().getNumber();
                        while (!Thread.currentThread().isInterrupted() && count < MAX_COUNT) {
                            // all three of these methods use {@link
                            // AionBlockchainImpl#pubBestBlock}
                            assertThat(bc.getBestBlockHash()).isNotNull();
                            bc.getSize();

                            Block block = bc.getBestBlock();
                            assertThat(block).isNotNull();
                            assertThat(block.getNumber()).isAtLeast(prevNumber);
                            prevNumber = block.getNumber();
                            count++;
                        }
                        endLatch.countDown();
                    });
        } finally {
            blockCreationService.shutdown();
            getBlockService.shutdown();
        }
    }
}

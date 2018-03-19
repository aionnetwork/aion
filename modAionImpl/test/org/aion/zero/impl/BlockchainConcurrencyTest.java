package org.aion.zero.impl;

import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.truth.Truth.assertThat;

public class BlockchainConcurrencyTest {

    @Test
    public void testPublishBestBlockSafely() {
        ExecutorService blockCreationService = Executors.newSingleThreadExecutor();
        ExecutorService getBlockService = Executors.newSingleThreadExecutor();

        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .build();
        StandaloneBlockchain bc = bundle.bc;

        final int MAX_COUNT = 100000;
        final CountDownLatch endLatch = new CountDownLatch(2);

        // this will not definitively prove
        try {
            blockCreationService.submit(() -> {
                int count = 0;

                List<AionTransaction> txList = Collections.emptyList();
                AionBlock block = bc.createNewBlock(bc.genesis, Collections.emptyList(), false);
                while (!Thread.currentThread().isInterrupted() && count < MAX_COUNT) {
                    block = bc.createNewBlock(block, txList, false);
                    count++;
                }
                System.out.println("completed block creation");
                endLatch.countDown();
            });

            getBlockService.submit(() -> {
                int count = 0;
                long prevNumber = bc.getBestBlock().getNumber();
                while(!Thread.currentThread().isInterrupted() && count < MAX_COUNT) {
                    // all three of these methods use {@link AionBlockchainImpl#pubBestBlock}
                    assertThat(bc.getBestBlockHash()).isNotNull();
                    bc.getSize();

                    AionBlock block = bc.getBestBlock();
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

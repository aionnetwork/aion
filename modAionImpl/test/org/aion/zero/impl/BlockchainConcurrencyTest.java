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

package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
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
                        AionBlock block =
                                bc.createNewBlock(bc.genesis, Collections.emptyList(), false);
                        while (!Thread.currentThread().isInterrupted() && count < MAX_COUNT) {
                            block = bc.createNewBlock(block, txList, false);
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

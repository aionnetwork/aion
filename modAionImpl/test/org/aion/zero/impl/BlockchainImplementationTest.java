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
import java.util.HashMap;
import java.util.Map;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Class for testing methods from {@link AionBlockchainImpl} that are not part of specific use
 * cases.
 *
 * @author Alexandra Roatis
 */
public class BlockchainImplementationTest {

    @BeforeClass
    public static void setup() {
        // logging to see errors
        Map<String, String> cfg = new HashMap<>();
        cfg.put("DB", "ERROR");
        cfg.put("CONS", "DEBUG");

        AionLoggerFactory.init(cfg);
    }

    /**
     * In TOP mode only the top K blocks have a stored state. Blocks older than the top K are have
     * restrictions due to pruning.
     */
    @Test
    public void testIsPruneRestricted_wTopState() {
        // number of blocks stored by the blockchain
        int stored = 200;
        // the maximum height considered by this test
        int height = 300;

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(new MockRepositoryConfig(new CfgPrune(stored)))
                        .build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();

        // creating (height) blocks
        long time = System.currentTimeMillis();
        for (int i = 0; i < height; i++) {
            BlockContext context =
                    chain.createNewBlockInternal(
                            chain.getBestBlock(), Collections.emptyList(), true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // testing restriction for unrestricted blocks: height to (height - stored + 1)
        for (int i = height; i >= height - stored + 1; i--) {
            assertThat(chain.isPruneRestricted(i)).isFalse();
            // ensure the state exists
            assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isTrue();
        }

        // testing restriction for restricted blocks: (height - stored) to 0
        for (int i = height - stored; i >= 0; i--) {
            assertThat(chain.isPruneRestricted(i)).isTrue();
            // ensure the state is missing
            if (i < height - stored) {
                assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isFalse();
            } else {
                // NOTE: state at (height - stored) exists, but is already restricted
                assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isTrue();
            }
        }
    }

    /**
     * In FULL mode the state is stored for all blocks. There are no restrictions due to pruning.
     */
    @Test
    public void testIsPruneRestricted_wFullState() {
        // the maximum height considered by this test
        int height = 300;

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(new MockRepositoryConfig(new CfgPrune(false)))
                        .build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();

        // creating (height) blocks
        long time = System.currentTimeMillis();
        for (int i = 0; i < height; i++) {
            BlockContext context =
                    chain.createNewBlockInternal(
                            chain.getBestBlock(), Collections.emptyList(), true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // testing restriction for unrestricted blocks
        for (int i = height; i >= 0; i--) {
            assertThat(chain.isPruneRestricted(i)).isFalse();
            // ensure the state exists
            assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isTrue();
        }
    }

    /**
     * In SPREAD mode the top K blocks and the blocks that are multiples of the archive rate have a
     * stored state. There are no restrictions due to pruning.
     */
    @Test
    public void testIsPruneRestricted_wSpreadState() {
        // number of blocks stored by the blockchain
        int stored = 200;
        // the maximum height considered by this test
        int height = 1300;
        // the interval at which blocks are indexed
        int index = 1000;

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(new MockRepositoryConfig(new CfgPrune(stored, index)))
                        .build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();

        // creating (height) blocks
        long time = System.currentTimeMillis();
        for (int i = 0; i < height; i++) {
            BlockContext context =
                    chain.createNewBlockInternal(
                            chain.getBestBlock(), Collections.emptyList(), true, time / 100000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // testing restriction for unrestricted blocks
        for (int i = height; i >= 0; i--) {
            assertThat(chain.isPruneRestricted(i)).isFalse();
            if (i % index == 0 || i >= height - stored) {
                // ensure the state exists
                assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isTrue();
            } else {
                // ensure the state is missing
                assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isFalse();
            }
        }
    }
}

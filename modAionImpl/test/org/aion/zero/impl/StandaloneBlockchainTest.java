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

import java.math.BigInteger;
import java.util.Collections;
import org.aion.base.type.AionAddress;
import org.aion.crypto.ECKey;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;

public class StandaloneBlockchainTest {

    @Test
    public void testStandaloneBlockchainGenerateAccounts() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withDefaultAccounts().build();

        assertThat(bundle.bc).isNotNull();
        assertThat(bundle.privateKeys).isNotNull();
        assertThat(bundle.privateKeys.size()).isEqualTo(10);

        for (ECKey k : bundle.privateKeys) {
            assertThat(bundle.bc.getRepository().getBalance(new AionAddress(k.getAddress())))
                    .isGreaterThan(BigInteger.ZERO);
        }
    }

    @Test
    public void testStandaloneBlockchainSetNumber() {
        final long BLOCK_NUMBER = 1_000_000L;
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withDefaultAccounts().build();

        bundle.bc.setBlockNumber(BLOCK_NUMBER);
        assertThat(bundle.bc.getBestBlock().getNumber()).isEqualTo(BLOCK_NUMBER);
        assertThat(bundle.bc.getBestBlock().getStateRoot())
                .isEqualTo(bundle.bc.genesis.getStateRoot());

        AionBlock block =
                bundle.bc.createNewBlock(bundle.bc.getBestBlock(), Collections.emptyList(), false);
        assertThat(block).isNotNull();
        assertThat(block.getNumber()).isEqualTo(BLOCK_NUMBER + 1);
    }
}

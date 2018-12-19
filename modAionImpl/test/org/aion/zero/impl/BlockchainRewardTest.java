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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */

package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.Collections;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.mcf.core.ImportResult;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Ignore;
import org.junit.Test;

public class BlockchainRewardTest {

    private IBlockConstants constants = new BlockConstants();

    /**
     * Test that blocks between the lower and upper bounds follow a certain function [0, 259200]
     *
     * <p>Note: this test is resource consuming!
     *
     * <p>Check {@link org.aion.zero.impl.core.RewardsCalculator} for algorithm related to the
     * ramp-up block time
     */
    @Ignore
    @Test
    public void testBlockchainRewardMonotonicallyIncreasing() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();

        StandaloneBlockchain bc = bundle.bc;
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);
        ImportResult res = bc.tryToConnect(block);
        assertThat(res).isEqualTo(ImportResult.IMPORTED_BEST);

        Address coinbase = block.getCoinbase();
        BigInteger previousBalance = bc.getRepository().getBalance(coinbase);

        // first block already sealed
        for (int i = 2; i < 99999; i++) {
            AionBlock b = bc.createNewBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);
            ImportResult r = bc.tryToConnect(b);
            assertThat(r).isEqualTo(ImportResult.IMPORTED_BEST);

            // note the assumption here that blocks are mined by one coinbase
            BigInteger balance = bc.getRepository().getBalance(coinbase);
            assertThat(balance).isGreaterThan(previousBalance);
            previousBalance = balance;

            if (b.getNumber() % 1000 == 0) System.out.println("added block #: " + i);
        }
    }
}

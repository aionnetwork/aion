package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.Collections;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.mcf.core.ImportResult;

import org.aion.types.Address;
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

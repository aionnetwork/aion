package org.aion.zero.impl;

import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;

public class StandaloneBlockchainTest {

    @Test
    public void testStandaloneBlockchainGenerateAccounts() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder
                .withDefaultAccounts()
                .build();

        assertThat(bundle.bc).isNotNull();
        assertThat(bundle.privateKeys).isNotNull();
        assertThat(bundle.privateKeys.size()).isEqualTo(10);

        for (ECKey k : bundle.privateKeys) {
            assertThat(bundle.bc.getRepository().getBalance(new Address(k.getAddress()))).isGreaterThan(BigInteger.ZERO);
        }
    }

    @Test
    public void testStandaloneBlockchainSetNumber() {
        final long BLOCK_NUMBER = 1_000_000L;
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder
                .withDefaultAccounts()
                .build();

        bundle.bc.setBlockNumber(BLOCK_NUMBER);
        assertThat(bundle.bc.getBestBlock().getNumber()).isEqualTo(BLOCK_NUMBER);
        assertThat(bundle.bc.getBestBlock().getStateRoot()).isEqualTo(bundle.bc.genesis.getStateRoot());

        AionBlock block = bundle.bc.createNewBlock(bundle.bc.getBestBlock(), Collections.emptyList(), false);
        assertThat(block).isNotNull();
        assertThat(block.getNumber()).isEqualTo(BLOCK_NUMBER + 1);
    }
}

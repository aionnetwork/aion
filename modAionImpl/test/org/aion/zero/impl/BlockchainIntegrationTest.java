/*******************************************************************************
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
 ******************************************************************************/
package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;

import org.aion.base.db.IRepository;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.ImportResult;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Blockchain integration test is for used to ensuring that each functionality within
 * the blockchain operates correctly.
 */
public class BlockchainIntegrationTest {

    public static byte[] TEST_COINBASE = ByteUtil.hexStringToBytes("CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3");
    public static byte[] TEST_EXTRADATA = "One ring to rule them all".getBytes();

    /**
     * Property test, simple test to verify that all variables
     * are set correctly and no null pointers occur.
     */
    @Test
    public void testSimpleBlockchainNullPointer() {
        /**
         * Mock Setups
         */
        StandaloneBlockchain bc = (new StandaloneBlockchain.Builder()).build().bc;

        // after instantiation, check that the following elements are set correctly
        // note that this is pre-best block setting
        assertThat(bc.getBestBlock()).isNotEqualTo(null);
        assertThat(bc.getBlockStore()).isNotEqualTo(null);
        assertThat(bc.getRepository()).isNotEqualTo(null);
        assertThat(bc.getTotalDifficulty()).isEqualTo(bc.getBestBlock().getCumulativeDifficulty());
        assertThat(bc.getTransactionStore()).isNotEqualTo(null);
        assertThat(bc.getMinerCoinbase()).isNotEqualTo(null);
    }

    // check that all accounts are loaded correctly
    @Test
    public void testSimpleBlockchainLoad() {
        StandaloneBlockchain.Bundle b = (new StandaloneBlockchain.Builder()).withDefaultAccounts().build();
        for (ECKey k : b.privateKeys) {
            assertThat(b.bc.getRepository().getBalance(Address.wrap(k.getAddress()))).isNotEqualTo(BigInteger.ZERO);
        }
        assertThat(b.privateKeys.size()).isEqualTo(10);
    }

    @Test
    public void testCreateNewEmptyBlock() {
        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder()).withDefaultAccounts().build();
        StandaloneBlockchain bc = bundle.bc;
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);
        assertThat(block.getParentHash()).isEqualTo(bc.getGenesis().getHash());
    }

    @Test
    public void testSimpleFailedTransactionInsufficientBalance() {
        // generate a recipient
        final Address receiverAddress = Address.wrap(ByteUtil.hexStringToBytes("CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE"));

        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder())
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();
        StandaloneBlockchain bc = bundle.bc;

        // (byte[] nonce, byte[] from, byte[] to, byte[] value, byte[] data)
        AionTransaction tx = new AionTransaction(
                BigInteger.valueOf(1).toByteArray(),
                receiverAddress,
                BigInteger.valueOf(100).toByteArray(),
                ByteUtil.EMPTY_BYTE_ARRAY,
                1L,
                1L);

        tx.sign(bundle.privateKeys.get(0));
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.singletonList(tx), true);

        assertThat(block.getTransactionsList()).isEmpty();
        assertThat(block.getTxTrieRoot()).isEqualTo(HashUtil.EMPTY_TRIE_HASH);

        ImportResult connection = bc.tryToConnect(block);
        assertThat(connection).isEqualTo(ImportResult.IMPORTED_BEST);
    }

    @Test
    public void testSimpleOneTokenBalanceTransfer() {
        // generate a recipient
        final Address receiverAddress = Address.wrap(ByteUtil.hexStringToBytes("CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE"));

        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder())
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();
        StandaloneBlockchain bc = bundle.bc;

        final ECKey sender = bundle.privateKeys.get(0);
        final BigInteger senderInitialBalance = bc.getRepository().getBalance(Address.wrap(sender.getAddress()));

        AionTransaction tx = new AionTransaction(
                BigInteger.valueOf(0).toByteArray(),
                receiverAddress,
                BigInteger.valueOf(100).toByteArray(),
                ByteUtil.EMPTY_BYTE_ARRAY,
                21000L,
                1L);
        tx.sign(sender);

        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.singletonList(tx), true);

        assertThat(block.getTransactionsList().size()).isEqualTo(1);
        assertThat(block.getTransactionsList().get(0)).isEqualTo(tx);

        ImportResult connection = bc.tryToConnect(block);
        assertThat(connection).isEqualTo(ImportResult.IMPORTED_BEST);

        // to be sure, perform some DB tests
        IRepository repo = bc.getRepository();

        assertThat(repo.getBalance(receiverAddress)).isEqualTo(BigInteger.valueOf(100));
        assertThat(repo.getBalance(Address.wrap(sender.getAddress())))
                .isEqualTo(senderInitialBalance.subtract(BigInteger
                        .valueOf(21000))
                        .subtract(BigInteger.valueOf(100)));
    }

    @Test
    public void testAppendIncorrectTimestampBlock() {
        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder())
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();
        StandaloneBlockchain bc = bundle.bc;
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);

        // set the block to be created 1 month in the future
        block.getHeader().setTimestamp((System.currentTimeMillis() / 1000) + 2592000);
        ImportResult result = bc.tryToConnect(block);
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    public void testPruningEnabledBalanceTransfer() {
        // generate a recipient
        final Address receiverAddress = Address.wrap(ByteUtil.hexStringToBytes("CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE"));

        // generate bc bundle with pruning enabled
        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder())
                .withBlockPruningEnabled()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();
        StandaloneBlockchain bc = bundle.bc;

        // desginate the first account in our list of private keys as the sender
        // (each key in the bundle is preloaded with balance)
        final ECKey sender = bundle.privateKeys.get(0);

        // generate transaction that transfers 100 tokens from sender to receiver
        // pk[0] -> receiverAddress
        AionTransaction tx = new AionTransaction(
                BigInteger.valueOf(0).toByteArray(),
                receiverAddress,
                BigInteger.valueOf(100).toByteArray(),
                ByteUtil.EMPTY_BYTE_ARRAY,
                21000L,
                1L);
        tx.sign(sender);

        // create a new block containing a single transaction (tx)
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.singletonList(tx), true);

        // import the block to our blockchain
        ImportResult connection = bc.tryToConnect(block);
        assertThat(connection).isEqualTo(ImportResult.IMPORTED_BEST);

        assertThat(bc.getRepository().getBalance(receiverAddress)).isEqualTo(BigInteger.valueOf(100));
    }

    /**
     * This is a special case for testing that the blockchain stores disconnected blocks
     * if we violate the contract of changing the block contents after importing best
     *
     * This behaviour was originally observed when running a pooled miner
     */
    @Test
    public void testDisconnection() {
        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder())
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();
        StandaloneBlockchain bc = bundle.bc;

        // store this as the best block,
        AionBlock block = bundle.bc.createNewBlock(bc.getGenesis(), Collections.emptyList(), true);
        byte[] block1Hash = block.getHash();

        // now modify this block to have a different value after connecting
        ImportResult result = bundle.bc.tryToConnect(block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // now modify the block in some way
        block.getHeader().setEnergyConsumed(42L);

        // now try submitting the block again
        result = bundle.bc.tryToConnect(block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        // now try creating a new block
        AionBlock newBlock = bundle.bc.createNewBlock(
                bundle.bc.getBestBlock(), Collections.emptyList(), true);

        // gets the first main-chain block
        AionBlock storedBlock1 = bundle.bc.getBlockStore().getChainBlockByNumber(1L);
        System.out.println(ByteUtil.toHexString(storedBlock1.getHash()));
        System.out.println(ByteUtil.toHexString(newBlock.getParentHash()));

        assertThat(newBlock.getParentHash()).isNotEqualTo(storedBlock1.getHash());
        assertThat(newBlock.getParentHash()).isEqualTo(block.getHash());
    }
}

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.trie.TrieImpl;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.BeforeClass;
import org.junit.Test;

/** @author Alexandra Roatis */
public class BlockchainDataRecoveryTest {

    private static final List<ECKey> accounts = BlockchainTestUtils.generateAccounts(10);
    private static final int NUMBER_OF_BLOCKS = 20;
    private static final int MAX_TX_PER_BLOCK = 60;

    @BeforeClass
    public static void setup() {
        // logging to see errors
        Map<String, String> cfg = new HashMap<>();
        cfg.put("DB", "ERROR");
        cfg.put("CONS", "INFO");

        AionLoggerFactory.init(cfg);
    }

    /** Test the recovery of the world state with start from the state of an ancestor block. */
    @Test
    public void testRecoverWorldStateWithPartialWorldState() {
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // first half of blocks will be correct
        long time = System.currentTimeMillis();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // second half of blocks will miss the state root
        List<byte[]> statesToDelete = new ArrayList<>();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            statesToDelete.add(context.block.getStateRoot());
            blocksToImport.add(context.block);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        // delete some world state root entries from the database
        TrieImpl trie = (TrieImpl) repo.getWorldState();
        IByteArrayKeyValueDatabase database = repo.getStateDatabase();

        // 1: direct recovery call

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverWorldState(repo, bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(worked).isTrue();
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();

        // 2: recovery at import

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // state missing before import
            assertThat(trie.isValidRoot(block.getStateRoot())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            // state present after import
            assertThat(trie.isValidRoot(block.getStateRoot())).isTrue();
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();
    }

    /** Test the recovery of the world state with start from the state of the genesis block. */
    @Test
    public void testRecoverWorldStateWithStartFromGenesis() {
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // all blocks will be incorrect
        long time = System.currentTimeMillis();
        List<byte[]> statesToDelete = new ArrayList<>();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            statesToDelete.add(context.block.getStateRoot());
            blocksToImport.add(context.block);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        // delete some world state root entries from the database
        TrieImpl trie = (TrieImpl) repo.getWorldState();
        IByteArrayKeyValueDatabase database = repo.getStateDatabase();

        // 1: direct recovery call

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverWorldState(repo, bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(worked).isTrue();
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();

        // 2: recovery at import

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // state missing before import
            assertThat(trie.isValidRoot(block.getStateRoot())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            // state present after import
            assertThat(trie.isValidRoot(block.getStateRoot())).isTrue();
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();
    }

    /**
     * Test the recovery of the world state when missing the genesis block state.
     *
     * <p>Under these circumstances the recovery will fail.
     */
    @Test
    public void testRecoverWorldStateWithoutGenesis() {
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // all blocks will be incorrect
        long time = System.currentTimeMillis();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            blocksToImport.add(context.block);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        // delete some world state root entries from the database
        TrieImpl trie = (TrieImpl) repo.getWorldState();
        IByteArrayKeyValueDatabase database = repo.getStateDatabase();

        // 1: direct recovery call

        repo.flush();
        List<byte[]> statesToDelete = new ArrayList<>();
        statesToDelete.addAll(database.keys());

        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverWorldState(repo, bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state was not recovered
        assertThat(worked).isFalse();
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // 2: recovery at import

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // state missing before import
            assertThat(trie.isValidRoot(block.getStateRoot())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            // state missing after import (because there is not genesis to recover from)
            assertThat(trie.isValidRoot(block.getStateRoot())).isFalse();
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();
    }

    @Test
    public void testRecoverWorldState_wNullBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        repo.flush();

        assertThat(chain.recoverWorldState(repo, null)).isFalse();
    }

    @Test
    public void testRecoverWorldState_wRepositorySnapshot() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();

        BlockContext context;
        List<AionTransaction> txs;

        // all blocks will be incorrect
        long time = System.currentTimeMillis();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }
        repo.flush();

        assertThat(chain.recoverWorldState(repo.getSnapshotTo(repo.getRoot()), chain.getBestBlock()))
                .isFalse();
    }

    @Test
    public void testRecoverWorldState_wDeletedBlock() {
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // all blocks will be incorrect
        long time = System.currentTimeMillis();
        List<byte[]> statesToDelete = new ArrayList<>();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            statesToDelete.add(context.block.getStateRoot());
            // skipping middle block
            if (context.block.getNumber() != NUMBER_OF_BLOCKS / 2) {
                blocksToImport.add(context.block);
            }
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        // delete middle block from db
        AionBlock middle = chain.getBlockByNumber(NUMBER_OF_BLOCKS / 2);
        repo.getBlockDatabase().delete(middle.getHash());

        // delete some world state root entries from the database
        TrieImpl trie = (TrieImpl) repo.getWorldState();
        IByteArrayKeyValueDatabase database = repo.getStateDatabase();

        // 1: direct recovery call

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality
        assertThat(chain.recoverWorldState(repo, bestBlock)).isFalse();

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state could not be recovered
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // 2: recovery at import

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // state missing before import
            assertThat(trie.isValidRoot(block.getStateRoot())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            if (block.getNumber() < middle.getNumber()) {
                // state present after import
                assertThat(trie.isValidRoot(block.getStateRoot())).isTrue();
            } else {
                // block after missing one cannot be recovered
                assertThat(trie.isValidRoot(block.getStateRoot())).isFalse();
            }
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // importing middle block
        assertThat(chain.tryToConnect(middle)).isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // checking only failed blocks from before
            if (block.getNumber() > middle.getNumber()) {
                // state missing before import
                assertThat(trie.isValidRoot(block.getStateRoot())).isFalse();
                assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
                // state present after import
                assertThat(trie.isValidRoot(block.getStateRoot())).isTrue();
            }
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();
    }

    /** Test the recovery of the index with start from the index of an ancestor block. */
    @Test
    public void testRecoverIndexWithPartialIndex_MainChain() {
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // first half of blocks will be correct
        long time = System.currentTimeMillis();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // second half of blocks will miss the index
        Map<Long, byte[]> blocksToDelete = new HashMap<>();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            blocksToImport.add(context.block);
            blocksToDelete.put(context.block.getNumber(), context.block.getHash());
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        // delete index entries from the database
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // 1: direct recovery call

        repo.flush();
        Map<Long, byte[]> deletedInfo = new HashMap<>();
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // saving the data for checking recovery
            deletedInfo.put(entry.getKey(), indexDatabase.get(indexKey).get());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverIndexEntry(repo, bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(worked).isTrue();
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isTrue();

        // check that the index information is correct
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            long level = entry.getKey();
            byte[] hash = entry.getValue();
            // checking at block store level
            assertThat(repo.isIndexed(hash, level)).isTrue();
            // checking at database level
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            assertThat(deletedInfo.get(level)).isEqualTo(indexDatabase.get(indexKey).get());
        }

        // ensure the size key is correct
        byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();

        // 2: recovery at import

        repo.flush();
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // index missing before import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            // index present after import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isTrue();
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isTrue();

        // check that the index information is correct at database level
        for (Long key : blocksToDelete.keySet()) {
            // checking at database level
            byte[] indexKey = ByteUtil.intToBytes(key.intValue());
            assertThat(deletedInfo.get(key)).isEqualTo(indexDatabase.get(indexKey).get());
        }

        // ensure the size key is correct
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();
    }

    /** Test the recovery of the index with start from the index of an ancestor block. */
    @Test
    public void testRecoverIndexWithPartialIndex_ShorterSideChain() {
        final int EVEN_NUMBER_OF_BLOCKS =
                NUMBER_OF_BLOCKS % 2 == 0 ? NUMBER_OF_BLOCKS : NUMBER_OF_BLOCKS + 1;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // adding common blocks
        long time = System.currentTimeMillis();
        for (int i = 0; i < EVEN_NUMBER_OF_BLOCKS / 2 - 1; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // splitting chains
        AionBlock mainChainBlock, sideChainBlock;
        mainChainBlock = chain.getBestBlock();
        sideChainBlock = mainChainBlock;

        txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
        context = chain.createNewBlockInternal(mainChainBlock, txs, true, time / 10000L);
        assertThat(chain.tryToConnectInternal(context.block, (time + 10)))
                .isEqualTo(ImportResult.IMPORTED_BEST);
        mainChainBlock = context.block;

        // starting side chain
        txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
        context = chain.createNewBlockInternal(sideChainBlock, txs, true, time / 10000L);
        context.block.setExtraData("other".getBytes());
        assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                .isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        sideChainBlock = context.block;

        // building chains; sidechain will have missing index
        Map<Long, byte[]> blocksToDelete = new HashMap<>();
        List<AionBlock> blocksToImportMainChain = new ArrayList<>();
        List<AionBlock> blocksToImportSideChain = new ArrayList<>();
        for (int i = 0; i < EVEN_NUMBER_OF_BLOCKS / 2 - 1; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(mainChainBlock, txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time + 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            mainChainBlock = context.block;
            blocksToImportMainChain.add(context.block);

            // adding side chain
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(sideChainBlock, txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_NOT_BEST);
            sideChainBlock = context.block;
            blocksToDelete.put(sideChainBlock.getNumber(), sideChainBlock.getHash());
            blocksToImportSideChain.add(context.block);
        }

        // making the main chain longer
        txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
        context = chain.createNewBlockInternal(mainChainBlock, txs, true, time / 10000L);
        assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                .isEqualTo(ImportResult.IMPORTED_BEST);
        mainChainBlock = context.block;

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(EVEN_NUMBER_OF_BLOCKS);
        assertThat(bestBlock.getHash()).isEqualTo(mainChainBlock.getHash());

        // delete index entries from the database
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // 1: direct recovery call

        repo.flush();
        Map<Long, byte[]> deletedInfo = new HashMap<>();
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // saving the data for checking recovery
            deletedInfo.put(entry.getKey(), indexDatabase.get(indexKey).get());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // call the recovery functionality for the main chain subsection
        boolean worked =
                chain.recoverIndexEntry(repo, chain.getBlockByHash(mainChainBlock.getParentHash()));

        // ensure that the index was corrupted only for the side chain
        assertThat(repo.isIndexed(sideChainBlock.getHash(), sideChainBlock.getNumber())).isFalse();
        assertThat(repo.isIndexed(mainChainBlock.getHash(), mainChainBlock.getNumber())).isTrue();
        assertThat(worked).isTrue();

        // call the recovery functionality
        worked = chain.recoverIndexEntry(repo, sideChainBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(worked).isTrue();
        assertThat(repo.isIndexed(sideChainBlock.getHash(), sideChainBlock.getNumber())).isTrue();

        // check that the index information is correct
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            long level = entry.getKey();
            byte[] hash = entry.getValue();
            // checking at block store level
            assertThat(repo.isIndexed(hash, level)).isTrue();
            // checking at database level
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // NOTE: this checks the correction of both main chain and side chain recovery
            assertThat(deletedInfo.get(level)).isEqualTo(indexDatabase.get(indexKey).get());
        }

        // ensure the size key is correct
        byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();

        // 2: recovery at import

        repo.flush();
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // call the recovery functionality indirectly for main chain
        for (AionBlock block : blocksToImportMainChain) {
            // index missing before import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            // index present after import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isTrue();
        }

        // ensure that the index was corrupted only for the side chain
        assertThat(repo.isIndexed(sideChainBlock.getHash(), sideChainBlock.getNumber())).isFalse();
        assertThat(repo.isIndexed(mainChainBlock.getHash(), mainChainBlock.getNumber())).isTrue();

        // call the recovery functionality indirectly for side chain
        for (AionBlock block : blocksToImportSideChain) {
            // index missing before import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            // index present after import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isTrue();
        }

        // ensure that the index was corrupted only for the side chain
        assertThat(repo.isIndexed(sideChainBlock.getHash(), sideChainBlock.getNumber())).isTrue();
        assertThat(repo.isIndexed(mainChainBlock.getHash(), mainChainBlock.getNumber())).isTrue();

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());

        // check that the index information is correct at database level
        for (Long key : blocksToDelete.keySet()) {
            // checking at database level
            byte[] indexKey = ByteUtil.intToBytes(key.intValue());
            // NOTE: this checks the correction of both main chain and side chain recovery
            assertThat(deletedInfo.get(key)).isEqualTo(indexDatabase.get(indexKey).get());
        }

        // ensure the size key is correct
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();
    }

    /** Test the index recovery when the index database contains only the size and genesis index. */
    @Test
    public void testRecoverIndexWithStartFromGenesis() {
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // all blocks will be incorrect
        long time = System.currentTimeMillis();
        Map<Long, byte[]> blocksToDelete = new HashMap<>();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            blocksToDelete.put(context.block.getNumber(), context.block.getHash());
            blocksToImport.add(context.block);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        // delete index entries from the database
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // 1: direct recovery call

        repo.flush();
        Map<Long, byte[]> deletedInfo = new HashMap<>();
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // saving the data for checking recovery
            deletedInfo.put(entry.getKey(), indexDatabase.get(indexKey).get());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverIndexEntry(repo, bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(worked).isTrue();
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isTrue();

        // check that the index information is correct
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            long level = entry.getKey();
            byte[] hash = entry.getValue();
            // checking at block store level
            assertThat(repo.isIndexed(hash, level)).isTrue();
            // checking at database level
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            assertThat(deletedInfo.get(level)).isEqualTo(indexDatabase.get(indexKey).get());
        }

        // ensure the size key is correct
        byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();

        // 2: recovery at import

        repo.flush();
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // index missing before import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            // index present after import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isTrue();
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isTrue();

        // check that the index information is correct at database level
        for (Long key : blocksToDelete.keySet()) {
            // checking at database level
            byte[] indexKey = ByteUtil.intToBytes(key.intValue());
            assertThat(deletedInfo.get(key)).isEqualTo(indexDatabase.get(indexKey).get());
        }

        // ensure the size key is correct
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();
    }

    /**
     * Test the index recovery when the index database is empty.
     *
     * <p>Under these circumstances the recovery process will fail.
     */
    @Test
    public void testRecoverIndexWithoutGenesis() {
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        long time = System.currentTimeMillis();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            blocksToImport.add(context.block);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // 1: direct recovery call

        repo.flush();
        // deleting the entire index database
        indexDatabase.drop();

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverIndexEntry(repo, bestBlock);

        // ensure that the best block is unchanged
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // check that the index recovery failed
        assertThat(worked).isFalse();
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // 2: recovery at import

        repo.flush();
        // deleting the entire index database
        indexDatabase.drop();

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // index missing before import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            // index missing after import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
        }

        // ensure that the best block is unchanged
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // check that the index recovery failed
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();
    }

    /**
     * Test the index recovery when the index database contains only the genesis index and is
     * missing the size key.
     */
    @Test
    public void testRecoverIndexWithStartFromGenesisWithoutSize() {
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // all blocks will be incorrect
        long time = System.currentTimeMillis();
        Map<Long, byte[]> blocksToDelete = new HashMap<>();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            blocksToDelete.put(context.block.getNumber(), context.block.getHash());
            blocksToImport.add(context.block);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        // delete index entries from the database
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // 1: direct recovery call

        repo.flush();
        Map<Long, byte[]> deletedInfo = new HashMap<>();

        byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
        deletedInfo.put(-1L, indexDatabase.get(sizeKey).get());
        indexDatabase.delete(sizeKey);

        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // saving the data for checking recovery
            deletedInfo.put(entry.getKey(), indexDatabase.get(indexKey).get());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverIndexEntry(repo, bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(worked).isTrue();
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isTrue();

        // ensure the size key was recovered
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();
        assertThat(indexDatabase.get(sizeKey).get()).isEqualTo(deletedInfo.get(-1L));
        deletedInfo.remove(-1L);

        // check that the index information is correct
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            long level = entry.getKey();
            byte[] hash = entry.getValue();
            // checking at block store level
            assertThat(repo.isIndexed(hash, level)).isTrue();
            // checking at database level
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            assertThat(deletedInfo.get(level)).isEqualTo(indexDatabase.get(indexKey).get());
        }

        // 2: recovery at import

        repo.flush();
        deletedInfo.put(-1L, indexDatabase.get(sizeKey).get());
        indexDatabase.delete(sizeKey);

        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // index missing before import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            // index present after import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isTrue();
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isTrue();

        // ensure the size key was recovered
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();
        assertThat(indexDatabase.get(sizeKey).get()).isEqualTo(deletedInfo.get(-1L));
        deletedInfo.remove(-1L);

        // check that the index information is correct at database level
        for (Long key : blocksToDelete.keySet()) {
            // checking at database level
            byte[] indexKey = ByteUtil.intToBytes(key.intValue());
            assertThat(deletedInfo.get(key)).isEqualTo(indexDatabase.get(indexKey).get());
        }
    }

    @Test
    public void testRecoverIndex_wNullBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        repo.flush();

        assertThat(chain.recoverIndexEntry(repo, null)).isFalse();
    }

    @Test
    public void testRecoverIndex_wRepositorySnapshot() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();

        BlockContext context;
        List<AionTransaction> txs;

        // all blocks will be incorrect
        long time = System.currentTimeMillis();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }
        repo.flush();

        assertThat(chain.recoverIndexEntry(repo.getSnapshotTo(repo.getRoot()), chain.getBestBlock()))
                .isFalse();
    }

    @Test
    public void testRecoverIndex_wDeletedBlock() {
        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // all blocks will be incorrect
        long time = System.currentTimeMillis();
        Map<Long, byte[]> blocksToDelete = new HashMap<>();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            blocksToDelete.put(context.block.getNumber(), context.block.getHash());
            // skipping middle block
            if (context.block.getNumber() != NUMBER_OF_BLOCKS / 2) {
                blocksToImport.add(context.block);
            }
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        // delete middle block from db
        AionBlock middle = chain.getBlockByNumber(NUMBER_OF_BLOCKS / 2);
        repo.getBlockDatabase().delete(middle.getHash());

        // delete index entries from the database
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // 1: direct recovery call

        repo.flush();
        Map<Long, byte[]> deletedInfo = new HashMap<>();
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // saving the data for checking recovery
            deletedInfo.put(entry.getKey(), indexDatabase.get(indexKey).get());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality
        assertThat(chain.recoverIndexEntry(repo, bestBlock)).isFalse();

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // ensure the size key is correct
        byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();

        // 2: recovery at import

        repo.flush();
        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            byte[] indexKey = ByteUtil.intToBytes(entry.getKey().intValue());
            // deleting the block info
            indexDatabase.delete(indexKey);
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // index missing before import
            assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
            assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
            if (block.getNumber() < middle.getNumber()) {
                // index present after import
                assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isTrue();
            } else {
                // blocks after missing one cannot be recovered
                assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
            }
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // importing middle block
        assertThat(chain.tryToConnect(middle)).isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        // call the recovery functionality indirectly
        for (AionBlock block : blocksToImport) {
            // checking only failed blocks from before
            if (block.getNumber() > middle.getNumber()) {
                // index missing before import
                assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isFalse();
                assertThat(chain.tryToConnect(block)).isEqualTo(ImportResult.EXIST);
                // index present after import
                assertThat(repo.isIndexed(block.getHash(), block.getNumber())).isTrue();
            }
        }

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isTrue();

        // check that the index information is correct at database level
        for (Long key : blocksToDelete.keySet()) {
            // checking at database level
            byte[] indexKey = ByteUtil.intToBytes(key.intValue());
            assertThat(deletedInfo.get(key)).isEqualTo(indexDatabase.get(indexKey).get());
        }

        // ensure the size key is correct
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();
    }
}

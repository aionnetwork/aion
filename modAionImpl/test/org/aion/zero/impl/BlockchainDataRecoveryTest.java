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

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.trie.TrieImpl;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static com.google.common.truth.Truth.assertThat;

// import org.aion.mcf.trie.JournalPruneDataSource;

/**
 * @author Alexandra Roatis
 */
public class BlockchainDataRecoveryTest {

    @BeforeClass
    public static void setup() {
        // logging to see errors
        Map<String, String> cfg = new HashMap<>();
        cfg.put("DB", "ERROR");
        cfg.put("CONS", "INFO");

        AionLoggerFactory.init(cfg);
    }

    /**
     * Test the recovery of the world state with start from the state of an ancestor block.
     */
    @Test
    public void testRecoverWorldStateWithPartialWorldState() {
        final int NUMBER_OF_BLOCKS = 6;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        // first half of blocks will be correct
        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            result = chain.tryToConnect(chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true));
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // second half of blocks will miss the state root
        List<byte[]> statesToDelete = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            AionBlock next = chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
            statesToDelete.add(next.getStateRoot());
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        // delete some world state root entries from the database
        TrieImpl trie = (TrieImpl) ((AionRepositoryImpl) chain.getRepository()).getWorldState();
        IByteArrayKeyValueDatabase database = (IByteArrayKeyValueDatabase) trie.getCache().getDb();

        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverWorldState(chain.getRepository(), bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(worked).isTrue();
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();
    }

    /**
     * Test the recovery of the world state with start from the state of the genesis block.
     */
    @Test
    public void testRecoverWorldStateWithStartFromGenesis() {
        final int NUMBER_OF_BLOCKS = 10;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        // all blocks will be incorrect
        ImportResult result;
        List<byte[]> statesToDelete = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            AionBlock next = chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
            statesToDelete.add(next.getStateRoot());
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();
        // System.out.println(Hex.toHexString(chain.getRepository().getRoot()));

        // delete some world state root entries from the database
        TrieImpl trie = (TrieImpl) ((AionRepositoryImpl) chain.getRepository()).getWorldState();
        IByteArrayKeyValueDatabase database = (IByteArrayKeyValueDatabase) trie.getCache().getDb();

        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }
        // System.out.println(Hex.toHexString(chain.getRepository().getRoot()));

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverWorldState(chain.getRepository(), bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(worked).isTrue();
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();
    }

    /**
     * Test the recovery of the world state when missing the genesis block state.
     *
     * Under these circumstances the recovery will fail.
     */
    @Test
    public void testRecoverWorldStateWithoutGenesis() {
        final int NUMBER_OF_BLOCKS = 3;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        // all blocks will be incorrect
        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            AionBlock next = chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        // delete some world state root entries from the database
        TrieImpl trie = (TrieImpl) ((AionRepositoryImpl) chain.getRepository()).getWorldState();
        IByteArrayKeyValueDatabase database = (IByteArrayKeyValueDatabase) trie.getCache().getDb();

        List<byte[]> statesToDelete = new ArrayList<>();
        statesToDelete.addAll(database.keys());

        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverWorldState(chain.getRepository(), bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state was not recovered
        assertThat(worked).isFalse();
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();
    }

    /**
     * Test the recovery of the index with start from the index of an ancestor block.
     */
    @Test
    public void testRecoverIndexWithPartialIndex_MainChain() {
        final int NUMBER_OF_BLOCKS = 6;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        // first half of blocks will be correct
        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            result = chain.tryToConnect(chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true));
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // second half of blocks will miss the index
        Map<Long, byte[]> blocksToDelete = new HashMap<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            AionBlock next = chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
            blocksToDelete.put(next.getNumber(), next.getHash());
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        // delete index entries from the database
        AionRepositoryImpl repo = (AionRepositoryImpl) chain.getRepository();
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            indexDatabase.delete(ByteUtil.intToBytes(entry.getKey().intValue()));
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverIndexEntry(chain.getRepository(), bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(worked).isTrue();
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isTrue();
        // ensure the size key is correct
        byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();
    }

    /**
     * Test the recovery of the index with start from the index of an ancestor block.
     */
    @Test
    public void testRecoverIndexWithPartialIndex_ShorterSideChain() {
        // should be even number
        final int NUMBER_OF_BLOCKS = 8;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        // adding common blocks
        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2 - 1; i++) {
            result = chain.tryToConnect(chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true));
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // splitting chains
        AionBlock mainChainBlock, sideChainBlock;
        mainChainBlock = chain.getBestBlock();
        sideChainBlock = mainChainBlock;

        AionBlock next = chain.createNewBlock(mainChainBlock, Collections.emptyList(), true);
        result = chain.tryToConnect(next);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        mainChainBlock = next;

        // starting side chain
        next = chain.createNewBlock(sideChainBlock, Collections.emptyList(), true);
        next.setExtraData("other".getBytes());
        result = chain.tryToConnect(next);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        sideChainBlock = next;

        // building chains; sidechain will have missing index
        Map<Long, byte[]> blocksToDelete = new HashMap<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2 - 1; i++) {
            next = chain.createNewBlock(mainChainBlock, Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
            mainChainBlock = next;

            // adding side chain
            next = chain.createNewBlock(sideChainBlock, Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
            sideChainBlock = next;
            blocksToDelete.put(next.getNumber(), next.getHash());
        }

        // making the main chain longer
        next = chain.createNewBlock(mainChainBlock, Collections.emptyList(), true);
        result = chain.tryToConnect(next);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        mainChainBlock = next;

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);
        assertThat(bestBlock.getHash()).isEqualTo(mainChainBlock.getHash());

        chain.getRepository().flush();

        // delete index entries from the database
        AionRepositoryImpl repo = (AionRepositoryImpl) chain.getRepository();
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            indexDatabase.delete(ByteUtil.intToBytes(entry.getKey().intValue()));
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(sideChainBlock.getHash(), sideChainBlock.getNumber())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverIndexEntry(chain.getRepository(), sideChainBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(worked).isTrue();
        assertThat(repo.isIndexed(sideChainBlock.getHash(), sideChainBlock.getNumber())).isTrue();
        // ensure the size key is correct
        byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();
    }

    /**
     * Test the index recovery when the index database contains only the size and genesis index.
     */
    @Test
    public void testRecoverIndexWithStartFromGenesis() {
        final int NUMBER_OF_BLOCKS = 3;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        // all blocks will be incorrect
        ImportResult result;
        Map<Long, byte[]> blocksToDelete = new HashMap<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            AionBlock next = chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
            blocksToDelete.put(next.getNumber(), next.getHash());
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        // delete index entries from the database
        AionRepositoryImpl repo = (AionRepositoryImpl) chain.getRepository();
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        for (Map.Entry<Long, byte[]> entry : blocksToDelete.entrySet()) {
            indexDatabase.delete(ByteUtil.intToBytes(entry.getKey().intValue()));
            // ensure that the index was corrupted
            assertThat(repo.isIndexed(entry.getValue(), entry.getKey())).isFalse();
        }

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverIndexEntry(chain.getRepository(), bestBlock);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the index was recovered
        assertThat(worked).isTrue();
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isTrue();
        // ensure the size key is correct
        byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
        assertThat(indexDatabase.get(sizeKey).isPresent()).isTrue();
    }

    /**
     * Test the index recovery when the index database is empty.
     *
     * Under these circumstances the recovery process will fail.
     */
    @Test
    public void testRecoverIndexWithoutGenesis() {
        final int NUMBER_OF_BLOCKS = 3;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            AionBlock next = chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        AionRepositoryImpl repo = (AionRepositoryImpl) chain.getRepository();
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // deleting the entire index database
        indexDatabase.drop();

        // ensure that the index was corrupted
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();

        // call the recovery functionality
        boolean worked = chain.recoverIndexEntry(chain.getRepository(), bestBlock);

        // ensure that the best block is unchanged
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // check that the index recovery failed
        assertThat(worked).isFalse();
        assertThat(repo.isIndexed(bestBlock.getHash(), bestBlock.getNumber())).isFalse();
    }
}

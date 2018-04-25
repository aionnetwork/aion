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
import org.aion.mcf.core.ImportResult;
// import org.aion.mcf.trie.JournalPruneDataSource;
import org.aion.mcf.trie.TrieImpl;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * @author Alexandra Roatis
 */
public class BlockchainDataRecoveryTest {

    /**
     * Test the recovery of the world state in the case where it is missing from the database.
     */
    @Test
    public void testRecoverWorldStateWithPartialWorldState() {
        final int NUMBER_OF_BLOCKS = 10;

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
     * Test the recovery of the world state in the case where it is missing from the database.
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
     * Test the recovery of the world state in the case where it is missing from the database.
     */
    @Test
    public void testRecoverWorldStateWithoutGenesis() {
        final int NUMBER_OF_BLOCKS = 10;

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
}

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

import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;

public class BlockchainForkingTest {

    /*-
     * Tests the case where multiple threads submit a single block (content) but
     * with different mining nonces and solutions. In this case our rules dictate
     * that all subsequent blocks are considered invalid.
     *
     *          (common ancestor)
     *          /               \
     *         /                 \
     *        /                   \
     *       (a)o                 (b)x
     *
     * Given:
     * a.td == b.td
     */
    @Test
    public void testSameBlockDifferentNonceAndSolutionSimple() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle b = builder
                .withValidatorConfiguration("simple")
                .build();

        StandaloneBlockchain bc = b.bc;
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.emptyList(), true);
        AionBlock sameBlock = new AionBlock(block.getEncoded());

        ImportResult firstRes = bc.tryToConnect(block);

        // check that the returned block is the first block
        assertThat(bc.getBestBlock() == block).isTrue();

        ImportResult secondRes = bc.tryToConnect(sameBlock);

        // the second block should get rejected, so check that the reference still refers
        // to the first block (we dont change the published reference)
        assertThat(bc.getBestBlock() == block).isTrue();

        assertThat(firstRes).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(secondRes).isEqualTo(ImportResult.EXIST);
    }

    /*-
     * Test the general forking case, where an incoming block (b) has a greater total
     * difficulty than our current block. In this scenario, we should switch to
     * the branch (sub-tree) that has (b) at its tip.
     *
     * This is the simplest case, where the distance between (a) (our current head) and
     * (b) is 2. This implies that the common ancestor is directly adjacent to both blocks.
     *
     *          (common ancestor)
     *          /               \
     *         /                 \
     *        /                   \
     *       (a)x(low td)           (b)o(higher td)
     *
     * In this simple case:
     * b.td > a.td
     * a_worldState === b_worldState
     *
     */
    @Test
    public void testHigherDifficultyBlockFork() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle b = builder
                .withValidatorConfiguration("simple")
                .build();

        StandaloneBlockchain bc = b.bc;
        AionBlock bestBlock = bc.getBestBlock();
        AionBlock standardBlock = bc.createNewBlock(bc.getBestBlock(), Collections.emptyList(), true);

        ChainConfiguration cc = new ChainConfiguration();
        AionBlock higherDifficultyBlock = new AionBlock(standardBlock);
        higherDifficultyBlock.getHeader().setTimestamp(bestBlock.getTimestamp() + 1);

        BigInteger difficulty = cc
                .getDifficultyCalculator()
                .calculateDifficulty(
                        higherDifficultyBlock.getHeader(),
                        bestBlock.getHeader());

        assertThat(difficulty).isGreaterThan(standardBlock.getDifficultyBI());
        higherDifficultyBlock.getHeader().setDifficulty(difficulty.toByteArray());

        System.out.println("before any processing: " + new ByteArrayWrapper(bc.getRepository().getRoot()));
        System.out.println("trie: " + ((AionRepositoryImpl) bc.getRepository()).getWorldState().getTrieDump());

        ImportResult result = bc.tryToConnect(standardBlock);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // assert that the block we just inserted (best) is the instance that is returned
        assertThat(bc.getBestBlock() == standardBlock).isTrue();

        System.out.println(new ByteArrayWrapper(bc.getRepository().getRoot()));

        ImportResult higherDifficultyResult = bc.tryToConnect(higherDifficultyBlock);

        assertThat(higherDifficultyResult).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(bc.getBestBlockHash()).isEqualTo(higherDifficultyBlock.getHash());

        // the object reference here is intentional
        assertThat(bc.getBestBlock() == higherDifficultyBlock).isTrue();
    }
}

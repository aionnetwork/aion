/**
 * *****************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * The aion network project leverages useful source code from other open source projects. We
 * greatly appreciate the effort that was invested in these projects and we thank the individual
 * contributors for their work. For provenance information and contributors please see
 * <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 * <ether.camp> team through the ethereumJ library.
 * Ether.Camp Inc.
 * (US) team through Ethereum Harmony.
 * John Tromp through the Equihash solver.
 * Samuel Neves through the BLAKE2 implementation.
 * Zcash project team.
 * Bitcoinj team.
 * ****************************************************************************
 */
package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.base.util.BIUtil;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

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
        StandaloneBlockchain.Bundle b = builder.withValidatorConfiguration("simple").build();

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
    public void testInvalidFirstBlockDifficulty() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle b = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain bc = b.bc;
        AionBlock bestBlock = bc.getBestBlock();
        AionBlock standardBlock =
                bc.createNewBlock(bc.getBestBlock(), Collections.emptyList(), true);

        ChainConfiguration cc = new ChainConfiguration();
        AionBlock higherDifficultyBlock = new AionBlock(standardBlock);
        higherDifficultyBlock.getHeader().setTimestamp(bestBlock.getTimestamp() + 1);

        BigInteger difficulty =
                cc.getDifficultyCalculator()
                        .calculateDifficulty(
                                higherDifficultyBlock.getHeader(), bestBlock.getHeader());

        assertThat(difficulty).isGreaterThan(standardBlock.getDifficultyBI());
        higherDifficultyBlock.getHeader().setDifficulty(difficulty.toByteArray());

        System.out.println("before any processing: " + new ByteArrayWrapper(bc.getRepository().getRoot()));
        System.out.println("trie: " + bc.getRepository().getWorldState().getTrieDump());

        ImportResult result = bc.tryToConnect(standardBlock);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // assert that the block we just inserted (best) is the instance that is returned
        assertThat(bc.getBestBlock() == standardBlock).isTrue();

        System.out.println(new ByteArrayWrapper(bc.getRepository().getRoot()));

        ImportResult higherDifficultyResult = bc.tryToConnect(higherDifficultyBlock);

        /**
         * With our updates to difficulty verification and calculation, this block is now invalid
         */
        assertThat(higherDifficultyResult).isEqualTo(ImportResult.INVALID_BLOCK);
        assertThat(bc.getBestBlockHash()).isEqualTo(standardBlock.getHash());

        // the object reference here is intentional
        assertThat(bc.getBestBlock() == standardBlock).isTrue();

        // check for correct state rollback
        assertThat(bc.getRepository().getRoot()).isEqualTo(standardBlock.getStateRoot());
        assertThat(bc.getTotalDifficulty())
                .isEqualTo(bc.getRepository().getBlockStore().getTotalDifficultyForHash(standardBlock.getHash()));

    }

    /*-
     *
     * Recall previous forking logic worked as follows:
     *
     *          [ parent block ]
     *          /              \
     *         /                \
     *        /                  \
     *  [block_1]              [block_2]
     *  TD=101                 TD=102
     *
     * Where if block_1 had a greater timestamp (and thus lower TD) than
     * block_2, then block_2 would be accepted as the best block for
     * that particular level (until later re-orgs prove this to be untrue)
     *
     * With our changes to difficulty calculations, difficulty is calculated
     * with respect to the two previous blocks (parent, grandParent) blocks
     * in the sequence.
     *
     * This leads to the following:
     *
     *          [ parent block - 1] TD = 50
     *                  |
     *                  |
     *          [ parent block ] D = 50
     *          /              \
     *         /                \
     *        /                  \
     *    [block_1]            [block_2]
     *    TD=100               TD=100
     *
     * Where both blocks are guaranteed to have the same TD if they directly
     * branch off of the same parent. In fact, this guarantees us that the
     * first block coming in from the network (for a particular level) is
     * the de-facto best block for a short period of time.
     *
     * It is only when the block after comes in (for both chains) that a re-org
     * will happen on one of the chains (possibly)
     *
     *
     *             ...prev
     *   [block_1]              [block_2]
     *   T(n) = T(n-1) + 4      T(n) = T(n-1) + 20
     *       |                         |
     *       |                         |
     *       |                         |
     *   [block_1_2]            [block_1_2]
     *   TD = 160               TD = 140
     *
     * At which point a re-org should occur on most blocks. Remember that this reorg
     * is a minimum, given equal hashing power and particularily bad luck, two parties
     * could indefinitely stay on their respective chains, but the chances of this is
     * extraordinarily small.
     */
    @Test
    public void testSecondBlockHigherDifficultyFork() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts().build();

        long time = System.currentTimeMillis();

        StandaloneBlockchain bc = bundle.bc;
        List<ECKey> accs = bundle.privateKeys;

        // generate three blocks, on the third block we get flexibility
        // for what difficulties can occur

        BlockContext firstBlock =
                bc.createNewBlockInternal(
                        bc.getGenesis(), Collections.emptyList(), true, time / 1000L);
        assertThat(bc.tryToConnectInternal(firstBlock.block, (time += 10)))
                .isEqualTo(ImportResult.IMPORTED_BEST);

        // now connect the second block
        BlockContext secondBlock =
                bc.createNewBlockInternal(
                        firstBlock.block, Collections.emptyList(), true, time / 1000L);
        assertThat(bc.tryToConnectInternal(secondBlock.block, time += 10))
                .isEqualTo(ImportResult.IMPORTED_BEST);

        // now on the third block, we diverge with one block having higher TD than the other
        BlockContext fasterSecondBlock =
                bc.createNewBlockInternal(
                        secondBlock.block, Collections.emptyList(), true, time / 1000L);
        AionBlock slowerSecondBlock = new AionBlock(fasterSecondBlock.block);

        slowerSecondBlock.getHeader().setTimestamp(time / 1000L + 100);

        assertThat(bc.tryToConnectInternal(fasterSecondBlock.block, time + 100))
                .isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(bc.tryToConnectInternal(slowerSecondBlock, time + 100))
                .isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        // represents the amount of time we would have waited for the lower TD block to come in
        long timeDelta = 1000L;

        // loweredDifficulty = bi - bi / 1024
        BigInteger loweredDifficulty =
                BIUtil.max(
                        secondBlock
                                .block
                                .getDifficultyBI()
                                .subtract(
                                        secondBlock
                                                .block
                                                .getDifficultyBI()
                                                .divide(BigInteger.valueOf(1024L))),
                        BigInteger.valueOf(16L));

        time += 100;

        BlockContext fastBlockDescendant =
                bc.createNewBlockInternal(
                        fasterSecondBlock.block, Collections.emptyList(), true, time / 1000L);
        BlockContext slowerBlockDescendant =
                bc.createNewBlockInternal(
                        slowerSecondBlock, Collections.emptyList(), true, time / 1000L + 100 + 1);

        // increment by another hundred (this is supposed to be when the slower block descendant is
        // completed)
        time += 100;

        assertThat(fastBlockDescendant.block.getDifficultyBI())
                .isGreaterThan(slowerBlockDescendant.block.getDifficultyBI());
        System.out.println(
                "faster block descendant TD: " + fastBlockDescendant.block.getDifficultyBI());
        System.out.println(
                "slower block descendant TD: " + slowerBlockDescendant.block.getDifficultyBI());

        assertThat(bc.tryToConnectInternal(slowerBlockDescendant.block, time))
                .isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(bc.tryToConnectInternal(fastBlockDescendant.block, time))
                .isEqualTo(ImportResult.IMPORTED_BEST);

        assertThat(bc.getBestBlock()).isEqualTo(fastBlockDescendant.block);
    }

    /**
     * Test fork with exception.
     */
    @Test
    public void testSecondBlockHigherDifficultyFork_wExceptionOnFasterBlockAdd() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").withDefaultAccounts().build();

        long time = System.currentTimeMillis();

        StandaloneBlockchain bc = bundle.bc;

        // generate three blocks, on the third block we get flexibility
        // for what difficulties can occur

        BlockContext firstBlock = bc
                .createNewBlockInternal(bc.getGenesis(), Collections.emptyList(), true, time / 1000L);
        assertThat(bc.tryToConnectInternal(firstBlock.block, (time += 10))).isEqualTo(ImportResult.IMPORTED_BEST);

        // now connect the second block
        BlockContext secondBlock = bc
                .createNewBlockInternal(firstBlock.block, Collections.emptyList(), true, time / 1000L);
        assertThat(bc.tryToConnectInternal(secondBlock.block, time += 10)).isEqualTo(ImportResult.IMPORTED_BEST);

        // now on the third block, we diverge with one block having higher TD than the other
        BlockContext fasterSecondBlock = bc
                .createNewBlockInternal(secondBlock.block, Collections.emptyList(), true, time / 1000L);
        AionBlock slowerSecondBlock = new AionBlock(fasterSecondBlock.block);

        slowerSecondBlock.getHeader().setTimestamp(time / 1000L + 100);

        assertThat(bc.tryToConnectInternal(fasterSecondBlock.block, time + 100)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(bc.tryToConnectInternal(slowerSecondBlock, time + 100)).isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        time += 100;

        BlockContext fastBlockDescendant = bc
                .createNewBlockInternal(fasterSecondBlock.block, Collections.emptyList(), true, time / 1000L);
        BlockContext slowerBlockDescendant = bc
                .createNewBlockInternal(slowerSecondBlock, Collections.emptyList(), true, time / 1000L + 100 + 1);

        // increment by another hundred (this is supposed to be when the slower block descendant is completed)
        time += 100;

        assertThat(fastBlockDescendant.block.getDifficultyBI())
                .isGreaterThan(slowerBlockDescendant.block.getDifficultyBI());
        System.out.println("faster block descendant TD: " + fastBlockDescendant.block.getDifficultyBI());
        System.out.println("slower block descendant TD: " + slowerBlockDescendant.block.getDifficultyBI());

        assertThat(bc.tryToConnectInternal(slowerBlockDescendant.block, time)).isEqualTo(ImportResult.IMPORTED_BEST);

        // corrupt the parent for the fast block descendant
        bc.getRepository().getStateDatabase().delete(fasterSecondBlock.block.getStateRoot());
        assertThat(bc.getRepository().isValidRoot(fasterSecondBlock.block.getStateRoot())).isFalse();

        // attempt adding the fastBlockDescendant
        assertThat(bc.tryToConnectInternal(fastBlockDescendant.block, time)).isEqualTo(ImportResult.INVALID_BLOCK);

        // check for correct state rollback
        assertThat(bc.getBestBlock()).isEqualTo(slowerBlockDescendant.block);
        assertThat(bc.getRepository().getRoot()).isEqualTo(slowerBlockDescendant.block.getStateRoot());
        assertThat(bc.getTotalDifficulty()).isEqualTo(bc.getRepository().getBlockStore()
                                                              .getTotalDifficultyForHash(slowerBlockDescendant.block
                                                                                                 .getHash()));
    }

    @Test
    public void testRollbackWithAddInvalidBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle b = builder.withValidatorConfiguration("simple").build();
        StandaloneBlockchain bc = b.bc;
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.emptyList(), true);

        assertThat(bc.tryToConnect(block)).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the returned block is the first block
        assertThat(bc.getBestBlock() == block).isTrue();

        AionBlock invalidBlock = bc.createNewBlock(bc.getBestBlock(), Collections.emptyList(), true);
        invalidBlock.getHeader().setDifficulty(BigInteger.ONE.toByteArray());

        // attempting to add invalid block
        assertThat(bc.tryToConnect(invalidBlock)).isEqualTo(ImportResult.INVALID_BLOCK);

        // check for correct state rollback
        assertThat(bc.getBestBlock()).isEqualTo(block);
        assertThat(bc.getRepository().getRoot()).isEqualTo(block.getStateRoot());
        assertThat(bc.getTotalDifficulty())
                .isEqualTo(bc.getRepository().getBlockStore().getTotalDifficultyForHash(block.getHash()));
    }

    /*
     * Tests VM update behaviour from an external perspective
     */
}

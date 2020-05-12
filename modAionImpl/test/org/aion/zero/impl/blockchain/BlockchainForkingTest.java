package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.MIN_SELF_STAKE;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateTransactions;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader.Seal;
import org.aion.base.InternalVmType;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractInformation;
import org.aion.zero.impl.types.MiningBlock;
import org.aion.zero.impl.types.MiningBlockHeader;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.types.BlockUtil;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BlockchainForkingTest {

    @Before
    public void setup() {
        // reduce default logging levels
        Map<LogEnum, LogLevel> cfg = new HashMap<>();
        cfg.put(LogEnum.TX, LogLevel.DEBUG);
        cfg.put(LogEnum.VM, LogLevel.DEBUG);
        // enable CONS->DEBUG to see messages from staking contract helper
        // cfg.put(LogEnum.CONS, LogLevel.DEBUG);
        AionLoggerFactory.initAll(cfg);

        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @After
    public void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

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
        MiningBlock block = bc.createNewMiningBlock(bc.getBestBlock(), Collections.emptyList(), true);
        MiningBlock sameBlock = (MiningBlock) BlockUtil.newBlockFromRlp(block.getEncoded());

        ImportResult firstRes = bc.tryToConnect(block);

        // check that the returned block is the first block
        assertThat(bc.getBestBlock() == block).isTrue();
        assertThat(firstRes).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the correct caching context was used
        Pair<Long, BlockCachingContext> cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(block.getNumber() - 1);
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.MAINCHAIN);

        ImportResult secondRes = bc.tryToConnect(sameBlock);

        // the second block should get rejected, so check that the reference still refers
        // to the first block (we dont change the published reference)
        assertThat(bc.getBestBlock() == block).isTrue();
        assertThat(secondRes).isEqualTo(ImportResult.EXIST);

        // the caching context does not change for already known blocks
        cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(block.getNumber() - 1);
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.MAINCHAIN);
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
        Block bestBlock = bc.getBestBlock();
        MiningBlock standardBlock =
                bc.createNewMiningBlock(bc.getBestBlock(), Collections.emptyList(), true);

        ChainConfiguration cc = new ChainConfiguration();
        MiningBlock higherDifficultyBlock = new MiningBlock(standardBlock);
        MiningBlockHeader newBlockHeader =
                MiningBlockHeader.Builder.newInstance()
                        .withHeader(higherDifficultyBlock.getHeader())
                        .withTimestamp(bestBlock.getTimestamp() + 1)
                        .build();
        higherDifficultyBlock.updateHeader(newBlockHeader);

        BigInteger difficulty =
                cc.getPreUnityDifficultyCalculator()
                        .calculateDifficulty(
                                higherDifficultyBlock.getHeader(), bestBlock.getHeader());

        assertThat(difficulty).isGreaterThan(standardBlock.getDifficultyBI());

        newBlockHeader =
            MiningBlockHeader.Builder.newInstance()
                .withHeader(higherDifficultyBlock.getHeader())
                .withDifficulty(difficulty.toByteArray())
                .build();
        higherDifficultyBlock.updateHeader(newBlockHeader);

        System.out.println(
                "before any processing: " + Hex.toHexString(bc.getRepository().getRoot()));
        System.out.println("trie: " + bc.getRepository().getWorldState().getTrieDump());

        ImportResult result = bc.tryToConnect(standardBlock);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the correct caching context was used
        Pair<Long, BlockCachingContext> cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(standardBlock.getNumber() - 1);
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.MAINCHAIN);

        // assert that the block we just inserted (best) is the instance that is returned
        assertThat(bc.getBestBlock() == standardBlock).isTrue();

        System.out.println(Hex.toHexString(bc.getRepository().getRoot()));

        ImportResult higherDifficultyResult = bc.tryToConnect(higherDifficultyBlock);

        /**
         * With our updates to difficulty verification and calculation, this block is now invalid
         */
        assertThat(higherDifficultyResult).isEqualTo(ImportResult.INVALID_BLOCK);
        assertThat(bc.getBestBlockHash()).isEqualTo(standardBlock.getHash());

        // since the block is second for that height, it is assumed as sidechain
        cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(standardBlock.getNumber() - 1);
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.SIDECHAIN);

        // the object reference here is intentional
        assertThat(bc.getBestBlock() == standardBlock).isTrue();

        // check for correct state rollback
        assertThat(bc.getRepository().getRoot()).isEqualTo(standardBlock.getStateRoot());
        assertThat(bc.getTotalDifficulty())
                .isEqualTo(bc.getTotalDifficultyForHash(standardBlock.getHash()));
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

        // generate three blocks, on the third block we get flexibility
        // for what difficulties can occur

        MiningBlock firstBlock =
                bc.createNewMiningBlockInternal(
                                bc.getGenesis(), Collections.emptyList(), true, time / 1000L)
                        .block;
        assertThat(bc.tryToConnect(firstBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the correct caching context was used
        Pair<Long, BlockCachingContext> cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(firstBlock.getNumber() - 1);
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.MAINCHAIN);

        // now connect the second block
        MiningBlock secondBlock =
                bc.createNewMiningBlockInternal(firstBlock, Collections.emptyList(), true, time / 1000L)
                        .block;
        assertThat(bc.tryToConnect(secondBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the correct caching context was used
        cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(firstBlock.getNumber()); // the parent
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.MAINCHAIN);

        // now on the third block, we diverge with one block having higher TD than the other
        MiningBlock fasterSecondBlock =
                bc.createNewMiningBlockInternal(secondBlock, Collections.emptyList(), true, time / 1000L)
                        .block;
        MiningBlock slowerSecondBlock = new MiningBlock(fasterSecondBlock);

        MiningBlockHeader newBlockHeader =
            MiningBlockHeader.Builder.newInstance()
                .withHeader(slowerSecondBlock.getHeader())
                .withTimestamp(time / 1000L + 100)
                .build();
        slowerSecondBlock.updateHeader(newBlockHeader);

        assertThat(bc.tryToConnect(fasterSecondBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the correct caching context was used
        cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(secondBlock.getNumber()); // the parent
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.MAINCHAIN);

        assertThat(bc.tryToConnect(slowerSecondBlock)).isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        // check that the correct caching context was used
        cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(secondBlock.getNumber()); // the parent
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.SIDECHAIN);

        time += 100;

        MiningBlock fastBlockDescendant =
                bc.createNewMiningBlockInternal(
                                fasterSecondBlock, Collections.emptyList(), true, time / 1000L)
                        .block;
        MiningBlock slowerBlockDescendant =
                bc.createNewMiningBlockInternal(
                                slowerSecondBlock,
                                Collections.emptyList(),
                                true,
                                time / 1000L + 100 + 1)
                        .block;

        // increment by another hundred (this is supposed to be when the slower block descendant is
        // completed)
        time += 100;

        assertThat(fastBlockDescendant.getDifficultyBI())
                .isGreaterThan(slowerBlockDescendant.getDifficultyBI());
        System.out.println("faster block descendant TD: " + fastBlockDescendant.getDifficultyBI());
        System.out.println(
                "slower block descendant TD: " + slowerBlockDescendant.getDifficultyBI());

        assertThat(bc.tryToConnect(slowerBlockDescendant)).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the correct caching context was used
        cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(0); // no known parent
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.DEEP_SIDECHAIN);

        assertThat(bc.tryToConnect(fastBlockDescendant)).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the correct caching context was used
        cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(0); // parent had been made side chain
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.DEEP_SIDECHAIN);

        assertThat(bc.getBestBlock()).isEqualTo(fastBlockDescendant);

        // ensuring that the caching is correct for the nest block to be added
        MiningBlock switchBlock =
                bc.createNewMiningBlockInternal(
                                fastBlockDescendant, Collections.emptyList(), true, time / 1000L)
                        .block;

        assertThat(bc.tryToConnect(switchBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the correct caching context was used
        cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(secondBlock.getNumber()); // common ancestor
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.SWITCHING_MAINCHAIN);

        // ensuring that the caching is correct for the nest block to be added
        MiningBlock lastBlock =
                bc.createNewMiningBlockInternal(switchBlock, Collections.emptyList(), true, time / 1000L)
                        .block;

        assertThat(bc.tryToConnect(lastBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the correct caching context was used
        cacheContext = bc.getAvmCachingContext();
        assertThat(cacheContext.getLeft()).isEqualTo(switchBlock.getNumber()); // parent
        assertThat(cacheContext.getRight()).isEqualTo(BlockCachingContext.MAINCHAIN);
    }

    /** Test fork with exception. */
    @Test
    public void testSecondBlockHigherDifficultyFork_wExceptionOnFasterBlockAdd() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts().build();

        long time = System.currentTimeMillis();

        StandaloneBlockchain bc = bundle.bc;

        // generate three blocks, on the third block we get flexibility
        // for what difficulties can occur

        BlockContext firstBlock =
                bc.createNewMiningBlockInternal(
                        bc.getGenesis(), Collections.emptyList(), true, time / 1000L);
        assertThat(bc.tryToConnect(firstBlock.block)).isEqualTo(ImportResult.IMPORTED_BEST);

        // now connect the second block
        BlockContext secondBlock =
                bc.createNewMiningBlockInternal(
                        firstBlock.block, Collections.emptyList(), true, time / 1000L);
        assertThat(bc.tryToConnect(secondBlock.block)).isEqualTo(ImportResult.IMPORTED_BEST);

        // now on the third block, we diverge with one block having higher TD than the other
        BlockContext fasterSecondBlock =
                bc.createNewMiningBlockInternal(
                        secondBlock.block, Collections.emptyList(), true, time / 1000L);
        MiningBlock slowerSecondBlock = new MiningBlock(fasterSecondBlock.block);

        MiningBlockHeader newBlockHeader =
            MiningBlockHeader.Builder.newInstance()
                .withHeader(slowerSecondBlock.getHeader())
                .withTimestamp(time / 1000L + 100)
                .build();
        slowerSecondBlock.updateHeader(newBlockHeader);

        assertThat(bc.tryToConnect(fasterSecondBlock.block)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(bc.tryToConnect(slowerSecondBlock)).isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        time += 100;

        BlockContext fastBlockDescendant =
                bc.createNewMiningBlockInternal(
                        fasterSecondBlock.block, Collections.emptyList(), true, time / 1000L);
        BlockContext slowerBlockDescendant =
                bc.createNewMiningBlockInternal(
                        slowerSecondBlock, Collections.emptyList(), true, time / 1000L + 100 + 1);

        assertThat(fastBlockDescendant.block.getDifficultyBI())
                .isGreaterThan(slowerBlockDescendant.block.getDifficultyBI());
        System.out.println(
                "faster block descendant TD: " + fastBlockDescendant.block.getDifficultyBI());
        System.out.println(
                "slower block descendant TD: " + slowerBlockDescendant.block.getDifficultyBI());

        assertThat(bc.tryToConnect(slowerBlockDescendant.block)).isEqualTo(ImportResult.IMPORTED_BEST);

        // corrupt the parent for the fast block descendant
        ((MockDB) bc.getRepository().getStateDatabase()).deleteAndCommit(fasterSecondBlock.block.getStateRoot());
        assertThat(bc.getRepository().isValidRoot(fasterSecondBlock.block.getStateRoot()))
                .isFalse();

        // attempt adding the fastBlockDescendant
        assertThat(bc.tryToConnect(fastBlockDescendant.block)).isEqualTo(ImportResult.INVALID_BLOCK);

        // check for correct state rollback
        assertThat(bc.getBestBlock()).isEqualTo(slowerBlockDescendant.block);
        assertThat(bc.getRepository().getRoot())
                .isEqualTo(slowerBlockDescendant.block.getStateRoot());
        assertThat(bc.getTotalDifficulty())
                .isEqualTo(bc.getTotalDifficultyForHash(slowerBlockDescendant.block.getHash()));
    }

    @Test
    public void testRollbackWithAddInvalidBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle b = builder.withValidatorConfiguration("simple").build();
        StandaloneBlockchain bc = b.bc;
        MiningBlock block = bc.createNewMiningBlock(bc.getBestBlock(), Collections.emptyList(), true);

        assertThat(bc.tryToConnect(block)).isEqualTo(ImportResult.IMPORTED_BEST);

        // check that the returned block is the first block
        assertThat(bc.getBestBlock() == block).isTrue();

        MiningBlock invalidBlock =
                bc.createNewMiningBlock(bc.getBestBlock(), Collections.emptyList(), true);

        MiningBlockHeader newBlockHeader =
            MiningBlockHeader.Builder.newInstance()
                .withHeader(invalidBlock.getHeader())
                .withDifficulty(BigInteger.ONE.toByteArray())
                .build();
        invalidBlock.updateHeader(newBlockHeader);

        // attempting to add invalid block
        assertThat(bc.tryToConnect(invalidBlock)).isEqualTo(ImportResult.INVALID_BLOCK);

        // check for correct state rollback
        assertThat(bc.getBestBlock()).isEqualTo(block);
        assertThat(bc.getRepository().getRoot()).isEqualTo(block.getStateRoot());
        assertThat(bc.getTotalDifficulty())
                .isEqualTo(bc.getTotalDifficultyForHash(block.getHash()));
    }

    /**
     * Test the fork case when the block being replaced had contract storage changes that differ
     * from the previous block and are replaced by new changes in the updated block.
     *
     * <p>Ensures that the output of applying the block after the fork is the same as applying the
     * block first.
     */
    @Test
    public void testForkWithRevertOnSmallContractStorage() {

        // ****** setup ******

        // build a blockchain with CONCURRENT_THREADS_PER_TYPE blocks
        List<ECKey> accounts = generateAccounts(10);
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain sourceChain =
                builder.withValidatorConfiguration("simple")
                        .withDefaultAccounts(accounts)
                        .build()
                        .bc;
        StandaloneBlockchain testChain =
                builder.withValidatorConfiguration("simple")
                        .withDefaultAccounts(accounts)
                        .build()
                        .bc;

        assertThat(testChain).isNotEqualTo(sourceChain);
        assertThat(testChain.genesis).isEqualTo(sourceChain.genesis);

        long time = System.currentTimeMillis();

        // add a block with contract deploy
        ECKey sender = accounts.remove(0);
        AionTransaction deployTx = deployContract(sender);

        MiningBlock block =
                sourceChain.createNewMiningBlockInternal(
                                sourceChain.genesis, Arrays.asList(deployTx), true, time / 10_000L)
                        .block;

        Pair<ImportResult, AionBlockSummary> connectResult = sourceChain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);
        assertThat(receipt.isSuccessful()).isTrue();

        ImportResult result = connectResult.getLeft();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        result = testChain.tryToConnect(block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        assertThat(testChain.getRepository().getRoot())
                .isEqualTo(sourceChain.getRepository().getRoot());

        AionAddress contract = TxUtil.calculateContractAddress(receipt.getTransaction());
        // add a block with transactions to both
        List<AionTransaction> txs = generateTransactions(20, accounts, sourceChain.getRepository());

        block =
                sourceChain.createNewMiningBlockInternal(
                                sourceChain.getBestBlock(), txs, true, time / 10_000L)
                        .block;

        result = sourceChain.tryToConnect(block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        result = testChain.tryToConnect(block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        assertThat(testChain.getRepository().getRoot())
                .isEqualTo(sourceChain.getRepository().getRoot());

        // create a slow / fast block distinction
        AionTransaction callTx = callSetValue2(sender, contract, 5, 6, BigInteger.ONE);
        MiningBlock fastBlock =
                sourceChain.createNewMiningBlockInternal(
                                sourceChain.getBestBlock(),
                                Arrays.asList(callTx),
                                true,
                                time / 10_000L)
                        .block;

        callTx = callSetValue2(sender, contract, 1, 9, BigInteger.ONE);
        MiningBlock slowBlock =
                new MiningBlock(
                        sourceChain.createNewMiningBlockInternal(
                                        sourceChain.getBestBlock(),
                                        Arrays.asList(callTx),
                                        true,
                                        time / 10_000L)
                                .block);

        MiningBlockHeader newBlockHeader =
            MiningBlockHeader.Builder.newInstance()
                .withHeader(slowBlock.getHeader())
                .withTimestamp(time / 10_000L + 100)
                .build();
        slowBlock.updateHeader(newBlockHeader);

        time += 100;

        // sourceChain imports only fast block
        assertThat(sourceChain.tryToConnect(fastBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // testChain imports both blocks
        assertThat(testChain.tryToConnect(fastBlock)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(testChain.tryToConnect(slowBlock)).isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        // build two blocks with different contract storage calls
        // the second block gets a higher total difficulty
        callTx = callSetValue(sender, contract, 5, BigInteger.TWO);

        MiningBlock lowBlock =
                testChain.createNewMiningBlockInternal(
                                slowBlock, Arrays.asList(callTx), true, time / 10_000L + 101)
                        .block;

        callTx = callSetValue(sender, contract, 9, BigInteger.TWO);
        MiningBlock highBlock =
                sourceChain.createNewMiningBlockInternal(
                                fastBlock, Arrays.asList(callTx), true, time / 10_000L)
                        .block;

        // System.out.println("***highBlock TD: " + highBlock.getDifficultyBI());
        // System.out.println("***lowBlock TD: " + lowBlock.getDifficultyBI());
        assertThat(highBlock.getDifficultyBI()).isGreaterThan(lowBlock.getDifficultyBI());

        // build first chain with highBlock applied directly
        connectResult = sourceChain.tryToConnectAndFetchSummary(highBlock);
        receipt = connectResult.getRight().getReceipts().get(0);
        assertThat(receipt.isSuccessful()).isTrue();

        result = connectResult.getLeft();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // collect the consensus information from the block & receipt
        AionBlockSummary blockSummary = connectResult.getRight();
        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        // ****** test fork behavior ******

        // first import lowBlock
        assertThat(testChain.tryToConnect(lowBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // next import highBlock causing the fork
        connectResult = testChain.tryToConnectAndFetchSummary(highBlock);
        receipt = connectResult.getRight().getReceipts().get(0);
        assertThat(receipt.isSuccessful()).isTrue();
        System.out.println(receipt);

        result = connectResult.getLeft();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // collect the consensus information from the block & receipt
        blockSummary = connectResult.getRight();
        assertThat(testChain.getBestBlock()).isEqualTo(sourceChain.getBestBlock());
        assertThat(blockSummary.getBlock().getStateRoot()).isEqualTo(stateRoot);
        assertThat(blockSummary.getBlock().getReceiptsRoot()).isEqualTo(blockReceiptsRoot);
        assertThat(receipt.getReceiptTrieEncoded()).isEqualTo(receiptTrieEncoded);
    }

    private AionTransaction deployContract(ECKey sender) {
        // contract source code for reference
        /*
        pragma solidity ^0.4.15;
        contract Storage {
            uint128 value;
            mapping(uint128 => uint128) private userPrivilege;
            struct Entry {
                uint128 id;
                uint128 value;
            }
            Entry value2;
            function Storage(){
            value = 10;
            userPrivilege[value] = value;
            value2.id = 100;
            value2.value = 200;
            userPrivilege[value2.id] = value2.value;
            }
            function setValue(uint128 newValue)  {
            value = newValue;
            userPrivilege[newValue] = newValue+1;
            }
            function setValue2(uint128 v1, uint128 v2)  {
            value2.id = v1;
            value2.value = v2;
            userPrivilege[v1] = v1+v2;
            }
            function getValue() returns(uint)  {
            return value;
            }
        }
        */

        // code for contract
        String contractCode =
                "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";

        return AionTransaction.create(
                sender,
                BigInteger.ZERO.toByteArray(),
                null,
                BigInteger.ZERO.toByteArray(),
                ByteUtil.hexStringToBytes(contractCode),
                5_000_000L,
                10_123_456_789L,
                TransactionTypes.DEFAULT, null);
    }

    private AionTransaction callSetValue(
            ECKey sender, AionAddress contract, int digit, BigInteger nonce) {
        // calls setValue(digit)
        if (digit < 0 || digit > 9) {
            return null; // should actually be a digit
        }
        // code for contract call
        String contractCode = "62eb702a0000000000000000000000000000000" + digit;

        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                contract,
                BigInteger.ZERO.toByteArray(),
                Hex.decode(contractCode),
                2_000_000L,
                10_123_456_789L,
                TransactionTypes.DEFAULT, null);
    }

    private AionTransaction callSetValue2(
            ECKey sender, AionAddress contract, int digit1, int digit2, BigInteger nonce) {
        // calls setValue2(digit, digit)
        if (digit1 < 0 || digit1 > 9 || digit2 < 0 || digit2 > 9) {
            return null; // should actually be a digit
        }
        // code for contract call
        String contractCode =
                "1677b0ff0000000000000000000000000000000"
                        + digit1
                        + "0000000000000000000000000000000"
                        + digit2;

        return AionTransaction.create(
                sender,
                nonce.toByteArray(),
                contract,
                BigInteger.ZERO.toByteArray(),
                Hex.decode(contractCode),
                2_000_000L,
                10_123_456_789L,
                TransactionTypes.DEFAULT, null);
    }

    /**
     * Ensures that if a side-chain block is imported after a main-chain block creating the same
     * contract address X but using different VMs, then each chain will operate on the correct VM.
     */
    @Test
    public void testVmTypeRetrieval_ForkWithConflictingContractVM() throws Exception {
        TestResourceProvider resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        IAvmResourceFactory resourceFactory = resourceProvider.factoryForVersion1;

        // blocks to be built
        MiningBlock block, fastBlock, slowBlock, lowBlock, highBlock;

        // transactions used in blocks
        AionTransaction deployOnAVM, deployOnFVM, callTxOnFVM;

        // for processing block results
        Pair<ImportResult, AionBlockSummary> connectResult;
        ImportResult result;
        AionTxReceipt receipt;

        // build a blockchain
        TransactionTypeRule.allowAVMContractTransaction();
        List<ECKey> accounts = generateAccounts(10);
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain sourceChain =
                builder.withValidatorConfiguration("simple")
                        .withDefaultAccounts(accounts)
                        .build()
                        .bc;
        StandaloneBlockchain testChain =
                builder.withValidatorConfiguration("simple")
                        .withDefaultAccounts(accounts)
                        .build()
                        .bc;
        ECKey sender = accounts.remove(0);

        assertThat(testChain).isNotEqualTo(sourceChain);
        assertThat(testChain.genesis).isEqualTo(sourceChain.genesis);

        long time = System.currentTimeMillis();

        // add a block to both chains
        block =
                sourceChain.createNewMiningBlockInternal(
                                sourceChain.getBestBlock(),
                                Collections.emptyList(),
                                true,
                                time / 10_000L)
                        .block;
        assertThat(sourceChain.tryToConnect(block)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(testChain.tryToConnect(block)).isEqualTo(ImportResult.IMPORTED_BEST);

        // ****** setup side chain ******

        // deploy contracts on different VMs for the two chains
        deployOnAVM = deployStatefulnessAVMContract(resourceFactory, sender);
        fastBlock =
                sourceChain.createNewMiningBlockInternal(
                                sourceChain.getBestBlock(),
                                Arrays.asList(deployOnAVM),
                                true,
                                time / 10_000L)
                        .block;

        deployOnFVM = deployContract(sender);
        slowBlock =
                new MiningBlock(
                        sourceChain.createNewMiningBlockInternal(
                                        sourceChain.getBestBlock(),
                                        Arrays.asList(deployOnFVM),
                                        true,
                                        time / 10_000L)
                                .block);

        MiningBlockHeader newBlockHeader =
            MiningBlockHeader.Builder.newInstance()
                .withHeader(slowBlock.getHeader())
                .withTimestamp(time / 10_000L + 100)
                .build();
        slowBlock.updateHeader(newBlockHeader);

        time += 100;

        // sourceChain imports only fast block
        connectResult = sourceChain.tryToConnectAndFetchSummary(fastBlock);
        result = connectResult.getLeft();
        receipt = connectResult.getRight().getReceipts().get(0);

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.isSuccessful()).isTrue();

        AionAddress contract = TxUtil.calculateContractAddress(receipt.getTransaction());

        // testChain imports both blocks
        connectResult = testChain.tryToConnectAndFetchSummary(fastBlock);
        result = connectResult.getLeft();
        receipt = connectResult.getRight().getReceipts().get(0);

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.isSuccessful()).isTrue();
        assertThat(TxUtil.calculateContractAddress(receipt.getTransaction())).isEqualTo(contract);

        connectResult = testChain.tryToConnectAndFetchSummary(slowBlock);
        result = connectResult.getLeft();
        receipt = connectResult.getRight().getReceipts().get(0);

        assertThat(result).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        assertThat(receipt.isSuccessful()).isTrue();
        assertThat(TxUtil.calculateContractAddress(receipt.getTransaction())).isEqualTo(contract);

        // ****** check that the correct contract details are kept ******

        // check that both chains have the correct vm type for the AVM contract
        byte[] codeHashAVM = sourceChain.getRepository().getAccountState(contract).getCodeHash();
        assertThat(testChain.getRepository().getVMUsed(contract, codeHashAVM))
                .isEqualTo(sourceChain.getRepository().getVMUsed(contract, codeHashAVM));

        // check that only the second chain has the vm type for the FVM contract
        byte[] codeHashFVM =
                ((AionRepositoryImpl)
                                testChain.getRepository().getSnapshotTo(slowBlock.getStateRoot()))
                        .getAccountState(contract)
                        .getCodeHash();
        assertThat(sourceChain.getRepository().getVMUsed(contract, codeHashFVM))
                .isEqualTo(InternalVmType.UNKNOWN);
        assertThat(testChain.getRepository().getVMUsed(contract, codeHashFVM))
                .isEqualTo(InternalVmType.FVM);

        // check the stored information details
        ContractInformation infoSingleImport =
                sourceChain.getRepository().getIndexedContractInformation(contract);
        System.out.println("without side chain:" + infoSingleImport);

        assertThat(infoSingleImport.getVmUsed(codeHashAVM)).isEqualTo(InternalVmType.AVM);
        assertThat(infoSingleImport.getInceptionBlocks(codeHashAVM))
                .isEqualTo(Set.of(fastBlock.getHashWrapper()));
        assertThat(infoSingleImport.getVmUsed(codeHashFVM)).isEqualTo(InternalVmType.UNKNOWN);
        assertThat(infoSingleImport.getInceptionBlocks(codeHashFVM)).isEmpty();

        ContractInformation infoMultiImport =
                testChain.getRepository().getIndexedContractInformation(contract);
        System.out.println("with side chain:" + infoMultiImport);

        assertThat(infoMultiImport.getVmUsed(codeHashAVM)).isEqualTo(InternalVmType.AVM);
        assertThat(infoMultiImport.getInceptionBlocks(codeHashAVM))
                .isEqualTo(Set.of(fastBlock.getHashWrapper()));
        assertThat(infoMultiImport.getVmUsed(codeHashFVM)).isEqualTo(InternalVmType.FVM);
        assertThat(infoMultiImport.getInceptionBlocks(codeHashFVM))
                .isEqualTo(Set.of(slowBlock.getHashWrapper()));

        // build two blocks where the second block has a higher total difficulty
        callTxOnFVM = callSetValue(sender, contract, 9, BigInteger.ONE);
        lowBlock =
                testChain.createNewMiningBlockInternal(
                                slowBlock, Arrays.asList(callTxOnFVM), true, time / 10_000L + 101)
                        .block;

        int expectedCount = 3;
        List<AionTransaction> callTxOnAVM =
                callStatefulnessAVM(resourceFactory, sender, expectedCount, BigInteger.ONE, contract);
        highBlock =
                sourceChain.createNewMiningBlockInternal(fastBlock, callTxOnAVM, true, time / 10_000L)
                        .block;

        assertThat(highBlock.getDifficultyBI()).isGreaterThan(lowBlock.getDifficultyBI());

        // build first chain with highBlock applied directly
        connectResult = sourceChain.tryToConnectAndFetchSummary(highBlock);
        receipt = connectResult.getRight().getReceipts().get(expectedCount); // get last tx
        assertThat(receipt.isSuccessful()).isTrue();

        result = connectResult.getLeft();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // collect the consensus information from the block & receipt
        AionBlockSummary blockSummary = connectResult.getRight();
        byte[] stateRoot = blockSummary.getBlock().getStateRoot();
        byte[] blockReceiptsRoot = blockSummary.getBlock().getReceiptsRoot();
        byte[] receiptTrieEncoded = receipt.getReceiptTrieEncoded();

        int returnedCount = resourceFactory.newDecoder(blockSummary.getReceipts().get(expectedCount).getTransactionOutput()).decodeOneInteger();
        assertThat(returnedCount).isEqualTo(expectedCount);

        // ****** test fork behavior ******

        // first import lowBlock
        assertThat(testChain.tryToConnect(lowBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // next import highBlock causing the fork
        connectResult = testChain.tryToConnectAndFetchSummary(highBlock);
        receipt = connectResult.getRight().getReceipts().get(expectedCount);
        assertThat(receipt.isSuccessful()).isTrue();
        System.out.println(receipt);

        result = connectResult.getLeft();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // collect the consensus information from the block & receipt
        blockSummary = connectResult.getRight();
        assertThat(testChain.getBestBlock()).isEqualTo(sourceChain.getBestBlock());
        assertThat(blockSummary.getBlock().getStateRoot()).isEqualTo(stateRoot);
        assertThat(blockSummary.getBlock().getReceiptsRoot()).isEqualTo(blockReceiptsRoot);
        assertThat(receipt.getReceiptTrieEncoded()).isEqualTo(receiptTrieEncoded);

        returnedCount = resourceFactory.newDecoder(blockSummary.getReceipts().get(expectedCount).getTransactionOutput()).decodeOneInteger();
        assertThat(returnedCount).isEqualTo(expectedCount);
    }

    private AionTransaction deployStatefulnessAVMContract(IAvmResourceFactory resourceFactory, ECKey sender) {
        byte[] statefulnessAVM = resourceFactory.newContractFactory().getDeploymentBytes(AvmContract.STATEFULNESS);

        return AionTransaction.create(
                sender,
                BigInteger.ZERO.toByteArray(),
                null,
                BigInteger.ZERO.toByteArray(),
                statefulnessAVM,
                5_000_000L,
                10_123_456_789L,
                TransactionTypes.AVM_CREATE_CODE, null);
    }

    private List<AionTransaction> callStatefulnessAVM(
            IAvmResourceFactory resourceFactory, ECKey sender, int count, BigInteger nonce, AionAddress contract) {

        List<AionTransaction> txs = new ArrayList<>();
        AionTransaction transaction;

        //  make call transactions
        for (int i = 0; i < count; i++) {
            transaction =
                    AionTransaction.create(
                            sender,
                            nonce.toByteArray(),
                            contract,
                            BigInteger.ZERO.toByteArray(),
                            resourceFactory.newStreamingEncoder().encodeOneString("incrementCounter").getEncoding(),
                            2_000_000L,
                            10_123_456_789L,
                            TransactionTypes.DEFAULT, null);
            txs.add(transaction);
            // increment nonce
            nonce = nonce.add(BigInteger.ONE);
        }

        //  make one getCount transaction
        transaction =
                AionTransaction.create(
                        sender,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        resourceFactory.newStreamingEncoder().encodeOneString("getCount").getEncoding(),
                        2_000_000L,
                        10_123_456_789L,
                        TransactionTypes.DEFAULT, null);
        txs.add(transaction);

        return txs;
    }

    /**
     * The test builds two blockchains causing several re-branching on top of the unity block, the first staking block and the next mining block.
     */
    @Test
    public void testReBranchOnUnityBlockchain() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {

        // setup accounts
        List<ECKey> accounts = generateAccounts(10);
        ECKey stakingRegistryOwner = accounts.get(0);
        List<ECKey> stakersOnChain1 = List.of(accounts.get(1), accounts.get(2), accounts.get(3));
        List<ECKey> stakersOnChain2 = List.of(accounts.get(4), accounts.get(5), accounts.get(6));
        List<ECKey> stakersOnBothChains = List.of(accounts.get(7), accounts.get(8), accounts.get(9));

        // setup Unity fork and AVM
        long unityForkBlock = 2;
        AvmTestConfig.clearConfigurations(); // clear setting from @Before
        TransactionTypeRule.allowAVMContractTransaction();
        TestResourceProvider resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
        AvmTestConfig.supportBothAvmVersions(0, unityForkBlock, 0); // enable both AVMs without overlap

        // setup two identical blockchains
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain firstChain = builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).withAvmEnabled().build().bc;
        StandaloneBlockchain secondChain = builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).withAvmEnabled().build().bc;
        firstChain.forkUtility.enableUnityFork(unityForkBlock);
        secondChain.forkUtility.enableUnityFork(unityForkBlock);

        assertThat(secondChain).isNotEqualTo(firstChain);
        assertThat(secondChain.genesis).isEqualTo(firstChain.genesis);

        /* Setup the first block in the chain with the staker registry deployment.
         * After this both chains will have:
         *     (gen)->(staker-registry)
         */
        // create block with staker registry
        Block blockWithRegistry = BlockchainTestUtils.generateNextMiningBlockWithStakerRegistry(firstChain, firstChain.getGenesis(), resourceProvider, stakingRegistryOwner);
        // import block on firstChain
        Pair<ImportResult, AionBlockSummary> result = firstChain.tryToConnectAndFetchSummary(blockWithRegistry);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(result.getRight().getReceipts().get(0).isSuccessful()).isTrue();
        assertThat(result.getRight().getReceipts().get(0).getLogInfoList()).isNotEmpty();
        assertThat(result.getRight().getReceipts().get(0).getEnergyUsed()).isEqualTo(1_225_655L);
        // import block on secondChain
        result = secondChain.tryToConnectAndFetchSummary(blockWithRegistry);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(result.getRight().getReceipts().get(0).isSuccessful()).isTrue();
        assertThat(result.getRight().getReceipts().get(0).getLogInfoList()).isNotEmpty();
        assertThat(result.getRight().getReceipts().get(0).getEnergyUsed()).isEqualTo(1_225_655L);
        // ensure both chains have the same root
        assertThat(secondChain.getRepository().getRoot()).isEqualTo(firstChain.getRepository().getRoot());

        // set the staking contract address in the staking genesis
        AionTransaction deploy = blockWithRegistry.getTransactionsList().get(0);
        AionAddress contract = TxUtil.calculateContractAddress(deploy.getSenderAddress().toByteArray(), deploy.getNonceBI());
        firstChain.getGenesis().setStakingContractAddress(contract);
        secondChain.getGenesis().setStakingContractAddress(contract);

        /* Import blocks such that a re-branching occurs on the Unity fork block.
         * The firstChain imports blocks 2 and 3 without re-org for state comparison:
         *     (gen)->(staker-registry)->(block2Unity)->(block3Staking)
         * The secondChain imports blocks 1, 2 and 3, in the order of their indices, causing a re-org:
         *     (gen)->(staker-registry)->(block1Unity)
         *                             ->(block2Unity)->(block3Staking)
         */
        // setup firstChain
        // create Unity block with stakersOnChain1
        Block block2Unity = BlockchainTestUtils.generateNextMiningBlockWithStakers(firstChain, firstChain.getBestBlock(), resourceProvider, stakersOnChain1, MIN_SELF_STAKE);
        // import block2Unity on firstChain (main chain)
        result = firstChain.tryToConnectAndFetchSummary(block2Unity);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        verifyReceipts(result.getRight().getReceipts(), 3, true);
        // create staking block with stakersOnBothChains
        Block block3Staking = BlockchainTestUtils.generateNextStakingBlockWithStakers(firstChain, block2Unity, resourceProvider, stakersOnBothChains, MIN_SELF_STAKE, stakersOnChain1.get(0));
        assertThat(block3Staking).isNotNull();
        // import block3Staking on firstChain (main chain)
        result = firstChain.tryToConnectAndFetchSummary(block3Staking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        verifyReceipts(result.getRight().getReceipts(), 3, true);

        // setup secondChain
        // create Unity block with stakersOnChain2
        Block block1Unity = BlockchainTestUtils.generateNextMiningBlockWithStakers(secondChain, secondChain.getBestBlock(), resourceProvider, stakersOnChain2, MIN_SELF_STAKE);
        // import block1Unity on secondChain (main chain)
        result = secondChain.tryToConnectAndFetchSummary(block1Unity);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        verifyReceipts(result.getRight().getReceipts(), 3, true);
        // create staking block with stakersOnBothChains as setup block for next section -- not connecting
        // NOTE: this block can be created only on top of the main chain and therefore must be built here
        Block block4Staking = BlockchainTestUtils.generateNextStakingBlockWithStakers(secondChain, block1Unity, resourceProvider, stakersOnBothChains, MIN_SELF_STAKE, stakersOnChain2.get(0));
        assertThat(block4Staking).isNotNull();
        // verify stakers from stakersOnChain2 exist and stakersOnChain1 and stakersOnBothChains are not present
        verifyEffectiveSelfStake(stakersOnChain1, secondChain, block1Unity, BigInteger.ZERO);
        verifyEffectiveSelfStake(stakersOnChain2, secondChain, block1Unity, MIN_SELF_STAKE);
        verifyEffectiveSelfStake(stakersOnBothChains, secondChain, block1Unity, BigInteger.ZERO);
        // importing block2Unity on secondChain (side chain)
        result = secondChain.tryToConnectAndFetchSummary(block2Unity);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        verifyReceipts(result.getRight().getReceipts(), 3, true);
        // importing block3Staking on secondChain (main chain after re-branch)
        result = secondChain.tryToConnectAndFetchSummary(block3Staking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        verifyReceipts(result.getRight().getReceipts(), 3, true);
        // verify stakers from stakersOnChain1 and stakersOnBothChains exist and stakersOnChain2 are not present
        verifyEffectiveSelfStake(stakersOnChain1, secondChain, block3Staking, MIN_SELF_STAKE);
        verifyEffectiveSelfStake(stakersOnChain2, secondChain, block3Staking, BigInteger.ZERO);
        verifyEffectiveSelfStake(stakersOnBothChains, secondChain, block3Staking, MIN_SELF_STAKE);

        // ensuring the same state root for the two import paths
        assertThat(secondChain.getRepository().getRoot()).isEqualTo(firstChain.getRepository().getRoot());

        /* Import blocks 4 and 5 such that a re-branching occurs on secondChain:
         *     (gen)->(staker-registry)->(block1Unity)->(block4Staking)->(block5Mining)
         *                             ->(block2Unity)->(block3Staking)
         */
        // importing block4Staking on secondChain (side chain)
        result = secondChain.tryToConnectAndFetchSummary(block4Staking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        verifyReceipts(result.getRight().getReceipts(), 3, true);
        // create diverse set of transactions
        List<AionTransaction> txs = generateMixedTransactions(secondChain, block4Staking, resourceProvider, stakersOnChain1, stakersOnBothChains, stakersOnChain2);
        assertThat(txs.size()).isEqualTo(8);
        // create mining block on top of block4Staking
        Block block5Mining = BlockchainTestUtils.generateNextMiningBlock(secondChain, block4Staking, txs);
        // importing block5Mining on secondChain (main chain after re-branch)
        result = secondChain.tryToConnectAndFetchSummary(block5Mining);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        verifyReceipts(result.getRight().getReceipts(), 8, false);

        // verify stakers updates
        verifyEffectiveSelfStake(stakersOnChain1, secondChain, block5Mining, MIN_SELF_STAKE);
        verifyEffectiveSelfStake(stakersOnBothChains, secondChain, block5Mining, MIN_SELF_STAKE.multiply(BigInteger.TWO));
        verifyEffectiveSelfStake(List.of(stakersOnChain2.get(0), stakersOnChain2.get(1)), secondChain, block5Mining, BigInteger.ZERO);
        verifyEffectiveSelfStake(List.of(stakersOnChain2.get(2)), secondChain, block5Mining, MIN_SELF_STAKE);

        /* Import blocks such that a re-branching occurs on the first mining block after Unity.
         * The firstChain imports blocks without re-org for state comparison:
         *     (gen)->(staker-registry)->(block2Unity)->(block3Staking)->(block6Mining)->(block7Staking)
         * The secondChain imports blocks 6 and 7 in the order of their indices, causing a re-org:
         *     (gen)->(staker-registry)->(block1Unity)->(block4Staking)->(block5Mining)
         *                             ->(block2Unity)->(block3Staking)->(block6Mining)->(block7Staking)
         */
        // create diverse set of transactions
        txs = generateMixedTransactions(firstChain, block3Staking, resourceProvider, stakersOnChain2, stakersOnChain1, stakersOnBothChains);
        assertThat(txs.size()).isEqualTo(8);
        // create alternative mining block on top of block3Staking
        Block block6Mining = BlockchainTestUtils.generateNextMiningBlock(firstChain, block3Staking, txs);
        // import block6Mining on firstChain (main chain)
        result = firstChain.tryToConnectAndFetchSummary(block6Mining);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        verifyReceipts(result.getRight().getReceipts(), 8, false);
        // create staking block on top of block6Mining
        Block block7Staking = BlockchainTestUtils.generateNextStakingBlock(firstChain, block6Mining, Collections.emptyList(), stakersOnChain1.get(2));
        assertThat(block7Staking).isNotNull();
        // import block7Staking on firstChain (main chain)
        result = firstChain.tryToConnectAndFetchSummary(block7Staking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // import block6Mining on secondChain (side chain)
        result = secondChain.tryToConnectAndFetchSummary(block6Mining);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        verifyReceipts(result.getRight().getReceipts(), 8, false);
        // import block7Staking on secondChain (main chain after re-branch)
        result = secondChain.tryToConnectAndFetchSummary(block7Staking);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);

        // verify stakers updates
        verifyEffectiveSelfStake(stakersOnChain2, secondChain, block7Staking, MIN_SELF_STAKE);
        verifyEffectiveSelfStake(stakersOnChain1, secondChain, block7Staking, MIN_SELF_STAKE.multiply(BigInteger.TWO));
        verifyEffectiveSelfStake(List.of(stakersOnBothChains.get(0), stakersOnBothChains.get(1)), secondChain, block7Staking, BigInteger.ZERO);
        verifyEffectiveSelfStake(List.of(stakersOnBothChains.get(2)), secondChain, block7Staking, MIN_SELF_STAKE);

        // ensuring the same state root for the two import paths
        assertThat(secondChain.getRepository().getRoot()).isEqualTo(firstChain.getRepository().getRoot());

        // fast forward 58 blocks to check that the stake was transferred
        AionLoggerFactory.initAll(); // this resets logging to warnings to avoid spam
        List<ECKey> stakers = new ArrayList<>();
        stakers.addAll(stakersOnChain2);
        stakers.addAll(stakersOnChain1);
        stakers.add(stakersOnBothChains.get(2));
        BlockchainTestUtils.generateRandomUnityChain(firstChain, resourceProvider, 58, 1, stakers, stakingRegistryOwner, 10);

        // finalize transfer and verify
        AionTransaction tx = BlockchainTestUtils.generateTransferFinalizeTransactions(firstChain, firstChain.getBestBlock(), resourceProvider, stakersOnBothChains.get(1), 0L);
        assertThat(firstChain.getBestBlock().getHeader().getSealType()).isEqualTo(Seal.PROOF_OF_STAKE);
        Block finalizeTransferBlock = BlockchainTestUtils.generateNextMiningBlock(firstChain, firstChain.getBestBlock(), List.of(tx));
        result = firstChain.tryToConnectAndFetchSummary(finalizeTransferBlock);
        assertThat(result.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        verifyReceipts(result.getRight().getReceipts(), 1, false);
        verifyEffectiveSelfStake(List.of(stakersOnBothChains.get(2)), firstChain, firstChain.getBestBlock(), MIN_SELF_STAKE.multiply(BigInteger.TWO));
    }

    public List<AionTransaction> generateMixedTransactions(StandaloneBlockchain chain, Block parent, TestResourceProvider resourceProvider, List<ECKey> newStakers, List<ECKey> bondStakers, List<ECKey> mixedStakers) {
        if (mixedStakers.size() != 3) {
            throw new IllegalStateException("Please provide the accounts required to build a Unity chain.");
        }

        // create diverse set of transactions
        List<AionTransaction> txs = new ArrayList<>();
        // adding more stakers
        txs.addAll(BlockchainTestUtils.generateStakerRegistrationTransactions(chain, parent, resourceProvider, newStakers, MIN_SELF_STAKE));
        // adding more stake to existing stakers
        txs.addAll(BlockchainTestUtils.generateIncreaseStakeTransactions(chain, parent, resourceProvider, bondStakers, MIN_SELF_STAKE));
        // unbound staker
        txs.addAll(BlockchainTestUtils.generateDecreaseStakeTransactions(chain, parent, resourceProvider, List.of(mixedStakers.get(0)), MIN_SELF_STAKE, BigInteger.ZERO));
        // transfer staker
        txs.addAll(BlockchainTestUtils.generateTransferStakeTransactions(chain, parent, resourceProvider, List.of(mixedStakers.get(1), mixedStakers.get(2)), MIN_SELF_STAKE, BigInteger.ZERO));

        // sorting the transactions by hash to randomize the order
        return txs.stream().sorted((t1, t2) -> Arrays.compare(t1.getTransactionHash(), t2.getTransactionHash())).collect(Collectors.toList());
    }

    public void verifyReceipts(List<AionTxReceipt> stakerRegistrationReceipts, int expectedSize, boolean checkEnergy) {
        assertThat(stakerRegistrationReceipts.size()).isEqualTo(expectedSize);

        for (AionTxReceipt receipt : stakerRegistrationReceipts) {
            assertThat(receipt.isSuccessful()).isTrue();
            assertThat(receipt.getLogInfoList()).isNotEmpty();
            if (checkEnergy) {
                // the value below can differ slightly depending on the address of the caller
                assertThat(receipt.getEnergyUsed()).isAtLeast(180_000L);
            }
        }
    }

    public void verifyEffectiveSelfStake(List<ECKey> stakers, AionBlockchainImpl chain, Block block, BigInteger expectedStake) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        for (ECKey key : stakers) {
            AionAddress address = new AionAddress(key.getAddress());
            assertThat(chain.getStakingContractHelper().getEffectiveStake(address, address, block)).isEqualTo(expectedStake);
        }
    }
}

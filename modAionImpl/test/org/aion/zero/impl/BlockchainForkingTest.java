package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.BlockchainTestUtils.generateTransactions;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;
import org.aion.util.biginteger.BIUtil;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BlockchainForkingTest {

    @Before
    public void setup() {
        LongLivedAvm.createAndStartLongLivedAvm();
    }

    @After
    public void shutdown() {
        LongLivedAvm.destroy();
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

        System.out.println(
                "before any processing: " + new ByteArrayWrapper(bc.getRepository().getRoot()));
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
                .isEqualTo(
                        bc.getRepository()
                                .getBlockStore()
                                .getTotalDifficultyForHash(standardBlock.getHash()));
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

        // corrupt the parent for the fast block descendant
        bc.getRepository().getStateDatabase().delete(fasterSecondBlock.block.getStateRoot());
        assertThat(bc.getRepository().isValidRoot(fasterSecondBlock.block.getStateRoot()))
                .isFalse();

        // attempt adding the fastBlockDescendant
        assertThat(bc.tryToConnectInternal(fastBlockDescendant.block, time))
                .isEqualTo(ImportResult.INVALID_BLOCK);

        // check for correct state rollback
        assertThat(bc.getBestBlock()).isEqualTo(slowerBlockDescendant.block);
        assertThat(bc.getRepository().getRoot())
                .isEqualTo(slowerBlockDescendant.block.getStateRoot());
        assertThat(bc.getTotalDifficulty())
                .isEqualTo(
                        bc.getRepository()
                                .getBlockStore()
                                .getTotalDifficultyForHash(slowerBlockDescendant.block.getHash()));
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

        AionBlock invalidBlock =
                bc.createNewBlock(bc.getBestBlock(), Collections.emptyList(), true);
        invalidBlock.getHeader().setDifficulty(BigInteger.ONE.toByteArray());

        // attempting to add invalid block
        assertThat(bc.tryToConnect(invalidBlock)).isEqualTo(ImportResult.INVALID_BLOCK);

        // check for correct state rollback
        assertThat(bc.getBestBlock()).isEqualTo(block);
        assertThat(bc.getRepository().getRoot()).isEqualTo(block.getStateRoot());
        assertThat(bc.getTotalDifficulty())
                .isEqualTo(
                        bc.getRepository()
                                .getBlockStore()
                                .getTotalDifficultyForHash(block.getHash()));
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

        AionBlock block =
                sourceChain.createNewBlockInternal(
                                sourceChain.genesis, Arrays.asList(deployTx), true, time / 10_000L)
                        .block;

        Pair<ImportResult, AionBlockSummary> connectResult =
                sourceChain.tryToConnectAndFetchSummary(block, (time += 10), true);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);
        assertThat(receipt.isSuccessful()).isTrue();

        ImportResult result = connectResult.getLeft();
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        result = testChain.tryToConnectInternal(block, time);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        assertThat(testChain.getRepository().getRoot())
                .isEqualTo(sourceChain.getRepository().getRoot());

        Address contract = receipt.getTransaction().getContractAddress();
        // add a block with transactions to both
        List<AionTransaction> txs = generateTransactions(20, accounts, sourceChain.getRepository());

        block =
                sourceChain.createNewBlockInternal(
                                sourceChain.getBestBlock(), txs, true, time / 10_000L)
                        .block;

        result = sourceChain.tryToConnectInternal(block, (time += 10));
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        result = testChain.tryToConnectInternal(block, time);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        assertThat(testChain.getRepository().getRoot())
                .isEqualTo(sourceChain.getRepository().getRoot());

        // create a slow / fast block distinction
        AionTransaction callTx = callSetValue2(sender, contract, 5, 6, BigInteger.ONE);
        AionBlock fastBlock =
                sourceChain.createNewBlockInternal(
                                sourceChain.getBestBlock(),
                                Arrays.asList(callTx),
                                true,
                                time / 10_000L)
                        .block;

        callTx = callSetValue2(sender, contract, 1, 9, BigInteger.ONE);
        AionBlock slowBlock =
                new AionBlock(
                        sourceChain.createNewBlockInternal(
                                        sourceChain.getBestBlock(),
                                        Arrays.asList(callTx),
                                        true,
                                        time / 10_000L)
                                .block);

        slowBlock.getHeader().setTimestamp(time / 10_000L + 100);

        time += 100;

        // sourceChain imports only fast block
        assertThat(sourceChain.tryToConnectInternal(fastBlock, time))
                .isEqualTo(ImportResult.IMPORTED_BEST);

        // testChain imports both blocks
        assertThat(testChain.tryToConnectInternal(fastBlock, time))
                .isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(testChain.tryToConnectInternal(slowBlock, time))
                .isEqualTo(ImportResult.IMPORTED_NOT_BEST);

        // build two blocks with different contract storage calls
        // the second block gets a higher total difficulty
        callTx = callSetValue(sender, contract, 5, BigInteger.TWO);

        AionBlock lowBlock =
                testChain.createNewBlockInternal(
                                slowBlock, Arrays.asList(callTx), true, time / 10_000L + 101)
                        .block;

        callTx = callSetValue(sender, contract, 9, BigInteger.TWO);
        AionBlock highBlock =
                sourceChain.createNewBlockInternal(
                                fastBlock, Arrays.asList(callTx), true, time / 10_000L)
                        .block;

        // System.out.println("***highBlock TD: " + highBlock.getDifficultyBI());
        // System.out.println("***lowBlock TD: " + lowBlock.getDifficultyBI());
        assertThat(highBlock.getDifficultyBI()).isGreaterThan(lowBlock.getDifficultyBI());

        time += 100;

        // build first chain with highBlock applied directly
        connectResult = sourceChain.tryToConnectAndFetchSummary(highBlock, time, true);
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
        assertThat(testChain.tryToConnectInternal(lowBlock, time))
                .isEqualTo(ImportResult.IMPORTED_BEST);

        // next import highBlock causing the fork
        connectResult = testChain.tryToConnectAndFetchSummary(highBlock, time, true);
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

        AionTransaction contractDeploymentTx =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        ByteUtil.hexStringToBytes(contractCode),
                        5_000_000L,
                        10_123_456_789L);

        contractDeploymentTx.sign(sender);

        return contractDeploymentTx;
    }

    private AionTransaction callSetValue(
            ECKey sender, Address contract, int digit, BigInteger nonce) {
        // calls setValue(digit)
        if (digit < 0 || digit > 9) {
            return null; // should actually be a digit
        }
        // code for contract call
        String contractCode = "62eb702a0000000000000000000000000000000" + digit;

        AionTransaction contractCallTx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        Hex.decode(contractCode),
                        2_000_000L,
                        10_123_456_789L);

        contractCallTx.sign(sender);

        return contractCallTx;
    }

    private AionTransaction callSetValue2(
            ECKey sender, Address contract, int digit1, int digit2, BigInteger nonce) {
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

        AionTransaction contractCallTx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        Hex.decode(contractCode),
                        2_000_000L,
                        10_123_456_789L);

        contractCallTx.sign(sender);

        return contractCallTx;
    }

    /*
     * Tests VM update behaviour from an external perspective
     */
}

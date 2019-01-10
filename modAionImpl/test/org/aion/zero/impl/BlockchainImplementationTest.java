package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.BlockchainTestUtils.generateRandomChain;

import java.util.ArrayList;
import java.util.List;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.crypto.ECKey;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

/**
 * Class for testing methods from {@link AionBlockchainImpl} that are not part of specific use
 * cases.
 *
 * @author Alexandra Roatis
 */
public class BlockchainImplementationTest {

    private static final List<ECKey> accounts = BlockchainTestUtils.generateAccounts(10);
    private static final int MAX_TX_PER_BLOCK = 30;

    /**
     * In TOP mode only the top K blocks have a stored state. Blocks older than the top K are have
     * restrictions due to pruning.
     */
    @Test
    public void testIsPruneRestricted_wTopState() {
        // number of blocks stored by the blockchain
        int stored = 150;
        // the maximum height considered by this test
        int height = 200;

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(new MockRepositoryConfig(new CfgPrune(stored)))
                        .withDefaultAccounts(accounts)
                        .build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // creating (height) blocks
        long time = System.currentTimeMillis();
        for (int i = 0; i < height; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // testing restriction for unrestricted blocks: height to (height - stored + 1)
        for (int i = height; i >= height - stored + 1; i--) {
            assertThat(chain.isPruneRestricted(i)).isFalse();
            // ensure the state exists
            assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isTrue();
        }

        // testing restriction for restricted blocks: (height - stored) to 0
        for (int i = height - stored; i >= 0; i--) {
            assertThat(chain.isPruneRestricted(i)).isTrue();
            // ensure the state is missing
            if (i < height - stored) {
                assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isFalse();
            } else {
                // NOTE: state at (height - stored) exists, but is already restricted
                assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isTrue();
            }
        }
    }

    /**
     * In FULL mode the state is stored for all blocks. There are no restrictions due to pruning.
     */
    @Test
    public void testIsPruneRestricted_wFullState() {
        // the maximum height considered by this test
        int height = 200;

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(new MockRepositoryConfig(new CfgPrune(false)))
                        .withDefaultAccounts(accounts)
                        .build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // creating (height) blocks
        long time = System.currentTimeMillis();
        for (int i = 0; i < height; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // testing restriction for unrestricted blocks
        for (int i = height; i >= 0; i--) {
            assertThat(chain.isPruneRestricted(i)).isFalse();
            // ensure the state exists
            assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isTrue();
        }
    }

    /**
     * In SPREAD mode the top K blocks and the blocks that are multiples of the archive rate have a
     * stored state. There are no restrictions due to pruning.
     */
    @Test
    public void testIsPruneRestricted_wSpreadState() {
        // number of blocks stored by the blockchain
        int stored = 150;
        // the maximum height considered by this test
        int height = 1200;
        // the interval at which blocks are indexed
        int index = 1000;

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(new MockRepositoryConfig(new CfgPrune(stored, index)))
                        .withDefaultAccounts(accounts)
                        .build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // creating (height) blocks
        long time = System.currentTimeMillis();
        for (int i = 0; i < height; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 100000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // testing restriction for unrestricted blocks
        for (int i = height; i >= 0; i--) {
            assertThat(chain.isPruneRestricted(i)).isFalse();
            if (i % index == 0 || i >= height - stored) {
                // ensure the state exists
                assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isTrue();
            } else {
                // ensure the state is missing
                assertThat(repo.isValidRoot(chain.getBlockByNumber(i).getStateRoot())).isFalse();
            }
        }
    }

    @Test(expected = NullPointerException.class)
    public void testGetTotalDifficultyByHash_wNull() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder().withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        chain.getTotalDifficultyByHash(null);
    }

    @Test
    public void testSkipTryToConnect() {
        // the maximum height considered by this test
        int height = 200;

        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts(accounts)
                        .build();

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        AionBlock block;
        List<AionTransaction> txs;

        // creating (height) blocks
        long time = System.currentTimeMillis();
        for (int i = 0; i < height; i++) {
            txs = BlockchainTestUtils.generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);
            block =
                    chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L)
                            .block;
            assertThat(chain.tryToConnectInternal(block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);

            for (int j = 0; j < block.getNumber() + 100; j++) {
                if (j < block.getNumber() - 32 || j > block.getNumber() + 32) {
                    // check outside import range
                    assertThat(chain.skipTryToConnect(j)).isTrue();
                } else {
                    // check inside import range
                    assertThat(chain.skipTryToConnect(j)).isFalse();
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetListOfHashesEndWith_wNullHash() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 6, 2, accounts, MAX_TX_PER_BLOCK);

        chain.getListOfHashesEndWith(null, 1);
    }

    @Test
    public void testGetListOfHeadersEndWith_wGenesisBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 6, 2, accounts, MAX_TX_PER_BLOCK);

        AionBlock genesis = chain.getGenesis();
        byte[] genesisHash = genesis.getHash();

        int qty;
        // get one block
        qty = 1;
        assertEndWith_expectedOne(chain, genesisHash, qty);

        // get list of 10 from genesis
        qty = 10; // larger than block height
        assertEndWith_expectedOne(chain, genesisHash, qty);

        // get list of -10 from genesis
        qty = -10; // negative value
        assertEndWith_expectedOne(chain, genesisHash, qty);

        // get list of 0 from genesis
        qty = 0; // zero blocks requested
        assertEndWith_expectedOne(chain, genesisHash, qty);
    }

    @Test
    public void testGetListOfHeadersEndWith_wBestBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 12, 2, accounts, MAX_TX_PER_BLOCK);

        AionBlock best = chain.getBestBlock();
        assertThat(best.getNumber()).isGreaterThan(0L);
        byte[] bestHash = best.getHash();

        // expected result with qty >= chain height
        List<ByteArrayWrapper> expectedAll = new ArrayList<>();
        AionBlock block = best;
        while (block.getNumber() > 0) {
            expectedAll.add(ByteArrayWrapper.wrap(block.getHash()));
            block = chain.getBlockByHash(block.getParentHash());
        }

        int qty;
        // get one block
        qty = 1;
        assertEndWith_expectedOne(chain, bestHash, qty);

        // get list of chain height from best block
        qty = (int) best.getNumber(); // chain height
        assertEndWith(chain, bestHash, qty, expectedAll);

        // get list of half from best block
        qty = (int) (best.getNumber() / 2);

        List<ByteArrayWrapper> expectedHalf = new ArrayList<>();
        block = best;
        for (int i = 0; i < qty; i++) {
            expectedHalf.add(ByteArrayWrapper.wrap(block.getHash()));
            block = chain.getBlockByHash(block.getParentHash());
        }

        assertEndWith(chain, bestHash, qty, expectedHalf);

        // get list of 20 from genesis
        qty = 24; // larger than block height
        expectedAll.add(ByteArrayWrapper.wrap(chain.getGenesis().getHash()));
        assertEndWith(chain, bestHash, qty, expectedAll);

        // get list of -10 from best block
        qty = -10; // negative value
        assertEndWith_expectedOne(chain, bestHash, qty);

        // get list of 0 from best block
        qty = 0; // zero blocks requested
        assertEndWith_expectedOne(chain, bestHash, qty);
    }

    public static void assertEndWith_expectedOne(StandaloneBlockchain chain, byte[] hash, int qty) {
        List<byte[]> hashes = chain.getListOfHashesEndWith(hash, qty);
        assertThat(hashes.size()).isEqualTo(1);
        assertThat(hashes.get(0)).isEqualTo(hash);
    }

    public static void assertEndWith(
            StandaloneBlockchain chain, byte[] hash, int qty, List<ByteArrayWrapper> expected) {
        List<ByteArrayWrapper> hashes = new ArrayList<>();
        chain.getListOfHashesEndWith(hash, qty).forEach(h -> hashes.add(ByteArrayWrapper.wrap(h)));
        assertThat(hashes.size()).isEqualTo(expected.size());
        assertThat(hashes).isEqualTo(expected);
    }

    @Test
    public void testGetListOfHeadersStartFromBlock_wGenesisBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 12, 2, accounts, MAX_TX_PER_BLOCK);

        AionBlock genesis = chain.getGenesis();
        AionBlock best = chain.getBestBlock();

        assertThat(best.getNumber()).isGreaterThan(0L);
        byte[] genesisHash = genesis.getHash();

        // expected result with qty >= chain height
        List<ByteArrayWrapper> expectedAll = new ArrayList<>();
        AionBlock block = chain.getBlockByHash(best.getParentHash());
        while (block.getNumber() > 0) {
            expectedAll.add(0, ByteArrayWrapper.wrap(block.getHash()));
            block = chain.getBlockByHash(block.getParentHash());
        }
        expectedAll.add(0, ByteArrayWrapper.wrap(genesis.getHash()));

        int qty;
        // get one block
        qty = 1;
        assertStartFromBlock_expectedOne(chain, genesisHash, 0, qty);

        // get list of chain height from genesis block
        qty = (int) best.getNumber(); // chain height
        assertStartFromBlock(chain, 0, qty, expectedAll);

        // get list of half from genesis block
        qty = (int) (best.getNumber() / 2);

        List<ByteArrayWrapper> expectedHalf = new ArrayList<>();
        block = chain.getBlockByNumber(qty - 1);
        for (int i = 0; i < qty; i++) {
            expectedHalf.add(0, ByteArrayWrapper.wrap(block.getHash()));
            block = chain.getBlockByHash(block.getParentHash());
        }
        assertStartFromBlock(chain, 0, qty, expectedHalf);

        // get list of 20 from genesis
        qty = 24; // larger than block height
        expectedAll.add(ByteArrayWrapper.wrap(best.getHash()));
        assertStartFromBlock(chain, 0, qty, expectedAll);

        // get list of -10 from genesis block
        qty = -10; // negative value
        assertStartFromBlock_expectedOne(chain, genesisHash, 0, qty);

        // get list of 0 from genesis block
        qty = 0; // zero blocks requested
        assertStartFromBlock_expectedOne(chain, genesisHash, 0, qty);
    }

    @Test
    public void testGetListOfHeadersStartFromBlock_wBestBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 6, 2, accounts, MAX_TX_PER_BLOCK);

        AionBlock best = chain.getBestBlock();
        byte[] bestHash = best.getHash();
        long bestNumber = best.getNumber();

        int qty;
        // get one block
        qty = 1;
        assertStartFromBlock_expectedOne(chain, bestHash, bestNumber, qty);

        // get list of 10 from genesis
        qty = 10; // larger than block height
        assertStartFromBlock_expectedOne(chain, bestHash, bestNumber, qty);

        // get list of -10 from genesis
        qty = -10; // negative value
        assertStartFromBlock_expectedOne(chain, bestHash, bestNumber, qty);

        // get list of 0 from genesis
        qty = 0; // zero blocks requested
        assertStartFromBlock_expectedOne(chain, bestHash, bestNumber, qty);
    }

    public static void assertStartFromBlock_expectedOne(
            StandaloneBlockchain chain, byte[] hash, long number, int qty) {
        List<byte[]> hashes = chain.getListOfHashesStartFromBlock(number, qty);
        assertThat(hashes.size()).isEqualTo(1);
        assertThat(hashes.get(0)).isEqualTo(hash);
    }

    public static void assertStartFromBlock(
            StandaloneBlockchain chain, long number, int qty, List<ByteArrayWrapper> expected) {
        List<ByteArrayWrapper> hashes = new ArrayList<>();
        chain.getListOfHashesStartFromBlock(number, qty)
                .forEach(h -> hashes.add(ByteArrayWrapper.wrap(h)));
        assertThat(hashes.size()).isEqualTo(expected.size());
        assertThat(hashes).isEqualTo(expected);
    }

    @Test
    public void testGetListOfHeadersStartFrom_wNullBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 6, 2, accounts, MAX_TX_PER_BLOCK);

        assertThat(chain.getListOfHeadersStartFrom(chain.getBestBlock().getNumber() + 1, 1))
                .isEmpty();
        assertThat(chain.getListOfHeadersStartFrom(chain.getBestBlock().getNumber() + 10, 1))
                .isEmpty();
    }

    @Test
    public void testGetListOfHeadersStartFrom_wGenesisBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 12, 2, accounts, MAX_TX_PER_BLOCK);

        AionBlock genesis = chain.getGenesis();
        AionBlock best = chain.getBestBlock();

        assertThat(best.getNumber()).isGreaterThan(0L);
        byte[] genesisHash = genesis.getHash();

        // expected result with qty >= chain height
        List<ByteArrayWrapper> expectedAll = new ArrayList<>();
        AionBlock block = chain.getBlockByHash(best.getParentHash());
        while (block.getNumber() > 0) {
            expectedAll.add(0, ByteArrayWrapper.wrap(block.getHash()));
            block = chain.getBlockByHash(block.getParentHash());
        }
        expectedAll.add(0, ByteArrayWrapper.wrap(genesis.getHash()));

        int qty;
        // get one block
        qty = 1;
        assertStartFrom_expectedOne(chain, genesisHash, 0, qty);

        // get list of chain height from genesis block
        qty = (int) best.getNumber(); // chain height
        assertStartFrom(chain, 0, qty, expectedAll);

        // get list of half from genesis block
        qty = (int) (best.getNumber() / 2);

        List<ByteArrayWrapper> expectedHalf = new ArrayList<>();
        block = chain.getBlockByNumber(qty - 1);
        for (int i = 0; i < qty; i++) {
            expectedHalf.add(0, ByteArrayWrapper.wrap(block.getHash()));
            block = chain.getBlockByHash(block.getParentHash());
        }
        assertStartFrom(chain, 0, qty, expectedHalf);

        // get list of 20 from genesis
        qty = 24; // larger than block height
        expectedAll.add(ByteArrayWrapper.wrap(best.getHash()));
        assertStartFrom(chain, 0, qty, expectedAll);

        // get list of -10 from genesis block
        qty = -10; // negative value
        assertThat(chain.getListOfHeadersStartFrom(0, qty)).isEmpty();

        // get list of 0 from genesis block
        qty = 0; // zero blocks requested
        assertThat(chain.getListOfHeadersStartFrom(0, qty)).isEmpty();
    }

    @Test
    public void testGetListOfHeadersStartFrom_wBestBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 6, 2, accounts, MAX_TX_PER_BLOCK);

        AionBlock best = chain.getBestBlock();
        byte[] bestHash = best.getHash();
        long bestNumber = best.getNumber();

        int qty;
        // get one block
        qty = 1;
        assertStartFrom_expectedOne(chain, bestHash, bestNumber, qty);

        // get list of 10 from genesis
        qty = 10; // larger than block height
        assertStartFrom_expectedOne(chain, bestHash, bestNumber, qty);

        // get list of -10 from genesis
        qty = -10; // negative value
        assertThat(chain.getListOfHeadersStartFrom(bestNumber, qty)).isEmpty();

        // get list of 0 from genesis
        qty = 0; // zero blocks requested
        assertThat(chain.getListOfHeadersStartFrom(bestNumber, qty)).isEmpty();
    }

    public static void assertStartFrom_expectedOne(
            StandaloneBlockchain chain, byte[] hash, long number, int qty) {
        List<A0BlockHeader> hashes = chain.getListOfHeadersStartFrom(number, qty);
        assertThat(hashes.size()).isEqualTo(1);
        assertThat(hashes.get(0).getHash()).isEqualTo(hash);
    }

    public static void assertStartFrom(
            StandaloneBlockchain chain, long number, int qty, List<ByteArrayWrapper> expected) {
        List<ByteArrayWrapper> hashes = new ArrayList<>();
        chain.getListOfHeadersStartFrom(number, qty)
                .forEach(h -> hashes.add(ByteArrayWrapper.wrap(h.getHash())));
        assertThat(hashes.size()).isEqualTo(expected.size());
        assertThat(hashes).isEqualTo(expected);
    }
}

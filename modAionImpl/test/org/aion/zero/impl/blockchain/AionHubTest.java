package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.Thread.sleep;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateRandomChainWithoutTransactions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.blockchain.AionImpl.PendingTxCallback;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.trie.TrieImpl;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class AionHubTest {

    private void checkHubNullity(AionHub hub) {
        assertThat(hub).isNotNull();
        assertThat(hub.getBlockchain()).isNotNull();
        assertThat(hub.getPendingState()).isNotNull();
        assertThat(hub.getP2pMgr()).isNotNull();
        assertThat(hub.getRepository()).isNotNull();
        assertThat(hub.getEventMgr()).isNotNull();
        assertThat(hub.getSyncMgr()).isNotNull();
        assertThat(hub.getBlockStore()).isNotNull();
        assertThat(hub.getPropHandler()).isNotNull();
    }

    @BeforeClass
    public static void setup() {
        // logging to see errors
        Map<LogEnum, LogLevel> cfg = new HashMap<>();
        cfg.put(LogEnum.GEN, LogLevel.INFO);
        cfg.put(LogEnum.CONS, LogLevel.INFO);
        AionLoggerFactory.initAll(cfg);

        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @AfterClass
    public static void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

    @After
    public void exitAndKillThreads() {
        boolean wait = true;

        while (wait) {
            wait = false;
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.isAlive()) {
                    String name = t.getName();

                    if (name.contains("p2p-") || name.contains("EpPS")) {
                        t.interrupt();
                        wait = true;
                    }
                }
            }
        }
    }

    @Test
    public void MockHubInst_wStartAtGenesis() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;
        chain.setBestBlock(chain.getGenesis());

        AionHub hub = AionHub.createForTesting(CfgAion.inst(), chain, chain.getRepository(),
            new PendingTxCallback(new ArrayList<>()));
        checkHubNullity(hub);

        Block blk = hub.getStartingBlock();
        assertThat(blk).isNotNull();
        assertThat(blk.getNumber()).isEqualTo(0);

        hub.close();
        assertThat(hub.isRunning()).isFalse();
    }

    @Test
    public void MockHubInst_wStartAtBlock() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;
        int expectedStartBlock = 6;
        generateRandomChainWithoutTransactions(chain, expectedStartBlock, 1);

        AionHub hub = AionHub.createForTesting(CfgAion.inst(), chain, chain.getRepository(),
            new PendingTxCallback(new ArrayList<>()));
        checkHubNullity(hub);

        Block blk = hub.getStartingBlock();
        assertThat(blk).isNotNull();
        assertThat(blk.getNumber()).isEqualTo((long) expectedStartBlock);

        hub.close();
        assertThat(hub.isRunning()).isFalse();
    }

    @Test
    public void MockHubInst_wStartRecovery() {

        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts().build();

        int NUMBER_OF_BLOCKS = 10, MAX_TX_PER_BLOCK = 60;

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // first half of blocks will be correct
        long time = System.currentTimeMillis();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            txs =
                    BlockchainTestUtils.generateTransactions(
                            MAX_TX_PER_BLOCK, bundle.privateKeys, repo);
            context = chain.createNewMiningBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }

        // second half of blocks will miss the state root
        List<byte[]> statesToDelete = new ArrayList<>();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            txs =
                    BlockchainTestUtils.generateTransactions(
                            MAX_TX_PER_BLOCK, bundle.privateKeys, repo);
            context = chain.createNewMiningBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            statesToDelete.add(context.block.getStateRoot());
            blocksToImport.add(context.block);
        }

        Block bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        // delete some world state root entries from the database
        TrieImpl trie = (TrieImpl) repo.getWorldState();
        ByteArrayKeyValueDatabase database = repo.getStateDatabase();

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        // recovery should be called by loadBlockchain()
        AionHub hub = AionHub.createForTesting(CfgAion.inst(), chain, chain.getRepository(),
            new PendingTxCallback(new ArrayList<>()));
        checkHubNullity(hub);

        Block blk = hub.getStartingBlock();
        assertThat(blk).isNotNull();
        assertThat(blk.getNumber()).isEqualTo((long) NUMBER_OF_BLOCKS);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();

        hub.close();
        assertThat(hub.isRunning()).isFalse();
    }

    @Test
    public void MockHubInst_wStartRollback() {

        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts().build();

        int NUMBER_OF_BLOCKS = 10, MAX_TX_PER_BLOCK = 60;

        StandaloneBlockchain chain = bundle.bc;
        AionRepositoryImpl repo = chain.getRepository();
        BlockContext context;
        List<AionTransaction> txs;

        // first half of blocks will be correct
        long time = System.currentTimeMillis();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            txs =
                    BlockchainTestUtils.generateTransactions(
                            MAX_TX_PER_BLOCK, bundle.privateKeys, repo);
            context = chain.createNewMiningBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
        }
        BigInteger td6 = BigInteger.ZERO;

        // second half of blocks will miss the state root
        List<byte[]> statesToDelete = new ArrayList<>();
        List<AionBlock> blocksToImport = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_BLOCKS / 2; i++) {
            txs =
                    BlockchainTestUtils.generateTransactions(
                            MAX_TX_PER_BLOCK, bundle.privateKeys, repo);
            context = chain.createNewMiningBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
            assertThat(chain.tryToConnectInternal(context.block, (time += 10)))
                    .isEqualTo(ImportResult.IMPORTED_BEST);
            statesToDelete.add(context.block.getStateRoot());
            blocksToImport.add(context.block);

            if (context.block.getNumber() == 6) {
                td6 = context.block.getTotalDifficulty();
            }
        }

        Block bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        BigInteger td2 = chain.getTotalDifficulty();
        assertTrue(td2.longValue() > td6.longValue());

        // delete some world state root entries from the database
        TrieImpl trie = (TrieImpl) repo.getWorldState();
        ByteArrayKeyValueDatabase database = repo.getStateDatabase();

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();
        assertNotEquals(td6, chain.getTotalDifficulty());

        // Also, missing the block DB
        blocksToImport.remove(0);
        for (AionBlock b : blocksToImport) {
            repo.getBlockDatabase().delete(b.getHash());
        }
        repo.flush();

        // recovery should be called by loadBlockchain()
        AionHub hub = AionHub.createForTesting(CfgAion.inst(), chain, chain.getRepository(),
            new PendingTxCallback(new ArrayList<>()));
        checkHubNullity(hub);

        assertEquals(td6, chain.getTotalDifficulty());

        Block blk = hub.getStartingBlock();
        assertThat(blk).isNotNull();
        assertThat(blk.getNumber()).isEqualTo(6);
        // ensure that the world state is ok
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();

        hub.close();
        assertThat(hub.isRunning()).isFalse();
    }
}

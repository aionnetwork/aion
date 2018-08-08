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
import static java.lang.Thread.sleep;
import static org.aion.zero.impl.BlockchainTestUtils.generateRandomChainWithoutTransactions;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.trie.TrieImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.After;
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
        Map<String, String> cfg = new HashMap<>();
        cfg.put("GEN", "INFO");
        cfg.put("CONS", "INFO");
        cfg.put("DB", "ERROR");

        AionLoggerFactory.init(cfg);
    }

    private static boolean deleteRecursively(File file) {
        Path path = file.toPath();
        try {
            java.nio.file.Files.walkFileTree(
                    path,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(
                                final Path file, final BasicFileAttributes attrs)
                                throws IOException {
                            java.nio.file.Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(
                                final Path file, final IOException e) {
                            return handleException(e);
                        }

                        private FileVisitResult handleException(final IOException e) {
                            e.printStackTrace();
                            return FileVisitResult.TERMINATE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(
                                final Path dir, final IOException e) throws IOException {
                            if (e != null) {
                                return handleException(e);
                            }
                            java.nio.file.Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
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

                    if (name.contains("p2p-") || name.contains("pool-") || name.contains("EpPS")) {
                        t.interrupt();
                        wait = true;
                    }
                }
            }
        }
    }

    @Test
    public void AionHubGetRepoVersion() {
        assertThat(AionHub.getRepoVersion()).isEqualTo(Version.REPO_VERSION);
    }

    @Test
    public void AionHubInst() {
        AionHub hub = AionHub.inst();
        checkHubNullity(hub);

        hub.close();
        assertThat(hub.isRunning()).isFalse();

        // clear out the database directory
        File dbDir = new File(CfgAion.inst().getBasePath(), CfgAion.inst().getDb().getPath());
        assertThat(deleteRecursively(dbDir)).isTrue();
    }

    @Test
    public void MockHubInst_wStartAtGenesis() {
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;
        chain.setBestBlock(chain.getGenesis());

        CfgAion.inst().setGenesis(chain.getGenesis());

        AionHub hub = AionHub.createForTesting(CfgAion.inst(), chain, chain.getRepository());
        checkHubNullity(hub);

        AionBlock blk = hub.getStartingBlock();
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

        CfgAion.inst().setGenesis(chain.getGenesis());

        AionHub hub = AionHub.createForTesting(CfgAion.inst(), chain, chain.getRepository());
        checkHubNullity(hub);

        AionBlock blk = hub.getStartingBlock();
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
            context = chain.createNewBlockInternal(chain.getBestBlock(), txs, true, time / 10000L);
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

        repo.flush();
        for (byte[] key : statesToDelete) {
            database.delete(key);
            assertThat(trie.isValidRoot(key)).isFalse();
        }

        // ensure that the world state was corrupted
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isFalse();

        CfgAion.inst().setGenesis(chain.getGenesis());

        // recovery should be called by loadBlockchain()
        AionHub hub = AionHub.createForTesting(CfgAion.inst(), chain, chain.getRepository());
        checkHubNullity(hub);

        AionBlock blk = hub.getStartingBlock();
        assertThat(blk).isNotNull();
        assertThat(blk.getNumber()).isEqualTo((long) NUMBER_OF_BLOCKS);

        // ensure that the blockchain is ok
        assertThat(chain.getBestBlockHash()).isEqualTo(bestBlock.getHash());
        // ensure that the world state is ok
        assertThat(trie.isValidRoot(chain.getBestBlock().getStateRoot())).isTrue();

        hub.close();
        assertThat(hub.isRunning()).isFalse();
    }
}

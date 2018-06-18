/* ******************************************************************************
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
 ******************************************************************************/
package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.BlockchainTestUtils.generateTransactions;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import org.aion.base.type.Hash256;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** @author Alexandra Roatis */
public class BlockchainConcurrentImportTest {

    private static final int CONCURRENT_THREADS_PER_TYPE = 30;
    private static final int MAIN_CHAIN_FREQUENCY = 5;
    private static final int TIME_OUT = 100; // in seconds
    private static final boolean DISPLAY_MESSAGES = false;

    private static StandaloneBlockchain testChain;
    private static StandaloneBlockchain sourceChain;
    private static List<AionBlock> knownBlocks = new ArrayList<>();

    private static final List<ECKey> accounts = generateAccounts(10);
    private static final int MAX_TX_PER_BLOCK = 60;

    @BeforeClass
    public static void setup() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("DB", "ERROR");

        AionLoggerFactory.init(cfg);

        // build a blockchain with CONCURRENT_THREADS_PER_TYPE blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        testChain = bundle.bc;

        builder = new StandaloneBlockchain.Builder();
        sourceChain =
                builder.withValidatorConfiguration("simple")
                        .withDefaultAccounts(accounts)
                        .build()
                        .bc;

        generateBlocks();
    }

    private static void generateBlocks() {
        System.out.format("%nGenerating %d input blocks...%n", CONCURRENT_THREADS_PER_TYPE);

        Random rand = new Random();
        AionBlock parent, block, mainChain;
        mainChain = sourceChain.getGenesis();
        knownBlocks.add(mainChain);

        List<AionTransaction> txs;
        AionRepositoryImpl sourceRepo = sourceChain.getRepository();
        long time = System.currentTimeMillis();

        for (int i = 0; i < CONCURRENT_THREADS_PER_TYPE; i++) {

            // ensuring that we add to the main chain at least every MAIN_CHAIN_FREQUENCY block
            if (i % MAIN_CHAIN_FREQUENCY == 0) {
                // the parent will be the main chain
                parent = mainChain;
            } else {
                // the parent is a random already imported block
                parent = knownBlocks.get(rand.nextInt(knownBlocks.size()));
            }

            // generate transactions for correct root
            byte[] originalRoot = sourceRepo.getRoot();
            sourceRepo.syncToRoot(parent.getStateRoot());
            txs = generateTransactions(MAX_TX_PER_BLOCK, accounts, sourceRepo);
            sourceRepo.syncToRoot(originalRoot);

            block = sourceChain.createNewBlockInternal(parent, txs, true, time / 10000L).block;
            block.setExtraData(String.valueOf(i).getBytes());

            ImportResult result = sourceChain.tryToConnectInternal(block, (time += 10));
            knownBlocks.add(block);
            if (result == ImportResult.IMPORTED_BEST) {
                mainChain = block;
            }

            if (DISPLAY_MESSAGES) {
                System.out.format(
                        "Created block with hash: %s, number: %6d, extra data: %6s, txs: %3d, import status: %20s %n",
                        block.getShortHash(),
                        block.getNumber(),
                        new String(block.getExtraData()),
                        block.getTransactionsList().size(),
                        result.toString());
            }
        }

        // all blocks except the genesis will be imported by the other chain
        knownBlocks.remove(sourceChain.getGenesis());
    }

    @AfterClass
    public static void teardown() {
        testChain.close();
        sourceChain.close();
    }

    /**
     * Adds a new thread for importing an already known block.
     *
     * @param _threads list of threads to be executed; the current thread will be added to this list
     * @param _chain the blockchain where the blocks will be imported
     * @param _block the block to import
     */
    private void addThread_tryToConnect(
            List<Runnable> _threads, StandaloneBlockchain _chain, AionBlock _block) {
        _threads.add(
                () -> {
                    testChain.assertEqualTotalDifficulty();
                    // importing the given block
                    ImportResult result = _chain.tryToConnect(_block);
                    testChain.assertEqualTotalDifficulty();

                    if (DISPLAY_MESSAGES) {
                        System.out.format(
                                "Import block with hash: %s, number: %6d, extra data: %6s, txs: %3d, status: %20s in thread: %20s %n",
                                _block.getShortHash(),
                                _block.getNumber(),
                                new String(_block.getExtraData()),
                                _block.getTransactionsList().size(),
                                result.toString(),
                                Thread.currentThread().getName());
                    }

                    // checking total difficulty
                    if (result == ImportResult.IMPORTED_BEST
                            || result == ImportResult.IMPORTED_NOT_BEST) {
                        AionBlockStore store = _chain.getBlockStore();

                        BigInteger tdFromStore = store.getTotalDifficultyForHash(_block.getHash());
                        BigInteger tdCalculated =
                                store.getTotalDifficultyForHash(_block.getParentHash())
                                        .add(_block.getDifficultyBI());

                        assertThat(tdFromStore).isEqualTo(tdCalculated);
                        assertThat(tdCalculated)
                                .isEqualTo(
                                        _chain.getTotalDifficultyByHash(
                                                new Hash256(_block.getHash())));

                        if (result == ImportResult.IMPORTED_BEST) {
                            // can't check for equality since other blocks may have already been
                            // imported
                            assertThat(store.getTotalDifficulty()).isAtLeast(tdFromStore);
                        }
                    }
                });
    }

    /**
     * Adds a new thread for importing a block from the given queue.
     *
     * @param _threads list of threads to be executed; the current thread will be added to this list
     * @param _chain the blockchain where the blocks will be imported
     * @param _queue a queue containing new blocks
     * @param _imported a list of blocks that have been imported; blocks successfully imported are
     *     added to this list
     */
    private void addThread_tryToConnect(
            List<Runnable> _threads,
            StandaloneBlockchain _chain,
            ConcurrentLinkedQueue<AionBlock> _queue,
            ConcurrentLinkedQueue<AionBlock> _imported) {
        _threads.add(
                () -> {

                    // get next block from queue
                    AionBlock _block = _queue.poll();

                    if (_block != null) {

                        testChain.assertEqualTotalDifficulty();
                        // importing the given block
                        ImportResult result = _chain.tryToConnect(_block);
                        testChain.assertEqualTotalDifficulty();

                        if (DISPLAY_MESSAGES) {
                            System.out.format(
                                    "Import block with hash: %s, number: %6d, extra data: %6s, txs: %3d, status: %20s in thread: %20s (from queue)%n",
                                    _block.getShortHash(),
                                    _block.getNumber(),
                                    new String(_block.getExtraData()),
                                    _block.getTransactionsList().size(),
                                    result.toString(),
                                    Thread.currentThread().getName());
                        }

                        // checking total difficulty
                        if (result == ImportResult.IMPORTED_BEST
                                || result == ImportResult.IMPORTED_NOT_BEST) {
                            AionBlockStore store = _chain.getBlockStore();

                            BigInteger tdFromStore =
                                    store.getTotalDifficultyForHash(_block.getHash());
                            BigInteger tdCalculated =
                                    store.getTotalDifficultyForHash(_block.getParentHash())
                                            .add(_block.getDifficultyBI());

                            assertThat(tdFromStore).isEqualTo(tdCalculated);
                            assertThat(tdCalculated)
                                    .isEqualTo(
                                            _chain.getTotalDifficultyByHash(
                                                    new Hash256(_block.getHash())));

                            if (result == ImportResult.IMPORTED_BEST) {
                                // can't check for equality since other blocks may have already been
                                // imported
                                assertThat(store.getTotalDifficulty()).isAtLeast(tdFromStore);
                            }

                            // save the block for later comparison
                            _imported.add(_block);
                        }
                    } else {
                        if (DISPLAY_MESSAGES) {
                            System.out.format(
                                    "%62sNo block in queue. Skipping import in thread: %20s %n",
                                    " ", Thread.currentThread().getName());
                        }
                    }
                });
    }

    /**
     * Adds a new thread for creating a new block with a parent among the already known blocks.
     *
     * @param _threads list of threads to be executed; the current thread will be added to this list
     * @param _chain the blockchain where the blocks will be imported
     * @param _parent the block that will be the parent of the newly created block
     * @param _id number used for identifying the block; added as extra data
     * @param _queue a queue for storing the new blocks; to be imported by a separate thread
     */
    private void addThread_createNewBlock(
            List<Runnable> _threads,
            StandaloneBlockchain _chain,
            AionBlock _parent,
            int _id,
            ConcurrentLinkedQueue<AionBlock> _queue) {
        _threads.add(
                () -> {

                    // creating block only if parent already imported
                    if (_chain.isBlockExist(_parent.getHash())) {

                        testChain.assertEqualTotalDifficulty();

                        // only some of these txs may be valid
                        // cannot syncToRoot due to concurrency issues
                        AionRepositoryImpl repo = _chain.getRepository();
                        List<AionTransaction> txs =
                                generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);

                        AionBlock block = _chain.createNewBlock(_parent, txs, true);
                        block.setExtraData(String.valueOf(_id).getBytes());
                        testChain.assertEqualTotalDifficulty();

                        // checking if the new block was already imported
                        if (!_chain.isBlockExist(block.getHash())) {
                            // still adding this block
                            _queue.add(block);

                            if (DISPLAY_MESSAGES) {
                                System.out.format(
                                        "Create block with hash: %s, number: %6d, extra data: %6s, txs: %3d, parent: %20s in thread: %20s %n",
                                        block.getShortHash(),
                                        block.getNumber(),
                                        new String(block.getExtraData()),
                                        block.getTransactionsList().size(),
                                        _parent.getShortHash(),
                                        Thread.currentThread().getName());
                            }
                        } else {
                            if (DISPLAY_MESSAGES) {
                                System.out.format(
                                        "%57sBlock already imported. Skipping create in thread: %20s %n",
                                        " ", Thread.currentThread().getName());
                            }
                        }
                    } else {
                        if (DISPLAY_MESSAGES) {
                            System.out.format(
                                    "%60sParent not imported. Skipping create in thread: %20s %n",
                                    " ", Thread.currentThread().getName());
                        }
                    }
                });
    }

    /**
     * Adds a new thread for creating a new block with a parent among the already known blocks.
     *
     * @param _threads list of threads to be executed; the current thread will be added to this list
     * @param _chain the blockchain where the blocks will be imported
     * @param _id number used for identifying the block; added as extra data
     * @param _queue a queue for storing the new blocks; to be imported by a separate thread
     * @param _startHeight blocks are created only if a minimum height is reached
     */
    private void addThread_createNewBlock(
            List<Runnable> _threads,
            StandaloneBlockchain _chain,
            int _id,
            ConcurrentLinkedQueue<AionBlock> _queue,
            int _startHeight) {
        _threads.add(
                () -> {

                    // parent will be main chain block
                    AionBlock _parent = _chain.getBestBlock();

                    if (_parent.getNumber() >= _startHeight) {

                        testChain.assertEqualTotalDifficulty();

                        // only some of these txs may be valid
                        // cannot syncToRoot due to concurrency issues
                        AionRepositoryImpl repo = _chain.getRepository();
                        List<AionTransaction> txs =
                                generateTransactions(MAX_TX_PER_BLOCK, accounts, repo);

                        AionBlock block = _chain.createNewBlock(_parent, txs, true);
                        block.setExtraData(String.valueOf(_id).getBytes());
                        testChain.assertEqualTotalDifficulty();

                        // still adding this block
                        _queue.add(block);

                        if (DISPLAY_MESSAGES) {
                            System.out.format(
                                    "Create block with hash: %s, number: %6d, extra data: %6s, txs: %3d, parent: %20s in thread: %20s (using getBestBlock) %n",
                                    block.getShortHash(),
                                    block.getNumber(),
                                    new String(block.getExtraData()),
                                    block.getTransactionsList().size(),
                                    _parent.getShortHash(),
                                    Thread.currentThread().getName());
                        }
                    } else {
                        if (DISPLAY_MESSAGES) {
                            System.out.format(
                                    "%51sParent not at minimum height. Skipping create in thread: %20s %n",
                                    " ", Thread.currentThread().getName());
                        }
                    }
                });
    }

    @Test
    public void testConcurrent() throws InterruptedException {
        List<Runnable> threads = new ArrayList<>();

        int start = (int) sourceChain.getBestBlock().getNumber() - 1;
        ConcurrentLinkedQueue<AionBlock> queue = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<AionBlock> imported = new ConcurrentLinkedQueue<>();

        int blockCount = CONCURRENT_THREADS_PER_TYPE + 1;
        for (AionBlock blk : knownBlocks) {
            // connect to known blocks
            addThread_tryToConnect(threads, testChain, blk);

            // add new blocks with known parent
            addThread_createNewBlock(threads, testChain, blk, blockCount, queue);
            blockCount++;

            // add new blocks with best block parent
            addThread_createNewBlock(threads, testChain, blockCount, queue, start);
            blockCount++;

            // connect to new blocks
            addThread_tryToConnect(threads, testChain, queue, imported);
        }

        // run threads while not at minimum height
        long height, targetHeight = sourceChain.getBestBlock().getNumber() + MAIN_CHAIN_FREQUENCY;
        boolean done = false;

        while (!done) {
            System.out.format("%nRunning the %d generated threads...%n", threads.size());

            assertConcurrent("Testing tryToConnect(...) ", threads, TIME_OUT);

            // checking height
            height = testChain.getBestBlock().getNumber();
            done = height >= targetHeight;
            System.out.format("Current height = %d. Target height = %d.%n", height, targetHeight);
        }

        // adding new blocks to source chain
        System.out.format("%nAdding new blocks to source chain for testing...%n");
        AionBlock block = imported.poll();

        while (block != null) {
            ImportResult result = sourceChain.tryToConnect(block);
            knownBlocks.add(block);

            if (DISPLAY_MESSAGES) {
                System.out.format(
                        "Importing block with hash: %s, number: %6d, extra data: %6s, txs: %3d, status: %20s%n",
                        block.getShortHash(),
                        block.getNumber(),
                        new String(block.getExtraData()),
                        block.getTransactionsList().size(),
                        result.toString());
            }
            block = imported.poll();
        }

        // comparing total diff for the two chains
        assertThat(testChain.getTotalDifficulty()).isEqualTo(sourceChain.getTotalDifficulty());
        assertThat(testChain.getCachedTotalDifficulty())
                .isEqualTo(sourceChain.getCachedTotalDifficulty());
        testChain.assertEqualTotalDifficulty();

        AionBlockStore sourceStore = sourceChain.getBlockStore();

        // comparing total diff for each block of the two chains
        for (AionBlock blk : knownBlocks) {
            assertThat(testChain.getBlockStore().getTotalDifficultyForHash(blk.getHash()))
                    .isEqualTo(sourceStore.getTotalDifficultyForHash(blk.getHash()));
            Hash256 hash = new Hash256(blk.getHash());
            assertThat(testChain.getTotalDifficultyByHash(hash))
                    .isEqualTo(sourceChain.getTotalDifficultyByHash(hash));
        }
    }

    /**
     * From <a
     * href="https://github.com/junit-team/junit4/wiki/multithreaded-code-and-concurrency">JUnit
     * Wiki on multithreaded code and concurrency</a>
     */
    public static void assertConcurrent(
            final String message,
            final List<? extends Runnable> runnables,
            final int maxTimeoutSeconds)
            throws InterruptedException {
        final int numThreads = runnables.size();
        final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
        final ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
        try {
            final CountDownLatch allExecutorThreadsReady = new CountDownLatch(numThreads);
            final CountDownLatch afterInitBlocker = new CountDownLatch(1);
            final CountDownLatch allDone = new CountDownLatch(numThreads);
            for (final Runnable submittedTestRunnable : runnables) {
                threadPool.submit(
                        () -> {
                            allExecutorThreadsReady.countDown();
                            try {
                                afterInitBlocker.await();
                                submittedTestRunnable.run();
                            } catch (final Throwable e) {
                                exceptions.add(e);
                            } finally {
                                allDone.countDown();
                            }
                        });
            }
            // wait until all threads are ready
            assertTrue(
                    "Timeout initializing threads! Perform long lasting initializations before passing runnables to assertConcurrent",
                    allExecutorThreadsReady.await(runnables.size() * 10, TimeUnit.MILLISECONDS));
            // start all test runners
            afterInitBlocker.countDown();
            assertTrue(
                    message + " timeout! More than" + maxTimeoutSeconds + "seconds",
                    allDone.await(maxTimeoutSeconds, TimeUnit.SECONDS));
        } finally {
            threadPool.shutdownNow();
        }
        if (!exceptions.isEmpty()) {
            for (Throwable e : exceptions) {
                e.printStackTrace();
            }
        }
        assertTrue(
                message + "failed with " + exceptions.size() + " exception(s):" + exceptions,
                exceptions.isEmpty());
    }
}

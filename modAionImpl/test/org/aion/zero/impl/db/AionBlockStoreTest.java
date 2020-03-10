package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.mcf.blockchain.Block;
import org.aion.util.TestResources;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.BlockUtil;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link AionBlockStore}.
 *
 * @author Alexandra Roatis
 */
public class AionBlockStoreTest {

    public static final Logger log = LoggerFactory.getLogger("DB");

    // simply mocking the dbs didn't work possibly because of the use of locks
    // for some reason index.size() gets called by store.getChainBlockByNumber(X)
    ByteArrayKeyValueDatabase index = new MockDB("index", log);
    ByteArrayKeyValueDatabase blocks = new MockDB("blocks", log);

    // returns a list of blocks in ascending order of height
    List<Block> consecutiveBlocks = TestResources.consecutiveBlocks(4);

    @Before
    public void openDatabases() {
        index.open();
        blocks.open();
    }

    @After
    public void closeDatabases() {
        index.close();
        blocks.close();
    }

    @Test
    public void testGetBlocksByRange_withGensisFirstBlock() {
        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        assertThat(store.getBlocksByRange(0L, 11L)).isNull();
    }

    @Test
    public void testGetBlocksByRange_withNullFirstBlock() {
        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(10L)).thenReturn(null);
        when(store.getBlocksByRange(10L, 11L)).thenCallRealMethod();

        assertThat(store.getBlocksByRange(10L, 11L)).isNull();
    }

    @Test
    public void testGetBlocksByRange_withSingleBlock() {
        Block block = consecutiveBlocks.get(0);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(10L)).thenReturn(block);
        when(store.getBlocksByRange(10L, 10L)).thenCallRealMethod();

        List<Block> returned = store.getBlocksByRange(10L, 10L);
        assertThat(returned.size()).isEqualTo(1);
        assertThat(returned).contains(block);
    }

    @Test
    public void testGetBlocksByRange_withDescendingOrder() {
        Block first = consecutiveBlocks.get(2);
        Block middle = consecutiveBlocks.get(1);
        Block last = consecutiveBlocks.get(0);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getBlockByHashWithInfo(first.getParentHash())).thenReturn(middle);
        when(store.getBlockByHashWithInfo(middle.getParentHash())).thenReturn(last);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        List<Block> returned = store.getBlocksByRange(first.getNumber(), last.getNumber());
        assertThat(returned.size()).isEqualTo(3);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);
        assertThat(returned.get(2)).isEqualTo(last);
    }

    @Test
    public void testGetBlocksByRange_withDescendingOrderAndNullLast() {
        Block first = consecutiveBlocks.get(2);
        Block middle = consecutiveBlocks.get(1);
        Block last = consecutiveBlocks.get(0);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getBlockByHash(first.getParentHash())).thenReturn(middle);
        when(store.getBlockByHash(middle.getParentHash())).thenReturn(null);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        // the returned list is null due to missing block in range
        assertThat(store.getBlocksByRange(first.getNumber(), last.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withDescendingOrderAndGenesisLast() {
        Block first = consecutiveBlocks.get(2); // assigning it height 2
        Block middle = consecutiveBlocks.get(1); // assumed height 1
        Block last = consecutiveBlocks.get(0); // assumed height 0

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        // returning the block at a different number than its height
        when(store.getChainBlockByNumber(2L)).thenReturn(first);
        when(store.getBlockByHashWithInfo(first.getParentHash())).thenReturn(middle);
        when(store.getBlocksByRange(2L, 0L)).thenCallRealMethod();

        // the returned list has only 2 elements due to the null
        List<Block> returned = store.getBlocksByRange(2L, 0L);
        assertThat(returned.size()).isEqualTo(2);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);

        // there should be no attempt to retrieve the genesis
        verify(store, times(0)).getBlockByHashWithInfo(last.getParentHash());
        verify(store, times(0)).getBlockByHashWithInfo(middle.getParentHash());
        verify(store, times(1)).getBlockByHashWithInfo(first.getParentHash());
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrder() {
        Block first = consecutiveBlocks.get(0);
        Block middle = consecutiveBlocks.get(1);
        Block last = consecutiveBlocks.get(2);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getChainBlockByNumber(last.getNumber())).thenReturn(last);
        when(store.getBlockByHashWithInfo(last.getParentHash())).thenReturn(middle);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        List<Block> returned = store.getBlocksByRange(first.getNumber(), last.getNumber());
        assertThat(returned.size()).isEqualTo(3);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);
        assertThat(returned.get(2)).isEqualTo(last);
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrderAndNullMiddle() {
        Block first = consecutiveBlocks.get(0);
        Block last = consecutiveBlocks.get(2);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getChainBlockByNumber(last.getNumber())).thenReturn(last);
        when(store.getBlockByHashWithInfo(last.getParentHash())).thenReturn(null);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        // the returned list is null due to missing block in range
        assertThat(store.getBlocksByRange(first.getNumber(), last.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrderAndNullLast() {
        Block first = consecutiveBlocks.get(0);
        Block middle = consecutiveBlocks.get(1);
        Block best = consecutiveBlocks.get(2);
        Block last = consecutiveBlocks.get(3);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));

        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getChainBlockByNumber(last.getNumber())).thenReturn(null);
        when(store.getBestBlock()).thenReturn(best);
        when(store.getBlockByHashWithInfo(best.getParentHash())).thenReturn(middle);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        List<Block> returned = store.getBlocksByRange(first.getNumber(), last.getNumber());
        assertThat(returned.size()).isEqualTo(3);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);
        assertThat(returned.get(2)).isEqualTo(best);
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrderAndNullBest() {
        Block first = consecutiveBlocks.get(0);
        Block last = consecutiveBlocks.get(3);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getChainBlockByNumber(last.getNumber())).thenReturn(null);
        when(store.getBestBlock()).thenReturn(null);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        // the returned list is null due to corrupt kernel
        assertThat(store.getBlocksByRange(first.getNumber(), last.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrderAndIncorrectHeight() {
        Block first = consecutiveBlocks.get(0);
        Block last = consecutiveBlocks.get(1);
        Block best = consecutiveBlocks.get(2);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getChainBlockByNumber(last.getNumber())).thenReturn(null);
        when(store.getBestBlock()).thenReturn(best);

        // the returned list is null due to corrupt kernel
        assertThat(store.getBlocksByRange(first.getNumber(), last.getNumber())).isNull();
    }

    @Test
    public void testGetBlockByHashWithInfo_withNullInput() {
        AionBlockStore store = new AionBlockStore(index, blocks, false);
        Block block = store.getBlockByHashWithInfo(null);
        assertThat(block).isNull();
    }

    @Test
    public void testGetBlockByHashWithInfo_withMissingBlock() {
        byte[] blockHash = RandomUtils.nextBytes(32);

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        assertThat(index.isEmpty()).isTrue();
        assertThat(blocks.isEmpty()).isTrue();

        Block block = store.getBlockByHashWithInfo(blockHash);
        assertThat(block).isNull();
    }

    @Test
    public void testGetBlockByHashWithInfo() {
        Block givenBlock = consecutiveBlocks.get(0);
        BigInteger totalDifficulty = BigInteger.TEN;

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        // does not require accurate total difficulty
        store.saveBlock(givenBlock, totalDifficulty, true);

        Block block = store.getBlockByHashWithInfo(givenBlock.getHash());
        assertThat(block).isEqualTo(givenBlock);
        assertThat(block.getTotalDifficulty()).isEqualTo(totalDifficulty);
        assertThat(block.isMainChain()).isTrue();
    }

    @Test
    public void testGetBlockByHashWithInfo_withSideChain() {
        Block givenBlock = consecutiveBlocks.get(0);
        BigInteger totalDifficulty = BigInteger.TWO;

        BigInteger sideTotalDifficulty = BigInteger.TEN;
        Block sideBlock = BlockUtil.newBlockFromRlp(givenBlock.getEncoded());
        sideBlock.updateHeaderDifficulty(sideTotalDifficulty.toByteArray());
        assertThat(givenBlock.getHash()).isNotEqualTo(sideBlock.getHash());
        assertThat(givenBlock.getEncoded()).isNotEqualTo(sideBlock.getEncoded());

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        // does not require accurate total difficulty
        store.saveBlock(givenBlock, totalDifficulty, false);
        store.saveBlock(sideBlock, sideTotalDifficulty, true);

        Block block = store.getBlockByHashWithInfo(givenBlock.getHash());
        assertThat(block).isEqualTo(givenBlock);
        assertThat(block.getTotalDifficulty()).isEqualTo(totalDifficulty);
        assertThat(block.isMainChain()).isFalse();
    }

    @Test
    public void testRollback() {

        AionBlock blk1 =
                new AionBlock(
                        new byte[32],
                        AddressUtils.ZERO_ADDRESS,
                        new byte[256],
                        BigInteger.TEN.toByteArray(),
                        1,
                        1,
                        new byte[32],
                        new byte[32],
                        new byte[32],
                        new byte[32],
                        new byte[32],
                        new ArrayList<>(),
                        new byte[1408],
                        1,
                        1);
        AionBlock blk2 =
                new AionBlock(
                        new byte[32],
                        AddressUtils.ZERO_ADDRESS,
                        new byte[256],
                        BigInteger.TWO.toByteArray(),
                        2,
                        2,
                        new byte[32],
                        new byte[32],
                        new byte[32],
                        new byte[32],
                        new byte[32],
                    new ArrayList<>(),
                        new byte[1408],
                        1,
                        1);
        AionBlockStore store = new AionBlockStore(index, blocks, false);

        store.saveBlock(blk1, BigInteger.TEN, true);
        store.saveBlock(blk2, BigInteger.TEN.add(BigInteger.ONE), true);

        store.rollback(1);

        Block storedBlk = store.getBestBlock();
        assertThat(storedBlk.getNumber() == 1);
        assertThat(storedBlk.getDifficulty().equals(BigInteger.TEN.toByteArray()));
    }

    private static final int TIME_OUT = 100; // in seconds

    private void addThread_saveBlock(List<Runnable> threads, AionBlockStore store, Block block) {
        threads.add(
                () -> {
                    store.saveBlock(block, block.getDifficultyBI(), true);
                    assertThat(store.getBlockByHash(block.getHash())).isNotNull();
                });
    }

    private void addThread_getBlockByHash(
            List<Runnable> threads, AionBlockStore store, Block block) {
        threads.add(
                () -> {
                    Block fromDB = store.getBlockByHash(block.getHash());
                    if (fromDB != null) {
                        assertThat(fromDB).isEqualTo(block);
                    }
                });
    }

    private void addThread_getBlockHashByNumber(
            List<Runnable> threads, AionBlockStore store, Block block) {
        threads.add(
                () -> {
                    // because the blocks are added randomly
                    // must check if the block exists to avoid NullPointerException
                    if (store.getChainBlockByNumber(block.getNumber()) != null) {
                        byte[] fromDB = store.getBlockHashByNumber(block.getNumber());
                        if (fromDB != null) {
                            assertThat(fromDB).isEqualTo(block.getHash());
                        }
                    }
                });
    }

    private void addThread_getChainBlockByNumber(
            List<Runnable> threads, AionBlockStore store, Block block) {
        threads.add(
                () -> {
                    Block fromDB = store.getChainBlockByNumber(block.getNumber());
                    if (fromDB != null) {
                        assertThat(fromDB).isEqualTo(block);
                    }
                });
    }

    @Test
    public void testConcurrent() throws InterruptedException {
        System.out.println("Note: If this test fails there may be a thread synchronization issue inside the AionBlockStore.");

        // set up block store with cache to replicate normal execution
        AionBlockStore store = new AionBlockStore(index, blocks, false, 10);

        List<Block> testBlocks = TestResources.consecutiveBlocks(20);
        List<Runnable> threads = new ArrayList<>();
        for (Block blk : testBlocks) {
            addThread_saveBlock(threads, store, blk);
            addThread_getBlockByHash(threads, store, blk);
            addThread_getBlockHashByNumber(threads, store, blk);
            addThread_getChainBlockByNumber(threads, store, blk);
        }

        System.out.format("%nTest 1: Running the %d generated threads...%n", threads.size());
        assertConcurrent("Testing concurrent use of AionBlockStore", threads, TIME_OUT);

        testBlocks = TestResources.blocks(20);
        for (Block blk : testBlocks) {
            addThread_saveBlock(threads, store, blk);
            addThread_getBlockByHash(threads, store, blk);
            addThread_getBlockHashByNumber(threads, store, blk);
            addThread_getChainBlockByNumber(threads, store, blk);
        }

        System.out.format("%nTest 2: Running the %d generated threads...%n", threads.size());
        assertConcurrent("Testing concurrent use of AionBlockStore", threads, TIME_OUT);
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
        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
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
                            } catch (final Exception e) {
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
            for (Exception e : exceptions) {
                e.printStackTrace();
            }
        }
        assertTrue(
                message + "failed with " + exceptions.size() + " exception(s):" + exceptions,
                exceptions.isEmpty());
    }

    @Test
    public void testGetTwoGenerationBlocksByHashWithInfo_withNullInput() {
        AionBlockStore store = new AionBlockStore(index, blocks, false);
        Block[] blocks = store.getTwoGenerationBlocksByHashWithInfo(null);
        assertThat(blocks.length).isEqualTo(2);
        assertThat(blocks[0]).isNull();
        assertThat(blocks[1]).isNull();
    }

    @Test
    public void testGetTwoGenerationBlocksByHashWithInfo_withMissingParent() {
        byte[] parentHash = RandomUtils.nextBytes(32);

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        assertThat(index.isEmpty()).isTrue();
        assertThat(blocks.isEmpty()).isTrue();

        Block[] blocks = store.getTwoGenerationBlocksByHashWithInfo(parentHash);
        assertThat(blocks.length).isEqualTo(2);
        assertThat(blocks[0]).isNull();
        assertThat(blocks[1]).isNull();
    }

    @Test
    public void testGetTwoGenerationBlocksByHashWithInfo_withMissingGrandParent() {
        Block parent = consecutiveBlocks.get(0);

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        // does not require accurate total difficulty
        store.saveBlock(parent, BigInteger.TEN, true);

        Block[] blocks = store.getTwoGenerationBlocksByHashWithInfo(parent.getHash());
        assertThat(blocks.length).isEqualTo(2);
        assertThat(blocks[0]).isEqualTo(parent);
        assertThat(blocks[0].getTotalDifficulty()).isEqualTo(BigInteger.TEN);
        assertThat(blocks[0].isMainChain()).isTrue();
        assertThat(blocks[1]).isNull();
    }

    @Test
    public void testGetTwoGenerationBlocksByHashWithInfo() {
        Block grandparent = consecutiveBlocks.get(0);
        Block parent = consecutiveBlocks.get(1);

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        // does not require accurate total difficulty
        store.saveBlock(grandparent, BigInteger.TWO, true);
        store.saveBlock(parent, BigInteger.TEN, true);

        Block[] blocks = store.getTwoGenerationBlocksByHashWithInfo(parent.getHash());
        assertThat(blocks.length).isEqualTo(2);
        assertThat(blocks[0]).isEqualTo(parent);
        assertThat(blocks[0].getTotalDifficulty()).isEqualTo(BigInteger.TEN);
        assertThat(blocks[0].isMainChain()).isTrue();
        assertThat(blocks[1]).isEqualTo(grandparent);
        assertThat(blocks[1].getTotalDifficulty()).isEqualTo(BigInteger.TWO);
        assertThat(blocks[1].isMainChain()).isTrue();
    }

    @Test
    public void testGetTwoGenerationBlocksByHashWithInfo_withSidechainGrandparent() {
        Block grandparent = consecutiveBlocks.get(0);
        Block parent = consecutiveBlocks.get(1);

        Block sideGrandparent = spy(grandparent);
        byte[] newHash = RandomUtils.nextBytes(32);
        when(sideGrandparent.getHash()).thenReturn(newHash);
        when(sideGrandparent.getHashWrapper()).thenReturn(ByteArrayWrapper.wrap(newHash));
        assertThat(grandparent.getHash()).isNotEqualTo(sideGrandparent.getHash());

        Block sideParent = spy(parent);
        newHash = RandomUtils.nextBytes(32);
        when(sideParent.getHash()).thenReturn(newHash);
        when(sideParent.getHashWrapper()).thenReturn(ByteArrayWrapper.wrap(newHash));
        assertThat(parent.getHash()).isNotEqualTo(sideParent.getHash());

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        // does not require accurate total difficulty
        store.saveBlock(grandparent, BigInteger.TWO, false);
        store.saveBlock(sideGrandparent, sideGrandparent.getTotalDifficulty(), true);
        store.saveBlock(parent, BigInteger.TEN, false);
        store.saveBlock(sideParent, sideParent.getTotalDifficulty(), true);

        Block[] blocks = store.getTwoGenerationBlocksByHashWithInfo(parent.getHash());
        assertThat(blocks.length).isEqualTo(2);
        assertThat(blocks[0]).isEqualTo(parent);
        assertThat(blocks[0].getHash()).isEqualTo(parent.getHash());
        assertThat(blocks[0].getTotalDifficulty()).isEqualTo(BigInteger.TEN);
        assertThat(blocks[0].isMainChain()).isFalse();
        assertThat(blocks[1]).isEqualTo(grandparent);
        assertThat(blocks[1].getHash()).isEqualTo(grandparent.getHash());
        assertThat(blocks[1].getTotalDifficulty()).isEqualTo(BigInteger.TWO);
        assertThat(blocks[1].isMainChain()).isFalse();
    }

    @Test
    public void testGetThreeGenerationBlocksByHashWithInfo_withNullInput() {
        AionBlockStore store = new AionBlockStore(index, blocks, false);
        Block[] blocks = store.getThreeGenerationBlocksByHashWithInfo(null);
        assertThat(blocks.length).isEqualTo(3);
        assertThat(blocks[0]).isNull();
        assertThat(blocks[1]).isNull();
        assertThat(blocks[2]).isNull();
    }

    @Test
    public void testGetThreeGenerationBlocksByHashWithInfo_withMissingParent() {
        byte[] parentHash = RandomUtils.nextBytes(32);

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        assertThat(index.isEmpty()).isTrue();
        assertThat(blocks.isEmpty()).isTrue();

        Block[] blocks = store.getThreeGenerationBlocksByHashWithInfo(parentHash);
        assertThat(blocks.length).isEqualTo(3);
        assertThat(blocks[0]).isNull();
        assertThat(blocks[1]).isNull();
        assertThat(blocks[2]).isNull();
    }

    @Test
    public void testGetThreeGenerationBlocksByHashWithInfo_withMissingGrandparent() {
        Block parent = consecutiveBlocks.get(0);

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        // does not require accurate total difficulty
        store.saveBlock(parent, BigInteger.TEN, true);

        Block[] blocks = store.getThreeGenerationBlocksByHashWithInfo(parent.getHash());
        assertThat(blocks.length).isEqualTo(3);
        assertThat(blocks[0]).isEqualTo(parent);
        assertThat(blocks[0].getTotalDifficulty()).isEqualTo(BigInteger.TEN);
        assertThat(blocks[0].isMainChain()).isTrue();
        assertThat(blocks[1]).isNull();
        assertThat(blocks[2]).isNull();
    }

    @Test
    public void testGetThreeGenerationBlocksByHashWithInfo_withMissingGreatGrandparent() {
        Block grandparent = consecutiveBlocks.get(0);
        Block parent = consecutiveBlocks.get(1);

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        // does not require accurate total difficulty
        store.saveBlock(grandparent, BigInteger.TWO, true);
        store.saveBlock(parent, BigInteger.TEN, true);

        Block[] blocks = store.getThreeGenerationBlocksByHashWithInfo(parent.getHash());
        assertThat(blocks.length).isEqualTo(3);
        assertThat(blocks[0]).isEqualTo(parent);
        assertThat(blocks[0].getTotalDifficulty()).isEqualTo(BigInteger.TEN);
        assertThat(blocks[0].isMainChain()).isTrue();
        assertThat(blocks[1]).isEqualTo(grandparent);
        assertThat(blocks[1].getTotalDifficulty()).isEqualTo(BigInteger.TWO);
        assertThat(blocks[1].isMainChain()).isTrue();
        assertThat(blocks[2]).isNull();
    }

    @Test
    public void testGetThreeGenerationBlocksByHashWithInfo() {
        Block greatGrandparent = consecutiveBlocks.get(0);
        Block grandparent = consecutiveBlocks.get(1);
        Block parent = consecutiveBlocks.get(2);

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        // does not require accurate total difficulty
        store.saveBlock(greatGrandparent, BigInteger.ONE, true);
        store.saveBlock(grandparent, BigInteger.TWO, true);
        store.saveBlock(parent, BigInteger.TEN, true);

        Block[] blocks = store.getThreeGenerationBlocksByHashWithInfo(parent.getHash());
        assertThat(blocks.length).isEqualTo(3);
        assertThat(blocks[0]).isEqualTo(parent);
        assertThat(blocks[0].getTotalDifficulty()).isEqualTo(BigInteger.TEN);
        assertThat(blocks[0].isMainChain()).isTrue();
        assertThat(blocks[1]).isEqualTo(grandparent);
        assertThat(blocks[1].getTotalDifficulty()).isEqualTo(BigInteger.TWO);
        assertThat(blocks[1].isMainChain()).isTrue();
        assertThat(blocks[2]).isEqualTo(greatGrandparent);
        assertThat(blocks[2].getTotalDifficulty()).isEqualTo(BigInteger.ONE);
        assertThat(blocks[2].isMainChain()).isTrue();
    }

    @Test
    public void testGetThreeGenerationBlocksByHashWithInfo_withSidechains() {
        Block greatGrandparent = consecutiveBlocks.get(0);
        Block grandparent = consecutiveBlocks.get(1);
        Block parent = consecutiveBlocks.get(2);

        Block sideGreatGrandparent = spy(greatGrandparent);
        byte[] newHash = RandomUtils.nextBytes(32);
        when(sideGreatGrandparent.getHash()).thenReturn(newHash);
        when(sideGreatGrandparent.getHashWrapper()).thenReturn(ByteArrayWrapper.wrap(newHash));
        assertThat(greatGrandparent.getHash()).isNotEqualTo(sideGreatGrandparent.getHash());

        Block sideGrandparent = spy(grandparent);
        newHash = RandomUtils.nextBytes(32);
        when(sideGrandparent.getHash()).thenReturn(newHash);
        when(sideGrandparent.getHashWrapper()).thenReturn(ByteArrayWrapper.wrap(newHash));
        assertThat(grandparent.getHash()).isNotEqualTo(sideGrandparent.getHash());

        Block sideParent = spy(parent);
        newHash = RandomUtils.nextBytes(32);
        when(sideParent.getHash()).thenReturn(newHash);
        when(sideParent.getHashWrapper()).thenReturn(ByteArrayWrapper.wrap(newHash));
        assertThat(parent.getHash()).isNotEqualTo(sideParent.getHash());

        AionBlockStore store = new AionBlockStore(index, blocks, false);
        // does not require accurate total difficulty
        store.saveBlock(greatGrandparent, BigInteger.ONE, false);
        store.saveBlock(grandparent, BigInteger.TWO, false);
        store.saveBlock(parent, BigInteger.TEN, false);
        store.saveBlock(sideGreatGrandparent, sideGreatGrandparent.getTotalDifficulty(), true);
        store.saveBlock(sideGrandparent, sideGrandparent.getTotalDifficulty(), true);
        store.saveBlock(sideParent, sideParent.getTotalDifficulty(), true);

        Block[] blocks = store.getThreeGenerationBlocksByHashWithInfo(parent.getHash());
        assertThat(blocks.length).isEqualTo(3);
        assertThat(blocks[0]).isEqualTo(parent);
        assertThat(blocks[0].getHash()).isEqualTo(parent.getHash());
        assertThat(blocks[0].getTotalDifficulty()).isEqualTo(BigInteger.TEN);
        assertThat(blocks[0].isMainChain()).isFalse();
        assertThat(blocks[1]).isEqualTo(grandparent);
        assertThat(blocks[1].getHash()).isEqualTo(grandparent.getHash());
        assertThat(blocks[1].getTotalDifficulty()).isEqualTo(BigInteger.TWO);
        assertThat(blocks[1].isMainChain()).isFalse();
        assertThat(blocks[2]).isEqualTo(greatGrandparent);
        assertThat(blocks[2].getHash()).isEqualTo(greatGrandparent.getHash());
        assertThat(blocks[2].getTotalDifficulty()).isEqualTo(BigInteger.ONE);
        assertThat(blocks[2].isMainChain()).isFalse();
    }
}
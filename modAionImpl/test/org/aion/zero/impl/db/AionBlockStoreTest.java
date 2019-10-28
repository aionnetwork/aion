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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.mcf.blockchain.Block;
import org.aion.util.TestResources;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.types.AionBlock;
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

    AionBlockStore store;

    @Before
    public void openDatabases() {
        index.open();
        blocks.open();

        // setting up the store with the blocks
        store = new AionBlockStore(index, blocks, false);
        for (Block b : consecutiveBlocks) {
            // the fake TD is not part of the test
            store.saveBlock(b, BigInteger.ONE, true);
        }
    }

    @After
    public void closeDatabases() {
        index.close();
        blocks.close();
    }

    @Test
    public void testGetBlocksByRange_withGensisFirstBlock() {
        assertThat(store.getBlocksByRange(-1L, 0L)).isNull();
    }

    @Test
    public void testGetBlocksByRange_withNullBottomBlock() {
        Block bottom = consecutiveBlocks.get(0);
        Block top = consecutiveBlocks.get(2);

        // removing the db entry to cause a null return
        blocks.delete(bottom.getHash());

        assertThat(store.getBlocksByRange(top.getNumber(), bottom.getNumber())).isNull();
        assertThat(store.getBlocksByRange(bottom.getNumber(), top.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withNullMiddleBlock() {
        Block bottom = consecutiveBlocks.get(0);
        Block middle = consecutiveBlocks.get(1);
        Block top = consecutiveBlocks.get(2);

        // removing the db entry to cause a null return
        blocks.delete(middle.getHash());

        assertThat(store.getBlocksByRange(top.getNumber(), bottom.getNumber())).isNull();
        assertThat(store.getBlocksByRange(bottom.getNumber(), top.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withNullTopBlock() {
        Block bottom = consecutiveBlocks.get(0);
        Block top = consecutiveBlocks.get(2);

        // removing the db entry to cause a null return
        blocks.delete(top.getHash());

        assertThat(store.getBlocksByRange(top.getNumber(), bottom.getNumber())).isNull();
        assertThat(store.getBlocksByRange(bottom.getNumber(), top.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withNullBottomBlockHash() {
        Block bottom = consecutiveBlocks.get(0);
        Block top = consecutiveBlocks.get(2);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockHashByNumber(bottom.getNumber())).thenReturn(null);

        // the returned list is null due to missing block in range
        assertThat(store.getBlocksByRange(bottom.getNumber(), top.getNumber())).isNull();
        assertThat(store.getBlocksByRange(top.getNumber(), bottom.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withNullMiddleBlockHash() {
        Block bottom = consecutiveBlocks.get(0);
        Block middle = consecutiveBlocks.get(1);
        Block top = consecutiveBlocks.get(2);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockHashByNumber(middle.getNumber())).thenReturn(null);

        // the returned list is null due to missing block in range
        assertThat(store.getBlocksByRange(bottom.getNumber(), top.getNumber())).isNull();
        assertThat(store.getBlocksByRange(top.getNumber(), bottom.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withNullTopBlockHash() {
        Block bottom = consecutiveBlocks.get(0);
        Block top = consecutiveBlocks.get(2);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockHashByNumber(top.getNumber())).thenReturn(null);

        // the returned list is null due to missing block in range
        assertThat(store.getBlocksByRange(bottom.getNumber(), top.getNumber())).isNull();
        assertThat(store.getBlocksByRange(top.getNumber(), bottom.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withSingleBlock() {
        Block block = consecutiveBlocks.get(0);

        List<Block> returned = store.getBlocksByRange(block.getNumber(), block.getNumber());
        assertThat(returned.size()).isEqualTo(1);
        assertThat(returned).contains(block);
    }

    @Test
    public void testGetBlocksByRange_withDescendingOrder() {
        Block first = consecutiveBlocks.get(2);
        Block middle = consecutiveBlocks.get(1);
        Block last = consecutiveBlocks.get(0);

        List<Block> returned = store.getBlocksByRange(first.getNumber(), last.getNumber());
        assertThat(returned.size()).isEqualTo(3);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);
        assertThat(returned.get(2)).isEqualTo(last);
    }

    @Test
    public void testGetBlocksByRange_withDescendingOrderAndGenesisLast() {
        Block first = consecutiveBlocks.get(2); // assigning it height 2
        Block middle = consecutiveBlocks.get(1); // assumed height 1
        Block last = consecutiveBlocks.get(0); // assumed height 0

        ByteArrayKeyValueDatabase spyBlocks = spy(blocks);
        AionBlockStore store = spy(new AionBlockStore(index, spyBlocks, false));
        // returning the block at a different number than its height
        when(store.getChainBlockHashByNumber(2L)).thenReturn(first.getHash());
        when(store.getChainBlockHashByNumber(1L)).thenReturn(middle.getHash());
        when(store.getChainBlockHashByNumber(0L)).thenReturn(last.getHash());
        when(store.getBlocksByRange(2L, 0L)).thenCallRealMethod();

        // the returned list has only 2 elements due to the null
        List<Block> returned = store.getBlocksByRange(2L, 0L);
        assertThat(returned.size()).isEqualTo(2);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);

        // there should be no attempt to retrieve the genesis
        verify(store, times(0)).getChainBlockHashByNumber(0L);
        verify(spyBlocks, times(0)).get(last.getHash());
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrder() {
        Block first = consecutiveBlocks.get(0);
        Block middle = consecutiveBlocks.get(1);
        Block last = consecutiveBlocks.get(2);

        List<Block> returned = store.getBlocksByRange(first.getNumber(), last.getNumber());
        assertThat(returned.size()).isEqualTo(3);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);
        assertThat(returned.get(2)).isEqualTo(last);
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrderAndGenesisLast() {
        Block first = consecutiveBlocks.get(2); // assigning it height 2
        Block middle = consecutiveBlocks.get(1); // assumed height 1
        Block last = consecutiveBlocks.get(0); // assumed height 0

        ByteArrayKeyValueDatabase spyBlocks = spy(blocks);
        AionBlockStore store = spy(new AionBlockStore(index, spyBlocks, false));
        // returning the block at a different number than its height
        when(store.getChainBlockHashByNumber(2L)).thenReturn(first.getHash());
        when(store.getChainBlockHashByNumber(1L)).thenReturn(middle.getHash());
        when(store.getChainBlockHashByNumber(0L)).thenReturn(last.getHash());
        when(store.getBlocksByRange(0L, 2L)).thenCallRealMethod();

        // the returned list has only 2 elements due to the null
        List<Block> returned = store.getBlocksByRange(0L, 2L);
        assertThat(returned.size()).isEqualTo(2);
        assertThat(returned.get(0)).isEqualTo(middle);
        assertThat(returned.get(1)).isEqualTo(first);

        // there should be no attempt to retrieve the genesis
        verify(store, times(0)).getChainBlockHashByNumber(0L);
        verify(spyBlocks, times(0)).get(last.getHash());
    }

    @Test
    public void testRollback() {
        // ensuring empty db
        index.drop();
        blocks.drop();

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
        AionBlockStore store = new AionBlockStore(index, blocks, false, 10, 0);

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
}

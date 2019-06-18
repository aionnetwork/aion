package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.aion.util.TestResources;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.types.AionBlock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link AionBlockStore}.
 *
 * @author Alexandra Roatis
 */
public class AionBlockStoreTest {

    // simply mocking the dbs didn't work possibly because of the use of locks
    // for some reason index.size() gets called by store.getChainBlockByNumber(X)
    ByteArrayKeyValueDatabase index = new MockDB("index");
    ByteArrayKeyValueDatabase blocks = new MockDB("blocks");

    // returns a list of blocks in ascending order of height
    List<AionBlock> consecutiveBlocks = TestResources.consecutiveBlocks(4);

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
        AionBlock block = consecutiveBlocks.get(0);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(10L)).thenReturn(block);
        when(store.getBlocksByRange(10L, 10L)).thenCallRealMethod();

        List<AionBlock> returned = store.getBlocksByRange(10L, 10L);
        assertThat(returned.size()).isEqualTo(1);
        assertThat(returned).contains(block);
    }

    @Test
    public void testGetBlocksByRange_withDescendingOrder() {
        AionBlock first = consecutiveBlocks.get(2);
        AionBlock middle = consecutiveBlocks.get(1);
        AionBlock last = consecutiveBlocks.get(0);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getBlockByHash(first.getParentHash())).thenReturn(middle);
        when(store.getBlockByHash(middle.getParentHash())).thenReturn(last);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        List<AionBlock> returned = store.getBlocksByRange(first.getNumber(), last.getNumber());
        assertThat(returned.size()).isEqualTo(3);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);
        assertThat(returned.get(2)).isEqualTo(last);
    }

    @Test
    public void testGetBlocksByRange_withDescendingOrderAndNullLast() {
        AionBlock first = consecutiveBlocks.get(2);
        AionBlock middle = consecutiveBlocks.get(1);
        AionBlock last = consecutiveBlocks.get(0);

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
        AionBlock first = consecutiveBlocks.get(2); // assigning it height 2
        AionBlock middle = consecutiveBlocks.get(1); // assumed height 1
        AionBlock last = consecutiveBlocks.get(0); // assumed height 0

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        // returning the block at a different number than its height
        when(store.getChainBlockByNumber(2L)).thenReturn(first);
        when(store.getBlockByHash(first.getParentHash())).thenReturn(middle);
        when(store.getBlocksByRange(2L, 0L)).thenCallRealMethod();

        // the returned list has only 2 elements due to the null
        List<AionBlock> returned = store.getBlocksByRange(2L, 0L);
        assertThat(returned.size()).isEqualTo(2);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);

        // there should be no attempt to retrieve the genesis
        verify(store, times(0)).getBlockByHash(last.getParentHash());
        verify(store, times(0)).getBlockByHash(middle.getParentHash());
        verify(store, times(1)).getBlockByHash(first.getParentHash());
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrder() {
        AionBlock first = consecutiveBlocks.get(0);
        AionBlock middle = consecutiveBlocks.get(1);
        AionBlock last = consecutiveBlocks.get(2);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getChainBlockByNumber(last.getNumber())).thenReturn(last);
        when(store.getBlockByHash(last.getParentHash())).thenReturn(middle);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        List<AionBlock> returned = store.getBlocksByRange(first.getNumber(), last.getNumber());
        assertThat(returned.size()).isEqualTo(3);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);
        assertThat(returned.get(2)).isEqualTo(last);
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrderAndNullMiddle() {
        AionBlock first = consecutiveBlocks.get(0);
        AionBlock last = consecutiveBlocks.get(2);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getChainBlockByNumber(last.getNumber())).thenReturn(last);
        when(store.getBlockByHash(last.getParentHash())).thenReturn(null);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        // the returned list is null due to missing block in range
        assertThat(store.getBlocksByRange(first.getNumber(), last.getNumber())).isNull();
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrderAndNullLast() {
        AionBlock first = consecutiveBlocks.get(0);
        AionBlock middle = consecutiveBlocks.get(1);
        AionBlock best = consecutiveBlocks.get(2);
        AionBlock last = consecutiveBlocks.get(3);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));

        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getChainBlockByNumber(last.getNumber())).thenReturn(null);
        when(store.getBestBlock()).thenReturn(best);
        when(store.getBlockByHash(best.getParentHash())).thenReturn(middle);
        when(store.getBlocksByRange(first.getNumber(), last.getNumber())).thenCallRealMethod();

        List<AionBlock> returned = store.getBlocksByRange(first.getNumber(), last.getNumber());
        assertThat(returned.size()).isEqualTo(3);
        assertThat(returned.get(0)).isEqualTo(first);
        assertThat(returned.get(1)).isEqualTo(middle);
        assertThat(returned.get(2)).isEqualTo(best);
    }

    @Test
    public void testGetBlocksByRange_withAscendingOrderAndNullBest() {
        AionBlock first = consecutiveBlocks.get(0);
        AionBlock last = consecutiveBlocks.get(3);

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
        AionBlock first = consecutiveBlocks.get(0);
        AionBlock last = consecutiveBlocks.get(1);
        AionBlock best = consecutiveBlocks.get(2);

        AionBlockStore store = spy(new AionBlockStore(index, blocks, false));
        when(store.getChainBlockByNumber(first.getNumber())).thenReturn(first);
        when(store.getChainBlockByNumber(last.getNumber())).thenReturn(null);
        when(store.getBestBlock()).thenReturn(best);

        // the returned list is null due to corrupt kernel
        assertThat(store.getBlocksByRange(first.getNumber(), last.getNumber())).isNull();
    }

    @Test
    public void testRollback() {

        AionBlock blk1 =
                new AionBlock(
                        new byte[0],
                        AddressUtils.ZERO_ADDRESS,
                        new byte[0],
                        BigInteger.TEN.toByteArray(),
                        1,
                        1,
                        new byte[0],
                        new byte[0],
                        new byte[0],
                        new byte[0],
                        new byte[0],
                        null,
                        new byte[0],
                        1,
                        1);
        AionBlock blk2 =
                new AionBlock(
                        new byte[0],
                        AddressUtils.ZERO_ADDRESS,
                        new byte[0],
                        BigInteger.TWO.toByteArray(),
                        2,
                        2,
                        new byte[0],
                        new byte[0],
                        new byte[0],
                        new byte[0],
                        new byte[0],
                        null,
                        new byte[0],
                        1,
                        1);
        AionBlockStore store = new AionBlockStore(index, blocks, false);

        store.saveBlock(blk1, BigInteger.TEN, true);
        store.saveBlock(blk2, BigInteger.TEN.add(BigInteger.ONE), true);

        store.rollback(1);

        AionBlock storedBlk = store.getBestBlock();
        assertThat(storedBlk.getNumber() == 1);
        assertThat(storedBlk.getDifficulty().equals(BigInteger.TEN.toByteArray()));
    }
}

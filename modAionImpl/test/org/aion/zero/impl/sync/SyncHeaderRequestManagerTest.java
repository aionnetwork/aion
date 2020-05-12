package org.aion.zero.impl.sync;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.CLOSE_OVERLAPPING_BLOCKS;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.FAR_OVERLAPPING_BLOCKS;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.MAX_REQUEST_SIZE;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SWITCH_OVERLAPPING_BLOCKS_RANGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.ConstantUtil;
import org.aion.zero.impl.types.BlockHeader;
import org.aion.p2p.INode;
import org.aion.util.types.ByteArrayWrapper;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link SyncHeaderRequestManager}.
 *
 * @author Alexandra Roatis
 */
public class SyncHeaderRequestManagerTest {

    private static final ByteArrayWrapper EMPTY_TRIE_HASH = ByteArrayWrapper.wrap(ConstantUtil.EMPTY_TRIE_HASH);

    Logger syncLog, surveyLog;
    SyncHeaderRequestManager srm;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        syncLog = spy(LoggerFactory.getLogger("SYNC"));
        doNothing().when(syncLog).info(any());

        surveyLog = spy(LoggerFactory.getLogger("SURVEY"));
        doNothing().when(surveyLog).info(any());

        srm = new SyncHeaderRequestManager(syncLog, surveyLog);
    }

    /**
     * Generates {@code count} mock peers with ids from {@code start} to {@code start + count - 1}
     * and best block number {@code id * 1_000L}.
     */
    private Map<Integer, INode> generateMockPeers(int start, int count) {
        Map<Integer, INode> current = new HashMap<>();
        for (int i = start; i < count + start; i++) {
            INode mockPeer = mock(INode.class);
            when(mockPeer.getIdHash()).thenReturn(i);
            when(mockPeer.getIdShort()).thenReturn("peer" + i);
            when(mockPeer.getBestBlockNumber()).thenReturn(i * 1_000L);
            current.put(i, mockPeer);
        }
        return current;
    }

    @Test
    public void test_updateActiveNodes() {
        Map<Integer, INode> input;
        Pair<Boolean, String> output;

        // adding two nodes: peer1 & peer2
        input = generateMockPeers(1, 2);
        output =
                srm.assertUpdateActiveNodes(
                        input, null, null, input.keySet(), input.keySet(), 2_000L);
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();

        // remove peer1
        input.remove(1);
        output =
                srm.assertUpdateActiveNodes(
                        input, null, null, input.keySet(), input.keySet(), 2_000L);
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();

        // add peers 3,4,5 (keeping peer2)
        input = generateMockPeers(2, 4);
        output =
                srm.assertUpdateActiveNodes(
                        input, null, null, input.keySet(), input.keySet(), 5_000L);
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();

        // remove peers 2 and 5 (add peer 6)
        input.putAll(generateMockPeers(6, 1));
        input.remove(2);
        input.remove(5);
        output =
                srm.assertUpdateActiveNodes(
                        input, null, null, input.keySet(), input.keySet(), 6_000L);
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();
    }

    @Test
    public void test_updateStatesForRequests_startOfSync() {
        Map<Integer, INode> input;
        Pair<Boolean, String> output;

        input = generateMockPeers(10, 3);
        output =
                srm.assertUpdateActiveNodes(
                        input, null, null, input.keySet(), input.keySet(), 12_000L);
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();

        // generating request-able state
        output =
                srm.assertUpdateStatesForRequests(
                        0L,
                        Map.of(10, 1L, 11, 1L + MAX_REQUEST_SIZE, 12, 1L + 2 * MAX_REQUEST_SIZE),
                        Map.of(10, MAX_REQUEST_SIZE, 11, MAX_REQUEST_SIZE, 12, MAX_REQUEST_SIZE));
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();
    }

    @Test
    public void test_updateStatesForRequests_smallOverlap() {
        Map<Integer, INode> input;
        Pair<Boolean, String> output;

        input = generateMockPeers(10, 1);
        long networkHeight = 10_000L;
        output =
                srm.assertUpdateActiveNodes(
                        input, null, null, input.keySet(), input.keySet(), networkHeight);
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();

        // generating request-able state
        long localHeight = networkHeight - SWITCH_OVERLAPPING_BLOCKS_RANGE;
        output =
                srm.assertUpdateStatesForRequests(
                        localHeight,
                        Map.of(10, localHeight - FAR_OVERLAPPING_BLOCKS),
                        Map.of(10, MAX_REQUEST_SIZE));
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();
    }

    @Test
    public void test_updateStatesForRequests_largeOverlap() {
        Map<Integer, INode> input;
        Pair<Boolean, String> output;

        input = generateMockPeers(10, 1);
        long networkHeight = 10_000L;
        output =
                srm.assertUpdateActiveNodes(
                        input, null, null, input.keySet(), input.keySet(), networkHeight);
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();

        // generating request-able state
        long localHeight = networkHeight - SWITCH_OVERLAPPING_BLOCKS_RANGE + 1;
        output =
                srm.assertUpdateStatesForRequests(
                        localHeight,
                        Map.of(10, localHeight - CLOSE_OVERLAPPING_BLOCKS),
                        Map.of(10, MAX_REQUEST_SIZE));
        assertWithMessage(output.getRight()).that(output.getLeft()).isTrue();
    }

    @Test
    public void test_missingHeaders() {
        // retrieve when nothing was stored for the peer
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isNull();

        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        srm.storeHeaders(1, list);

        // retrieve when nothing was stored for the size
        assertThat(srm.matchAndDropHeaders(1, 12, EMPTY_TRIE_HASH)).isNull();
    }

    @Test
    public void test_receivedHeaderManagement() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTxTrieRootWrapper()).thenReturn(EMPTY_TRIE_HASH);
        when(list.get(0)).thenReturn(header);

        srm.storeHeaders(1, list);
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isEqualTo(list);

        // ensure the headers were dropped
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isNull();
    }

    @Test
    public void test_storeThenRetrieveEachWithSameSize() {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTxTrieRootWrapper()).thenReturn(EMPTY_TRIE_HASH);
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        when(list.get(0)).thenReturn(header);

        srm.storeHeaders(1, list);
        // removes the stored headers
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isEqualTo(list);

        List<BlockHeader> list2 = mock(List.class);
        when(list2.size()).thenReturn(10);
        when(list2.get(0)).thenReturn(header);

        srm.storeHeaders(1, list2);
        // removes the stored headers
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isEqualTo(list2);

        // ensure the headers were dropped
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isNull();
    }

    @Test
    public void test_storeMultipleThenRetrieveAllWithSameSize() {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTxTrieRootWrapper()).thenReturn(EMPTY_TRIE_HASH);
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        when(list.get(0)).thenReturn(header);
        srm.storeHeaders(1, list);

        List<BlockHeader> list2 = mock(List.class);
        when(list2.size()).thenReturn(10);
        when(list2.get(0)).thenReturn(header);
        srm.storeHeaders(1, list2);

        // removes the stored headers in the order they were added
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isEqualTo(list);
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isEqualTo(list2);

        // ensure the headers were dropped
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isNull();
    }

    @Test
    public void test_multipleSizeHeaderResponses() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTxTrieRootWrapper()).thenReturn(EMPTY_TRIE_HASH);
        when(list.get(0)).thenReturn(header);

        srm.storeHeaders(1, list);
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isEqualTo(list);

        // new wrapper with different size
        List<BlockHeader> list2 = mock(List.class);
        when(list2.size()).thenReturn(12);
        when(list2.get(0)).thenReturn(header);

        srm.storeHeaders(1, list);
        srm.storeHeaders(1, list2);
        assertThat(srm.matchAndDropHeaders(1, 12, EMPTY_TRIE_HASH)).isEqualTo(list2);
        assertThat(srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH)).isEqualTo(list);
    }

    @Test
    public void test_missingHeadersTrieRoot() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTxTrieRootWrapper()).thenReturn(EMPTY_TRIE_HASH);
        when(list.get(0)).thenReturn(header);

        srm.storeHeaders(1, list);
        assertThat(srm.matchAndDropHeaders(1, 10, ByteArrayWrapper.wrap(new byte[32]))).isNull();
    }

    @Test
    public void test_getHeadersForBodiesRequests_withoutPeerId() {
        List<List<BlockHeader>> output = srm.getHeadersForBodiesRequests(1);
        assertThat(output).isEmpty();
    }

    @Test
    public void test_getHeadersForBodiesRequests() {
        List<BlockHeader> list1 = mock(List.class);
        when(list1.size()).thenReturn(10);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTxTrieRootWrapper()).thenReturn(EMPTY_TRIE_HASH);
        when(list1.get(0)).thenReturn(header);
        List<BlockHeader> list2 = mock(List.class);
        when(list2.size()).thenReturn(10);
        // new wrapper with different size
        List<BlockHeader> list3 = mock(List.class);
        when(list3.size()).thenReturn(12);

        srm.storeHeaders(1, list1);
        srm.storeHeaders(1, list2);
        srm.storeHeaders(1, list3);
        List<List<BlockHeader>> output = srm.getHeadersForBodiesRequests(1);
        assertThat(output.size()).isEqualTo(2);
        assertThat(output.get(0)).isEqualTo(list1);
        assertThat(output.get(1)).isEqualTo(list3);

        srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH);
        output = srm.getHeadersForBodiesRequests(1);
        assertThat(output.size()).isEqualTo(2);
        assertThat(output.get(0)).isEqualTo(list2);
        assertThat(output.get(1)).isEqualTo(list3);
    }

    @Test
    public void testDropHeaders_withMissingPeer() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);

        // headers stored for peer 2
        srm.storeHeaders(2, list);
        // attempting to drop the list for peer 1
        assertThat(srm.dropHeaders(1, list)).isFalse();
    }

    @Test
    public void testDropHeaders_withMissingSize() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);

        // headers stored for size 10
        srm.storeHeaders(1, list);
        when(list.size()).thenReturn(11);
        // attempting to drop the list for size 11
        assertThat(srm.dropHeaders(1, list)).isFalse();
    }

    @Test
    public void testDropHeaders_withEmptyList() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        BlockHeader header = mock(BlockHeader.class);
        when(header.getTxTrieRootWrapper()).thenReturn(EMPTY_TRIE_HASH);
        when(list.get(0)).thenReturn(header);

        // headers stored for size 10
        srm.storeHeaders(1, list);
        // remove the list using the matching method
        srm.matchAndDropHeaders(1, 10, EMPTY_TRIE_HASH);
        // attempting to drop the list for size 11
        assertThat(srm.dropHeaders(1, list)).isFalse();
    }

    @Test
    public void testDropHeaders_withDifferentList() {
        List<BlockHeader> list1 = mock(List.class);
        when(list1.size()).thenReturn(10);
        List<BlockHeader> list2 = mock(List.class);
        when(list2.size()).thenReturn(10);

        // list1 stored with size 10
        srm.storeHeaders(1, list1);
        // attempting to drop list2 for size 10
        assertThat(srm.dropHeaders(1, list2)).isFalse();
    }

    @Test
    public void testDropHeaders_withExactObject() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);

        // list1 stored with size 10
        srm.storeHeaders(1, list);
        // attempting to drop list2 for size 10
        assertThat(srm.dropHeaders(1, list)).isTrue();
    }
}

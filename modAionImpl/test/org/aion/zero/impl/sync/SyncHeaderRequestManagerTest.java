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
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.p2p.INode;
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
        assertThat(srm.matchHeaders(1, 10)).isNull();

        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        HeadersWrapper hw1 = new HeadersWrapper(1, "peer1", list);
        srm.storeHeaders(1, hw1);

        // retrieve when nothing was stored for the size
        assertThat(srm.matchHeaders(1, 12)).isNull();
    }

    @Test
    public void test_receivedHeaderManagement() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        HeadersWrapper hw1 = new HeadersWrapper(1, "peer1", list);

        srm.storeHeaders(1, hw1);
        assertThat(srm.matchHeaders(1, 10)).isEqualTo(hw1);

        // ensure the headers were dropped
        assertThat(srm.matchHeaders(1, 10)).isNull();
    }

    @Test
    public void test_replaceHeaders() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        HeadersWrapper hw1 = new HeadersWrapper(1, "peer1", list);

        srm.storeHeaders(1, hw1);
        assertThat(srm.matchHeaders(1, 10)).isEqualTo(hw1);

        // same size wrapper is replaced
        HeadersWrapper hw2 = new HeadersWrapper(1, "peer1", list);
        srm.storeHeaders(1, hw2);
        assertThat(srm.matchHeaders(1, 10)).isEqualTo(hw2);

        // ensure the headers were dropped
        assertThat(srm.matchHeaders(1, 10)).isNull();
    }

    @Test
    public void test_mutipleSizeHeaderResponses() {
        List<BlockHeader> list = mock(List.class);
        when(list.size()).thenReturn(10);
        HeadersWrapper hw1 = new HeadersWrapper(1, "peer1", list);

        srm.storeHeaders(1, hw1);
        assertThat(srm.matchHeaders(1, 10)).isEqualTo(hw1);

        // new wrapper with different size
        list = mock(List.class);
        when(list.size()).thenReturn(12);
        HeadersWrapper hw2 = new HeadersWrapper(1, "peer1", list);

        srm.storeHeaders(1, hw2);
        assertThat(srm.matchHeaders(1, 12)).isEqualTo(hw2);
    }
}

package org.aion.zero.impl.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.evtmgr.IEventMgr;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link SyncMgr}.
 *
 * @author Alexandra Roatis
 */
public class SyncMgrTest {

    @Mock
    AionBlockchainImpl chain;
    @Mock
    Block bestBlock;
    long bestBlockNumber = 100L;
    @Mock
    IP2pMgr p2pMgr;
    @Mock
    IEventMgr evtMgr;

    SyncMgr syncMgr;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(chain.getBestBlock()).thenReturn(bestBlock);
        when(bestBlock.getNumber()).thenReturn(bestBlockNumber);
        syncMgr = new SyncMgr(chain, p2pMgr, evtMgr, false, Collections.emptySet(), 10);
    }

    @Test
    public void testRequestBodies_withoutRequests() {
        syncMgr.requestBodies(1, "peer1");

        // ensure that no requests were sent
        verify(p2pMgr, never()).send(anyInt(), anyString(), any(ReqBlocksBodies.class));
    }

    @Test
    public void testRequestBodies_withOneRequest() {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(101L);
        byte[] hash = Hex.decode("6fd8dae3304a9864f460ec7aec21bc94e14e34876e5dddd0a74d9c68ac7bc9ed");
        when(header.getHash()).thenReturn(hash);
        List<BlockHeader> list = new ArrayList<>();
        list.add(header);
        syncMgr.syncHeaderRequestManager.storeHeaders(1, list);

        syncMgr.requestBodies(1, "peer1");

        // ensure that 1 request was sent
        verify(p2pMgr, times(1)).send(anyInt(), anyString(), any(ReqBlocksBodies.class));
        // the list is still stored
        assertThat(syncMgr.syncHeaderRequestManager.matchAndDropHeaders(1, 1)).isEqualTo(list);
    }

    @Test
    public void testRequestBodies_withOneRequestFromMultipleOptions() {
        BlockHeader header1 = mock(BlockHeader.class);
        when(header1.getNumber()).thenReturn(101L);
        byte[] hash1 = Hex.decode("6fd8dae3304a9864f460ec7aec21bc94e14e34876e5dddd0a74d9c68ac7bc9ed");
        when(header1.getHash()).thenReturn(hash1);
        List<BlockHeader> list1 = new ArrayList<>();
        list1.add(header1);
        syncMgr.syncHeaderRequestManager.storeHeaders(1, list1);

        BlockHeader header2 = mock(BlockHeader.class);
        when(header2.getNumber()).thenReturn(102L);
        byte[] hash2 = Hex.decode("f2652dde61042e9306dce95ecdc41a1be2be7eb374f19427aef2a79101b471ea");
        when(header2.getHash()).thenReturn(hash2);
        List<BlockHeader> list2 = new ArrayList<>();
        list2.add(header2);
        syncMgr.syncHeaderRequestManager.storeHeaders(1, list2);

        syncMgr.requestBodies(1, "peer1");

        // ensure that 1 request was sent
        verify(p2pMgr, times(1)).send(anyInt(), anyString(), any(ReqBlocksBodies.class));
        // both lists are still stored and can be retrieved in order
        assertThat(syncMgr.syncHeaderRequestManager.matchAndDropHeaders(1, 1)).isEqualTo(list1);
        assertThat(syncMgr.syncHeaderRequestManager.matchAndDropHeaders(1, 1)).isEqualTo(list2);
    }

    @Test
    public void testRequestBodies_withTwoRequests() {
        BlockHeader header1 = mock(BlockHeader.class);
        when(header1.getNumber()).thenReturn(101L);
        byte[] hash1 = Hex.decode("6fd8dae3304a9864f460ec7aec21bc94e14e34876e5dddd0a74d9c68ac7bc9ed");
        when(header1.getHash()).thenReturn(hash1);
        List<BlockHeader> list1 = new ArrayList<>();
        list1.add(header1);
        syncMgr.syncHeaderRequestManager.storeHeaders(1, list1);

        BlockHeader header2 = mock(BlockHeader.class);
        when(header2.getNumber()).thenReturn(102L);
        byte[] hash2 = Hex.decode("f2652dde61042e9306dce95ecdc41a1be2be7eb374f19427aef2a79101b471ea");
        when(header2.getHash()).thenReturn(hash2);
        List<BlockHeader> list2 = new ArrayList<>();
        list2.add(header1);
        list2.add(header2);
        syncMgr.syncHeaderRequestManager.storeHeaders(1, list2);

        syncMgr.requestBodies(1, "peer1");

        // ensure that 1 request was sent
        verify(p2pMgr, times(2)).send(anyInt(), anyString(), any(ReqBlocksBodies.class));
        // both lists are still stored and can be retrieved in order
        assertThat(syncMgr.syncHeaderRequestManager.matchAndDropHeaders(1, 1)).isEqualTo(list1);
        assertThat(syncMgr.syncHeaderRequestManager.matchAndDropHeaders(1, 2)).isEqualTo(list2);
    }

    @Test
    public void testRequestBodies_withFilteringOnBlockHash() {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(100L);
        byte[] hash = Hex.decode("6fd8dae3304a9864f460ec7aec21bc94e14e34876e5dddd0a74d9c68ac7bc9ed");
        when(header.getHash()).thenReturn(hash);
        List<BlockHeader> list = new ArrayList<>();
        list.add(header);
        syncMgr.syncHeaderRequestManager.storeHeaders(1, list);
        syncMgr.importedBlockHashes.put(ByteArrayWrapper.wrap(hash), true);

        syncMgr.requestBodies(1, "peer1");

        // ensure that 1 request was sent
        verify(p2pMgr, never()).send(anyInt(), anyString(), any(ReqBlocksBodies.class));
        assertThat(syncMgr.syncHeaderRequestManager.matchAndDropHeaders(1, 1)).isNull();
    }

    @Test
    public void testRequestBodies_withFilteringOnBlockNumber() {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(bestBlockNumber);
        byte[] hash = Hex.decode("6fd8dae3304a9864f460ec7aec21bc94e14e34876e5dddd0a74d9c68ac7bc9ed");
        when(header.getHash()).thenReturn(hash);
        List<BlockHeader> list = new ArrayList<>();
        list.add(header);
        INode peer1 = mock(INode.class);
        when(peer1.getIdHash()).thenReturn(1);
        when(peer1.getIdShort()).thenReturn("peer1");
        when(peer1.getBestBlockNumber()).thenReturn(2 * bestBlockNumber);
        // ensure that peer1 exists in the syncHeaderRequestManager
        syncMgr.syncHeaderRequestManager.assertUpdateActiveNodes(Map.of(1, peer1), null, null, Set.of(1), Set.of(1), 2 * bestBlockNumber);
        syncMgr.syncHeaderRequestManager.storeHeaders(1, list);
        syncMgr.syncHeaderRequestManager.runInMode(1, SyncMode.NORMAL);

        syncMgr.requestBodies(1, "peer1");

        // ensure that 1 request was sent
        verify(p2pMgr, never()).send(anyInt(), anyString(), any(ReqBlocksBodies.class));
        assertThat(syncMgr.syncHeaderRequestManager.matchAndDropHeaders(1, 1)).isNull();
    }

    @Test
    public void testRequestBodies_withFilteringOnBlockNumberAndRemainingHeaders() {
        BlockHeader header1 = mock(BlockHeader.class);
        when(header1.getNumber()).thenReturn(bestBlockNumber);
        byte[] hash1 = Hex.decode("6fd8dae3304a9864f460ec7aec21bc94e14e34876e5dddd0a74d9c68ac7bc9ed");
        when(header1.getHash()).thenReturn(hash1);
        BlockHeader header2 = mock(BlockHeader.class);
        when(header2.getNumber()).thenReturn(bestBlockNumber + 1);
        byte[] hash2 = Hex.decode("f2652dde61042e9306dce95ecdc41a1be2be7eb374f19427aef2a79101b471ea");
        when(header2.getHash()).thenReturn(hash2);
        List<BlockHeader> list = new ArrayList<>();
        list.add(header1);
        list.add(header2);

        INode peer1 = mock(INode.class);
        when(peer1.getIdHash()).thenReturn(1);
        when(peer1.getIdShort()).thenReturn("peer1");
        when(peer1.getBestBlockNumber()).thenReturn(2 * bestBlockNumber);
        // ensure that peer1 exists in the syncHeaderRequestManager
        syncMgr.syncHeaderRequestManager.assertUpdateActiveNodes(Map.of(1, peer1), null, null, Set.of(1), Set.of(1), 2 * bestBlockNumber);
        syncMgr.syncHeaderRequestManager.storeHeaders(1, list);
        syncMgr.syncHeaderRequestManager.runInMode(1, SyncMode.NORMAL);

        syncMgr.requestBodies(1, "peer1");

        // ensure that 1 request was sent
        verify(p2pMgr, never()).send(anyInt(), anyString(), any(ReqBlocksBodies.class));
        assertThat(syncMgr.syncHeaderRequestManager.matchAndDropHeaders(1, 1)).contains(header2);
    }

    @Test
    public void testRequestBodies_withFilteringOnBlockHashAndReadHeaders() {
        BlockHeader header1 = mock(BlockHeader.class);
        when(header1.getNumber()).thenReturn(100L);
        byte[] hash1 = Hex.decode("6fd8dae3304a9864f460ec7aec21bc94e14e34876e5dddd0a74d9c68ac7bc9ed");
        when(header1.getHash()).thenReturn(hash1);
        BlockHeader header2 = mock(BlockHeader.class);
        when(header2.getNumber()).thenReturn(101L);
        byte[] hash2 = Hex.decode("f2652dde61042e9306dce95ecdc41a1be2be7eb374f19427aef2a79101b471ea");
        when(header2.getHash()).thenReturn(hash2);

        List<BlockHeader> list = new ArrayList<>();
        list.add(header1);
        list.add(header2);
        syncMgr.syncHeaderRequestManager.storeHeaders(1, list);
        syncMgr.importedBlockHashes.put(ByteArrayWrapper.wrap(hash1), true);

        syncMgr.requestBodies(1, "peer1");

        // ensure that 1 request was sent
        verify(p2pMgr, never()).send(anyInt(), anyString(), any(ReqBlocksBodies.class));
        // a subset of the list is re-added for future requests
        assertThat(syncMgr.syncHeaderRequestManager.matchAndDropHeaders(1, 1)).contains(header2);
    }
}
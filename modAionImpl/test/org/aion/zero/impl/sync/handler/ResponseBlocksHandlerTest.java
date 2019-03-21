package org.aion.zero.impl.sync.handler;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.rlp.RLP;
import org.aion.util.TestResources;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.FastSyncManager;
import org.aion.zero.impl.sync.msg.ResponseBlocks;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Unit tests for {@link ResponseBlocksHandler}.
 *
 * @author Alexandra Roatis
 */
public class ResponseBlocksHandlerTest {
    private static final int peerId = Integer.MAX_VALUE;
    private static final String displayId = "abcdef";

    @Test
    public void testHeader() {
        Logger log = mock(Logger.class);
        FastSyncManager fastSyncManager = mock(FastSyncManager.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        ResponseBlocksHandler handler = new ResponseBlocksHandler(log, fastSyncManager, p2p);
        // check handler header
        assertThat(handler.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(handler.getHeader().getAction()).isEqualTo(Act.RESPONSE_BLOCKS);
    }

    @Test
    public void testReceive_nullMessage() {
        Logger log = mock(Logger.class);
        FastSyncManager fastSyncManager = mock(FastSyncManager.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        ResponseBlocksHandler handler = new ResponseBlocksHandler(log, fastSyncManager, p2p);

        // receive null message
        handler.receive(peerId, displayId, null);

        verify(p2p, times(1)).errCheck(peerId, displayId);
        verify(log, times(1)).debug("<response-blocks empty message from peer={}>", displayId);
        verifyZeroInteractions(fastSyncManager);
    }

    @Test
    public void testReceive_emptyMessage() {
        Logger log = mock(Logger.class);
        FastSyncManager fastSyncManager = mock(FastSyncManager.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        ResponseBlocksHandler handler = new ResponseBlocksHandler(log, fastSyncManager, p2p);

        // receive empty message
        handler.receive(peerId, displayId, new byte[0]);

        verify(p2p, times(1)).errCheck(peerId, displayId);
        verify(log, times(1)).debug("<response-blocks empty message from peer={}>", displayId);
        verifyZeroInteractions(fastSyncManager);
    }

    @Test
    public void testReceive_incorrectMessage() {
        Logger log = mock(Logger.class);
        when(log.isTraceEnabled()).thenReturn(false);
        when(log.isDebugEnabled()).thenReturn(true);

        FastSyncManager fastSyncManager = mock(FastSyncManager.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        ResponseBlocksHandler handler = new ResponseBlocksHandler(log, fastSyncManager, p2p);

        // receive incorrect message
        byte[] incorrectEncoding = RLP.encodeInt(1);
        handler.receive(peerId, displayId, incorrectEncoding);

        verify(p2p, times(1)).errCheck(peerId, displayId);
        verify(log, times(1))
                .error(
                        "<response-blocks decode-error msg-bytes={} peer={}>",
                        incorrectEncoding.length,
                        displayId);
        verifyZeroInteractions(fastSyncManager);
    }

    @Test
    public void testReceive_incorrectMessage_withTrace() {
        Logger log = mock(Logger.class);
        when(log.isTraceEnabled()).thenReturn(true);

        FastSyncManager fastSyncManager = mock(FastSyncManager.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        ResponseBlocksHandler handler = new ResponseBlocksHandler(log, fastSyncManager, p2p);

        // receive incorrect message
        byte[] incorrectEncoding = RLP.encodeInt(1);
        handler.receive(peerId, displayId, incorrectEncoding);

        verify(p2p, times(1)).errCheck(peerId, displayId);
        verify(log, times(1))
                .error(
                        "<response-blocks decode-error msg-bytes={} peer={}>",
                        incorrectEncoding.length,
                        displayId);
        verify(log, times(1))
                .trace(
                        "<response-blocks decode-error for msg={} peer={}>",
                        Arrays.toString(incorrectEncoding),
                        displayId);
        verifyZeroInteractions(fastSyncManager);
    }

    @Test
    public void testReceive_correctMessage() {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        FastSyncManager fastSyncManager = mock(FastSyncManager.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        ResponseBlocksHandler handler = new ResponseBlocksHandler(log, fastSyncManager, p2p);

        // receive correct message
        byte[] encoding = RLP.encodeList(TestResources.consecutiveBlocks(1).get(0).getEncoded());
        handler.receive(peerId, displayId, encoding);

        ResponseBlocks response = ResponseBlocks.decode(encoding);

        verify(log, times(1)).debug("<response-blocks response={} peer={}>", response, displayId);
        verify(fastSyncManager, times(1)).validateAndAddBlocks(peerId, displayId, response);
        verifyZeroInteractions(p2p);
    }
}

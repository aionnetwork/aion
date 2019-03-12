package org.aion.zero.impl.sync.handler;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.rlp.RLP;
import org.aion.util.TestResources;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.RequestBlocks;
import org.aion.zero.impl.sync.msg.ResponseBlocks;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Unit tests for {@link RequestBlocksHandler}.
 *
 * @author Alexandra Roatis
 */
public class RequestBlocksHandlerTest {
    private static final int peerId = Integer.MAX_VALUE;
    private static final String displayId = "abcdef";

    private final byte isTrue = 1;
    private final byte isFalse = 0;

    @Test
    public void testHeader() {
        Logger log = mock(Logger.class);
        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);
        // check handler header
        assertThat(handler.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(handler.getHeader().getAction()).isEqualTo(Act.REQUEST_BLOCKS);
    }

    @Test
    public void testReceive_nullMessage() {
        Logger log = mock(Logger.class);
        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive null message
        handler.receive(peerId, displayId, null);

        verify(log, times(1)).debug("<request-blocks empty message from peer={}>", displayId);
        verifyZeroInteractions(chain);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_emptyMessage() {
        Logger log = mock(Logger.class);
        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive empty message
        handler.receive(peerId, displayId, new byte[0]);

        verify(log, times(1)).debug("<request-blocks empty message from peer={}>", displayId);
        verifyZeroInteractions(chain);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_incorrectMessage() {
        Logger log = mock(Logger.class);
        when(log.isTraceEnabled()).thenReturn(false);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive incorrect message
        byte[] incorrectEncoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encode(Byte.MAX_VALUE + 1));
        handler.receive(peerId, displayId, incorrectEncoding);

        verify(log, times(1))
                .error(
                        "<request-blocks decode-error msg-bytes={} peer={}>",
                        incorrectEncoding.length,
                        displayId);
        verifyZeroInteractions(chain);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_incorrectMessage_withTrace() {
        Logger log = mock(Logger.class);
        when(log.isTraceEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive incorrect message
        byte[] incorrectEncoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encode(consecutiveBlocks.get(0).getHash()),
                        RLP.encodeInt(10),
                        RLP.encode(Byte.MAX_VALUE + 1));
        handler.receive(peerId, displayId, incorrectEncoding);

        verify(log, times(1))
                .error(
                        "<request-blocks decode-error msg-bytes={} peer={}>",
                        incorrectEncoding.length,
                        displayId);
        verify(log, times(1))
                .trace(
                        "<request-blocks decode-error for msg={} peer={}>",
                        Arrays.toString(incorrectEncoding),
                        displayId);
        verifyZeroInteractions(chain);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_correctMessage_nullValue_withHeight() {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getBlocksByRange(10L, 1L)).thenReturn(null);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isTrue));
        handler.receive(peerId, displayId, encoding);

        verify(log, times(1))
                .debug("<request-blocks from-block={} count={} order={}>", 10L, 10, "DESC");
        verify(chain, times(1)).getBlocksByRange(10L, 1L);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_correctMessage_nullBlock_withHash() {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        byte[] hash = consecutiveBlocks.get(0).getHash();
        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getBlockByHash(hash)).thenReturn(null);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encode(hash),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isTrue));
        handler.receive(peerId, displayId, encoding);

        verify(log, times(1))
                .debug(
                        "<request-blocks from-block={} count={} order={}>",
                        Hex.toHexString(hash),
                        10,
                        "DESC");
        verify(chain, times(1)).getBlockByHash(hash);
        verify(chain, times(0)).getBlocksByRange(10L, 1L);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_correctMessage_nullValue_withHash() {
        AionBlock first = consecutiveBlocks.get(0);
        byte[] hash = first.getHash();
        AionBlock last = consecutiveBlocks.get(3);

        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getBlockByHash(hash)).thenReturn(first);
        when(chain.getBlocksByRange(first.getNumber(), last.getNumber())).thenReturn(null);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encode(hash),
                        RLP.encodeInt(4),
                        RLP.encodeByte(isFalse));
        handler.receive(peerId, displayId, encoding);

        verify(log, times(1))
                .debug(
                        "<request-blocks from-block={} count={} order={}>",
                        Hex.toHexString(hash),
                        4,
                        "ASC");
        verify(chain, times(1)).getBlockByHash(hash);
        verify(chain, times(1)).getBlocksByRange(first.getNumber(), last.getNumber());

        ResponseBlocks expectedResponse = new ResponseBlocks(List.of(first));
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }

    @Test
    public void testReceive_correctMessage_withException_withHeight() {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        Exception e = new NullPointerException();
        when(chain.getBlocksByRange(10L, 1L)).thenThrow(e);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isTrue));
        handler.receive(peerId, displayId, encoding);

        verify(log, times(1))
                .debug("<request-blocks from-block={} count={} order={}>", 10L, 10, "DESC");
        verify(log).error("<request-blocks value retrieval failed>", e);
        verify(chain, times(1)).getBlocksByRange(10L, 1L);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_correctMessage_withException_withHash() {
        AionBlock first = consecutiveBlocks.get(0);
        byte[] hash = first.getHash();
        AionBlock last = consecutiveBlocks.get(3);

        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getBlockByHash(hash)).thenReturn(first);
        Exception e = new NullPointerException();
        when(chain.getBlocksByRange(first.getNumber(), last.getNumber())).thenThrow(e);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encode(hash),
                        RLP.encodeInt(4),
                        RLP.encodeByte(isFalse));
        handler.receive(peerId, displayId, encoding);

        verify(log, times(1))
                .debug(
                        "<request-blocks from-block={} count={} order={}>",
                        Hex.toHexString(hash),
                        4,
                        "ASC");
        verify(log).error("<request-blocks value retrieval failed>", e);

        verify(chain, times(1)).getBlockByHash(hash);
        verify(chain, times(1)).getBlocksByRange(first.getNumber(), last.getNumber());

        ResponseBlocks expectedResponse = new ResponseBlocks(List.of(first));
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }

    // returns a list of blocks in ascending order of height
    List<AionBlock> consecutiveBlocks = TestResources.consecutiveBlocks(4);

    @Test
    public void testReceive_correctMessage_ascending_withHeight() {
        AionBlock first = consecutiveBlocks.get(0);
        AionBlock last = consecutiveBlocks.get(3);

        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getBlocksByRange(first.getNumber(), last.getNumber()))
                .thenReturn(consecutiveBlocks);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        RequestBlocks request = new RequestBlocks(first.getNumber(), 4, false);
        handler.receive(peerId, displayId, request.encode());

        verify(log, times(1))
                .debug(
                        "<request-blocks from-block={} count={} order={}>",
                        first.getNumber(),
                        4,
                        "ASC");
        verify(chain, times(1)).getBlocksByRange(first.getNumber(), last.getNumber());

        ResponseBlocks expectedResponse = new ResponseBlocks(consecutiveBlocks);
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }

    @Test
    public void testReceive_correctMessage_ascending_withHash() {
        AionBlock first = consecutiveBlocks.get(0);
        byte[] hash = first.getHash();
        AionBlock last = consecutiveBlocks.get(3);

        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getBlockByHash(hash)).thenReturn(first);
        when(chain.getBlocksByRange(first.getNumber(), last.getNumber()))
                .thenReturn(consecutiveBlocks);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        RequestBlocks request = new RequestBlocks(hash, 4, false);
        handler.receive(peerId, displayId, request.encode());

        verify(log, times(1))
                .debug(
                        "<request-blocks from-block={} count={} order={}>",
                        Hex.toHexString(hash),
                        4,
                        "ASC");
        verify(chain, times(1)).getBlockByHash(hash);
        verify(chain, times(1)).getBlocksByRange(first.getNumber(), last.getNumber());

        ResponseBlocks expectedResponse = new ResponseBlocks(consecutiveBlocks);
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }

    @Test
    public void testReceive_correctMessage_descending_withHeight() {
        AionBlock first = consecutiveBlocks.get(3);
        AionBlock last = consecutiveBlocks.get(0);
        // reverse the list order
        LinkedList<AionBlock> reverse = new LinkedList<>();
        for (AionBlock b : consecutiveBlocks) {
            reverse.addFirst(b);
        }

        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getBlocksByRange(first.getNumber(), last.getNumber())).thenReturn(reverse);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        RequestBlocks request = new RequestBlocks(first.getNumber(), 4, true);
        handler.receive(peerId, displayId, request.encode());

        verify(log, times(1))
                .debug(
                        "<request-blocks from-block={} count={} order={}>",
                        first.getNumber(),
                        4,
                        "DESC");
        verify(chain, times(1)).getBlocksByRange(first.getNumber(), last.getNumber());

        ResponseBlocks expectedResponse = new ResponseBlocks(reverse);
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }

    @Test
    public void testReceive_correctMessage_descending_withHash() {
        AionBlock first = consecutiveBlocks.get(3);
        byte[] hash = first.getHash();
        AionBlock last = consecutiveBlocks.get(0);
        // reverse the list order
        LinkedList<AionBlock> reverse = new LinkedList<>();
        for (AionBlock b : consecutiveBlocks) {
            reverse.addFirst(b);
        }

        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getBlockByHash(hash)).thenReturn(first);
        when(chain.getBlocksByRange(first.getNumber(), last.getNumber())).thenReturn(reverse);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        RequestBlocks request = new RequestBlocks(hash, 4, true);
        handler.receive(peerId, displayId, request.encode());

        verify(log, times(1))
                .debug(
                        "<request-blocks from-block={} count={} order={}>",
                        Hex.toHexString(hash),
                        4,
                        "DESC");
        verify(chain, times(1)).getBlockByHash(hash);
        verify(chain, times(1)).getBlocksByRange(first.getNumber(), last.getNumber());

        ResponseBlocks expectedResponse = new ResponseBlocks(reverse);
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }

    @Test
    public void testReceive_correctMessage_withHashOnSideChain() {
        AionBlock first = consecutiveBlocks.get(0);
        byte[] hash = first.getHash();
        AionBlock last = consecutiveBlocks.get(3);

        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getBlockByHash(hash)).thenReturn(first);
        when(chain.getBlocksByRange(first.getNumber(), last.getNumber())).thenReturn(List.of(last));

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestBlocksHandler handler = new RequestBlocksHandler(log, chain, p2p);

        // receive correct message
        RequestBlocks request = new RequestBlocks(hash, 4, false);
        handler.receive(peerId, displayId, request.encode());

        verify(log, times(1))
                .debug(
                        "<request-blocks from-block={} count={} order={}>",
                        Hex.toHexString(hash),
                        4,
                        "ASC");
        verify(chain, times(1)).getBlockByHash(hash);
        verify(chain, times(1)).getBlocksByRange(first.getNumber(), last.getNumber());

        ResponseBlocks expectedResponse = new ResponseBlocks(List.of(first));
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }
}

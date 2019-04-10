package org.aion.zero.impl.sync.handler;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.p2p.V1Constants.TRIE_DATA_REQUEST_MAXIMUM_BATCH_SIZE;
import static org.aion.zero.impl.sync.DatabaseType.STATE;
import static org.aion.zero.impl.sync.msg.RequestTrieDataTest.nodeKey;
import static org.aion.zero.impl.sync.msg.ResponseTrieDataTest.leafValue;
import static org.aion.zero.impl.sync.msg.ResponseTrieDataTest.multipleReferences;
import static org.aion.zero.impl.sync.msg.ResponseTrieDataTest.singleReference;
import static org.aion.zero.impl.sync.msg.ResponseTrieDataTest.wrappedNodeKey;
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
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ResponseTrieData;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Unit tests for {@link RequestTrieDataHandler}.
 *
 * @author Alexandra Roatis
 */
public class RequestTrieDataHandlerTest {
    private static final int peerId = Integer.MAX_VALUE;
    private static final String displayId = "abcdef";

    @Test
    public void testHeader() {
        Logger log = mock(Logger.class);
        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        RequestTrieDataHandler handler = new RequestTrieDataHandler(log, chain, p2p);
        // check handler header
        assertThat(handler.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(handler.getHeader().getAction()).isEqualTo(Act.REQUEST_TRIE_DATA);
    }

    @Test
    public void testReceive_nullMessage() {
        Logger log = mock(Logger.class);
        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        RequestTrieDataHandler handler = new RequestTrieDataHandler(log, chain, p2p);

        // receive null message
        handler.receive(peerId, displayId, null);

        verify(log, times(1)).debug("<req-trie empty message from peer={}>", displayId);
        verifyZeroInteractions(chain);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_emptyMessage() {
        Logger log = mock(Logger.class);
        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        RequestTrieDataHandler handler = new RequestTrieDataHandler(log, chain, p2p);

        // receive empty message
        handler.receive(peerId, displayId, new byte[0]);

        verify(log, times(1)).debug("<req-trie empty message from peer={}>", displayId);
        verifyZeroInteractions(chain);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_incorrectMessage() {
        Logger log = mock(Logger.class);
        when(log.isTraceEnabled()).thenReturn(false);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        IP2pMgr p2p = mock(P2pMgr.class);

        RequestTrieDataHandler handler = new RequestTrieDataHandler(log, chain, p2p);

        // receive incorrect message
        byte[] outOfOderEncoding =
                RLP.encodeList(
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeElement(nodeKey),
                        RLP.encodeInt(0));
        handler.receive(peerId, displayId, outOfOderEncoding);

        verify(log, times(1))
                .error(
                        "<req-trie decode-error msg-bytes={} peer={}>",
                        outOfOderEncoding.length,
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

        RequestTrieDataHandler handler = new RequestTrieDataHandler(log, chain, p2p);

        // receive incorrect message
        byte[] outOfOderEncoding =
                RLP.encodeList(
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeElement(nodeKey),
                        RLP.encodeInt(0));
        handler.receive(peerId, displayId, outOfOderEncoding);

        verify(log, times(1))
                .error(
                        "<req-trie decode-error msg-bytes={} peer={}>",
                        outOfOderEncoding.length,
                        displayId);
        verify(log, times(1))
                .trace(
                        "<req-trie decode-error for msg={} peer={}>",
                        Arrays.toString(outOfOderEncoding),
                        displayId);
        verifyZeroInteractions(chain);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_correctMessage_nullValue() {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getTrieNode(nodeKey, STATE)).thenReturn(null);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestTrieDataHandler handler = new RequestTrieDataHandler(log, chain, p2p);

        // receive correct message
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(0));
        handler.receive(peerId, displayId, encoding);

        verify(log, times(1))
                .debug("<req-trie from-db={} key={} peer={}>", STATE, wrappedNodeKey, displayId);
        verify(chain, times(1)).getTrieNode(nodeKey, STATE);
        verifyZeroInteractions(p2p);
    }

    @Test
    public void testReceive_correctMessage_limitOne() {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getTrieNode(nodeKey, STATE)).thenReturn(leafValue);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestTrieDataHandler handler = new RequestTrieDataHandler(log, chain, p2p);

        // receive correct message
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(1));
        handler.receive(peerId, displayId, encoding);

        verify(log, times(1))
                .debug("<req-trie from-db={} key={} peer={}>", STATE, wrappedNodeKey, displayId);

        verify(chain, times(1)).getTrieNode(nodeKey, STATE);

        ResponseTrieData expectedResponse = new ResponseTrieData(wrappedNodeKey, leafValue, STATE);
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }

    @Test
    public void testReceive_correctMessage_limitZero_akaMaxBatchSize() {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getTrieNode(nodeKey, STATE)).thenReturn(leafValue);
        when(chain.getReferencedTrieNodes(leafValue, TRIE_DATA_REQUEST_MAXIMUM_BATCH_SIZE, STATE))
                .thenReturn(multipleReferences);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestTrieDataHandler handler = new RequestTrieDataHandler(log, chain, p2p);

        // receive correct message
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(0));
        handler.receive(peerId, displayId, encoding);

        verify(log, times(1))
                .debug("<req-trie from-db={} key={} peer={}>", STATE, wrappedNodeKey, displayId);

        verify(chain, times(1)).getTrieNode(nodeKey, STATE);
        verify(chain, times(1))
                .getReferencedTrieNodes(leafValue, TRIE_DATA_REQUEST_MAXIMUM_BATCH_SIZE, STATE);

        ResponseTrieData expectedResponse =
                new ResponseTrieData(wrappedNodeKey, leafValue, multipleReferences, STATE);
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }

    @Test
    public void testReceive_correctMessage_limitTwo() {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        IAionBlockchain chain = mock(AionBlockchainImpl.class);
        when(chain.getTrieNode(nodeKey, STATE)).thenReturn(leafValue);
        when(chain.getReferencedTrieNodes(leafValue, 1, STATE)).thenReturn(singleReference);

        IP2pMgr p2p = mock(P2pMgr.class);

        RequestTrieDataHandler handler = new RequestTrieDataHandler(log, chain, p2p);

        // receive correct message
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(2));
        handler.receive(peerId, displayId, encoding);

        verify(log, times(1))
                .debug("<req-trie from-db={} key={} peer={}>", STATE, wrappedNodeKey, displayId);

        verify(chain, times(1)).getTrieNode(nodeKey, STATE);
        verify(chain, times(1)).getReferencedTrieNodes(leafValue, 1, STATE);

        ResponseTrieData expectedResponse =
                new ResponseTrieData(wrappedNodeKey, leafValue, singleReference, STATE);
        verify(p2p, times(1)).send(peerId, displayId, expectedResponse);
    }
}

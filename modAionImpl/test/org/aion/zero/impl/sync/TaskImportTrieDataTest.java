package org.aion.zero.impl.sync;

import static org.aion.zero.impl.sync.DatabaseType.STATE;
import static org.aion.zero.impl.sync.msg.RequestTrieDataTest.nodeKey;
import static org.aion.zero.impl.sync.msg.ResponseTrieDataTest.leafValue;
import static org.aion.zero.impl.sync.msg.ResponseTrieDataTest.wrappedNodeKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.mcf.trie.TrieNodeResult;
import org.aion.rlp.RLP;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.sync.msg.ResponseTrieData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

/**
 * Unit tests for {@link TaskImportTrieData}.
 *
 * @author Alexandra Roatis
 */
@RunWith(JUnitParamsRunner.class)
public class TaskImportTrieDataTest {
    private static final int peerId = Integer.MAX_VALUE;
    private static final String displayId = "abcdef";

    @Test
    public void testRun_shutdown() {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);

        FastSyncManager fastSyncMgr = mock(FastSyncManager.class);
        when(fastSyncMgr.isComplete()).thenReturn(true);

        BlockingQueue<TrieNodeWrapper> trieNodes = mock(LinkedBlockingQueue.class);

        // run task
        TaskImportTrieData task = new TaskImportTrieData(log, chain, trieNodes, fastSyncMgr);
        task.run();

        verify(log, times(1)).debug("<import-trie-nodes: shutdown>");
        verifyZeroInteractions(chain);
        verifyZeroInteractions(trieNodes);
        verify(fastSyncMgr, times(1)).isComplete();
    }

    @Test
    public void testRun_interrupt() throws InterruptedException {
        Logger log = mock(Logger.class);
        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);

        FastSyncManager fastSyncMgr = mock(FastSyncManager.class);
        when(fastSyncMgr.isComplete()).thenReturn(false);

        BlockingQueue<TrieNodeWrapper> trieNodes = mock(LinkedBlockingQueue.class);
        InterruptedException exception = new InterruptedException();
        when(trieNodes.take()).thenThrow(exception);

        // run task
        TaskImportTrieData task = new TaskImportTrieData(log, chain, trieNodes, fastSyncMgr);
        task.run();

        verify(log, times(1))
                .error("<import-trie-nodes: interrupted without shutdown request>", exception);
        verifyZeroInteractions(chain);
        verify(trieNodes, times(1)).take();
        verify(fastSyncMgr, times(2)).isComplete();
    }

    @Test
    public void testRun_filteredNodes() throws InterruptedException {
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(false);

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);

        FastSyncManager fastSyncMgr = mock(FastSyncManager.class);
        when(fastSyncMgr.isComplete()).thenReturn(false, true);
        when(fastSyncMgr.containsExact(any(), any())).thenReturn(true);

        BlockingQueue<TrieNodeWrapper> trieNodes = mock(LinkedBlockingQueue.class);
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeElement(leafValue),
                        RLP.encodeList(new byte[0]),
                        RLP.encodeString(STATE.toString()));
        ResponseTrieData response = ResponseTrieData.decode(encoding);
        TrieNodeWrapper node = new TrieNodeWrapper(peerId, displayId, response);
        when(trieNodes.take()).thenReturn(node);

        // run task
        TaskImportTrieData task = new TaskImportTrieData(log, chain, trieNodes, fastSyncMgr);
        task.run();

        verify(trieNodes, times(1)).take();
        verifyZeroInteractions(chain);
        verify(fastSyncMgr, times(2)).isComplete();
        verify(fastSyncMgr, times(0)).addImportedNode(wrappedNodeKey, leafValue, STATE);
        verify(fastSyncMgr, times(0)).updateRequests(wrappedNodeKey, Collections.emptySet(), STATE);
    }

    @Test
    @Parameters({"IMPORTED", "KNOWN"})
    public void testRun_addNode_success(String result) throws InterruptedException {
        TrieNodeResult success = TrieNodeResult.valueOf(result);
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(false);

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.importTrieNode(nodeKey, leafValue, STATE)).thenReturn(success);

        FastSyncManager fastSyncMgr = mock(FastSyncManager.class);
        when(fastSyncMgr.isComplete()).thenReturn(false, true);
        when(fastSyncMgr.containsExact(any(), any())).thenReturn(false);

        BlockingQueue<TrieNodeWrapper> trieNodes = mock(LinkedBlockingQueue.class);
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeElement(leafValue),
                        RLP.encodeList(new byte[0]),
                        RLP.encodeString(STATE.toString()));
        ResponseTrieData response = ResponseTrieData.decode(encoding);
        TrieNodeWrapper node = new TrieNodeWrapper(peerId, displayId, response);
        when(trieNodes.take()).thenReturn(node);

        // run task
        TaskImportTrieData task = new TaskImportTrieData(log, chain, trieNodes, fastSyncMgr);
        task.run();

        verify(log, times(1))
                .debug(
                        "<import-trie-nodes: key={}, value length={}, db={}, result={}, peer={}>",
                        wrappedNodeKey,
                        leafValue.length,
                        STATE,
                        success,
                        displayId);
        verify(trieNodes, times(1)).take();
        verify(chain, times(1)).importTrieNode(nodeKey, leafValue, STATE);
        verify(fastSyncMgr, times(2)).isComplete();
        verify(fastSyncMgr, times(1)).addImportedNode(wrappedNodeKey, leafValue, STATE);
        verify(fastSyncMgr, times(1)).updateRequests(wrappedNodeKey, Collections.emptySet(), STATE);
    }

    @Test
    @Parameters({"INCONSISTENT", "INVALID_KEY", "INVALID_VALUE"})
    public void testRun_addNode_fail(String result) throws InterruptedException {
        TrieNodeResult fail = TrieNodeResult.valueOf(result);
        Logger log = mock(Logger.class);
        when(log.isDebugEnabled()).thenReturn(true);

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.importTrieNode(nodeKey, leafValue, STATE)).thenReturn(fail);

        FastSyncManager fastSyncMgr = mock(FastSyncManager.class);
        when(fastSyncMgr.isComplete()).thenReturn(false, true);
        when(fastSyncMgr.containsExact(any(), any())).thenReturn(false);

        BlockingQueue<TrieNodeWrapper> trieNodes = mock(LinkedBlockingQueue.class);
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeElement(leafValue),
                        RLP.encodeList(new byte[0]),
                        RLP.encodeString(STATE.toString()));
        ResponseTrieData response = ResponseTrieData.decode(encoding);
        TrieNodeWrapper node = new TrieNodeWrapper(peerId, displayId, response);
        when(trieNodes.take()).thenReturn(node);

        // run task
        TaskImportTrieData task = new TaskImportTrieData(log, chain, trieNodes, fastSyncMgr);
        task.run();

        verify(log, times(1))
                .debug(
                        "<import-trie-nodes-failed: key={}, value={}, db={}, result={}, peer={}>",
                        wrappedNodeKey,
                        Hex.toHexString(leafValue),
                        STATE,
                        fail,
                        displayId);
        verify(trieNodes, times(1)).take();
        verify(chain, times(1)).importTrieNode(nodeKey, leafValue, STATE);
        verify(fastSyncMgr, times(2)).isComplete();
        verify(fastSyncMgr, times(1))
                .handleFailedImport(wrappedNodeKey, leafValue, STATE, peerId, displayId);
    }
}

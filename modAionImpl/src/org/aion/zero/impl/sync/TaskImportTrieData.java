package org.aion.zero.impl.sync;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.Hex;
import org.aion.mcf.trie.TrieNodeResult;
import org.aion.zero.impl.AionBlockchainImpl;
import org.slf4j.Logger;

/**
 * Processes the received trie nodes that were requested. The thread is shut down once the fast sync
 * manager indicates that the full trie is the complete.
 *
 * @author Alexandra Roatis
 */
final class TaskImportTrieData implements Runnable {

    private final Logger log;
    private final FastSyncManager fastSyncMgr;

    private final AionBlockchainImpl chain;
    private final BlockingQueue<TrieNodeWrapper> trieNodes;

    /**
     * Constructor.
     *
     * @param log logger for reporting execution information
     * @param chain the blockchain used by the application
     * @param trieNodes received trie nodes
     * @param fastSyncMgr manages the fast sync process and indicates when completeness is reached
     */
    TaskImportTrieData(
            final Logger log,
            final AionBlockchainImpl chain,
            final BlockingQueue<TrieNodeWrapper> trieNodes,
            final FastSyncManager fastSyncMgr) {
        this.log = log;
        this.chain = chain;
        this.trieNodes = trieNodes;
        this.fastSyncMgr = fastSyncMgr;
    }

    @Override
    public void run() {
        // importing the trie state should be highest priority
        // since it is usually the longest process (on large blockchains)
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        while (!fastSyncMgr.isComplete()) {
            TrieNodeWrapper tnw;
            try {
                tnw = trieNodes.take();
            } catch (InterruptedException ex) {
                if (!fastSyncMgr.isComplete()) {
                    log.error("<import-trie-nodes: interrupted without shutdown request>", ex);
                }
                return;
            }

            // filter nodes that already match imported values
            Map<ByteArrayWrapper, byte[]> nodes = filterImported(tnw);

            // skip batch if everything already imported
            if (nodes.isEmpty()) {
                continue;
            }

            DatabaseType dbType = tnw.getDbType();
            String peer = tnw.getDisplayId();
            ByteArrayWrapper key;
            byte[] value;
            boolean failed = false;

            for (Entry<ByteArrayWrapper, byte[]> e : nodes.entrySet()) {
                key = e.getKey();
                value = e.getValue();

                TrieNodeResult result = chain.importTrieNode(key.getData(), value, dbType);

                if (result.isSuccessful()) {
                    fastSyncMgr.addImportedNode(key, value, dbType);
                    log.debug(
                            "<import-trie-nodes: key={}, value length={}, db={}, result={}, peer={}>",
                            key,
                            value.length,
                            dbType,
                            result,
                            peer);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "<import-trie-nodes-failed: key={}, value={}, db={}, result={}, peer={}>",
                                key,
                                Hex.toHexString(value),
                                dbType,
                                result,
                                peer);
                    }
                    fastSyncMgr.handleFailedImport(key, value, dbType, tnw.getPeerId(), peer);
                    failed = true;
                    // exit this loop and ignore other imports
                    break;
                }
            }

            if (!failed) {
                // reexamine missing states and make further requests
                fastSyncMgr.updateRequests(
                        tnw.getNodeKey(), tnw.getReferencedNodes().keySet(), tnw.getDbType());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("<import-trie-nodes: shutdown>");
        }
    }

    /**
     * Filters out trie nodes that have been imported when both the key and the value match the
     * already imported data.
     *
     * @param wrapper the initial set of trie nodes to be imported
     * @return the remaining nodes after the exact matches have been filtered out
     */
    private Map<ByteArrayWrapper, byte[]> filterImported(TrieNodeWrapper wrapper) {
        Map<ByteArrayWrapper, byte[]> nodes =
                wrapper.getReferencedNodes()
                        .entrySet()
                        .stream()
                        .filter(e -> !fastSyncMgr.containsExact(e.getKey(), e.getValue()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        if (!fastSyncMgr.containsExact(wrapper.getNodeKey(), wrapper.getNodeValue())) {
            nodes.put(wrapper.getNodeKey(), wrapper.getNodeValue());
        }
        return nodes;
    }
}

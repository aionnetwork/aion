package org.aion.mcf.trie;

import static org.aion.rlp.Value.fromRlpEncoded;
import static org.aion.vm.api.types.ByteArrayWrapper.wrap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import org.aion.crypto.HashUtil;
import org.aion.interfaces.db.ByteArrayKeyValueStore;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.Value;
import org.aion.vm.api.types.ByteArrayWrapper;
import org.slf4j.Logger;

/** Cache class */
public class Cache {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private ByteArrayKeyValueStore dataSource;
    private Map<ByteArrayWrapper, Node> nodes = new LinkedHashMap<>();
    private Set<ByteArrayWrapper> removedNodes = new HashSet<>();
    private boolean isDirty;

    public Cache(ByteArrayKeyValueStore dataSource) {
        this.dataSource = dataSource;
    }

    public synchronized void markRemoved(byte[] key) {
        ByteArrayWrapper keyW = new ByteArrayWrapper(key);
        removedNodes.add(keyW);
        nodes.remove(keyW);
    }

    /**
     * Put the node in the cache if RLP encoded value is longer than 32 bytes
     *
     * @param o the Node which could be a pair-, multi-item Node or single Value
     * @return keccak hash of RLP encoded node if length &gt; 32 otherwise return node itself
     */
    public synchronized Object put(Object o) {
        Value value = new Value(o);
        byte[] enc = value.encode();
        if (enc.length >= 32) {
            byte[] sha = HashUtil.h256(value.encode());
            ByteArrayWrapper key = wrap(sha);
            this.nodes.put(key, new Node(value, true));
            this.removedNodes.remove(key);
            this.isDirty = true;

            return sha;
        }
        return value;
    }

    public synchronized Value get(byte[] key) {

        ByteArrayWrapper wrappedKey = wrap(key);
        Node node = nodes.get(wrappedKey);
        if (node != null) {
            // cachehits++;
            return node.getValue();
        }
        if (this.dataSource != null) {
            Optional<byte[]> data = this.dataSource.get(key);
            if (data.isPresent()) {
                // dbhits++;
                Value val = fromRlpEncoded(data.get());
                nodes.put(wrappedKey, new Node(val, false));
                return val;
            }
        }

        return null;
    }

    public synchronized void delete(byte[] key) {
        ByteArrayWrapper wrappedKey = wrap(key);
        this.nodes.remove(wrappedKey);

        if (dataSource != null) {
            this.dataSource.delete(key);
        }
    }

    public synchronized void commit(boolean flushCache) {
        // Don't try to commit if it isn't dirty
        if ((dataSource == null) || !this.isDirty) {
            // clear cache when flush requested
            if (flushCache) {
                this.nodes.clear();
            }
            return;
        }

        // long start = System.nanoTime();
        // int batchMemorySize = 0;
        Map<byte[], byte[]> batch = new HashMap<>();
        List<byte[]> deleteBatch = new ArrayList<>();
        for (ByteArrayWrapper nodeKey : this.nodes.keySet()) {
            Node node = this.nodes.get(nodeKey);

            if (node == null || node.isDirty()) {
                byte[] value;
                if (node != null) {
                    node.setDirty(false);
                    value = node.getValue().encode();
                } else {
                    value = null;
                }

                byte[] key = nodeKey.getData();

                batch.put(key, value);
                // batchMemorySize += length(key, value);
            }
        }
        for (ByteArrayWrapper removedNode : removedNodes) {
            deleteBatch.add(removedNode.getData());
        }

        this.dataSource.putBatch(batch);
        this.dataSource.deleteBatch(deleteBatch);
        this.isDirty = false;
        if (flushCache) {
            this.nodes.clear();
        }
        this.removedNodes.clear();
    }

    // not used
    //    public synchronized void undo() {
    //        this.nodes.entrySet().removeIf(entry -> entry.getValue().isDirty());
    //        this.isDirty = false;
    //    }

    public synchronized boolean isDirty() {
        return isDirty;
    }

    public synchronized Map<ByteArrayWrapper, Node> getNodes() {
        return nodes;
    }

    public synchronized ByteArrayKeyValueStore getDb() {
        return dataSource;
    }

    // not used
    //    public String cacheDump() {
    //        StringBuilder cacheDump = new StringBuilder();
    //        for (ByteArrayWrapper key : nodes.keySet()) {
    //            Node node = nodes.get(key);
    //            if (node.getValue() != null) {
    //                cacheDump
    //                        .append(key.toString())
    //                        .append(" : ")
    //                        .append(node.getValue().toString())
    //                        .append("\n");
    //            }
    //        }
    //
    //        return cacheDump.toString();
    //    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public synchronized void setDB(ByteArrayKeyValueStore kvds) {
        if (this.dataSource == kvds) {
            return;
        }

        Map<byte[], byte[]> rows = new HashMap<>();
        if (this.dataSource == null) {
            for (ByteArrayWrapper key : nodes.keySet()) {
                Node node = nodes.get(key);
                if (node == null) {
                    rows.put(key.getData(), null);
                } else if (!node.isDirty()) {
                    rows.put(key.getData(), node.getValue().encode());
                }
            }
        } else {
            Iterator<byte[]> iterator = dataSource.keys();
            while (iterator.hasNext()) {
                byte[] key = iterator.next();
                rows.put(key, this.dataSource.get(key).get());
            }

            try {
                this.dataSource.close();
            } catch (Exception e) {
                LOG.error("Unable to close data source.", e);
            }
        }

        kvds.putBatch(rows);
        this.dataSource = kvds;
    }

    public int getSize() {
        return nodes.size();
    }

    /**
     * Returns a copy of this cache.
     *
     * <p>The copied cache and this cache will each hold a reference to the same data source, and
     * each copied {@link Node} object will retain the same reference to its {@link Value} object as
     * its original.
     *
     * @return A copy of this cache.
     */
    public Cache copy() {
        Cache cacheCopy = new Cache(this.dataSource);
        cacheCopy.isDirty = this.isDirty;
        cacheCopy.nodes = copyOfNodes();
        cacheCopy.removedNodes = copyOfRemovedNodes();
        return cacheCopy;
    }

    private Map<ByteArrayWrapper, Node> copyOfNodes() {
        if (this.nodes == null) {
            return null;
        }

        Map<ByteArrayWrapper, Node> nodesCopy = new HashMap<>();
        for (Entry<ByteArrayWrapper, Node> nodesEntry : this.nodes.entrySet()) {
            ByteArrayWrapper keyWrapper = null;

            if (nodesEntry.getKey() != null) {
                byte[] keyBytes = nodesEntry.getKey().getData();
                keyWrapper = new ByteArrayWrapper(Arrays.copyOf(keyBytes, keyBytes.length));
            }

            nodesCopy.put(
                    keyWrapper,
                    (nodesEntry.getValue() == null) ? null : nodesEntry.getValue().copy());
        }
        return nodesCopy;
    }

    private Set<ByteArrayWrapper> copyOfRemovedNodes() {
        if (this.removedNodes == null) {
            return null;
        }

        Set<ByteArrayWrapper> removedNodesCopy = new HashSet<>();
        for (ByteArrayWrapper removedNode : this.removedNodes) {
            ByteArrayWrapper removedNodeWrapper = null;

            if (removedNode != null) {
                byte[] removedNodeBytes = removedNode.toBytes();
                removedNodeWrapper =
                        new ByteArrayWrapper(
                                Arrays.copyOf(removedNodeBytes, removedNodeBytes.length));
            }

            removedNodesCopy.add(removedNodeWrapper);
        }
        return removedNodesCopy;
    }
}

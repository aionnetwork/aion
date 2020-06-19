package org.aion.zero.impl.trie;

import static org.aion.rlp.Value.fromRlpEncoded;
import static org.aion.util.types.ByteArrayWrapper.wrap;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.util.types.ByteArrayWrapper;
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

    public void markRemoved(byte[] key) {
        ByteArrayWrapper keyW = ByteArrayWrapper.wrap(key);
        removedNodes.add(keyW);
        nodes.remove(keyW);
    }

    /**
     * Put the node in the cache if RLP encoded value is longer than 32 bytes
     *
     *
     * @param key
     * @param node the Node which could be a pair-, multi-item Node or single Value
     * @return keccak hash of RLP encoded node if length &gt; 32 otherwise return node itself
     */
    void put(ByteArrayWrapper key, Node node) {
        this.nodes.put(key, node);
        this.removedNodes.remove(key);
        this.isDirty = true;
    }

    public Node get(byte[] key) {
        ByteArrayWrapper wrappedKey = wrap(key);
        Node node = nodes.get(wrappedKey);
        if (node != null) {
            // cachehits++;
            return node;
        } else if (this.dataSource != null) {
            Optional<byte[]> data = this.dataSource.get(key);
            if (data.isPresent()) {
                // dbhits++;
                node = new Node(fromRlpEncoded(data.get()), false);
                nodes.put(wrappedKey, node);
                return node;
            }
        }

        return null;
    }

    public void delete(byte[] key) {
        ByteArrayWrapper wrappedKey = wrap(key);
        this.nodes.remove(wrappedKey);

        if (dataSource != null) {
            this.dataSource.delete(key);
        }
    }

    public void commitWithoutFlush() {
            commit(false);
    }

    @VisibleForTesting
    public void commitForTest() {
        commit(true);
    }

    public void commit() {
        commit(!isDirty || nodes.size() > 20);
    }

    private void commit(boolean flushCache) {
        // Don't try to commit if it isn't dirty
        if ((dataSource == null) || !this.isDirty) {
            // clear cache when flush requested
            if (flushCache) {
                this.nodes.clear();
            }
            return;
        }

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

                byte[] key = nodeKey.toBytes();

                batch.put(key, value);
            }
        }
        for (ByteArrayWrapper removedNode : removedNodes) {
            deleteBatch.add(removedNode.toBytes());
        }

        this.dataSource.putBatch(batch);
        this.dataSource.deleteBatch(deleteBatch);
        this.isDirty = false;
        if (flushCache) {
            this.nodes.clear();
        }
        this.removedNodes.clear();
    }

    public boolean isDirty() {
        return isDirty;
    }

    public Map<ByteArrayWrapper, Node> getNodes() {
        return nodes;
    }

    public ByteArrayKeyValueStore getDb() {
        return dataSource;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void setDB(ByteArrayKeyValueStore kvds) {
        if (this.dataSource == kvds) {
            return;
        }

        Map<byte[], byte[]> rows = new HashMap<>();
        if (this.dataSource == null) {
            for (ByteArrayWrapper key : nodes.keySet()) {
                Node node = nodes.get(key);
                if (node == null) {
                    rows.put(key.toBytes(), null);
                } else if (!node.isDirty()) {
                    rows.put(key.toBytes(), node.getValue().encode());
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
}

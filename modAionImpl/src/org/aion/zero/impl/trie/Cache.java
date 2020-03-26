package org.aion.zero.impl.trie;

import static org.aion.rlp.Value.fromRlpEncoded;
import static org.aion.util.types.ByteArrayWrapper.wrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.Value;
import org.aion.util.types.ByteArrayWrapper;
import org.slf4j.Logger;

/** Cache class */
public class Cache {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private ByteArrayKeyValueStore dataSource;
    private Map<ByteArrayWrapper, Node> nodes = new LinkedHashMap<>();
    private Set<ByteArrayWrapper> removedNodes = new HashSet<>();
    private boolean isDirty;

    private ReentrantLock lock = new ReentrantLock();

    public Cache(ByteArrayKeyValueStore dataSource) {
        this.dataSource = dataSource;
    }

    public void markRemoved(byte[] key) {
        lock.lock();
        try {
            ByteArrayWrapper keyW = ByteArrayWrapper.wrap(key);
            removedNodes.add(keyW);
            nodes.remove(keyW);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Put the node in the cache if RLP encoded value is longer than 32 bytes
     *
     *
     * @param key
     * @param value the Node which could be a pair-, multi-item Node or single Value
     * @return keccak hash of RLP encoded node if length &gt; 32 otherwise return node itself
     */
    void put(ByteArrayWrapper key, Value value) {
        lock.lock();
        try {
            this.nodes.put(key, new Node(value, true));
            this.removedNodes.remove(key);
            this.isDirty = true;
        } finally {
            lock.unlock();
        }
    }

    public Value get(byte[] key) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public void delete(byte[] key) {
        lock.lock();
        try {
            ByteArrayWrapper wrappedKey = wrap(key);
            this.nodes.remove(wrappedKey);

            if (dataSource != null) {
                this.dataSource.delete(key);
            }
        } finally {
            lock.unlock();
        }
    }

    public void commit(boolean flushCache) {
        lock.lock();
        try {
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

                    byte[] key = nodeKey.toBytes();

                    batch.put(key, value);
                    // batchMemorySize += length(key, value);
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
        } finally {
            lock.unlock();
        }
    }

    public boolean isDirty() {
        lock.lock();
        try {
            return isDirty;
        } finally {
            lock.unlock();
        }
    }

    public Map<ByteArrayWrapper, Node> getNodes() {
        lock.lock();
        try {
            return nodes;
        } finally {
            lock.unlock();
        }
    }

    public ByteArrayKeyValueStore getDb() {
        lock.lock();
        try {
            return dataSource;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void setDB(ByteArrayKeyValueStore kvds) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public int getSize() {
        lock.lock();
        try {
            return nodes.size();
        } finally {
            lock.unlock();
        }
    }
}

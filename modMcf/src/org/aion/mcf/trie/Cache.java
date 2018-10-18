/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.mcf.trie;

import static org.aion.base.util.ByteArrayWrapper.wrap;
import static org.aion.rlp.Value.fromRlpEncoded;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.aion.base.db.IByteArrayKeyValueStore;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.crypto.HashUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.Value;
import org.slf4j.Logger;

/**
 * Cache class
 */
public class Cache {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private IByteArrayKeyValueStore dataSource;
    private Map<ByteArrayWrapper, Node> nodes = new LinkedHashMap<>();
    private Set<ByteArrayWrapper> removedNodes = new HashSet<>();
    private boolean isDirty;

    public Cache(IByteArrayKeyValueStore dataSource) {
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
     * @param o
     *         the Node which could be a pair-, multi-item Node or single
     *         Value
     * @return keccak hash of RLP encoded node if length &gt; 32 otherwise
     * return node itself
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
            Optional<byte[]> data = (this.dataSource == null) ? Optional.empty() : this.dataSource.get(key);
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

    public synchronized void commit() {
        commit(true);
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
            batch.put(removedNode.getData(), null);
        }

        this.dataSource.putBatch(batch);
        this.isDirty = false;
        if (flushCache) {
            this.nodes.clear();
        }
        this.removedNodes.clear();

    }

    public synchronized void undo() {
        Iterator<Map.Entry<ByteArrayWrapper, Node>> iter = this.nodes.entrySet().iterator();
        while (iter.hasNext()) {
            if (iter.next().getValue().isDirty()) {
                iter.remove();
            }
        }
        this.isDirty = false;
    }

    public synchronized boolean isDirty() {
        return isDirty;
    }

    public synchronized void setDirty(boolean isDirty) {
        this.isDirty = isDirty;
    }

    public synchronized Map<ByteArrayWrapper, Node> getNodes() {
        return nodes;
    }

    public synchronized IByteArrayKeyValueStore getDb() {
        return dataSource;
    }

    public String cacheDump() {
        StringBuffer cacheDump = new StringBuffer();
        for (ByteArrayWrapper key : nodes.keySet()) {
            Node node = nodes.get(key);
            if (node.getValue() != null) {
                cacheDump.append(key.toString()).append(" : ").append(node.getValue().toString()).append("\n");
            }
        }

        return cacheDump.toString();
    }

    public synchronized void setDB(IByteArrayKeyValueStore kvds) {
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
            for (byte[] key : this.dataSource.keys()) {
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

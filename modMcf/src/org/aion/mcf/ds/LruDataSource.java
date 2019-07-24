package org.aion.mcf.ds;

import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.util.types.ByteArrayWrapper;
import org.apache.commons.collections4.map.LRUMap;

/**
 * Adds an LRU cache of predefined size to the {@link ObjectDataSource}.
 *
 * @author Alexandra Roatis
 */
public class LruDataSource<V> extends ObjectDataSource<V> {

    protected final LRUMap<ByteArrayWrapper, V> cache;

    // only DataSource should know about this implementation
    LruDataSource(ByteArrayKeyValueDatabase src, Serializer<V, byte[]> serializer, int cacheSize) {
        super(src, serializer);
        this.cache = new LRUMap<>(cacheSize);
    }

    public void put(byte[] key, V value) {
        super.put(key, value);
        cache.put(ByteArrayWrapper.wrap(key), value);
    }

    public void putToBatch(byte[] key, V value) {
        super.putToBatch(key, value);
        cache.put(ByteArrayWrapper.wrap(key), value);
    }

    public void deleteInBatch(byte[] key) {
        super.deleteInBatch(key);
        cache.remove(ByteArrayWrapper.wrap(key));
    }

    public void delete(byte[] key) {
        super.delete(key);
        cache.remove(ByteArrayWrapper.wrap(key));
    }

    public V get(byte[] key) {
        ByteArrayWrapper wrappedKey = ByteArrayWrapper.wrap(key);
        if (cache.containsKey(wrappedKey)) {
            return cache.get(wrappedKey);
        } else {
            V val = super.get(key);
            cache.put(wrappedKey, val);
            return val;
        }
    }
}

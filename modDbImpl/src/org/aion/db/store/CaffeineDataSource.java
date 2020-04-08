package org.aion.db.store;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.concurrent.TimeUnit;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.util.types.ByteArrayWrapper;

/**
 * Adds a Window-TinyLfu cache of predefined size to the {@link ObjectDataSource} using {@link
 * Caffeine} (<a href=https://github.com/ben-manes/caffeine/wiki/Efficiency>efficiency details</a>).
 *
 * @author Alexandra Roatis
 */
final class CaffeineDataSource<V> extends ObjectDataSource<V> {

    private final LoadingCache<ByteArrayWrapper, V> cache;

    // Only DataSource should know about this implementation
    CaffeineDataSource(ByteArrayKeyValueDatabase src, Serializer<V> serializer, int cacheSize) {
        super(src, serializer);
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(cacheSize)
                        .expireAfterWrite(6, TimeUnit.MINUTES)
                        .build(key -> getFromDatabase(key.toBytes()));
    }

    @Override
    public void put(byte[] key, V value) {
        super.put(key, value);
        cache.put(ByteArrayWrapper.wrap(key), value);
    }

    @Override
    public void delete(byte[] key) {
        super.delete(key);
        cache.invalidate(ByteArrayWrapper.wrap(key));
    }

    @Override
    public V get(byte[] key) {
        // the cache automatically loads the entries it is missing as defined in the constructor
        return cache.get(ByteArrayWrapper.wrap(key));
    }
}

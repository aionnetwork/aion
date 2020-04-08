package org.aion.db.store;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.concurrent.TimeUnit;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.util.types.ByteArrayWrapper;
import org.slf4j.Logger;

/**
 * Adds a Window-TinyLfu cache of predefined size to the {@link ObjectDataSource} using {@link
 * Caffeine} (<a href=https://github.com/ben-manes/caffeine/wiki/Efficiency>efficiency
 * details</a>).
 *
 * <p>This implementation prints information on missed caching opportunities when {@link
 * Logger#isTraceEnabled()} and usage statistics at {@link DebugCaffeineDataSource#close()} when
 * {@link Logger#isInfoEnabled()}.
 *
 * @author Alexandra Roatis
 */
class DebugCaffeineDataSource<V> extends ObjectDataSource<V> {

    protected final LoadingCache<ByteArrayWrapper, V> cache;

    // for printing debug information
    private Logger log;

    // only DataSource should know about this implementation
    DebugCaffeineDataSource(ByteArrayKeyValueDatabase src, Serializer<V> serializer, int cacheSize, Logger log) {
        super(src, serializer);
        this.log = log;

        // build with recordStats
        if (this.log.isTraceEnabled()) {
            this.cache =
                    Caffeine.newBuilder()
                            .expireAfterWrite(6, TimeUnit.MINUTES)
                            .maximumSize(cacheSize)
                            .recordStats()
                            .build(
                                    key -> {
                                        // logging information on missed caching opportunities
                                        this.log.trace("[Database:" + getName() + "] Stack trace for missed cache retrieval: ", new Exception());
                                        return getFromDatabase(key.toBytes());
                                    });
        } else {
            this.cache =
                    Caffeine.newBuilder()
                            .expireAfterWrite(6, TimeUnit.MINUTES)
                            .maximumSize(cacheSize)
                            .recordStats()
                            .build(key -> getFromDatabase(key.toBytes()));
        }
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

    @Override
    public void close() {
        super.close();

        // log statistics at shutdown
        log.info("[Database:" + getName() + "] Cache utilization: " + cache.stats());
    }

    private String getName() {
        return getSrc().getName().orElse("UNKNOWN");
    }
}

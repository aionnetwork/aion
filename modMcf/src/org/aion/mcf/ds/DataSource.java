package org.aion.mcf.ds;

import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * Builder for different data source implementations.
 *
 * @author Alexandra Roatis
 */
public final class DataSource<V> {

    // Required parameters
    private final ByteArrayKeyValueDatabase src;
    private final Serializer<V, byte[]> serializer;

    // Optional parameters
    private int cacheSize;
    private Type cacheType;
    private boolean isDebug;
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CACHE.name());

    public enum Type {
        LRU,
        Window_TinyLfu
    }

    /**
     * Required data for {@link ObjectDataSource} and {@link DataSourceArray}.
     *
     * @param src the source database
     * @param serializer the serializer used to convert data to byte arrays and vice versa
     */
    public DataSource(ByteArrayKeyValueDatabase src, Serializer<V, byte[]> serializer) {
        this.src = src;
        this.serializer = serializer;
        this.cacheSize = 0;
    }

    /**
     * Adds caching to the used data source.
     *
     * @param cacheSize the size of the added cache
     * @param cacheType the type of cache to be used
     * @return a builder that will return a data source with cache when the given size is greater
     *     than zero
     */
    public DataSource<V> withCache(int cacheSize, Type cacheType) {
        // allows the case where cacheSize == 0 to facilitate enabling/disabling cache usage
        if (cacheSize < 0) {
            throw new IllegalArgumentException("Please provide a positive number as cache size.");
        }
        this.cacheSize = cacheSize;
        this.cacheType = cacheType;
        this.isDebug = false;
        return this;
    }

    /**
     * Recommended for debugging in correlation with the {@link LogEnum#CACHE} selected logging
     * level. The returned implementation prints information on missed caching opportunities when
     * {@link Logger#isTraceEnabled()} and usage statistics at data source close when {@link
     * Logger#isInfoEnabled()}.
     *
     * @return a builder that will return a data source with statistics on cache usage
     */
    public DataSource<V> withStatistics() {
        this.isDebug = true;
        return this;
    }

    public ObjectDataSource<V> buildObjectSource() {
        if (cacheSize != 0) {
            if (isDebug) {
                switch (cacheType) {
                    case LRU:
                        return new DebugLruDataSource<>(src, serializer, cacheSize, LOG);
                    case Window_TinyLfu:
                        return new DebugCaffeineDataSource<>(src, serializer, cacheSize, LOG);
                }
            } else {
                switch (cacheType) {
                    case LRU:
                        return new LruDataSource<>(src, serializer, cacheSize);
                    case Window_TinyLfu:
                        return new CaffeineDataSource<>(src, serializer, cacheSize);
                }
            }
        }

        // in case the given cache size is equal to zero
        return new ObjectDataSource<>(src, serializer);
    }

    public DataSourceArray<V> buildArraySource() {
        return new DataSourceArray<>(this.buildObjectSource());
    }
}

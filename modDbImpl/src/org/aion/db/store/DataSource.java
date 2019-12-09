package org.aion.db.store;

import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.slf4j.Logger;

/**
 * Builder for different data source implementations.
 *
 * @author Alexandra Roatis
 */
final class DataSource<V> {

    // Required parameters
    private final ByteArrayKeyValueDatabase src;
    private final Serializer<V> serializer;

    // Optional parameters
    private int cacheSize;
    private Type cacheType;
    private boolean isDebug;
    private boolean isLocked = false;
    private Logger log;

    public enum Type {
        LRU,
        Window_TinyLfu
    }

    /**
     * Required data for {@link ObjectDataSource}.
     *
     * @param src the source database
     * @param serializer the serializer used to convert data to byte arrays and vice versa
     */
    DataSource(ByteArrayKeyValueDatabase src, Serializer<V> serializer) {
        this.src = src;
        this.serializer = serializer;
        this.cacheSize = 0;
    }

    /**
     * Adds locks to the used data source to make it thread safe.
     *
     * @return a builder that will return a data source with locks when the given size is greater
     *     than zero
     */
    DataSource<V> withLocks(boolean isLocked) {
        this.isLocked = isLocked;
        return this;
    }

    /**
     * Adds caching to the used data source.
     *
     * @param cacheSize the size of the added cache
     * @param cacheType the type of cache to be used
     * @return a builder that will return a data source with cache when the given size is greater
     *     than zero
     */
    DataSource<V> withCache(int cacheSize, Type cacheType) {
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
     * Recommended for debugging in correlation with the give {@link Logger} selected logging
     * level. The returned implementation prints information on missed caching opportunities when
     * {@link Logger#isTraceEnabled()} and usage statistics at data source close when {@link
     * Logger#isInfoEnabled()}.
     *
     * @return a builder that will return a data source with statistics on cache usage
     */
    DataSource<V> withStatistics(Logger log) {
        this.isDebug = true;
        this.log = log;
        return this;
    }

    ObjectStore<V> buildObjectSource() {
        ObjectStore<V> instance = null;

        if (cacheSize != 0) {
            if (isDebug) {
                switch (cacheType) {
                    case LRU:
                        instance = new DebugLruDataSource<>(src, serializer, cacheSize, log);
                        break;
                    case Window_TinyLfu:
                        instance = new DebugCaffeineDataSource<>(src, serializer, cacheSize, log);
                        break;
                }
            } else {
                switch (cacheType) {
                    case LRU:
                        instance = new LruDataSource<>(src, serializer, cacheSize);
                        break;
                    case Window_TinyLfu:
                        instance = new CaffeineDataSource<>(src, serializer, cacheSize);
                        break;
                }
            }
        }

        if (instance == null) {
            // in case the given cache size is equal to zero
            instance = new ObjectDataSource<>(src, serializer);
        }

        if (isLocked) {
            instance = new LockedObjectStore<>(instance);
        }

        return instance;
    }
}

package org.aion.db.store;

import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.store.DataSource.Type;
import org.slf4j.Logger;

/**
 * Static factory methods for a variety of data stores.
 *
 * @author Alexandra Roatis
 */
public final class Stores {

    private Stores() {
        throw new IllegalStateException("This class is used only for static factory methods and should not be instantiated.");
    }

    /**
     * Creates a key value store that interacts with objects that are serialized to byte arrays and
     * deserialized back into objects using a specified {@link Serializer} implementation. The
     * stored data is interpreted as an array allowing access according to the defined {@code long}
     * index values. The elements of the array are indexed from 0 to highest stored index value.
     */
    public static <V> ArrayStore<V> newArrayStore(ByteArrayKeyValueDatabase database, Serializer<V> serializer) {
        return new DataSourceArray<>(database, serializer);
    }

    public static <V> ObjectStore<V> newObjectStore(ByteArrayKeyValueDatabase database, Serializer<V> serializer) {
        return new DataSource<>(database, serializer).buildObjectSource();
    }

    public static <V> ObjectStore<V> newObjectStoreWithCache(ByteArrayKeyValueDatabase database, Serializer<V> serializer, int size) {
        DataSource<V> ds;
        if (size >= 200) {
            // Caffeine should perform better for this size
            ds = new DataSource<>(database, serializer).withCache(size, Type.Window_TinyLfu);
        } else {
            // small caches default to LRU
            ds = new DataSource<>(database, serializer).withCache(size, Type.LRU);
        }
        return ds.buildObjectSource();
    }

    public static <V> ObjectStore<V> newObjectStoreWithDebugCache(ByteArrayKeyValueDatabase database,Serializer<V> serializer, int size, Logger log) {
        DataSource<V> ds;
        if (size >= 200) {
            // Caffeine should perform better for this size
            ds = new DataSource<>(database, serializer).withCache(size, Type.Window_TinyLfu);
        } else {
            // small caches default to LRU
            ds = new DataSource<>(database, serializer).withCache(size, Type.LRU);
        }
        // enable debug logging
        ds.withStatistics(log);
        return ds.buildObjectSource();
    }
}

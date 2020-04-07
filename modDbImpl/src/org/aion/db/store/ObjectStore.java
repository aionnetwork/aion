package org.aion.db.store;

import java.io.Closeable;

/**
 * A key value store that interacts with objects that are serialized to byte arrays and deserialized
 * back into objects using a specified {@link Serializer} implementation.
 *
 * @param <V> the class of objects used by a specific implementation
 */
public interface ObjectStore<V> extends Closeable {
    // TODO AKI-352: convert to using ByteArrayWrapper

    /** Adds a key-value entry to the database. */
    void put(byte[] key, V value);

    /** Deletes the object stored at the given key. */
    void delete(byte[] key);

    /** Adds a key-value entry to the database as a batch operation. */
    void putToBatch(byte[] key, V value); // TODO AKI-353: merge with put

    /** Deletes the object stored at the given key as a batch operation. */
    void deleteInBatch(byte[] key); // TODO AKI-353: merge with delete

    /** Pushes batch changes to the underlying database. */
    void flushBatch(); // TODO AKI-353: merge with commit

    /** Retrieves the object stored at the given key. */
    V get(byte[] key);

    /** Returns {@code true} to indicate that the database is open, {@code false} otherwise. */
    boolean isOpen();
}

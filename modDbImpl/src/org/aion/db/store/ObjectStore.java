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

    /**
     * Adds a key-value entry to the database as part of a batch operation.
     *
     * @apiNote Requires {@link #flushBatch()} to push the changes to the underlying database.
     */
    void putToBatch(byte[] key, V value);

    /**
     * Deletes the object stored at the given key as part of a batch operation.
     *
     * @apiNote Requires {@link #flushBatch()} to push the changes to the underlying database.
     */
    void deleteInBatch(byte[] key);

    /** Pushes the current batch changes to the underlying database. */
    void flushBatch();

    /**
     * Retrieves the object stored at the given key.
     *
     * @apiNote Values that have been added with {@link #putToBatch(byte[], Object)} or deleted with {@link #deleteInBatch(byte[])} are not guaranteed to be retrieved until {@link #flushBatch()} is called.
     */
    V get(byte[] key);

    /** Returns {@code true} to indicate that the database is open, {@code false} otherwise. */
    boolean isOpen();
}

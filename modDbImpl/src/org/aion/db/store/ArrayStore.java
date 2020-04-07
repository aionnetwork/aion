package org.aion.db.store;

import java.io.Closeable;

/**
 * A key value store that interacts with objects that are serialized to byte arrays and deserialized
 * back into objects using a specified {@link Serializer} implementation. The stored data is
 * interpreted as an array allowing access according to the defined {@code long} index values. The
 * elements of the array are indexed from 0 to highest stored index value.
 *
 * @param <V> the class of objects used by a specific implementation
 * @author Alexandra Roatis
 */
public interface ArrayStore<V> extends Closeable {

    /** Inserts an object at the specified index. */
    void set(long index, V value);

    /** Removes the object at the given index. */
    void remove(long index);

    /** Retrieves the object stored at the given index. */
    V get(long index);

    /** Retrieves the size of the array defined as the highest stored entry plus one. */
    long size();

    /** Returns {@code true} to indicate that the database is open, {@code false} otherwise. */
    boolean isOpen();
}

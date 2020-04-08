package org.aion.db.store;

import java.util.Optional;
import org.aion.db.impl.ByteArrayKeyValueDatabase;

/**
 * Object Datasource.
 *
 * @param <V>
 */
class ObjectDataSource<V> implements ObjectStore<V> {

    private ByteArrayKeyValueDatabase src;
    private Serializer<V> serializer;

    public ObjectDataSource(ByteArrayKeyValueDatabase src, Serializer<V> serializer) {
        this.src = src;
        this.serializer = serializer;
    }

    /** @apiNote Will throw an exception if the given value is {@code null}. */
    @Override
    public void put(byte[] key, V value) {
        src.putToBatch(key, serializer.serialize(value));
    }

    @Override
    public void delete(byte[] key) {
        src.deleteInBatch(key);
    }

    @Override
    public void commit() {
        src.commitBatch();
    }

    @Override
    public V get(byte[] key) {
        return getFromDatabase(key);
    }

    // used by inheriting classes when automatically loading entries from the database
    protected V getFromDatabase(byte[] key) {
        // Fetch the results from cache or database. Return null if doesn't exist.
        Optional<byte[]> val = src.get(key);
        return val.map(serializer::deserialize).orElse(null);
    }

    /** Returns the underlying cache source. */
    protected ByteArrayKeyValueDatabase getSrc() {
        return src;
    }

    /**
     * Checks that the underlying storage was correctly initialized and open.
     *
     * @return true if correctly initialized and the data storage is open, false otherwise.
     */
    @Override
    public boolean isOpen() {
        return src.isOpen();
    }

    @Override
    public void close() {
        src.close();
    }
}

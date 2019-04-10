package org.aion.mcf.ds;

import java.io.Closeable;
import java.util.Optional;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.aion.interfaces.db.Flushable;

/**
 * Object Datasource.
 *
 * @param <V>
 */
public class ObjectDataSource<V> implements Flushable, Closeable {

    private ByteArrayKeyValueDatabase src;
    private Serializer<V, byte[]> serializer;

    public ObjectDataSource(ByteArrayKeyValueDatabase src, Serializer<V, byte[]> serializer) {
        this.src = src;
        this.serializer = serializer;
    }

    public void flush() {
        // for write-back type cache only
        if (!this.src.isAutoCommitEnabled()) {
            this.src.commit();
        }
    }

    public void put(byte[] key, V value) {
        byte[] bytes = serializer.serialize(value);
        /*
         * src.put(key, bytes); if (cacheOnWrite) { cache.put(new
         * ByteArrayWrapper(key), value); }
         */
        // TODO @yao - Don't know if just writing to cache is correct logic
        // or what this was intended to be. Why do a flush then?
        src.put(key, bytes);
    }

    /** @apiNote Will throw an exception if the given value is {@code null}. */
    public void putToBatch(byte[] key, V value) {
        src.putToBatch(key, serializer.serialize(value));
    }

    public void deleteInBatch(byte[] key) {
        src.deleteInBatch(key);
    }

    public void flushBatch() {
        src.commitBatch();
    }

    public void delete(byte[] key) {
        src.delete(key);
    }

    public V get(byte[] key) {
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
    public boolean isOpen() {
        return src.isOpen();
    }

    @Override
    public void close() {
        src.close();
    }
}

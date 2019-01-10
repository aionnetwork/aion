package org.aion.mcf.ds;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.IByteArrayKeyValueStore;

/**
 * A data source with archived data that must no be deleted.
 *
 * @author Alexandra Roatis
 */
public class ArchivedDataSource implements IByteArrayKeyValueStore {

    IByteArrayKeyValueDatabase data, archive;

    public ArchivedDataSource(IByteArrayKeyValueDatabase _db, IByteArrayKeyValueDatabase _archive) {
        this.data = _db;
        this.archive = _archive;
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public Iterator<byte[]> keys() {
        return data.keys();
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        return data.get(key);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        if (value != null) {
            data.put(key, value);
        } else {
            // internal delete will check if archived
            delete(key);
        }
    }

    @Override
    public void delete(byte[] key) {
        // delete key only if not archived
        if (!archive.get(key).isPresent()) {
            data.delete(key);
        }
    }

    @Override
    public void putBatch(Map<byte[], byte[]> batch) {
        for (Map.Entry<byte[], byte[]> entry : batch.entrySet()) {
            // will check if archived
            putToBatch(entry.getKey(), entry.getValue());
        }
        commitBatch();
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        if (value != null) {
            data.putToBatch(key, value);
        } else {
            // deleted key only if not archived
            if (!archive.get(key).isPresent()) {
                data.putToBatch(key, null);
            }
        }
    }

    @Override
    public void commitBatch() {
        data.commitBatch();
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        for (byte[] key : keys) {
            // will check if archived
            putToBatch(key, null);
        }
        commitBatch();
    }

    @Override
    public void check() {
        data.check();
        archive.check();
    }

    @Override
    public void close() {
        data.close();
        archive.close();
    }

    public IByteArrayKeyValueDatabase getArchiveDatabase() {
        return archive;
    }
}

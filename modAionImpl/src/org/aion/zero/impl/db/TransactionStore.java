package org.aion.zero.impl.db;

import static org.aion.util.others.Utils.dummy;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.db.Flushable;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.store.ObjectStore;
import org.aion.db.store.Serializer;
import org.aion.db.store.Stores;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.AionTxInfo;
import org.apache.commons.collections4.map.LRUMap;

public class TransactionStore implements Flushable, Closeable {
    private final LRUMap<ByteArrayWrapper, Object> lastSavedTxHash = new LRUMap<>(5000);
    private final ObjectStore<List<AionTxInfo>> source;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public TransactionStore(
            ByteArrayKeyValueDatabase src, Serializer<List<AionTxInfo>> serializer) {
        source = Stores.newObjectStore(src, serializer);
    }

    public boolean putToBatch(AionTxInfo tx) {
        lock.writeLock().lock();

        try {
            byte[] txHash = tx.getReceipt().getTransaction().getTransactionHash();

            List<AionTxInfo> existingInfos = null;
            if (lastSavedTxHash.put(new ByteArrayWrapper(txHash), dummy) != null
                    || !lastSavedTxHash.isFull()) {
                existingInfos = source.get(txHash);
            }

            if (existingInfos == null) {
                existingInfos = new ArrayList<>();
            } else {
                for (AionTxInfo info : existingInfos) {
                    if (Arrays.equals(info.getBlockHash(), tx.getBlockHash())) {
                        return false;
                    }
                }
            }
            existingInfos.add(tx);
            source.putToBatch(txHash, existingInfos);

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void flushBatch() {
        source.flushBatch();
    }

    public AionTxInfo get(byte[] txHash, byte[] blockHash) {
        lock.readLock().lock();

        try {
            List<AionTxInfo> existingInfos = source.get(txHash);
            for (AionTxInfo info : existingInfos) {
                if (Arrays.equals(info.getBlockHash(), blockHash)) {
                    return info;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<AionTxInfo> get(byte[] key) {
        lock.readLock().lock();
        try {
            return source.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void flush() {
        lock.writeLock().lock();
        try {
            source.commit();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            source.close();
        } finally {
            lock.writeLock().unlock();
        }
    }
}

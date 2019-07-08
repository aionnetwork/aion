package org.aion.mcf.db;

import static org.aion.util.others.Utils.dummy;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.ds.ObjectDataSource;
import org.aion.mcf.ds.Serializer;
import org.aion.base.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;
import org.aion.util.types.ByteArrayWrapper;
import org.apache.commons.collections4.map.LRUMap;

public class TransactionStore<
                TX extends AbstractTransaction,
                TXR extends AbstractTxReceipt<TX>,
                INFO extends AbstractTxInfo<TXR, TX>>
        implements Flushable, Closeable {
    private final LRUMap<ByteArrayWrapper, Object> lastSavedTxHash = new LRUMap<>(5000);
    private final ObjectDataSource<List<INFO>> source;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public TransactionStore(
            ByteArrayKeyValueDatabase src, Serializer<List<INFO>, byte[]> serializer) {
        source = new ObjectDataSource(src, serializer);
    }

    public boolean putToBatch(INFO tx) {
        lock.writeLock().lock();

        try {
            byte[] txHash = tx.getReceipt().getTransaction().getTransactionHash();

            List<INFO> existingInfos = null;
            if (lastSavedTxHash.put(new ByteArrayWrapper(txHash), dummy) != null
                    || !lastSavedTxHash.isFull()) {
                existingInfos = source.get(txHash);
            }

            if (existingInfos == null) {
                existingInfos = new ArrayList<>();
            } else {
                for (AbstractTxInfo<TXR, TX> info : existingInfos) {
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

    public INFO get(byte[] txHash, byte[] blockHash) {
        lock.readLock().lock();

        try {
            List<INFO> existingInfos = source.get(txHash);
            for (INFO info : existingInfos) {
                if (Arrays.equals(info.getBlockHash(), blockHash)) {
                    return info;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<INFO> get(byte[] key) {
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
            source.flush();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            source.close();
        } finally {
            lock.writeLock().unlock();
        }
    }
}

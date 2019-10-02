package org.aion.zero.impl.db;

import static org.aion.util.others.Utils.dummy;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.store.ObjectStore;
import org.aion.db.store.Serializer;
import org.aion.db.store.Stores;
import org.aion.types.InternalTransaction;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.AionTxInfo;
import org.apache.commons.collections4.map.LRUMap;

public class TransactionStore implements Closeable {
    private final LRUMap<ByteArrayWrapper, Object> lastSavedTxHash = new LRUMap<>(5000);
    private final ObjectStore<List<AionTxInfo>> txInfoSource;
    private final ObjectStore<Set<ByteArrayWrapper>> aliasSource;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public TransactionStore(
        ByteArrayKeyValueDatabase txInfoSrc, Serializer<List<AionTxInfo>> serializer) {
        txInfoSource = Stores.newObjectStore(txInfoSrc, serializer);
        aliasSource = Stores.newObjectStore(txInfoSrc, aliasSerializer);
    }

    public boolean putTxInfoToBatch(AionTxInfo tx) {

        lock.writeLock().lock();

        try {
            byte[] txHash = tx.getReceipt().getTransaction().getTransactionHash();

            List<AionTxInfo> existingInfos = null;
            if (lastSavedTxHash.put(new ByteArrayWrapper(txHash), dummy) != null
                    || !lastSavedTxHash.isFull()) {
                existingInfos = txInfoSource.get(txHash);
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
            txInfoSource.putToBatch(txHash, existingInfos);

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void putAliasesToBatch(AionTxInfo txInfo) {
        byte[] txHash = txInfo.getReceipt().getTransaction().getTransactionHash();

        lock.writeLock().lock();

        try {
            for (InternalTransaction itx : txInfo.getInternalTransactions()) {
                byte[] invokableHash = itx.copyOfInvokableHash();

                if (invokableHash != null) {
                    Set<ByteArrayWrapper> existingAliases = aliasSource.get(invokableHash);

                    if (existingAliases == null) {
                        existingAliases = new HashSet<>();
                    }
                    existingAliases.add(new ByteArrayWrapper(txHash));
                    aliasSource.putToBatch(invokableHash, existingAliases);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void flushBatch() {
        txInfoSource.flushBatch();
        aliasSource.flushBatch();
    }

    public AionTxInfo getTxInfo(byte[] txHash, byte[] blockHash) {
        lock.readLock().lock();

        try {
            List<AionTxInfo> existingInfos = txInfoSource.get(txHash);
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

    public List<AionTxInfo> getTxInfo(byte[] key) {
        lock.readLock().lock();
        try {
            return txInfoSource.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void commit() {
        lock.writeLock().lock();
        try {
            txInfoSource.commit();
            aliasSource.commit();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            txInfoSource.close();
            aliasSource.close();
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public Set<ByteArrayWrapper> getAliases(byte[] innerHash) {
        lock.readLock().lock();

        try {
            return aliasSource.get(innerHash);
        } finally {
            lock.readLock().unlock();
        }
    }

    private static final Serializer<Set<ByteArrayWrapper>> aliasSerializer =
        new Serializer<>() {
            @Override
            public byte[] serialize(Set<ByteArrayWrapper> outerHashes) {

                byte[] outerHashesEncoded = new byte[outerHashes.size() * 32];
                int i = 0;
                for (ByteArrayWrapper hash : outerHashes) {
                    System.arraycopy(hash.toBytes(), 0, outerHashesEncoded, i * 32, 32);
                    i++;
                }
                return outerHashesEncoded;
            }

            @Override
            public Set<ByteArrayWrapper> deserialize(byte[] stream) {
                try {
                    Set<ByteArrayWrapper> ret = new HashSet<>();
                    for (int pos = 0; pos < stream.length; pos += 32) {
                        byte[] holder = new byte[32];
                        System.arraycopy(stream, pos, holder, 0, 32);
                        ret.add(new ByteArrayWrapper(holder));
                    }
                    return ret;
                } catch (Exception e) {
                    return null;
                }
            }
        };
}

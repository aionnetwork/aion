package org.aion.zero.impl.db;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

public class TransactionStore implements Closeable {
    private final ObjectStore<Map<ByteArrayWrapper, AionTxInfo>> txInfoSource;
    private final ObjectStore<Set<ByteArrayWrapper>> aliasSource;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public TransactionStore(ByteArrayKeyValueDatabase txInfoSrc, Serializer<Map<ByteArrayWrapper, AionTxInfo>> serializer) {
        // TODO AKI-436: introduce caching of recent transactions
        txInfoSource = Stores.newObjectStore(txInfoSrc, serializer);
        aliasSource = Stores.newObjectStore(txInfoSrc, aliasSerializer);
    }

    public void putTxInfoToBatch(AionTxInfo tx) {

        lock.writeLock().lock();

        try {
            byte[] txHash = tx.getReceipt().getTransaction().getTransactionHash();

            Map<ByteArrayWrapper, AionTxInfo> existingInfos = txInfoSource.get(txHash);
            if (existingInfos == null) {
                existingInfos = new HashMap<>();
            }

            // overwrites existing entry to update it with/without internal transactions
            // depending on the chosen configuration at block import
            existingInfos.put(tx.blockHash, tx);
            txInfoSource.putToBatch(txHash, existingInfos);
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
                    existingAliases.add(ByteArrayWrapper.wrap(txHash));
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
        // ensuring non-null input since this can be called with input from the API
        if (txHash == null || blockHash == null) return null;

        lock.readLock().lock();

        try {
            return txInfoSource.get(txHash).get(ByteArrayWrapper.wrap(blockHash));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<ByteArrayWrapper, AionTxInfo> getTxInfo(byte[] key) {
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
                        ret.add(ByteArrayWrapper.wrap(holder));
                    }
                    return ret;
                } catch (Exception e) {
                    return null;
                }
            }
        };
}

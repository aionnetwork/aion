/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors :
 *     Aion foundation.
 ******************************************************************************/
package org.aion.mcf.db;

import org.aion.base.db.Flushable;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.FastByteComparisons;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.ds.ObjectDataSource;
import org.aion.mcf.ds.Serializer;
import org.aion.mcf.types.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;
import org.apache.commons.collections4.map.LRUMap;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.aion.base.util.Utils.dummy;

public class TransactionStore<TX extends AbstractTransaction, TXR extends AbstractTxReceipt<TX>, INFO extends AbstractTxInfo<TXR, TX>>
        implements Flushable, Closeable {
    private final LRUMap<ByteArrayWrapper, Object> lastSavedTxHash = new LRUMap<>(5000);
    private final ObjectDataSource<List<INFO>> source;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public TransactionStore(IByteArrayKeyValueDatabase src, Serializer<List<INFO>, byte[]> serializer) {
        source = new ObjectDataSource(src, serializer);
    }

    public boolean put(INFO tx) {
        lock.writeLock().lock();

        try {
            byte[] txHash = tx.getReceipt().getTransaction().getHash();

            List<INFO> existingInfos = null;
            if (lastSavedTxHash.put(new ByteArrayWrapper(txHash), dummy) != null || !lastSavedTxHash.isFull()) {
                existingInfos = source.get(txHash);
            }

            if (existingInfos == null) {
                existingInfos = new ArrayList<>();
            } else {
                for (AbstractTxInfo<TXR, TX> info : existingInfos) {
                    if (FastByteComparisons.equal(info.getBlockHash(), tx.getBlockHash())) {
                        return false;
                    }
                }
            }
            existingInfos.add(tx);
            source.put(txHash, existingInfos);

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public INFO get(byte[] txHash, byte[] blockHash) {
        lock.readLock().lock();

        try {
            List<INFO> existingInfos = source.get(txHash);
            for (INFO info : existingInfos) {
                if (FastByteComparisons.equal(info.getBlockHash(), blockHash)) {
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

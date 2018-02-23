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
 *
 ******************************************************************************/
package org.aion.mcf.db;

import java.util.ArrayList;
import java.util.List;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.FastByteComparisons;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.ds.ObjectDataSource;
import org.aion.mcf.ds.Serializer;
import org.aion.mcf.types.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;
import org.apache.commons.collections4.map.LRUMap;

public class TransactionStore<TX extends AbstractTransaction, TXR extends AbstractTxReceipt<TX>, INFO extends AbstractTxInfo<TXR, TX>>
        extends ObjectDataSource<List<INFO>> {
    private final LRUMap<ByteArrayWrapper, Object> lastSavedTxHash = new LRUMap<>(5000);
    private final Object object = new Object();

    public boolean put(INFO tx) {
        byte[] txHash = tx.getReceipt().getTransaction().getHash();

        List<INFO> existingInfos = null;
        synchronized (lastSavedTxHash) {
            if (lastSavedTxHash.put(new ByteArrayWrapper(txHash), object) != null || !lastSavedTxHash.isFull()) {
                existingInfos = get(txHash);
            }
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
        put(txHash, existingInfos);

        return true;
    }

    public INFO get(byte[] txHash, byte[] blockHash) {
        List<INFO> existingInfos = get(txHash);
        for (INFO info : existingInfos) {
            if (FastByteComparisons.equal(info.getBlockHash(), blockHash)) {
                return info;
            }
        }
        return null;
    }

    public TransactionStore(IByteArrayKeyValueDatabase src, Serializer<List<INFO>, byte[]> serializer) {
        super(src, serializer);
        // withCacheSize(256);
        withCacheOnWrite(true);
    }

    @Override
    public void flush() {
        if (!getSrc().isAutoCommitEnabled()) {
            getSrc().commit();
        }
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (Exception e) {
            throw e;
        }
    }
}

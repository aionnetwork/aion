/*******************************************************************************
 *
 * Copyright (c) 2017, 2018 Aion foundation.
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 *******************************************************************************/
package org.aion.mcf.trie;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.IByteArrayKeyValueStore;
import org.aion.base.type.IBlock;
import org.aion.base.type.IBlockHeader;
import org.aion.base.util.ByteArrayWrapper;

import java.util.*;

/**
 * The DataSource which doesn't immediately forward delete updates (unlike
 * inserts) but collects them tied to the block where these changes were made
 * (the changes are mapped to a block upon [storeBlockChanges] call). When the
 * [prune] is called for a block the deletes for this block are submitted to the
 * underlying DataSource with respect to following inserts. E.g. if the key was
 * deleted at block N and then inserted at block N + 10 this delete is not
 * passed.
 */
public class JournalPruneDataSource<BLK extends IBlock<?, ?>, BH extends IBlockHeader>
        implements IByteArrayKeyValueStore {

    private class Updates {

        BH blockHeader;
        Set<ByteArrayWrapper> insertedKeys = new HashSet<>();
        Set<ByteArrayWrapper> deletedKeys = new HashSet<>();
    }

    private static class Ref {

        boolean dbRef;
        int journalRefs;

        public Ref(boolean dbRef) {
            this.dbRef = dbRef;
        }

        public int getTotRefs() {
            return journalRefs + (dbRef ? 1 : 0);
        }
    }

    Map<ByteArrayWrapper, Ref> refCount = new HashMap<>();

    private IByteArrayKeyValueDatabase src;
    // block hash => updates
    private LinkedHashMap<ByteArrayWrapper, Updates> blockUpdates = new LinkedHashMap<>();
    private Updates currentUpdates = new Updates();
    private boolean enabled = true;

    public JournalPruneDataSource(IByteArrayKeyValueDatabase src) {
        this.src = src;
    }

    public void setPruneEnabled(boolean e) {
        enabled = e;
    }

    /**
     * ***** updates ******
     */
    public synchronized void put(byte[] key, byte[] value) {
        ByteArrayWrapper keyW = new ByteArrayWrapper(key);

        // Check to see the value exists.
        if (value != null) {

            // If it exists and pruning is enabled.
            if (enabled) {
                currentUpdates.insertedKeys.add(keyW);
                incRef(keyW);
            }

            // Insert into the database.
            src.put(key, value);

        } else {
            // Value does not exist, so we delete from current updates
            if (enabled) {
                currentUpdates.deletedKeys.add(keyW);
            }
            // TODO: Do we delete the key?
        }
    }

    public synchronized void delete(byte[] key) {
        if (!enabled) { return; }
        currentUpdates.deletedKeys.add(new ByteArrayWrapper(key));
        // delete is delayed
    }

    public synchronized void updateBatch(Map<byte[], byte[]> rows) {
        Map<byte[], byte[]> insertsOnly = new HashMap<>();
        for (Map.Entry<byte[], byte[]> entry : rows.entrySet()) {
            ByteArrayWrapper keyW = new ByteArrayWrapper(entry.getKey());
            if (entry.getValue() != null) {
                if (enabled) {
                    currentUpdates.insertedKeys.add(keyW);
                    incRef(keyW);
                }
                insertsOnly.put(entry.getKey(), entry.getValue());
            } else {
                if (enabled) {
                    currentUpdates.deletedKeys.add(keyW);
                }
            }
        }
        src.putBatch(insertsOnly);
    }

    public synchronized void updateBatch(Map<ByteArrayWrapper, byte[]> rows, boolean erasure) {
        throw new UnsupportedOperationException();
    }

    private void incRef(ByteArrayWrapper keyW) {
        Ref cnt = refCount.get(keyW);
        if (cnt == null) {
            cnt = new Ref(src.get(keyW.getData()) != null);
            refCount.put(keyW, cnt);
        }
        cnt.journalRefs++;
    }

    private Ref decRef(ByteArrayWrapper keyW) {
        Ref cnt = refCount.get(keyW);
        cnt.journalRefs -= 1;
        if (cnt.journalRefs == 0) {
            refCount.remove(keyW);
        }
        return cnt;
    }

    public synchronized void storeBlockChanges(BH header) {
        if (!enabled) { return; }
        currentUpdates.blockHeader = header;
        blockUpdates.put(new ByteArrayWrapper(header.getHash()), currentUpdates);
        currentUpdates = new Updates();
    }

    public synchronized void prune(BH header) {
        if (!enabled) { return; }
        ByteArrayWrapper blockHashW = new ByteArrayWrapper(header.getHash());
        Updates updates = blockUpdates.remove(blockHashW);
        if (updates != null) {
            for (ByteArrayWrapper insertedKey : updates.insertedKeys) {
                decRef(insertedKey).dbRef = true;
            }

            Map<byte[], byte[]> batchRemove = new HashMap<>();
            for (ByteArrayWrapper key : updates.deletedKeys) {
                Ref ref = refCount.get(key);
                if (ref == null || ref.journalRefs == 0) {
                    batchRemove.put(key.getData(), null);
                } else if (ref != null) {
                    ref.dbRef = false;
                }
            }
            src.putBatch(batchRemove);

            rollbackForkBlocks(header.getNumber());
        }
    }

    private void rollbackForkBlocks(long blockNum) {
        for (Updates updates : new ArrayList<>(blockUpdates.values())) {
            if (updates.blockHeader.getNumber() == blockNum) {
                rollback(updates.blockHeader);
            }
        }
    }

    private synchronized void rollback(BH header) {
        ByteArrayWrapper blockHashW = new ByteArrayWrapper(header.getHash());
        Updates updates = blockUpdates.remove(blockHashW);
        Map<byte[], byte[]> batchRemove = new HashMap<>();
        for (ByteArrayWrapper insertedKey : updates.insertedKeys) {
            Ref ref = decRef(insertedKey);
            if (ref.getTotRefs() == 0) {
                batchRemove.put(insertedKey.getData(), null);
            }
        }
        src.putBatch(batchRemove);
    }

    public Map<ByteArrayWrapper, Ref> getRefCount() {
        return refCount;
    }

    public LinkedHashMap<ByteArrayWrapper, Updates> getBlockUpdates() {
        return blockUpdates;
    }

    /**
     * *** other ****
     */
    public Optional<byte[]> get(byte[] key) {
        return src.get(key);
    }

    public Set<byte[]> keys() {
        return src.keys();
    }

    @Override
    public void close() {
        src.close();
    }

    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        updateBatch(inputMap);
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commitBatch() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    public IByteArrayKeyValueDatabase getSrc() {
        return src;
    }
}

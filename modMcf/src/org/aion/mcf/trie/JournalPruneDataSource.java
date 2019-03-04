package org.aion.mcf.trie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.aion.interfaces.db.ByteArrayKeyValueStore;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.ds.ArchivedDataSource;
import org.aion.types.ByteArrayWrapper;
import org.slf4j.Logger;

/**
 * The DataSource which doesn't immediately forward delete updates (unlike inserts) but collects
 * them tied to the block where these changes were made (the changes are mapped to a block upon
 * [storeBlockChanges] call). When the [prune] is called for a block the deletes for this block are
 * submitted to the underlying DataSource with respect to following inserts. E.g. if the key was
 * deleted at block N and then inserted at block N + 10 this delete is not passed.
 */
public class JournalPruneDataSource implements ByteArrayKeyValueStore {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private class Updates {
        ByteArrayWrapper blockHeader;
        long blockNumber;
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

        @Override
        public String toString() {
            return "refs: " + String.valueOf(journalRefs) + " db: " + String.valueOf(dbRef);
        }
    }

    Map<ByteArrayWrapper, Ref> refCount = new HashMap<>();

    private ByteArrayKeyValueStore src;
    // block hash => updates
    private LinkedHashMap<ByteArrayWrapper, Updates> blockUpdates = new LinkedHashMap<>();
    private Updates currentUpdates = new Updates();
    private AtomicBoolean enabled = new AtomicBoolean(false);
    private final boolean hasArchive;

    public JournalPruneDataSource(ByteArrayKeyValueStore src) {
        this.src = src;
        this.hasArchive = src instanceof ArchivedDataSource;
    }

    public void setPruneEnabled(boolean _enabled) {
        enabled.set(_enabled);
    }

    public boolean isArchiveEnabled() {
        return hasArchive;
    }

    public void put(byte[] key, byte[] value) {
        checkNotNull(key);
        checkNotNull(value);

        lock.writeLock().lock();

        try {
            if (enabled.get()) {
                // pruning enabled
                ByteArrayWrapper keyW = ByteArrayWrapper.wrap(key);

                // Check to see the value exists.
                if (value != null) {
                    // If it exists and pruning is enabled.
                    currentUpdates.insertedKeys.add(keyW);
                    incRef(keyW);

                    // put to source database.
                    src.put(key, value);

                } else {
                    check();

                    // Value does not exist, so we delete from current updates
                    currentUpdates.deletedKeys.add(keyW);
                }
            } else {
                // pruning disabled
                if (value != null) {
                    src.put(key, value);
                } else {
                    check();
                }
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not put key-value pair due to ", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete(byte[] key) {
        checkNotNull(key);
        if (!enabled.get()) {
            check();
            return;
        }

        lock.writeLock().lock();

        try {
            check();

            currentUpdates.deletedKeys.add(ByteArrayWrapper.wrap(key));
            // delete is delayed
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not delete key due to ", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        checkNotNull(inputMap.keySet());
        checkNotNull(inputMap.values());

        lock.writeLock().lock();

        try {
            Map<byte[], byte[]> insertsOnly = new HashMap<>();
            if (enabled.get()) {
                for (Map.Entry<byte[], byte[]> entry : inputMap.entrySet()) {
                    ByteArrayWrapper keyW = ByteArrayWrapper.wrap(entry.getKey());
                    if (entry.getValue() != null) {
                        currentUpdates.insertedKeys.add(keyW);
                        incRef(keyW);
                        insertsOnly.put(entry.getKey(), entry.getValue());
                    } else {
                        currentUpdates.deletedKeys.add(keyW);
                    }
                }
            } else {
                for (Map.Entry<byte[], byte[]> entry : inputMap.entrySet()) {
                    if (entry.getValue() != null) {
                        insertsOnly.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            src.putBatch(insertsOnly);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not put batch due to ", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void incRef(ByteArrayWrapper keyW) {
        Ref cnt = refCount.get(keyW);
        if (cnt == null) {
            cnt = new Ref(src.get(keyW.getData()).isPresent());
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

    public void storeBlockChanges(byte[] blockHash, long blockNumber) {
        if (!enabled.get()) {
            return;
        }

        lock.writeLock().lock();

        try {
            ByteArrayWrapper hash = ByteArrayWrapper.wrap(blockHash);
            currentUpdates.blockHeader = hash;
            currentUpdates.blockNumber = blockNumber;
            blockUpdates.put(hash, currentUpdates);
            currentUpdates = new Updates();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void prune(byte[] blockHash, long blockNumber) {
        if (!enabled.get()) {
            return;
        }

        lock.writeLock().lock();

        try {
            ByteArrayWrapper blockHashW = ByteArrayWrapper.wrap(blockHash);
            Updates updates = blockUpdates.remove(blockHashW);
            if (updates != null) {
                for (ByteArrayWrapper insertedKey : updates.insertedKeys) {
                    decRef(insertedKey).dbRef = true;
                }

                List<byte[]> batchRemove = new ArrayList<>();
                for (ByteArrayWrapper key : updates.deletedKeys) {
                    Ref ref = refCount.get(key);
                    if (ref == null || ref.journalRefs == 0) {
                        batchRemove.add(key.getData());
                    } else if (ref != null) {
                        ref.dbRef = false;
                    }
                }
                src.deleteBatch(batchRemove);

                rollbackForkBlocks(blockNumber);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void rollbackForkBlocks(long blockNum) {
        for (Updates updates : new ArrayList<>(blockUpdates.values())) {
            if (updates.blockNumber == blockNum) {
                rollback(updates.blockHeader);
            }
        }
    }

    private void rollback(ByteArrayWrapper blockHashW) {
        Updates updates = blockUpdates.remove(blockHashW);
        List<byte[]> batchRemove = new ArrayList<>();
        for (ByteArrayWrapper insertedKey : updates.insertedKeys) {
            Ref ref = decRef(insertedKey);
            if (ref.getTotRefs() == 0) {
                batchRemove.add(insertedKey.getData());
            }
        }
        src.deleteBatch(batchRemove);
    }

    public Map<ByteArrayWrapper, Ref> getRefCount() {
        return refCount;
    }

    public LinkedHashMap<ByteArrayWrapper, Updates> getBlockUpdates() {
        return blockUpdates;
    }

    public int getDeletedKeysCount() {
        lock.readLock().lock();
        try {
            return currentUpdates.deletedKeys.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getInsertedKeysCount() {
        lock.readLock().lock();
        try {
            return currentUpdates.insertedKeys.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<byte[]> get(byte[] key) {
        lock.readLock().lock();
        try {
            return src.get(key);
        } catch (Exception e) {
            LOG.error("Could not get key due to ", e);
            throw e;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Iterator<byte[]> keys() {
        lock.readLock().lock();
        try {
            return src.keys();
        } catch (Exception e) {
            LOG.error("Could not get keys due to ", e);
            throw e;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        lock.writeLock().lock();

        try {
            src.close();
        } catch (Exception e) {
            LOG.error("Could not close source due to ", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteInBatch(byte[] key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commitBatch() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        checkNotNull(keys);
        if (!enabled.get()) {
            check();
            return;
        }

        lock.writeLock().lock();

        try {
            check();

            // deletes are delayed
            keys.forEach(key -> currentUpdates.deletedKeys.add(ByteArrayWrapper.wrap(key)));
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not delete batch due to ", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.readLock().lock();

        try {
            // the delayed deletes are not considered by this check until applied to the db
            if (!currentUpdates.insertedKeys.isEmpty()) {
                check();
                return false;
            } else {
                return src.isEmpty();
            }
        } catch (Exception e) {
            LOG.error("Could not check if empty due to ", e);
            throw e;
        } finally {
            lock.readLock().unlock();
        }
    }

    public ByteArrayKeyValueStore getSrc() {
        return src;
    }

    public ByteArrayKeyValueDatabase getArchiveSource() {
        if (!hasArchive) {
            return null;
        } else {
            return ((ArchivedDataSource) src).getArchiveDatabase();
        }
    }

    @Override
    public void check() {
        src.check();
    }

    /**
     * Checks that the given key is not null. Throws a {@link IllegalArgumentException} if the key
     * is null.
     */
    public static void checkNotNull(byte[] k) {
        if (k == null) {
            throw new IllegalArgumentException("The data store does not accept null keys.");
        }
    }

    /**
     * Checks that the given collection of keys does not contain null values. Throws a {@link
     * IllegalArgumentException} if a null key is present.
     */
    public static void checkNotNull(Collection<byte[]> keys) {
        if (keys.contains(null)) {
            throw new IllegalArgumentException("The data store does not accept null keys.");
        }
    }
}

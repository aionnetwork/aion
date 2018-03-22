package org.aion.db.generic;

import org.aion.base.db.IByteArrayKeyValueDatabase;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implements locking functionality for a generic database implementation.
 * <p>
 * Allows more flexibility in using the database implementations, by separating locking form database usage.
 *
 * @param <DB>
 *         a database implementing the {@link IByteArrayKeyValueDatabase} interface.
 * @author Alexandra Roatis
 */
public class LockedDatabase<DB extends IByteArrayKeyValueDatabase> implements IByteArrayKeyValueDatabase {

    /** Unlocked database. */
    private final DB database;

    /** Read-write lock allowing concurrent reads and single write operations. */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public LockedDatabase(DB _unlockedDatabase) {
        this.database = _unlockedDatabase;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " of " + database.toString();
    }

    // IDatabase functionality -----------------------------------------------------------------------------------------

    @Override
    public boolean open() {
        // acquire write lock
        lock.writeLock().lock();

        boolean open = false;

        try {
            open = database.open();
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
            return open;
        }
    }

    @Override
    public void close() {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.close();
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean commit() {
        // acquire write lock
        lock.writeLock().lock();

        boolean success = false;

        try {
            success = database.commit();
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
            return success;
        }
    }

    @Override
    public void compact() {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.compact();
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<String> getName() {
        // no locks because the name never changes
        return database.getName();
    }

    @Override
    public Optional<String> getPath() {
        // no locks because the path never changes
        return database.getPath();
    }

    @Override
    public boolean isOpen() {
        // acquire read lock
        lock.readLock().lock();

        boolean open = false;

        try {
            open = database.isOpen();
        } finally {
            // releasing read lock
            lock.readLock().unlock();
            return open;
        }
    }

    @Override
    public boolean isClosed() {
        // isOpen also handles locking
        return !isOpen();
    }

    @Override
    public boolean isLocked() {
        // being able to acquire a write lock means that the resource is not locked
        // only one write lock can be taken at a time, also excluding any concurrent read locks
        if (lock.writeLock().tryLock()) {
            lock.writeLock().unlock();
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean isAutoCommitEnabled() {
        // no locks because the autocommit flag never changes
        return database.isAutoCommitEnabled();
    }

    @Override
    public boolean isPersistent() {
        // no locks because the persistence flag never changes
        return database.isPersistent();
    }

    @Override
    public boolean isCreatedOnDisk() {
        // acquire read lock
        lock.readLock().lock();

        boolean onDisk = false;

        try {
            onDisk = database.isCreatedOnDisk();
        } finally {
            // releasing read lock
            lock.readLock().unlock();
            return onDisk;
        }
    }

    @Override
    public long approximateSize() {
        // acquire read lock
        lock.readLock().lock();

        long size = -1L;

        try {
            size = database.approximateSize();
        } finally {
            // releasing read lock
            lock.readLock().unlock();
            return size;
        }
    }

    // IKeyValueStore functionality ------------------------------------------------------------------------------------

    @Override
    public boolean isEmpty() {
        // acquire read lock
        lock.readLock().lock();

        boolean isEmpty = true;

        try {
            isEmpty = database.isEmpty();
        } finally {
            // releasing read lock
            lock.readLock().unlock();
            return isEmpty;
        }
    }

    @Override
    public Set<byte[]> keys() {
        // acquire read lock
        lock.readLock().lock();

        // initializing to null to limit object creation
        // the called database should create the instance
        Set<byte[]> keys = null;

        try {
            keys = database.keys();
        } finally {
            // releasing read lock
            lock.readLock().unlock();
            return keys;
        }
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        // acquire read lock
        lock.readLock().lock();

        Optional<byte[]> value = Optional.empty();

        try {
            value = database.get(key);
        } finally {
            // releasing read lock
            lock.readLock().unlock();
            return value;
        }
    }

    @Override
    public void put(byte[] key, byte[] value) {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.put(key, value);
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(byte[] key) {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.delete(key);
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public void putBatch(Map<byte[], byte[]> keyValuePairs) {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.putBatch(keyValuePairs);
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.deleteBatch(keys);
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }
}

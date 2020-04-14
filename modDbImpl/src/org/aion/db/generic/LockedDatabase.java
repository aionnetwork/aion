package org.aion.db.generic;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.PersistenceMethod;
import org.slf4j.Logger;

/**
 * Implements locking functionality for a generic database implementation.
 *
 * <p>Allows more flexibility in using the database implementations, by separating locking form
 * database usage.
 *
 * @author Alexandra Roatis
 */
public class LockedDatabase implements ByteArrayKeyValueDatabase {

    /** Unlocked database. */
    protected final ByteArrayKeyValueDatabase database;

    /** Read-write lock allowing concurrent reads and single write operations. */
    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

    protected final Logger LOG;

    public LockedDatabase(ByteArrayKeyValueDatabase unlockedDatabase, Logger log) {
        this.database = unlockedDatabase;
        this.LOG = log;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " over " + database.toString();
    }

    // IDatabase functionality
    // -----------------------------------------------------------------------------------------

    @Override
    public boolean open() {
        // acquire write lock
        lock.writeLock().lock();

        try {
            return database.open();
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
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

        try {
            return database.isOpen();
        } finally {
            // releasing read lock
            lock.readLock().unlock();
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
    public PersistenceMethod getPersistenceMethod() {
        // no locks because the persistence flag never changes
        return database.getPersistenceMethod();
    }

    @Override
    public boolean isCreatedOnDisk() {
        // acquire read lock
        lock.readLock().lock();

        try {
            return database.isCreatedOnDisk();
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    @Override
    public long approximateSize() {
        // acquire read lock
        lock.readLock().lock();

        try {
            return database.approximateSize();
        } catch (Exception e) {
            throw e;
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    // IKeyValueStore functionality
    // ------------------------------------------------------------------------------------

    @Override
    public boolean isEmpty() {
        // acquire read lock
        lock.readLock().lock();

        try {
            return database.isEmpty();
        } catch (Exception e) {
            throw e;
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    @Override
    public Iterator<byte[]> keys() {
        // acquire read lock
        lock.readLock().lock();

        try {
            return database.keys();
        } catch (Exception e) {
            throw e;
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        // acquire read lock
        lock.readLock().lock();

        try {
            return database.get(key);
        } catch (Exception e) {
            throw e;
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    @Override
    public void put(byte[] key, byte[] value) {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.put(key, value);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not put key-value pair due to ", e);
            }
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
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not delete key due to ", e);
            }
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
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not put batch due to ", e);
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.putToBatch(key, value);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not put to batch due to ", e);
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteInBatch(byte[] key) {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.deleteInBatch(key);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not delete in batch due to ", e);
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public void commit() {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.commit();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not put batch due to ", e);
            }
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
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not delete batch due to ", e);
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public void check() {
        // acquire read lock
        lock.readLock().lock();

        try {
            database.check();
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    @Override
    public void drop() {
        // acquire write lock
        lock.writeLock().lock();

        try {
            database.drop();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not drop database due to ", e);
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }
}

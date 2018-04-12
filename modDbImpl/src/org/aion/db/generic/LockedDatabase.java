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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.db.generic;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

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
 * @author Alexandra Roatis
 */
public class LockedDatabase implements IByteArrayKeyValueDatabase {

    /** Unlocked database. */
    protected final IByteArrayKeyValueDatabase database;

    /** Read-write lock allowing concurrent reads and single write operations. */
    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    public LockedDatabase(IByteArrayKeyValueDatabase _unlockedDatabase) {
        this.database = _unlockedDatabase;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " over " + database.toString();
    }

    // IDatabase functionality -----------------------------------------------------------------------------------------

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
    public boolean commit() {
        // acquire write lock
        lock.writeLock().lock();

        try {
            return database.commit();
        } catch (Exception e) {
            throw e;
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

    // IKeyValueStore functionality ------------------------------------------------------------------------------------

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
    public Set<byte[]> keys() {
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

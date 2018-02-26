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
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.db.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.primitives.Longs;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Common functionality for database implementations including heap caching functionality.
 *
 * @author yao
 * @author Alexandra Roatis
 * @implNote Assumes persistent database. Overwrite method if this is not the case.
 */
public abstract class AbstractDatabaseWithCache implements IByteArrayKeyValueDatabase {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    /** The lock for the current cache instance.  Does not lock / unlock the underlying data source. */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Underlying database implementation. */
    protected AbstractDB database;
    /** Underlying cache implementation that will be instantiated by default as a LRU cache. */
    private LoadingCache<ByteArrayWrapper, Optional<byte[]>> loadingCache = null;

    /** Keeps track of the entries that have been modified. */
    private Map<ByteArrayWrapper, byte[]> dirtyEntries = null;

    /** The underlying cache max size, will default to DEFAULT_JAVA_CACHE_SIZE at first. */
    private long maxSize;

    /** The flag to indicate if the stats are enabled or not. */
    private boolean statsEnabled;
    /** Flag for determining how to handle commits. */
    private boolean enableAutoCommit;

    protected AbstractDatabaseWithCache(boolean enableAutoCommit, String max_cache_size, boolean enableStats) {
        this.enableAutoCommit = enableAutoCommit;

        Long val = max_cache_size != null ? Longs.tryParse(max_cache_size) : null;
        this.maxSize = val == null ? 0 : val;

        this.statsEnabled = enableStats;
    }

    /**
     * Assists in setting up the underlying cache for the current instance.
     *
     * @param size
     * @param enableStats
     */
    private void setupLoadingCache(final long size, final boolean enableStats) {
        // check to see if the data source is not open
        check();

        this.dirtyEntries = new HashMap<>();

        // Use CacheBuilder to create the cache.
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();

        // Set the size.
        // Actually when size is 0, we make it unbounded
        if (size != 0) {
            builder.maximumSize(size);
        }

        // Enable stats if passed in.
        if (enableStats) {
            builder.recordStats();
        }

        // Utilize CacheBuilder and pass in the parameters to create the cache.
        this.loadingCache = builder.build(new CacheLoader<ByteArrayWrapper, Optional<byte[]>>() {
            @Override
            public Optional<byte[]> load(ByteArrayWrapper keyToLoad) {
                // It is safe to say keyToLoad is not null or the data is null.
                // Load from the data source.
                return database.get(keyToLoad.getData());
            }
        });
    }

    /**
     * Returns the size of the underlying cache of the current instance.
     *
     * @return
     */
    public long getMaximumCacheSize() {
        return this.maxSize;
    }

    public long getApproximateCacheSize() {
        return (this.loadingCache != null) ? this.loadingCache.size() : 0L;
    }

    /**
     * Returns if the stats are enabled are not for the cache implementation.
     *
     * @return
     */
    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    /**
     * Returns the stats from the underlying cache implementation. Mainly used for
     * testing.
     *
     * @return
     */
    public CacheStats getStats() {
        return this.loadingCache.stats();
    }

    /**
     * Checks that the database connection is open.
     * Throws a {@link RuntimeException} if the database connection is closed.
     *
     * @implNote Always do this check after acquiring a lock on the class/data.
     *         Otherwise it might produce inconsistent results due to lack of synchronization.
     */
    private void check() {
        if (!database.isOpen()) {
            throw new RuntimeException("Database is not opened: " + this);
        }
    }

    /**
     * For testing the lock functionality of public methods.
     * Used to ensure that locks are released after normal or exceptional execution.
     *
     * @return {@code true} when the resource is locked,
     *         {@code false} otherwise
     */
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

    // IDatabase functionality -----------------------------------------------------------------------------------------

    /**
     * @inheritDoc
     */
    @Override
    public boolean open() {
        // acquire write lock
        lock.writeLock().lock();

        if (isOpen()) {
            // releasing write lock and return status
            lock.writeLock().unlock();
            return true;
        }

        boolean open;

        try {
            LOG.debug("init heap cache {} for {}", this.toString(), database.toString());

            open = database.open();

            // setup cache only id database was opened successfully
            if (open) {
                setupLoadingCache(maxSize, statsEnabled);
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }

        return open;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void close() {
        // acquire write lock
        lock.writeLock().lock();

        try {
            // close database
            database.close();

            // clear the cache
            loadingCache.invalidateAll();

            // clear the dirty entries
            dirtyEntries.clear();
        } finally {
            // ensuring the db is null after close was called
            loadingCache = null;
            dirtyEntries = null;

            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean commit() {
        // acquire write lock
        lock.writeLock().lock();

        boolean success;

        try {
            check();

            if (enableAutoCommit) {
                LOG.warn("Commit called on database where automatic commits are already enabled.");
                if (dirtyEntries != null && dirtyEntries.size() > 0) {
                    // there should be nothing to commit
                    LOG.error("Non-permanent data found in the cache where automatic commits are enabled.");
                }
                // just return, everything should have already been made permanent
                success = true;
            } else {
                if (dirtyEntries == null) {
                    LOG.error("Commit called without an initialized cache for storing changes.");
                    success = false;
                } else {
                    // push to data source
                    success = database.commitCache(dirtyEntries);

                    // the dirty entries now match the storage
                    dirtyEntries.clear();
                }
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }

        return success;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Optional<String> getName() {
        return database.getName();
    }

    /**
     * @inheritDoc
     */
    @Override
    public Optional<String> getPath() {
        return database.getPath();
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isOpen() {
        // acquire read lock
        lock.readLock().lock();

        boolean open = database.isOpen();

        // releasing read lock
        lock.readLock().unlock();

        return open;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isClosed() {
        return !isOpen();
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isAutoCommitEnabled() {
        return enableAutoCommit;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isPersistent() {
        return database.isPersistent();
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isCreatedOnDisk() {
        // acquire read lock
        lock.readLock().lock();

        boolean onDisk = database.isCreatedOnDisk();

        // releasing read lock
        lock.readLock().unlock();

        return onDisk;
    }

    /**
     * @inheritDoc
     */
    @Override
    public long approximateSize() {
        // acquire read lock
        lock.readLock().lock();

        long size = database.approximateSize();

        // releasing read lock
        lock.readLock().unlock();

        return size;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + propertiesInfo();
    }

    private String propertiesInfo() {
        return "<autocommit=" + (enableAutoCommit ? "ON" : "OFF") + //
                ",size" + (maxSize == 0 ? "=UNBOUND" : "<" + maxSize) + //
                ",stats=" + (statsEnabled ? "ON" : "OFF") + ">";
    }

    // IKeyValueStore functionality ------------------------------------------------------------------------------------

    /**
     * @inheritDoc
     */
    @Override
    public boolean isEmpty() {
        // acquire read lock
        lock.readLock().lock();

        boolean isEmpty = true;

        try {
            check();

            if (loadingCache.size() > dirtyEntries.size()) {
                // check loading cache only when greater than dirtyEntries
                Collection<Optional<byte[]>> values = loadingCache.asMap().values();
                if (!values.contains(Optional.empty())) {
                    // no deletions => all are non-empty
                    isEmpty = false;
                } else {
                    for (Optional<byte[]> value : values) {
                        if (!value.equals(Optional.empty())) {
                            // found an existing (not deleted) value
                            isEmpty = false;
                        }
                    }
                }
            } else {
                // if all values are updates check the dirtyEntries
                if (dirtyEntries.size() > 0) {
                    Collection<byte[]> values = dirtyEntries.values();
                    if (!values.contains(null)) {
                        // no deletions => all are non-empty
                        isEmpty = false;
                    } else {
                        for (byte[] value : values) {
                            if (value != null) {
                                // found an existing (not deleted) value
                                isEmpty = false;
                            }
                        }
                    }
                }
            }

            // so far empty => check the source
            if (isEmpty) {
                isEmpty = database.isEmpty();
            }
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }

        return isEmpty;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Set<byte[]> keys() {
        // acquire read lock
        lock.readLock().lock();

        Set<byte[]> keys = new HashSet<>();

        try {
            check();

            // add all database keys
            keys.addAll(database.keys());

            // add updated cached keys
            dirtyEntries.forEach((k, v) -> {
                if (v == null) {
                    keys.remove(k.getData());
                } else {
                    keys.add(k.getData());
                }
            });
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }

        return keys;
    }

    /**
     * Returns the value from the cache if it exists or if not, loads it from the
     * database given the loader and return that.
     */
    @Override
    public Optional<byte[]> get(byte[] k) {
        AbstractDB.check(k);

        // acquire read lock
        lock.readLock().lock();

        Optional<byte[]> v = Optional.empty();

        try {
            // this runtime exception should not be caught here
            check();

            try {
                // gets the value from the cache or loads it from the database
                v = this.loadingCache.get(ByteArrayWrapper.wrap(k));
            } catch (Exception e) {
                LOG.error("Unable to retrieve value for the given key.", e);
            }
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }

        return v;
    }

    @Override
    public void put(byte[] k, byte[] v) {
        AbstractDB.check(k);

        // acquire write lock
        lock.writeLock().lock();

        try {
            check();

            ByteArrayWrapper key = ByteArrayWrapper.wrap(k);

            this.loadingCache.put(key, Optional.ofNullable(v));
            // keeping track of dirty data
            this.dirtyEntries.put(key, v);

            if (enableAutoCommit) {
                flushInternal();
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(byte[] k) {
        // put also handles synchronization
        put(k, null);
    }

    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        AbstractDB.check(inputMap.keySet());

        // acquire write lock
        lock.writeLock().lock();

        try {
            check();

            for (Map.Entry<byte[], byte[]> entry : inputMap.entrySet()) {
                ByteArrayWrapper key = ByteArrayWrapper.wrap(entry.getKey());
                byte[] value = entry.getValue();

                this.loadingCache.put(key, Optional.ofNullable(value));
                // keeping track of dirty data
                this.dirtyEntries.put(key, value);
            }

            if (enableAutoCommit) {
                flushInternal();
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        AbstractDB.check(keys);

        // acquire write lock
        lock.writeLock().lock();

        try {
            check();

            for (byte[] k : keys) {
                ByteArrayWrapper key = ByteArrayWrapper.wrap(k);

                this.loadingCache.put(key, Optional.empty());
                // keeping track of dirty data
                this.dirtyEntries.put(key, null);
            }

            if (enableAutoCommit) {
                flushInternal();
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    /**
     * Pushes all the dirty key-value pairs to the database.
     * Does not make any guarantees with respect to their continued / discontinued storage in the cache.
     *
     * @apiNote This method should be used where write locks have already been acquired
     *         since it does not acquire write locks before modifying the data.
     */
    private void flushInternal() {
        // push to data source
        database.commitCache(dirtyEntries);

        // the dirty entries now match the storage
        dirtyEntries.clear();
    }
}

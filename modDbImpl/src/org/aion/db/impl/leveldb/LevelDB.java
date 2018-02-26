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
package org.aion.db.impl.leveldb;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @implNote The read-write lock is used only for those operations that are not synchronized
 *         by the JNI on top of the native LevelDB, namely open and close operations.
 */
public class LevelDB extends AbstractDB {

    private DB db;

    public LevelDB(String name, String path, boolean enableCache, boolean enableCompression) {
        super(name, path, enableCache, enableCompression);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + propertiesInfo();
    }

    private Options setupLevelDbOptions() {
        Options options = new Options();

        options.createIfMissing(true);
        options.compressionType(enableDbCompression ? CompressionType.SNAPPY : CompressionType.NONE);
        options.blockSize(10 * 1024 * 1024);
        options.writeBufferSize(DEFAULT_WRITE_BUFFER_SIZE_BYTES); // (levelDb default: 8mb)
        options.cacheSize(enableDbCache ? DEFAULT_CACHE_SIZE_BYTES : 0);
        options.paranoidChecks(true);
        options.verifyChecksums(true);
        options.maxOpenFiles(32);

        return options;
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

        try {
            LOG.debug("init database {}", this.toString());

            File f = new File(path);
            File dbRoot = f.getParentFile();

            // make the parent directory if not exists
            if (!dbRoot.exists()) {
                if (!f.getParentFile().mkdirs()) {
                    LOG.error("Failed to initialize the database storage for " + this.toString() + ".");

                    // releasing write lock and return status
                    lock.writeLock().unlock();
                    return false;
                }
            }

            Options options = setupLevelDbOptions();

            try {
                db = JniDBFactory.factory.open(f, options);
            } catch (Exception e1) {
                LOG.error("Failed to open the database " + this.toString() + " due to: ", e1);

                try {
                    // attempt repair
                    JniDBFactory.factory.repair(f, options);
                } catch (Exception e2) {
                    LOG.error("Failed to repair the database " + this.toString() + " due to: ", e2);
                }

                // close the connection and cleanup if needed
                close();
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }

        return isOpen();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void close() {
        // acquire write lock
        lock.writeLock().lock();

        // do nothing if already closed
        if (db == null) {
            // releasing write lock
            lock.writeLock().unlock();
            return;
        }

        try {
            // attempt to close the database
            db.close();
        } catch (IOException e) {
            LOG.error("Failed to close the database " + this.toString() + ".", e);
        } finally {
            // ensuring the db is null after close was called
            db = null;
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isOpen() {
        // acquire read lock
        lock.readLock().lock();

        boolean open = db != null;

        // releasing read lock
        lock.readLock().unlock();

        return open;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isCreatedOnDisk() {
        // acquire read lock
        lock.readLock().lock();

        // working heuristic for Ubuntu: both the LOCK and LOG files should get created on creation
        // TODO: implement a platform independent way to do this
        boolean onDisk = new File(path, "LOCK").exists() && new File(path, "LOG").exists();

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

        long count;

        try {
            check();

            count = 0;

            File[] files = (new File(path)).listFiles();

            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        count += f.length();
                    }
                }
            } else {
                count = -1L;
            }
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }

        return count;
    }

    // IKeyValueStore functionality ------------------------------------------------------------------------------------

    /**
     * @inheritDoc
     */
    @Override
    public boolean isEmpty() {
        // acquire read lock
        lock.readLock().lock();

        boolean status = true;

        try {
            check();

            try (DBIterator itr = db.iterator()) {
                itr.seekToFirst();

                // check if there is at least one item
                status = !itr.hasNext();
            } catch (Exception e) {
                LOG.error("Unable to extract information from database " + this.toString() + ".", e);
            }
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }

        return status;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Set<byte[]> keys() {
        // acquire read lock
        lock.readLock().lock();

        Set<byte[]> set = new HashSet<>();

        try {
            check();

            try (DBIterator itr = db.iterator()) {
                // extract keys
                for (itr.seekToFirst(); itr.hasNext(); itr.next()) {
                    set.add(itr.peekNext().getKey());
                }
            } catch (Exception e) {
                LOG.error("Unable to extract keys from database " + this.toString() + ".", e);
            }
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }

        // empty when retrieval failed
        return set;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Optional<byte[]> get(byte[] k) {
        check(k);

        // acquire read lock
        lock.readLock().lock();

        byte[] v;

        try {
            check();

            v = db.get(k);
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }

        return Optional.ofNullable(v);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void put(byte[] k, byte[] v) {
        check(k);

        // acquire read lock
        lock.readLock().lock();

        try {
            check();

            if (v == null) {
                db.delete(k);
            } else {
                db.put(k, v);
            }
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void delete(byte[] k) {
        check(k);

        // acquire read lock
        lock.readLock().lock();

        try {
            check();
            db.delete(k);
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        check(inputMap.keySet());

        // acquire read lock
        lock.readLock().lock();

        try {
            check();

            // try-with-resources will automatically close the batch object
            try (WriteBatch batch = db.createWriteBatch()) {
                // add put and delete operations to batch
                for (Map.Entry<byte[], byte[]> e : inputMap.entrySet()) {
                    byte[] key = e.getKey();
                    byte[] value = e.getValue();

                    if (value == null) {
                        batch.delete(key);
                    } else {
                        batch.put(key, value);
                    }
                }

                // bulk atomic update
                db.write(batch);
            } catch (DBException e) {
                LOG.error("Unable to execute batch put/update operation on " + this.toString() + ".", e);
            } catch (IOException e) {
                LOG.error("Unable to close WriteBatch object in " + this.toString() + ".", e);
            }
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        check(keys);

        // acquire read lock
        lock.readLock().lock();

        try {
            check();

            try (WriteBatch batch = db.createWriteBatch()) {
                // add delete operations to batch
                for (byte[] k : keys) {
                    batch.delete(k);
                }

                // bulk atomic update
                db.write(batch);
            } catch (DBException e) {
                LOG.error("Unable to execute batch delete operation on " + this.toString() + ".", e);
            } catch (IOException e) {
                LOG.error("Unable to close WriteBatch object in " + this.toString() + ".", e);
            }
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }
    }

    // AbstractDB functionality ----------------------------------------------------------------------------------------

    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        // acquire read lock
        lock.readLock().lock();

        boolean success = false;

        try {
            check();

            // try-with-resources will automatically close the batch object
            try (WriteBatch batch = db.createWriteBatch()) {
                // add put and delete operations to batch
                for (Map.Entry<ByteArrayWrapper, byte[]> e : cache.entrySet()) {
                    if (e.getValue() == null) {
                        batch.delete(e.getKey().getData());
                    } else {
                        batch.put(e.getKey().getData(), e.getValue());
                    }
                }

                // bulk atomic update
                db.write(batch);

                success = true;
            } catch (DBException e) {
                LOG.error("Unable to commit heap cache to " + this.toString() + ".", e);
            } catch (IOException e) {
                LOG.error("Unable to close WriteBatch object in " + this.toString() + ".", e);
            }
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }

        return success;

    }
}
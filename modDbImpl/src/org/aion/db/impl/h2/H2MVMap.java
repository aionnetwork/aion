/*
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
 */
package org.aion.db.impl.h2;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.h2.mvstore.FileStore;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreTool;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/*
 * IMPORTANT IMPLEMENTATION NOTE:
 * H2MvMap behaves like Java concurrent collections and NOT traditional databases:
 * actions in a thread prior to placing an object into a ConcurrentMap as a key or
 * value happen-before actions subsequent to the access or removal of that object
 * from the ConcurrentMap in another thread
 *
 * MVMap implements the ConcurrentMap interface and does not support batch
 * writes; internally, all writes to disk are batched since this implementation
 * of MVMap uses a WRITE_BUFFER_SIZE of 10mb (ie. up to 10mb of writes may be
 * stored in the write buffer before being flushed to disk).
 */
public class H2MVMap extends AbstractDB {

    private final String dbFilePath; // path tp db file
    /**
     * it's OK to hold a reference here as opposed to getting a reference every time
     * from MVStore since store.getFileStore() transparently passes a reference back
     * up to the fs object
     */
    private FileStore mvStoreFileRef;

    private MVStore store;
    private MVMap<byte[], byte[]> map; // MVMap implements ConcurrentMap

    public H2MVMap(String name, String path, boolean enableDbCache, boolean enableDbCompression) {
        super(name, path, enableDbCache, enableDbCompression);

        this.dbFilePath = new File(path, name + "/" + name + ".mv.db").getAbsolutePath();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + propertiesInfo();
    }

    private MVStore.Builder setupMVStoreBuilder() {
        /*-
         *  H2 features NOT supported by this driver:
         *
         *  > read-only data stores
         *  > encrypted data stores
         *  > deflate compression (highCompression)
         *  > setReuseSpace = false
         */

        MVStore.Builder builder = new MVStore.Builder();
        builder.fileName(dbFilePath);
        builder.cacheSize(enableDbCache ? DEFAULT_CACHE_SIZE_BYTES / (1024 * 1024) : 0); // in mb
        builder.autoCommitBufferSize(DEFAULT_WRITE_BUFFER_SIZE_BYTES / (1024)); // in kb
        if (enableDbCompression) {
            builder.compress();
            // use a larger page split size to improve compression ratio
            builder.pageSplitSize(64 * 1024); // bytes
        }

        builder.backgroundExceptionHandler((t, e) -> {
            LOG.error("H2 MVStore Uncaught Exception at Thread: {}\nException: {}", t.toString(), e.toString());
            throw new RuntimeException(e);
        });

        return builder;
    }

    // IDatabase functionality -----------------------------------------------------------------------------------------

    @Override
    public boolean open() {
        if (isOpen()) {
            return true;
        }

        LOG.debug("init database {}", this.toString());

        File f = new File(path);

        // make directory for db file if none exists
        if (!f.exists()) {
            if (!f.mkdirs()) {
                LOG.error("Failed to initialize the database storage for " + this.toString() + ".");
                return false;
            }
        }

        try {
            // cleanup artifacts of ungraceful compacting-process shutdown
            MVStoreTool.compactCleanUp(dbFilePath);

            store = setupMVStoreBuilder().open();

            if (store != null) {
                mvStoreFileRef = store.getFileStore();
                map = store.openMap(name);
            } else {
                LOG.error("Failed to open the database " + this.toString() + ".");
                return false;
            }
        } catch (Exception e) {
            if (e instanceof NullPointerException) {
                LOG.error("Failed to open the database " + this.toString()
                    + ". A probable cause is that the H2 database cannot access the file path. "
                    + "Check if you have two instances running on the same database.", e);
            } else {
                LOG.error("Failed to open the database " + this.toString() + " due to: ", e);
            }

            // close the connection and cleanup if needed
            close();
        }

        return isOpen();
    }

    @Override
    public void close() {
        // do nothing if already closed
        if (store == null) {
            // but ensure the map is also null
            map = null;
            return;
        }

        LOG.info("Closing database " + this.toString());

        try {
            // attempt to close the database
            store.close();
        } catch (Exception e) {
            LOG.error("Failed to close database " + this.toString() + " gracefully due to: ", e);
            LOG.warn("Attempting a hard close.");
            // attempt force close
            store.closeImmediately();
        } finally {
            // ensuring the store is null after close was called
            store = null;
            map = null; // MVMap automatically closed upon MVStore closing
        }
    }

    @Override
    public boolean isOpen() {
        return map != null;
    }

    @Override
    public boolean isCreatedOnDisk() {
        // since this is a single-file db, the file defined by "path" should exist on a
        // successful call to open()
        return new File(dbFilePath).exists();
    }

    @Override
    public long approximateSize() {
        long size = -1L;

        check();

        if (this.mvStoreFileRef != null) {
            size = this.mvStoreFileRef.size();
        }

        return size;
    }

    // IKeyValueStore functionality ------------------------------------------------------------------------------------

    @Override
    public boolean isEmpty() {
        boolean status = true;

        // this runtime exception should not be caught here
        check();

        try {
            status = map.isEmpty();
        } catch (Exception e) {
            LOG.error("Unable to extract information from database " + this.toString() + ".", e);
        }

        return status;
    }

    @Override
    public Set<byte[]> keys() {

        Set<byte[]> keys = new HashSet<>();

        check();

        keys.addAll(map.keySet());

        return keys;
    }

    @Override
    public byte[] getInternal(byte[] k) {
        return map.get(k);
    }

    @Override
    public void put(byte[] k, byte[] v) {
        check(k);

        check();

        if (v == null) {
            map.remove(k);
        } else {
            map.put(k, v);
        }
    }

    @Override
    public void delete(byte[] k) {
        check(k);

        check();

        map.remove(k);
    }

    /**
     * MVMap implements the ConcurrentMap interface and does not support batch
     * writes; internally, all writes to disk are batched since this implementation
     * of MVMap uses a WRITE_BUFFER_SIZE of 10mb (ie. up to 10mb of writes may be
     * stored in the write buffer before being flushed to disk).
     * <p>
     * This implementation of putBatch provides no guarantees on WHEN the batched
     * data gets flushed to disk. It is the application developer's responsibility
     * to call flush()
     * <p>
     * Places a batch of key value mappings into the DB, one guarantee that should
     * be made is that this function should execute atomically
     *
     * @param inputMap
     */
    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        check(inputMap.keySet());

        // this runtime exception should not be caught here
        check();

        try {
            // doesn't actually have functionality for batch operations
            for (Map.Entry<byte[], byte[]> e : inputMap.entrySet()) {
                byte[] key = e.getKey();
                byte[] value = e.getValue();

                if (value == null) {
                    map.remove(key);
                } else {
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            LOG.error("Unable to execute batch put/update operation on " + this.toString() + ".", e);
        }
    }

    @Override
    public void putToBatch(byte[] k, byte[] v) {
        // same as put since batch operations are not supported
        put(k, v);
    }

    @Override
    public void commitBatch() {
        // nothing to do since batch operations are not supported
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        check(keys);

        // this runtime exception should not be caught here
        check();

        try {
            for (byte[] k : keys) {
                // can handle null keys correctly
                map.remove(k);
            }
        } catch (Exception e) {
            LOG.error("Unable to execute batch delete operation on " + this.toString() + ".", e);
        }
    }

    // AbstractDB functionality ----------------------------------------------------------------------------------------

    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        boolean success = false;

        try {
            check();

            // doesn't actually have functionality for batch operations
            for (Entry<ByteArrayWrapper, byte[]> e : cache.entrySet()) {
                if (e.getValue() == null) {
                    map.remove(e.getKey().getData());
                } else {
                    map.put(e.getKey().getData(), e.getValue());
                }
            }

            success = true;
        } catch (Exception e) {
            LOG.error("Unable to commit heap cache to " + this.toString() + ".", e);
        }

        return success;
    }

    /**
     * Compact the database file, that is, compact blocks that have a low fill rate,
     * and move chunks next to each other. This will typically shrink the database
     * file. Changes are flushed to the file, and old chunks are overwritten.
     */
    public void compact() {
        LOG.info("Compacting " + this.toString() + ".");

        store.setRetentionTime(0);

        // old implementation with time limit
        // long start = System.nanoTime();
        while (store.compact(95, 16 * 1024 * 1024)) {
            store.sync();
            store.compactMoveChunks(95, 16 * 1024 * 1024);

            // old implementation with time limit
            // maxCompactTime: the maximum time in milliseconds to compact
            // long time = System.nanoTime() - start;
            // if (time > TimeUnit.MILLISECONDS.toNanos(maxCompactTime)) { break; }
        }
    }

    // TODO: Find a way to expose flush() up to the application level

    /**
     * It is recommended by the authors of MVMap to rely on the auto-commit feature
     * (enabled in this DB driver implementation) to auto flush the changes to disk.
     * (auto-commit internally calls commit() from time to time or when enough
     * changes have accumulated.
     */
    public void flush() {
        check();
        store.commit();
        // store.sync() a "harder" flush to disk using
        // FileChannel.force(true)
    }
}

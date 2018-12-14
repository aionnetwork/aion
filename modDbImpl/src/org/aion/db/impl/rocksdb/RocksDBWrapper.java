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
 * Contributors:
 *     Aion foundation.
 */
package org.aion.db.impl.rocksdb;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

public class RocksDBWrapper extends AbstractDB {

    private RocksDB db;
    private final int maxOpenFiles;
    private final int blockSize;
    private final int writeBufferSize;
    private final int readBufferSize;
    private final int cacheSize;

    public RocksDBWrapper(
            String name,
            String path,
            boolean enableDbCache,
            boolean enableDbCompression,
            int maxOpenFiles,
            int blockSize,
            int writeBufferSize,
            int readBufferSize,
            int cacheSize) {
        super(name, path, enableDbCache, enableDbCompression);

        this.maxOpenFiles = maxOpenFiles;
        this.blockSize = blockSize;
        this.writeBufferSize = writeBufferSize;
        this.readBufferSize = readBufferSize;
        this.cacheSize = cacheSize;

        RocksDB.loadLibrary();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + propertiesInfo();
    }

    private Options setupRocksDbOptions() {
        Options options = new Options();

        options.setCreateIfMissing(true);
        options.setCompressionType(
                enableDbCompression
                        ? CompressionType.SNAPPY_COMPRESSION
                        : CompressionType.NO_COMPRESSION);

        options.setWriteBufferSize(this.writeBufferSize);
        options.setRandomAccessMaxBufferSize(this.readBufferSize);
        options.setParanoidChecks(true);
        options.setMaxOpenFiles(this.maxOpenFiles);
        options.setTableFormatConfig(setupBlockBasedTableConfig());

        return options;
    }

    private BlockBasedTableConfig setupBlockBasedTableConfig() {
        BlockBasedTableConfig bbtc = new BlockBasedTableConfig();
        bbtc.setBlockSize(this.blockSize);
        bbtc.setBlockCacheSize(this.cacheSize);

        return bbtc;
    }

    // IDatabase Functionality
    @Override
    public boolean open() {
        if (isOpen()) {
            return true;
        }

        LOG.debug("Initialising RockDB {}", this.toString());

        File f = new File(path);
        File dbRoot = f.getParentFile();

        // make the parent directory if not exists
        if (!dbRoot.exists()) {
            if (!f.getParentFile().mkdirs()) {
                LOG.error("Failed to initialize the database storage for " + this.toString() + ".");
                return false;
            }
        }

        Options options = setupRocksDbOptions();

        try {
            db = RocksDB.open(options, f.getAbsolutePath());
        } catch (RocksDBException e) {
            if (e.getMessage().contains("lock")) {
                LOG.error(
                        "Failed to open the database "
                                + this.toString()
                                + "\nCheck if you have two instances running on the same database."
                                + "\nFailure due to: ",
                        e);
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
        if (db == null) {
            return;
        }

        LOG.info("Closing database " + this.toString());

        // attempt to close the database
        db.close();
        db = null;
    }

    @Override
    public void compact() {
        LOG.info("Compacting " + this.toString() + ".");
        try {
            db.compactRange(new byte[] {(byte) 0x00}, new byte[] {(byte) 0xff});
        } catch (RocksDBException e) {
            LOG.error("Cannot compact data.");
            e.printStackTrace();
        }
    }

    @Override
    public boolean isOpen() {
        return db != null;
    }

    @Override
    public boolean isCreatedOnDisk() {
        // working heuristic for Ubuntu: both the LOCK and LOG files should get created on creation
        // TODO: implement a platform independent way to do this
        return new File(path, "LOCK").exists() && new File(path, "LOG").exists();
    }

    @Override
    public long approximateSize() {
        check();

        long count = 0;

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

        return count;
    }

    // IKetValueStore functionality

    @Override
    public boolean isEmpty() {
        check();

        try (RocksIterator itr = db.newIterator()) {
            itr.seekToFirst();

            // check if there is at least one valid item
            return !itr.isValid();
        } catch (Exception e) {
            LOG.error("Unable to extract information from database " + this.toString() + ".", e);
        }

        return true;
    }

    @Override
    public Set<byte[]> keys() {
        Set<byte[]> set = new HashSet<>();
        check();

        try (RocksIterator itr = db.newIterator()) {
            itr.seekToFirst();
            // extract keys
            while (itr.isValid()) {
                set.add(itr.key());
                itr.next();
            }
        } catch (Exception e) {
            LOG.error("Unable to extract keys from database " + this.toString() + ".", e);
        }

        // empty when retrieval failed
        return set;
    }

    @Override
    protected byte[] getInternal(byte[] key) {
        try {
            return db.get(key);
        } catch (RocksDBException e) {
            LOG.error("Unable to get key " + Arrays.toString(key) + ". " + e);
        }

        return null;
    }

    @Override
    public void putInternal(byte[] key, byte[] value) {
        try {
            db.put(key, value);
        } catch (RocksDBException e) {
            LOG.error("Unable to put / update key " + Arrays.toString(key) + ". " + e);
        }
    }

    @Override
    public void deleteInternal(byte[] key) {
        try {
            db.delete(key);
        } catch (RocksDBException e) {
            LOG.error("Unable to delete key " + Arrays.toString(key) + ". " + e);
        }
    }

    private WriteBatch batch = null;

    @Override
    public void putToBatchInternal(byte[] key, byte[] value) {
        if (batch == null) {
            batch = new WriteBatch();
        }

        try {
            batch.put(key, value);
        } catch (RocksDBException e) {
            LOG.error("Unable to perform put to batch operation on " + this.toString() + ".", e);
        } finally {
            // attempting to write directly since batch operation didn't work
            putInternal(key, value);
            try {
                batch.close();
            } finally {
                batch = null;
            }
        }
    }

    @Override
    public void deleteInBatchInternal(byte[] key) {
        if (batch == null) {
            batch = new WriteBatch();
        }

        try {
            batch.delete(key);
        } catch (RocksDBException e) {
            LOG.error("Unable to perform delete in batch operation on " + this.toString() + ".", e);
        } finally {
            // attempting to write directly since batch operation didn't work
            deleteInternal(key);
            try {
                batch.close();
            } finally {
                batch = null;
            }
        }
    }

    @Override
    public void commitBatch() {
        if (batch != null) {
            try {
                db.write(new WriteOptions(), batch);
            } catch (RocksDBException e) {
                LOG.error(
                        "Unable to execute batch put/update/delete operation on "
                                + this.toString()
                                + ".",
                        e);
            }
            batch.close();
            batch = null;
        }
    }

    @Override
    public void putBatchInternal(Map<byte[], byte[]> input) {
        // try-with-resources will automatically close the batch object
        try (WriteBatch batch = new WriteBatch()) {
            // add put and delete operations to batch
            for (Map.Entry<byte[], byte[]> e : input.entrySet()) {
                byte[] key = e.getKey();
                byte[] value = e.getValue();

                batch.put(key, value);
            }

            // bulk atomic update
            db.write(new WriteOptions(), batch);
        } catch (RocksDBException e) {
            LOG.error(
                    "Unable to execute batch put/update operation on " + this.toString() + ".", e);
        }
    }

    @Override
    public void deleteBatchInternal(Collection<byte[]> keys) {
        try (WriteBatch batch = new WriteBatch()) {
            // add delete operations to batch
            for (byte[] key : keys) {
                batch.delete(key);
            }

            // bulk atomic update
            db.write(new WriteOptions(), batch);
        } catch (RocksDBException e) {
            LOG.error("Unable to execute batch delete operation on " + this.toString() + ".", e);
        }
    }

    @Override
    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        boolean success = false;

        check();

        // try-with-resources will automatically close to batch object

        try (WriteBatch batch = new WriteBatch()) {
            for (Map.Entry<ByteArrayWrapper, byte[]> e : cache.entrySet()) {
                if (e.getValue() == null) {
                    batch.delete(e.getKey().getData());
                } else {
                    batch.put(e.getKey().getData(), e.getValue());
                }
            }

            // bulk automatic update
            db.write(new WriteOptions(), batch);

            success = true;
        } catch (RocksDBException e) {
            LOG.error("Unable to commit heap cache to " + this.toString() + ".", e);
        }

        return success;
    }
}

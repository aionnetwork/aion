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
package org.aion.db.impl.leveldb;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;

/**
 * @implNote The read-write lock is used only for those operations that are not synchronized by the
 *     JNI on top of the native LevelDB, namely open and close operations.
 */
public class LevelDB extends AbstractDB {

    private final int maxOpenFiles;
    private final int blockSize;
    private final int writeBufferSize;
    private final int cacheSize;

    private DB db;

    public LevelDB(
            String name,
            String path,
            boolean enableCache,
            boolean enableCompression,
            int maxOpenFiles,
            int blockSize,
            int writeBufferSize,
            int cacheSize) {
        super(name, path, enableCache, enableCompression);
        this.maxOpenFiles = maxOpenFiles;
        this.blockSize = blockSize;
        this.writeBufferSize = writeBufferSize;
        this.cacheSize = cacheSize;
    }

    /**
     * Original constructor for LevelDB, to keep compatibility with tests, for future use the user
     * should set the {@link #maxOpenFiles} and {@link #blockSize} directly.
     *
     * <p>Note: the values set in this constructor are not optimal, only historical.
     */
    @Deprecated
    public LevelDB(String name, String path, boolean enableCache, boolean enableCompression) {
        this(
                name,
                path,
                enableCache,
                enableCompression,
                LevelDBConstants.MAX_OPEN_FILES,
                LevelDBConstants.BLOCK_SIZE,
                LevelDBConstants.WRITE_BUFFER_SIZE,
                LevelDBConstants.CACHE_SIZE);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + propertiesInfo();
    }

    private Options setupLevelDbOptions() {
        Options options = new Options();

        options.createIfMissing(true);
        options.compressionType(
                enableDbCompression ? CompressionType.SNAPPY : CompressionType.NONE);
        options.blockSize(this.blockSize);
        options.writeBufferSize(this.writeBufferSize); // (levelDb default: 8mb)
        options.cacheSize(enableDbCache ? this.cacheSize : 0);
        options.paranoidChecks(true);
        options.verifyChecksums(true);
        options.maxOpenFiles(this.maxOpenFiles);

        return options;
    }

    // IDatabase functionality
    // -----------------------------------------------------------------------------------------

    @Override
    public boolean open() {
        if (isOpen()) {
            return true;
        }

        LOG.debug("init database {}", this.toString());

        File f = new File(path);
        File dbRoot = f.getParentFile();

        // make the parent directory if not exists
        if (!dbRoot.exists()) {
            if (!f.getParentFile().mkdirs()) {
                LOG.error("Failed to initialize the database storage for " + this.toString() + ".");
                return false;
            }
        }

        Options options = setupLevelDbOptions();

        try {
            db = JniDBFactory.factory.open(f, options);
        } catch (Exception e1) {
            if (e1.getMessage().contains("lock")) {
                LOG.error(
                        "Failed to open the database "
                                + this.toString()
                                + "\nCheck if you have two instances running on the same database."
                                + "\nFailure due to: ",
                        e1);
            } else {
                LOG.error("Failed to open the database " + this.toString() + " due to: ", e1);
            }

            if (e1.getMessage() != null && e1.getMessage().contains("No space left on device")) {
                LOG.error("Shutdown due to lack of disk space.");
                System.exit(0);
            }

            try {
                LOG.warn("attempting to repair database {}", this.toString());
                // attempt repair
                JniDBFactory.factory.repair(f, options);
            } catch (Exception e2) {
                LOG.error("Failed to repair the database " + this.toString() + " due to: ", e2);
                // the repair failed
                // close the connection and cleanup if needed
                close();
            }

            // the repair didn't throw an exception
            // try to open again
            try {
                db = JniDBFactory.factory.open(f, options);
            } catch (Exception e2) {
                LOG.error(
                        "Failed second attempt to open the database "
                                + this.toString()
                                + " due to: ",
                        e2);
                // close the connection and cleanup if needed
                close();
            }
        }

        return isOpen();
    }

    private void repair() {
        if (isOpen()) {
            this.close();
        }

        File f = new File(path);
        Options options = setupLevelDbOptions();

        try {
            LOG.warn("attempting to repair database {}", this.toString());
            // attempt repair
            JniDBFactory.factory.repair(f, options);
        } catch (Exception e2) {
            LOG.error("Failed to repair the database " + this.toString() + " due to: ", e2);
        }

        this.open();
    }

    @Override
    public void close() {
        // do nothing if already closed
        if (db == null) {
            return;
        }

        LOG.info("Closing database " + this.toString());

        try {
            // attempt to close the database
            db.close();
        } catch (IOException e) {
            LOG.error("Failed to close the database " + this.toString() + ".", e);
        } finally {
            // ensuring the db is null after close was called
            db = null;
        }
    }

    @Override
    public void compact() {
        LOG.info("Compacting " + this.toString() + ".");
        db.compactRange(new byte[] {(byte) 0x00}, new byte[] {(byte) 0xff});
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

    // IKeyValueStore functionality
    // ------------------------------------------------------------------------------------

    @Override
    public boolean isEmpty() {
        check();

        try (DBIterator itr = db.iterator()) {
            itr.seekToFirst();

            // check if there is at least one item
            return !itr.hasNext();
        } catch (Exception e) {
            LOG.error("Unable to extract information from database " + this.toString() + ".", e);
        }

        return true;
    }

    @Override
    public Set<byte[]> keys() {
        Set<byte[]> set = new HashSet<>();

        check();

        try (DBIterator itr = db.iterator()) {
            // extract keys
            for (itr.seekToFirst(); itr.hasNext(); itr.next()) {
                set.add(itr.peekNext().getKey());
            }
        } catch (Exception e) {
            LOG.error("Unable to extract keys from database " + this.toString() + ".", e);
        }

        // empty when retrieval failed
        return set;
    }

    @Override
    public byte[] getInternal(byte[] k) {
        try {
            return db.get(k);
        } catch (DBException e) {
            repair();
            // will throw the exception if the repair did not work
            return db.get(k);
        }
    }

    @Override
    public void putInternal(byte[] key, byte[] value) {
        db.put(key, value);
    }

    @Override
    public void deleteInternal(byte[] key) {
        db.delete(key);
    }

    @Override
    public void putToBatchInternal(byte[] key, byte[] value) {
        if (batch == null) {
            batch = db.createWriteBatch();
        }

        batch.put(key, value);
    }

    @Override
    public void deleteInBatchInternal(byte[] key) {
        if (batch == null) {
            batch = db.createWriteBatch();
        }

        batch.delete(key);
    }

    @Override
    public void commitBatch() {
        if (batch != null) {
            try {
                db.write(batch);
            } catch (DBException e) {
                LOG.error(
                        "Unable to execute batch put/update operation on " + this.toString() + ".",
                        e);
            }
            try {
                batch.close();
            } catch (IOException e) {
                LOG.error("Unable to close WriteBatch object in " + this.toString() + ".", e);
            }
            batch = null;
        }
    }

    @Override
    public void putBatchInternal(Map<byte[], byte[]> input) {
        // try-with-resources will automatically close the batch object
        try (WriteBatch batch = db.createWriteBatch()) {
            // add put and delete operations to batch
            for (Map.Entry<byte[], byte[]> e : input.entrySet()) {
                byte[] key = e.getKey();
                byte[] value = e.getValue();

                batch.put(key, value);
            }

            // bulk atomic update
            db.write(batch);
        } catch (DBException e) {
            LOG.error(
                    "Unable to execute batch put/update operation on " + this.toString() + ".", e);
        } catch (IOException e) {
            LOG.error("Unable to close WriteBatch object in " + this.toString() + ".", e);
        }
    }

    private WriteBatch batch = null;

    @Override
    public void deleteBatchInternal(Collection<byte[]> keys) {
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
    }

    // AbstractDB functionality
    // ----------------------------------------------------------------------------------------

    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        boolean success = false;

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

        return success;
    }
}

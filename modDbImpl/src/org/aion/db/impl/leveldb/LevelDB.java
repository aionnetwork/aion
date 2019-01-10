package org.aion.db.impl.leveldb;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;
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
    public Iterator<byte[]> keys() {
        check();

        try {
            ReadOptions readOptions = new ReadOptions();
            readOptions.snapshot(db.getSnapshot());
            return new LevelDBIteratorWrapper(readOptions, db.iterator(readOptions));
        } catch (Exception e) {
            LOG.error("Unable to extract keys from database " + this.toString() + ".", e);
        }

        // empty when retrieval failed
        return Collections.emptyIterator();
    }

    /**
     * A wrapper for the {@link DBIterator} conforming to the {@link Iterator} interface.
     *
     * @author Alexandra Roatis
     */
    private static class LevelDBIteratorWrapper implements Iterator<byte[]> {
        private final DBIterator iterator;
        private final ReadOptions readOptions;
        private boolean closed;

        /**
         * @implNote Building two wrappers for the same {@link DBIterator} will lead to inconsistent
         *     behavior.
         */
        LevelDBIteratorWrapper(final ReadOptions readOptions, final DBIterator iterator) {
            this.readOptions = readOptions;
            this.iterator = iterator;
            iterator.seekToFirst();
            closed = false;
        }

        @Override
        public boolean hasNext() {
            if (!closed) {
                boolean hasNext = iterator.hasNext();

                // close iterator after last entry
                if (!hasNext) {
                    try {
                        iterator.close();
                        readOptions.snapshot().close();
                    } catch (IOException e) {
                        LOG.error("Unable to close iterator object.", e);
                    }
                    closed = true;
                }

                return hasNext;
            } else {
                return false;
            }
        }

        @Override
        public byte[] next() {
            byte[] key = iterator.peekNext().getKey();
            iterator.next();
            return key;
        }
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
    public void put(byte[] k, byte[] v) {
        check(k);

        check();

        if (v == null) {
            db.delete(k);
        } else {
            db.put(k, v);
        }
    }

    @Override
    public void delete(byte[] k) {
        check(k);

        check();
        db.delete(k);
    }

    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        check(inputMap.keySet());

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
            LOG.error(
                    "Unable to execute batch put/update operation on " + this.toString() + ".", e);
        } catch (IOException e) {
            LOG.error("Unable to close WriteBatch object in " + this.toString() + ".", e);
        }
    }

    private WriteBatch batch = null;

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        check(key);

        check();

        if (batch == null) {
            batch = db.createWriteBatch();
        }

        if (value == null) {
            batch.delete(key);
        } else {
            batch.put(key, value);
        }
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
    public void deleteBatch(Collection<byte[]> keys) {
        check(keys);

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

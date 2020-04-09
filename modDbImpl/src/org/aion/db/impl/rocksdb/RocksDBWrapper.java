package org.aion.db.impl.rocksdb;

import static java.lang.Math.max;
import static org.aion.db.impl.rocksdb.RocksDBConstants.BLOCK_SIZE;
import static org.aion.db.impl.rocksdb.RocksDBConstants.BLOOMFILTER_BITS_PER_KEY;
import static org.aion.db.impl.rocksdb.RocksDBConstants.BYTES_PER_SYNC;
import static org.aion.db.impl.rocksdb.RocksDBConstants.LEVEL0_SLOWDOWN_WRITES_TRIGGER;
import static org.aion.db.impl.rocksdb.RocksDBConstants.LEVEL0_STOP_WRITES_TRIGGER;
import static org.aion.db.impl.rocksdb.RocksDBConstants.MAX_BACKGROUND_COMPACTIONS;
import static org.aion.db.impl.rocksdb.RocksDBConstants.MAX_BACKGROUND_FLUSHES;
import static org.aion.db.impl.rocksdb.RocksDBConstants.MIN_WRITE_BUFFER_NUMBER_TOMERGE;
import static org.aion.db.impl.rocksdb.RocksDBConstants.OPTIMIZE_LEVEL_STYLE_COMPACTION;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import org.aion.db.impl.AbstractDB;
import org.aion.util.types.ByteArrayWrapper;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.CompactionPriority;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;

public class RocksDBWrapper extends AbstractDB {

    private RocksDB db;
    private final int maxOpenFiles;
    private final int blockSize;
    private final int writeBufferSize;
    private final int readBufferSize;
    private final int cacheSize;
    private WriteOptions writeOptions;

    public RocksDBWrapper(
            String name,
            String path,
            Logger log,
            boolean enableDbCache,
            boolean enableDbCompression,
            int maxOpenFiles,
            int blockSize,
            int writeBufferSize,
            int readBufferSize,
            int cacheSize) {
        super(name, path, log, enableDbCache, enableDbCompression);

        this.maxOpenFiles = maxOpenFiles;
        this.blockSize = blockSize;
        this.writeBufferSize = writeBufferSize;
        this.readBufferSize = readBufferSize;
        this.cacheSize = cacheSize;

        LOG.info("RocksDb Options: EnableCompression:{} XaxOpenFiles:{} BlockSize:{} WriteBuffer:{} ReadBuffer:{} EnableCache:{} CacheSize:{}"
            , enableDbCompression, maxOpenFiles, blockSize, writeBufferSize, readBufferSize, enableDbCache, cacheSize);

        RocksDB.loadLibrary();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + propertiesInfo();
    }

    private Options setupRocksDbOptions() {
        Options options = new Options();

        options.setCreateIfMissing(true);
        options.setUseFsync(false);
        options.setCompressionType(
                enableDbCompression
                        ? CompressionType.LZ4_COMPRESSION
                        : CompressionType.NO_COMPRESSION);

        options.setBottommostCompressionType(CompressionType.ZLIB_COMPRESSION);
        options.setMinWriteBufferNumberToMerge(MIN_WRITE_BUFFER_NUMBER_TOMERGE);
        options.setLevel0StopWritesTrigger(LEVEL0_STOP_WRITES_TRIGGER);
        options.setLevel0SlowdownWritesTrigger(LEVEL0_SLOWDOWN_WRITES_TRIGGER);
        options.setAtomicFlush(true);
        options.setWriteBufferSize(this.writeBufferSize);
        options.setRandomAccessMaxBufferSize(this.readBufferSize);
        options.setParanoidChecks(true);
        options.setMaxOpenFiles(this.maxOpenFiles);
        options.setTableFormatConfig(setupBlockBasedTableConfig());
        options.setDisableAutoCompactions(false);
        options.setIncreaseParallelism(max(1, Runtime.getRuntime().availableProcessors() / 2));

        options.setLevelCompactionDynamicLevelBytes(true);
        options.setMaxBackgroundCompactions(MAX_BACKGROUND_COMPACTIONS);
        options.setMaxBackgroundFlushes(MAX_BACKGROUND_FLUSHES);
        options.setBytesPerSync(BYTES_PER_SYNC);
        options.setCompactionPriority(CompactionPriority.MinOverlappingRatio);
        options.optimizeLevelStyleCompaction(OPTIMIZE_LEVEL_STYLE_COMPACTION);

        return options;
    }

    private BlockBasedTableConfig setupBlockBasedTableConfig() {
        BlockBasedTableConfig bbtc = new BlockBasedTableConfig();
        bbtc.setBlockSize(BLOCK_SIZE);
        bbtc.setCacheIndexAndFilterBlocks(true);
        bbtc.setPinL0FilterAndIndexBlocksInCache(true);
        bbtc.setFilterPolicy(new BloomFilter(BLOOMFILTER_BITS_PER_KEY, false));
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

        writeOptions = setupWriteOptions();

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

    private WriteOptions setupWriteOptions() {
        WriteOptions options = new WriteOptions();
        options.setLowPri(true);
        return options;
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
    public Iterator<byte[]> keys() {
        check();

        try {
            ReadOptions readOptions = new ReadOptions();
            readOptions.setSnapshot(db.getSnapshot());
            return new RocksDBIteratorWrapper(readOptions, db.newIterator(readOptions));
        } catch (Exception e) {
            LOG.error("Unable to extract keys from database " + this.toString() + ".", e);
        }

        // empty when retrieval failed
        return Collections.emptyIterator();
    }

    /**
     * A wrapper for the {@link RocksIterator} conforming to the {@link Iterator} interface.
     *
     * @author Alexandra Roatis
     */
    private static class RocksDBIteratorWrapper implements Iterator<byte[]> {
        private final RocksIterator iterator;
        private final ReadOptions readOptions;
        private boolean closed;

        /**
         * @implNote Building two wrappers for the same {@link RocksIterator} will lead to
         *     inconsistent behavior.
         */
        RocksDBIteratorWrapper(final ReadOptions readOptions, final RocksIterator iterator) {
            this.readOptions = readOptions;
            this.iterator = iterator;
            iterator.seekToFirst();
            closed = false;
        }

        @Override
        public boolean hasNext() {
            if (!closed) {
                boolean isValid = iterator.isValid();

                // close iterator after last entry
                if (!isValid) {
                    iterator.close();
                    readOptions.close();
                    closed = true;
                }

                return isValid;
            } else {
                return false;
            }
        }

        @Override
        public byte[] next() {
            iterator.next();
            return iterator.key();
        }
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
                db.write(writeOptions, batch);
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
            db.write(writeOptions, batch);
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
            db.write(writeOptions, batch);
        } catch (RocksDBException e) {
            LOG.error("Unable to execute batch delete operation on " + this.toString() + ".", e);
        }
    }
}

package org.aion.db.impl.rocksdb;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;
import org.rocksdb.*;

import java.io.File;
import java.util.*;

public class RocksWrapperDB extends AbstractDB {

    private RocksDB db;
    private final int maxOpenFiles;
    private final int blockSize;
    private final int writeBufferSize;
    //private final int cacheSize;

    protected RocksWrapperDB(String name,
                             String path,
                             boolean enableDbCache,
                             boolean enableDbCompression,
                             int maxOpenFiles,
                             int blockSize,
                             int writeBufferSize) {
                             //int cacheSize) {
        super(name, path, enableDbCache, enableDbCompression);

        this.maxOpenFiles = maxOpenFiles;
        this.blockSize = blockSize;
        this.writeBufferSize = writeBufferSize;
        //this.cacheSize = cacheSize;

        RocksDB.loadLibrary();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + propertiesInfo();
    }

    private Options setupRocksDbOptions() {
        Options options = new Options();

        options.setCreateIfMissing(true);
        options.setCompressionType(enableDbCompression ? CompressionType.SNAPPY_COMPRESSION : CompressionType.NO_COMPRESSION);

        /* @Todo (?): The size of one block in arena memory allocation. If <= 0, a proper value is automatically calculated (usually 1/10 of writer_buffer_size). There are two additonal restriction of the The specified size: (1) size should be in the range of [4096, 2 << 30] and (2) be the multiple of the CPU word (which helps with the memory alignment). We'll automatically check and adjust the size number to make sure it conforms to the restrictions. Default: 0 @return the size of an arena block */
        options.setArenaBlockSize(this.blockSize);
        options.setWriteBufferSize(this.writeBufferSize);
        //options.setCacheSize(); @todo: do we have something like this
        options.setParanoidChecks(true);
        // options.setVerifyChecksums() @todo:
        options.setMaxOpenFiles(this.maxOpenFiles);

        return options;
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
            LOG.error("Failed to open the database " + this.toString() + " due to: ", e);

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
            db.compactRange(new byte[] { (byte) 0x00 }, new byte[] { (byte) 0xff });
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
    protected byte[] getInternal(byte[] k) {
        try {
            return db.get(k);
        } catch (RocksDBException e) {
            LOG.error("Unable to get key " + Arrays.toString(k) + ". " + e);
        }

        return null;
    }

    // AbstractDB functionality

    @Override
    public void put(byte[] k, byte[] v) {
        check(k);

        check();

        try {
            if (v == null) {
                db.delete(k);
            } else {
                db.put(k, v);
            }
        } catch (RocksDBException e) {
            LOG.error("Unable to put / delete key " + Arrays.toString(k) + ". " + e);
        }
    }

    @Override
    public void delete(byte[] k) {
        check(k);

        check();
        try {
            db.delete(k);
        } catch (RocksDBException e) {
            LOG.error("Unable to delete key " + Arrays.toString(k) + ". " + e);
        }
    }

    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        check(inputMap.keySet());

        check();

        // try-with-resources will automatically close the batch object
        try (WriteBatch batch = new WriteBatch()) {
            WriteOptions writeOptions = new WriteOptions();
            // @todo: add writeOptions

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
            db.write(writeOptions, batch);
        } catch (RocksDBException e) {
            LOG.error("Unable to execute batch put/update operation on " + this.toString() + ".", e);
        }
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {

    }

    @Override
    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        boolean success = false;

        check();

        // try-with-resources will automatically close to batch object
        WriteBatch batch = new WriteBatch();
        WriteOptions writeOptions = new WriteOptions();
        // @todo: writeOptions


        try {
            for (Map.Entry<ByteArrayWrapper, byte[]> e : cache.entrySet()) {
                if (e.getValue() == null) {
                    batch.delete(e.getKey().getData());
                } else {
                    batch.put(e.getKey().getData(), e.getValue());
                }
            }

            // bulk automatic update
            db.write(writeOptions, batch);

            success = true;
        } catch (RocksDBException e) {
            LOG.error("Unable to commit heap cache to " + this.toString() + ".", e);
        }

        return success;
    }
}

package org.aion.db.impl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.PersistenceMethod;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * Common functionality for database implementations.
 *
 * @author Alexandra Roatis
 * @implNote Assumes persistent database. Overwrite method if this is not the case.
 */
public abstract class AbstractDB implements IByteArrayKeyValueDatabase {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    protected static final int DEFAULT_CACHE_SIZE_BYTES = 128 * 1024 * 1024; // 128mb
    protected static final int DEFAULT_WRITE_BUFFER_SIZE_BYTES = 16 * 1024 * 1024; // 16mb

    protected final String name;
    protected String path = null;
    protected boolean enableDbCache = false;
    protected boolean enableDbCompression = false;

    protected AbstractDB(String name) {
        Objects.requireNonNull(name, "The database name cannot be null.");
        this.name = name;
    }

    protected AbstractDB(
            String name, String path, boolean enableDbCache, boolean enableDbCompression) {
        this(name);

        Objects.requireNonNull(path, "The database path cannot be null.");
        this.path = new File(path, name).getAbsolutePath();

        this.enableDbCache = enableDbCache;
        this.enableDbCompression = enableDbCompression;
    }

    protected String propertiesInfo() {
        return "<name="
                + name
                + ",autocommit=ON,cache="
                + (enableDbCache ? "ON" : "OFF")
                + //
                ",compression="
                + (enableDbCompression ? "ON" : "OFF")
                + ">"; //
    }

    @Override
    public boolean commit() {
        // not implemented since we always commit the changes to the database for this
        // implementation
        throw new UnsupportedOperationException(
                "Only automatic commits are supported by " + this.toString());
    }

    @Override
    public void compact() {
        LOG.warn("Compact not supported by " + this.toString() + ".");
    }

    @Override
    public void drop() {
        boolean wasOpen = isOpen();
        close();

        try (Stream<Path> stream = Files.walk(new File(path).toPath())) {
            stream.map(Path::toFile).forEach(File::delete);
        } catch (Exception e) {
            LOG.error("Unable to delete path due to: ", e);
        }

        if (wasOpen) {
            open();
        }
    }

    @Override
    public Optional<String> getName() {
        return Optional.ofNullable(this.name);
    }

    @Override
    public Optional<String> getPath() {
        return Optional.ofNullable(this.path);
    }

    @Override
    public void check() {
        if (!isOpen()) {
            throw new RuntimeException("Database is not opened: " + this);
        }
    }

    /**
     * Checks that the given key is not null. Throws a {@link IllegalArgumentException} if the key
     * is null.
     */
    public static void check(byte[] keyOrValue) {
        if (keyOrValue == null) {
            throw new IllegalArgumentException("The database does not accept null keys or values.");
        }
    }

    /**
     * Checks that the given collection of keys does not contain null values. Throws a {@link
     * IllegalArgumentException} if a null key is present.
     */
    public static void check(Collection<byte[]> keysOrValues) {
        if (keysOrValues.contains(null)) {
            throw new IllegalArgumentException("The database does not accept null keys or values.");
        }
    }

    @Override
    public boolean isClosed() {
        return !isOpen();
    }

    @Override
    public boolean isAutoCommitEnabled() {
        // autocommit is always enabled when not overwritten by the class
        return true;
    }

    @Override
    public PersistenceMethod getPersistenceMethod() {
        // Default to file-based since most of our dbs are that
        return PersistenceMethod.FILE_BASED;
    }

    /**
     * For testing the lock functionality of public methods. Helps ensure that locks are released
     * after normal or exceptional execution.
     *
     * @return {@code true} when the resource is locked, {@code false} otherwise
     */
    @Override
    public boolean isLocked() {
        return false;
    }

    /** Functionality for directly interacting with the heap cache. */
    public abstract boolean commitCache(Map<ByteArrayWrapper, byte[]> cache);

    @Override
    public Optional<byte[]> get(byte[] key) {
        check(key);
        check();

        byte[] v = getInternal(key);
        return Optional.ofNullable(v);
    }

    /**
     * Database specific get functionality, without locking or integrity checks required. Locking
     * and checks are applied in {@link #get(byte[])}.
     *
     * @param key the key for which the method must return the associated value
     * @return the value stored in the database for the give key
     */
    protected abstract byte[] getInternal(byte[] key);

    @Override
    public void put(byte[] key, byte[] value) {
        check(key);
        check(value);
        check();

        putInternal(key, value);
    }

    /**
     * Database specific put functionality, without locking or integrity checks required. Locking
     * and checks are applied in {@link #put(byte[], byte[])}.
     *
     * @param key the key for the new entry
     * @param value the value for the new entry
     */
    protected abstract void putInternal(byte[] key, byte[] value);

    @Override
    public void delete(byte[] key) {
        check(key);
        check();

        deleteInternal(key);
    }

    /**
     * Database specific delete functionality, without locking or integrity checks required. Locking
     * and checks are applied in {@link #delete(byte[])}.
     *
     * @param key the key for the new entry
     */
    protected abstract void deleteInternal(byte[] key);

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        check(key);
        check(value);
        check();

        putToBatchInternal(key, value);
    }

    /**
     * Database specific put to batch functionality, without locking or integrity checks required.
     * Locking and checks are applied in {@link #putToBatch(byte[], byte[])}.
     *
     * @param key the key for the new entry
     * @param value the value for the new entry
     */
    protected abstract void putToBatchInternal(byte[] key, byte[] value);

    @Override
    public void deleteInBatch(byte[] key) {
        check(key);
        check();

        deleteInBatchInternal(key);
    }

    /**
     * Database specific delete in batch functionality, without locking or integrity checks
     * required. Locking and checks are applied in {@link #deleteInBatch(byte[])}.
     *
     * @param key the key for the new entry
     */
    protected abstract void deleteInBatchInternal(byte[] key);

    @Override
    public void putBatch(Map<byte[], byte[]> input) {
        check(input.keySet());
        check(input.values());
        check();

        putBatchInternal(input);
    }

    /**
     * Database specific put batch functionality, without locking or integrity checks required.
     * Locking and checks are applied in {@link #putBatch(Map)}.
     *
     * @param input a {@link Map} of key-value pairs to be updated in the database
     */
    public abstract void putBatchInternal(Map<byte[], byte[]> input);

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        check(keys);
        check();

        deleteBatchInternal(keys);
    }

    /**
     * Database specific delete batch functionality, without locking or integrity checks required.
     * Locking and checks are applied in {@link #deleteBatch(Collection)}.
     *
     * @param keys a {@link Collection} of keys to be deleted form storage
     */
    protected abstract void deleteBatchInternal(Collection<byte[]> keys);
}

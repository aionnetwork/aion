package org.aion.db.generic;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.aion.interfaces.db.PersistenceMethod;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.util.conversions.Hex;
import org.slf4j.Logger;

/**
 * Times different database operations and logs the time.
 *
 * @author Alexandra Roatis
 */
public class TimedDatabase implements ByteArrayKeyValueDatabase {

    /** Unlocked database. */
    protected final ByteArrayKeyValueDatabase database;

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    public TimedDatabase(ByteArrayKeyValueDatabase _database) {
        this.database = _database;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " over " + database.toString();
    }

    // IDatabase functionality
    // -----------------------------------------------------------------------------------------

    @Override
    public boolean open() {
        long t1 = System.nanoTime();
        boolean open = database.open();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " open() in " + (t2 - t1) + " ns.");
        return open;
    }

    @Override
    public void close() {
        long t1 = System.nanoTime();
        database.close();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " close() in " + (t2 - t1) + " ns.");
    }

    @Override
    public boolean commit() {
        long t1 = System.nanoTime();
        boolean cmt = database.commit();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " commit() in " + (t2 - t1) + " ns.");
        return cmt;
    }

    @Override
    public void compact() {
        long t1 = System.nanoTime();
        database.compact();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " compact() in " + (t2 - t1) + " ns.");
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
        long t1 = System.nanoTime();
        boolean open = database.isOpen();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " isOpen() in " + (t2 - t1) + " ns.");
        return open;
    }

    @Override
    public boolean isClosed() {
        // isOpen also handles locking
        return !isOpen();
    }

    @Override
    public boolean isLocked() {
        return false;
    }

    @Override
    public boolean isAutoCommitEnabled() {
        // no locks because the autocommit flag never changes
        return database.isAutoCommitEnabled();
    }

    @Override
    public PersistenceMethod getPersistenceMethod() {
        // no locks because the persistence flag never changes
        return database.getPersistenceMethod();
    }

    @Override
    public boolean isCreatedOnDisk() {
        long t1 = System.nanoTime();
        boolean result = database.isCreatedOnDisk();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " isCreatedOnDisk() in " + (t2 - t1) + " ns.");
        return result;
    }

    @Override
    public long approximateSize() {
        long t1 = System.nanoTime();
        long result = database.approximateSize();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " approximateSize() in " + (t2 - t1) + " ns.");
        return result;
    }

    // IKeyValueStore functionality
    // ------------------------------------------------------------------------------------

    @Override
    public boolean isEmpty() {
        long t1 = System.nanoTime();
        boolean result = database.isEmpty();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " isEmpty() in " + (t2 - t1) + " ns.");
        return result;
    }

    @Override
    public Iterator<byte[]> keys() {
        long t1 = System.nanoTime();
        Iterator<byte[]> result = database.keys();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " keys() in " + (t2 - t1) + " ns.");
        return result;
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        long t1 = System.nanoTime();
        Optional<byte[]> value = database.get(key);
        long t2 = System.nanoTime();

        LOG.debug(
                database.toString()
                        + " get(key) in "
                        + (t2 - t1)
                        + " ns."
                        + "\n\t\t\t\t\tkey = "
                        + (key != null ? Hex.toHexString(key) : "null")
                        + "\n\t\t\t\t\treturned value = "
                        + (value.isPresent() ? Hex.toHexString(value.get()) : "null"));
        return value;
    }

    @Override
    public void put(byte[] key, byte[] value) {
        long t1 = System.nanoTime();
        database.put(key, value);
        long t2 = System.nanoTime();

        LOG.debug(
                database.toString()
                        + " put(key,value) in "
                        + (t2 - t1)
                        + " ns."
                        + "\n\t\t\t\t\tkey = "
                        + (key != null ? Hex.toHexString(key) : "null")
                        + "\n\t\t\t\t\tvalue = "
                        + (value != null ? Hex.toHexString(value) : "null"));
    }

    @Override
    public void delete(byte[] key) {
        long t1 = System.nanoTime();
        database.delete(key);
        long t2 = System.nanoTime();

        LOG.debug(
                database.toString()
                        + " delete(key) in "
                        + (t2 - t1)
                        + " ns."
                        + "\n\t\t\t\t\tkey = "
                        + (key != null ? Hex.toHexString(key) : "null"));
    }

    @Override
    public void putBatch(Map<byte[], byte[]> keyValuePairs) {
        long t1 = System.nanoTime();
        database.putBatch(keyValuePairs);
        long t2 = System.nanoTime();

        LOG.debug(
                database.toString()
                        + " putBatch("
                        + (keyValuePairs != null ? keyValuePairs.size() : "null")
                        + ") in "
                        + (t2 - t1)
                        + " ns.");
    }

    @Override
    public void putToBatch(byte[] key, byte[] value) {
        long t1 = System.nanoTime();
        database.putToBatch(key, value);
        long t2 = System.nanoTime();

        LOG.debug(
                database.toString()
                        + " putToBatch(key,value) in "
                        + (t2 - t1)
                        + " ns."
                        + "\n\t\t\t\t\tkey = "
                        + Hex.toHexString(key)
                        + "\n\t\t\t\t\tvalue = "
                        + (value != null ? Hex.toHexString(value) : "null"));
    }

    @Override
    public void deleteInBatch(byte[] key) {
        long t1 = System.nanoTime();
        database.deleteInBatch(key);
        long t2 = System.nanoTime();

        LOG.debug(
                database.toString()
                        + " deleteInBatch(key) in "
                        + (t2 - t1)
                        + " ns."
                        + "\n\t\t\t\t\tkey = "
                        + Hex.toHexString(key));
    }

    @Override
    public void commitBatch() {
        long t1 = System.nanoTime();
        database.commitBatch();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " commitBatch() in " + (t2 - t1) + " ns.");
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        long t1 = System.nanoTime();
        database.deleteBatch(keys);
        long t2 = System.nanoTime();

        LOG.debug(
                database.toString()
                        + " deleteBatch("
                        + (keys != null ? keys.size() : "null")
                        + ") in "
                        + (t2 - t1)
                        + " ns.");
    }

    @Override
    public void check() {
        long t1 = System.nanoTime();
        database.check();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " check() in " + (t2 - t1) + " ns.");
    }

    @Override
    public void drop() {
        long t1 = System.nanoTime();
        database.drop();
        long t2 = System.nanoTime();

        LOG.debug(database.toString() + " drop() in " + (t2 - t1) + " ns.");
    }
}

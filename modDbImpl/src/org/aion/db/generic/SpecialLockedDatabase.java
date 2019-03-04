package org.aion.db.generic;

import java.util.Collection;
import java.util.Map;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;

/**
 * Implements locking functionality for a database that is mostly thread-safe except for open and
 * close (like LevelDB).
 *
 * @author Alexandra Roatis
 */
public class SpecialLockedDatabase extends LockedDatabase implements ByteArrayKeyValueDatabase {

    public SpecialLockedDatabase(ByteArrayKeyValueDatabase _unlockedDatabase) {
        super(_unlockedDatabase);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        // acquire write lock
        lock.readLock().lock();

        try {
            database.put(key, value);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not put key-value pair due to ", e);
            }
        } finally {
            // releasing write lock
            lock.readLock().unlock();
        }
    }

    @Override
    public void delete(byte[] key) {
        // acquire write lock
        lock.readLock().lock();

        try {
            database.delete(key);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not delete key due to ", e);
            }
        } finally {
            // releasing write lock
            lock.readLock().unlock();
        }
    }

    @Override
    public void putBatch(Map<byte[], byte[]> keyValuePairs) {
        // acquire write lock
        lock.readLock().lock();

        try {
            database.putBatch(keyValuePairs);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not put batch due to ", e);
            }
        } finally {
            // releasing write lock
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        // acquire write lock
        lock.readLock().lock();

        try {
            database.deleteBatch(keys);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                LOG.error("Could not delete batch due to ", e);
            }
        } finally {
            // releasing write lock
            lock.readLock().unlock();
        }
    }
}

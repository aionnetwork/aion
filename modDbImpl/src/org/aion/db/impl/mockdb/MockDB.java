package org.aion.db.impl.mockdb;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;

import java.util.*;

public class MockDB extends AbstractDB {

    private Map<ByteArrayWrapper, byte[]> kv;

    public MockDB(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":<name=" + name + ">";
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

        LOG.debug("init database {}", this.toString());

        // using a regular map since synchronization is handled through the read-write lock
        kv = new HashMap<>();

        // releasing write lock
        lock.writeLock().unlock();

        return isOpen();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void close() {
        // acquire write lock
        lock.writeLock().lock();

        // release resources if needed
        if (kv != null) {kv.clear();}

        // set map to null
        kv = null;

        // releasing write lock
        lock.writeLock().unlock();
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isOpen() {
        // acquire read lock
        lock.readLock().lock();

        boolean open = kv != null;

        // releasing read lock
        lock.readLock().unlock();

        return open;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isPersistent() {
        return false;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isCreatedOnDisk() {
        return false;
    }

    /**
     * @inheritDoc
     */
    @Override
    public long approximateSize() {
        // acquire read lock
        lock.readLock().lock();

        try {
            check();
        } finally {
            // releasing read lock
            lock.readLock().unlock();
        }

        return -1L;
    }

    // IKeyValueStore functionality ------------------------------------------------------------------------------------

    /**
     * @inheritDoc
     */
    @Override
    public boolean isEmpty() {
        // acquire read lock
        lock.readLock().lock();

        boolean status;

        try {
            check();

            status = kv.isEmpty();
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

            kv.keySet().forEach(k -> set.add(k.getData()));
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
    public byte[] getInternal(byte[] k) {
        return kv.get(ByteArrayWrapper.wrap(k));
    }

    /**
     * @inheritDoc
     */
    @Override
    public void put(byte[] k, byte[] v) {
        check(k);

        // acquire write lock
        lock.writeLock().lock();

        try {
            check();

            if (v == null) {
                kv.remove(ByteArrayWrapper.wrap(k));
            } else {
                kv.put(ByteArrayWrapper.wrap(k), v);
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void delete(byte[] k) {
        check(k);

        // acquire write lock
        lock.writeLock().lock();

        try {
            check();

            kv.remove(ByteArrayWrapper.wrap(k));
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void putBatch(Map<byte[], byte[]> inputMap) {
        check(inputMap.keySet());

        // acquire write lock
        lock.writeLock().lock();

        try {
            // this runtime exception should not be caught here
            check();

            try {
                // simply do a put, because setting a kv pair to null is same as delete
                inputMap.forEach((key, value) -> {
                    if (value == null) {
                        kv.remove(ByteArrayWrapper.wrap(key));
                    } else {
                        kv.put(ByteArrayWrapper.wrap(key), value);
                    }
                });
            } catch (Exception e) {
                LOG.error("Unable to execute batch put/update operation on " + this.toString() + ".", e);
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void deleteBatch(Collection<byte[]> keys) {
        check(keys);

        // acquire write lock
        lock.writeLock().lock();

        try {
            // this runtime exception should not be caught here
            check();

            try {
                keys.forEach((e) -> kv.remove(ByteArrayWrapper.wrap(e)));
            } catch (Exception e) {
                LOG.error("Unable to execute batch delete operation on " + this.toString() + ".", e);
            }
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }
    }

    // AbstractDB functionality ----------------------------------------------------------------------------------------

    public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
        // acquire write lock
        lock.writeLock().lock();

        boolean success = false;

        try {
            check();

            // simply do a put, because setting a kv pair to null is same as delete
            cache.forEach((key, value) -> {
                if (value == null) {
                    kv.remove(key);
                } else {
                    kv.put(key, value);
                }
            });

            success = true;
        } catch (Exception e) {
            LOG.error("Unable to commit heap cache to " + this.toString() + ".", e);
        } finally {
            // releasing write lock
            lock.writeLock().unlock();
        }

        return success;
    }
}







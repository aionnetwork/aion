package org.aion.db.store;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Adds locking on top of a {@link ObjectStore} to make it thread-safe.
 *
 * @param <V> The type of objects that the {@link ObjectStore} holds.
 * @author Alexandra Roatis
 */
class LockedObjectStore<V> implements ObjectStore<V> {
    private ObjectStore<V> source;
    private Lock lock = new ReentrantLock();

    LockedObjectStore(ObjectStore<V> source) {
        this.source = source;
    }

    @Override
    public void put(byte[] key, V value) {
        lock.lock();

        try {
            source.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(byte[] key) {
        lock.lock();

        try {
            source.delete(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void commit() {
        lock.lock();

        try {
            source.commit();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V get(byte[] key) {
        lock.lock();

        try {
            return source.get(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isOpen() {
        lock.lock();

        try {
            return source.isOpen();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.lock();

        try {
            source.close();
        } finally {
            lock.unlock();
        }
    }
}

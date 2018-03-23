/*******************************************************************************
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
 *
 * Contributors:
 *     Aion foundation.
 *
 ******************************************************************************/
package org.aion.mcf.ds;

import java.util.Optional;

import org.aion.base.db.Flushable;
import org.aion.base.db.IByteArrayKeyValueDatabase;

/**
 * Object Datasource.
 *
 * @param <V>
 */
public class ObjectDataSource<V> implements Flushable {

    private IByteArrayKeyValueDatabase src;
    Serializer<V, byte[]> serializer;
    boolean cacheOnWrite = true;

    public ObjectDataSource(IByteArrayKeyValueDatabase src, Serializer<V, byte[]> serializer) {
        this.src = src;
        this.serializer = serializer;
    }

    public ObjectDataSource<V> withWriteThrough(boolean writeThrough) {
        if (!writeThrough) {
            throw new RuntimeException("Not implemented yet");
        }
        return this;
    }

    public ObjectDataSource<V> withCacheOnWrite(boolean cacheOnWrite) {
        this.cacheOnWrite = cacheOnWrite;
        return this;
    }

    public void flush() {
        // for write-back type cache only
        if (!this.src.isAutoCommitEnabled()) {
            this.src.commit();
        }
    }

    public void put(byte[] key, V value) {
        byte[] bytes = serializer.serialize(value);
        /*
         * src.put(key, bytes); if (cacheOnWrite) { cache.put(new
         * ByteArrayWrapper(key), value); }
         */
        // TODO @yao - Don't know if just writing to cache is correct logic
        // or what this was intended to be. Why do a flush then?
        src.put(key, bytes);
    }

    public void delete(byte[] key) {
        src.delete(key);
    }

    public V get(byte[] key) {

        // Fetch the results from cache or database. Return null if doesn't
        // exist.
        Optional<byte[]> val = src.get(key);
        return val.map(serializer::deserialize).orElse(null);
    }

    /**
     * Returns the underlying cache source.
     *
     * @return
     */
    protected IByteArrayKeyValueDatabase getSrc() {
        return src;
    }

    public void close() {
        src.close();
    }
}

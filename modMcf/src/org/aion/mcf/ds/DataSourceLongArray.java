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
 ******************************************************************************/
package org.aion.mcf.ds;

import org.aion.base.db.Flushable;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;

/**
 * DataSource LongArray.
 *
 * @param <V>
 */
public class DataSourceLongArray<V> implements Flushable {

    private ObjectDataSource<V> src;
    private static final byte[] sizeKey = ByteUtil.longToBytes(-2L);
    private long size = -1;

    public DataSourceLongArray(ObjectDataSource<V> src) {
        this.src = src;
    }

    @Override
    public void flush() {
        src.flush();
    }

    public V set(long idx, V value) {
        if (idx >= size()) {
            setSize(idx + 1);
        }
        src.put(ByteUtil.longToBytes(idx), value);
        return value;
    }

    public void add(long index, V element) {
        set(index, element);
    }

    public void remove(long index) {
        src.delete(ByteUtil.longToBytes(index));
        if (index < size()) {
            setSize(index);
        }
    }

    public V get(long idx) {
        if (idx < 0 || idx >= size()) {
            throw new IndexOutOfBoundsException(idx + " > " + size);
        }
        return src.get(ByteUtil.longToBytes(idx));
    }

    public long size() {

        if (size < 0) {
            // Read the value from the database directly and
            // convert to the size, and if it doesn't exist, 0.
            size = src.getSrc().get(sizeKey).map(ByteUtil::byteArrayToLong).orElse(0L);
        }

        return size;
    }

    private synchronized void setSize(long newSize) {
        size = newSize;
        src.getSrc().put(sizeKey, ByteUtil.longToBytes(newSize));
    }

}

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

import java.util.Optional;

/**
 * DataSource Array.
 *
 * @param <V>
 */
public class DataSourceArray<V> implements Flushable {

    private final ObjectDataSource<V> src;
    private static final byte[] sizeKey = Hex.decode("FFFFFFFFFFFFFFFF");
    private long size = -1L;

    public DataSourceArray(ObjectDataSource<V> src) {
        this.src = src;
    }

    @Override
    public void flush() {
        src.flush();
    }

    public V set(long index, V value) {
        if (index <= Integer.MAX_VALUE) {
            src.put(ByteUtil.intToBytes((int) index), value);
        } else {
            src.put(ByteUtil.longToBytes(index), value);
        }
        if (index >= size()) {
            setSize(index + 1);
        }
        return value;
    }

    public void remove(long index) {
        // without this check it will remove the sizeKey
        if (index < 0 || index >= size()) {
            return;
        }

        if (index <= Integer.MAX_VALUE) {
            src.delete(ByteUtil.intToBytes((int) index));
        } else {
            src.delete(ByteUtil.longToBytes(index));
        }
        if (index < size()) {
            setSize(index);
        }
    }

    public V get(long index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException(
                    "Incorrect index value <" + index + ">. Allowed values are >= 0 and < " + size + ".");
        }

        V value;

        if (index <= Integer.MAX_VALUE) {
            value = src.get(ByteUtil.intToBytes((int) index));
        } else {
            value = src.get(ByteUtil.longToBytes(index));
        }
        return value;
    }

    public long size() {

        if (size < 0) {
            // Read the value from the database directly and
            // convert to the size, and if it doesn't exist, 0.
            Optional<byte[]> optBytes = src.getSrc().get(sizeKey);
            if (!optBytes.isPresent()) {
                size = 0L;
            } else {
                byte[] bytes = optBytes.get();

                if (bytes.length == 4) {
                    size = (long) ByteUtil.byteArrayToInt(bytes);
                } else {
                    size = ByteUtil.byteArrayToLong(bytes);
                }
            }
        }

        return size;
    }

    private synchronized void setSize(long newSize) {
        size = newSize;
        if (size <= Integer.MAX_VALUE) {
            src.getSrc().put(sizeKey, ByteUtil.intToBytes((int) newSize));
        } else {
            src.getSrc().put(sizeKey, ByteUtil.longToBytes(newSize));
        }
    }
}

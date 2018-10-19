/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>Contributors: Aion foundation.
 *
 * <p>****************************************************************************
 */
package org.aion.txpool.common;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.base.util.ByteArrayWrapper;

public class AccountState {
    private final SortedMap<BigInteger, AbstractMap.SimpleEntry<ByteArrayWrapper, BigInteger>>
            txMap = Collections.synchronizedSortedMap(new TreeMap<>());
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public void updateMap(
            Map<BigInteger, AbstractMap.SimpleEntry<ByteArrayWrapper, BigInteger>> map) {
        if (map != null && !map.isEmpty()) {
            txMap.putAll(map);
            setDirty();
        }
    }

    public void setDirty() {
        dirty.set(true);
    }

    public SortedMap<BigInteger, AbstractMap.SimpleEntry<ByteArrayWrapper, BigInteger>> getMap() {
        return txMap;
    }

    public void sorted() {
        dirty.set(false);
    }

    public BigInteger getFirstNonce() {
        return txMap.isEmpty() ? null : txMap.firstKey();
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public boolean isEmpty() {
        return txMap.isEmpty();
    }
}

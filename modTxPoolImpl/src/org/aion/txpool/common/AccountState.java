package org.aion.txpool.common;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.vm.api.types.ByteArrayWrapper;

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

package org.aion.db.generic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.aion.base.util.ByteArrayWrapper;

/**
 * A wrapper for the iterator needed by {@link DatabaseWithCache} conforming to the {@link
 * Iterator<byte[]>} interface.
 *
 * @implNote Assumes that the given database iterator does not return duplicate values.
 * @author Alexandra Roatis
 */
public class CacheIteratorWrapper implements Iterator<byte[]> {
    Iterator<byte[]> itr;
    byte[] next;
    List<ByteArrayWrapper> additions;
    List<ByteArrayWrapper> removals;

    public CacheIteratorWrapper(Iterator<byte[]> itr, Map<ByteArrayWrapper, byte[]> dirtyEntries) {
        this.itr = itr;
        additions = new ArrayList<>();
        removals = new ArrayList<>();

        for (Map.Entry<ByteArrayWrapper, byte[]> entry : dirtyEntries.entrySet()) {
            if (entry.getValue() == null) {
                removals.add(entry.getKey());
            } else {
                additions.add(entry.getKey());
            }
        }
    }

    @Override
    public boolean hasNext() {
        boolean seek = true;
        ByteArrayWrapper wrapper;
        // check in the database iterator
        while (seek && itr.hasNext()) {
            next = itr.next();
            wrapper = ByteArrayWrapper.wrap(next);
            if (removals.contains(wrapper)) {
                // key deleted, move to next in iterator
                removals.remove(wrapper);
            } else if (additions.contains(wrapper)) {
                // found an entry that was updated
                seek = false;
                additions.remove(wrapper);
            } else {
                // found an entry that was not changed
                seek = false;
            }
        }

        // exhausted the initial iterator, trying the dirty entries
        // check in the cached entries
        if (seek && !additions.isEmpty()) {
            next = additions.remove(0).getData();
            seek = false;
        }

        return !seek;
    }

    @Override
    public byte[] next() {
        return next;
    }
}

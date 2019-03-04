package org.aion.db.generic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.aion.types.ByteArrayWrapper;

/**
 * A wrapper for the iterator needed by {@link DatabaseWithCache} conforming to the {@link Iterator}
 * interface.
 *
 * @implNote Assumes that the given database iterator does not return duplicate values.
 * @author Alexandra Roatis
 */
public class CacheIteratorWrapper implements Iterator<byte[]> {
    private final Iterator<byte[]> iterator;
    private byte[] next;
    private final List<ByteArrayWrapper> additions;
    private final List<ByteArrayWrapper> removals;

    /**
     * @implNote Building two wrappers for the same {@link Iterator} will lead to inconsistent
     *     behavior.
     */
    public CacheIteratorWrapper(
            final Iterator<byte[]> iterator, Map<ByteArrayWrapper, byte[]> dirtyEntries) {
        this.iterator = iterator;
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
        while (seek && iterator.hasNext()) {
            next = iterator.next();
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

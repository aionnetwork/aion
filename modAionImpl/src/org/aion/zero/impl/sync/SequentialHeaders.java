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
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl.sync;

import java.util.*;

import org.aion.mcf.types.AbstractBlockHeader;

/**
 * 
 * @author chris Grow-able sequential list of blocks headers
 * @inspector yao
 */

public final class SequentialHeaders<H extends AbstractBlockHeader> extends ArrayList<H> {

    private final static long serialVersionUID = 1L;

    private class SortByBlockNumber implements Comparator<H> {
        @Override
        public int compare(H h0, H h1) {
            return Long.compare(h0.getNumber(), h1.getNumber());
        }
    }

    private final Comparator<? super H> comparator = new SortByBlockNumber();

    /**
     * Adds a list of headers to the current list, this will try to add append
     * the list given that there are elements for which: <br>
     * {@code h[i] >= s[-1]} within the input collections.
     *
     * Given that there is a subset of input h such that the lowest member of h
     * (h_0) such that h_0 is adjacent to s[-1] (s[-1] + 1), and the subset is
     * defined such that all elements are uniquely adjacent, then the sequential
     * header will append such a set to itself sorted in ascending order.
     *
     * @param _headers
     */
    @Override
    public boolean addAll(final Collection<? extends H> _headers) {
        if (_headers != null && _headers.size() > 0) {
            final List<? extends H> l = new ArrayList<>(_headers);

            l.sort(comparator);
            int currentSize = this.size();

            int offset = 0;
            if (currentSize > 0) {
                long existLastBlockNumber = this.get(currentSize - 1).getNumber();

                // TODO: refactor this to use number + 1, simplifies logic
                int index = Collections.binarySearch(l, this.get(currentSize - 1), comparator);

                // case where our latest element is higher than the list
                if (index == l.size()) {
                    return false;
                }

                // did not found our element, but convert the positional index
                // back, to an index (this should give us the index of the next
                // largest
                // value)
                if (index < 0) {
                    index = Math.abs(index + 1);
                }

                /*
                 * we may end up in any position, so its important for us to
                 * ensure that we always find the earliest number + 1 element in
                 * the array. This means iterating through all of the same
                 * values
                 */
                offset = index;
                Iterator<? extends H> it = l.listIterator(index);
                boolean found = false;
                while (it.hasNext()) {
                    H h = it.next();
                    if (h.getNumber() == existLastBlockNumber + 1) {
                        found = true;
                        break;
                    }
                    offset++;
                }

                if (!found)
                    return false;
            }

            H firstHeader = l.get(offset);
            super.add(firstHeader);
            long highestNumber = firstHeader.getNumber();

            for (int i = offset + 1, m = l.size(); i < m; i++) {
                H currentHeader = l.get(i);
                if (currentHeader.getNumber() != highestNumber + 1) {
                    continue;
                }

                highestNumber = highestNumber + 1;
                super.add(currentHeader);
            }
            return true;
        }
        return false;
    }

    public boolean containsElement(final H h) {
        return (Collections.binarySearch(this, h, comparator) > -1);
    }

}
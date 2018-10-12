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
package org.aion.mcf.blockchain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.type.IBlock;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.types.AbstractBlockHeaderWrapper;

/**
 * Abstract SyncQueue Class
 */
public abstract class AbstractSyncQueue<BLK extends IBlock<?, ?>, BHW extends AbstractBlockHeaderWrapper<?>>
    implements ISyncQueue<BLK, BHW> {

    protected static int MAX_CHAIN_LEN = 192;
    protected Map<Long, Map<ByteArrayWrapper, HeaderElement<BLK, BHW>>> headers = new HashMap<>();
    protected long minNum = Integer.MAX_VALUE;
    protected long maxNum = 0;
    protected long darkZoneNum = 0;

    public HeaderElement<BLK, BHW> getParent(HeaderElement<?, ?> self) {
        long bn = self.header.getNumber();
        Map<ByteArrayWrapper, HeaderElement<BLK, BHW>> genHeaders = headers.get(bn - 1);
        if (genHeaders == null) {
            return null;
        }
        return genHeaders.get(new ByteArrayWrapper(self.header.getHeader().getParentHash()));
    }

    public List<HeaderElement<BLK, BHW>> getChildren(HeaderElement<?, ?> self) {
        List<HeaderElement<BLK, BHW>> ret = new ArrayList<>();
        long bn = self.header.getNumber();
        Map<ByteArrayWrapper, HeaderElement<BLK, BHW>> childGenHeaders = headers.get(bn + 1);
        if (childGenHeaders != null) {
            for (HeaderElement<?, ?> child : childGenHeaders.values()) {
                if (Arrays
                    .equals(child.header.getHeader().getParentHash(), self.header.getHash())) {
                    ret.add((AbstractSyncQueue<BLK, BHW>.HeaderElement<BLK, BHW>) child);
                }
            }
        }
        return ret;
    }

    protected HeaderElement<BLK, BHW> findHeaderElement(BLK blk) {
        Map<ByteArrayWrapper, HeaderElement<BLK, BHW>> genHeaders = headers.get(blk.getNumber());
        if (genHeaders == null) {
            return null;
        }
        return genHeaders.get(new ByteArrayWrapper(blk.getHash()));
    }

    /**
     * TODO: should not be called setBlock?
     */
    protected HeaderElement<BLK, BHW> addBlock(BLK block) {
        HeaderElement<BLK, BHW> headerElement = findHeaderElement(block);
        if (headerElement != null) {
            headerElement.block = block;
        }
        return headerElement;
    }

    protected List<BLK> exportBlocks() {
        List<BLK> ret = new ArrayList<>();
        for (long i = minNum; i <= maxNum; i++) {
            Map<ByteArrayWrapper, HeaderElement<BLK, BHW>> gen = headers.get(i);
            if (gen == null) {
                break;
            }

            boolean hasAny = false;
            for (HeaderElement<BLK, BHW> element : gen.values()) {
                HeaderElement<BLK, BHW> parent = getParent(element);
                if (element.block != null && (i == minNum || (parent != null && parent.exported))) {
                    // if (!element.exported) {
                    /**
                     * What is the purpose of exportNewBlock()?
                     *
                     * @TODO: Yao
                     */
                    // exportNewBlock(element.block);
                    ret.add(element.block);
                    element.exported = true;
                    // }
                    hasAny = true;
                }
            }
            if (!hasAny) {
                break;
            }
        }

        trimExported();
        return ret;
    }

    protected void trimExported() {
        for (; minNum < darkZoneNum; minNum++) {
            Map<ByteArrayWrapper, HeaderElement<BLK, BHW>> genHeaders = headers.get(minNum);
            assert genHeaders.size() == 1;
            HeaderElement<BLK, BHW> headerElement = genHeaders.values().iterator().next();
            if (headerElement.exported) {
                headers.remove(minNum);
            } else {
                break;
            }
        }
    }

    protected List<HeaderElement<BLK, BHW>> getLongestChain() {
        Map<ByteArrayWrapper, HeaderElement<BLK, BHW>> lastValidatedGen = headers.get(darkZoneNum);
        assert lastValidatedGen.size() == 1;
        return getLongestChain(lastValidatedGen.values().iterator().next());
    }

    protected List<HeaderElement<BLK, BHW>> getLongestChain(HeaderElement<?, ?> parent) {

        Map<ByteArrayWrapper, HeaderElement<BLK, BHW>> gen = headers
            .get(parent.header.getNumber() + 1);

        List<HeaderElement<BLK, BHW>> longest = null;// = new ArrayList<>();
        long lSize = 0;
        if (gen != null) {
            for (HeaderElement<BLK, BHW> header : gen.values()) {
                if (getParent(header) == parent) {
                    List<HeaderElement<BLK, BHW>> childLongest = getLongestChain(header);
                    if (childLongest.size() > lSize) {// longest.size()) {
                        lSize = childLongest.size();
                        longest = childLongest;
                    }
                }
            }
        }
        List<HeaderElement<BLK, BHW>> ret = new ArrayList<>();
        ret.add((AbstractSyncQueue<BLK, BHW>.HeaderElement<BLK, BHW>) parent);
        if (longest != null) {
            ret.addAll(longest);
        }
        return ret;
    }

    public boolean hasGaps() {
        List<HeaderElement<BLK, BHW>> longestChain = getLongestChain();
        return longestChain.get(longestChain.size() - 1).header.getNumber() < maxNum;
    }

    protected void trimChain() {
        List<HeaderElement<BLK, BHW>> longestChain = getLongestChain();
        if (longestChain.size() > MAX_CHAIN_LEN) {
            long newTrimNum = getLongestChain().get(longestChain.size() - MAX_CHAIN_LEN).header
                .getNumber();
            for (int i = 0; darkZoneNum < newTrimNum; darkZoneNum++, i++) {
                ByteArrayWrapper wHash = new ByteArrayWrapper(longestChain.get(i).header.getHash());
                putGenHeaders(darkZoneNum, Collections.singletonMap(wHash, longestChain.get(i)));
            }
            darkZoneNum--;
        }
    }

    protected void putGenHeaders(long num,
        Map<ByteArrayWrapper, HeaderElement<BLK, BHW>> genHeaders) {
        minNum = Math.min(minNum, num);
        maxNum = Math.max(maxNum, num);
        headers.put(num, genHeaders);
    }

    @Override
    public synchronized List<BLK> addBlocks(Collection<BLK> blocks) {
        for (BLK block : blocks) {
            addBlock(block);
        }
        return exportBlocks();
    }

    @Override
    public synchronized int getHeadersCount() {
        return (int) (maxNum - minNum);
    }

    @Override
    public synchronized void addHeaders(Collection<BHW> headers) {
        for (BHW header : headers) {
            addHeader(header);
        }
        trimChain();
    }

    protected boolean addHeader(BHW header) {
        long num = header.getNumber();
        if (num <= darkZoneNum || num > maxNum + MAX_CHAIN_LEN * 2) {
            // dropping too distant headers
            return false;
        }
        return addHeaderPriv(header);
    }

    protected boolean addHeaderPriv(BHW header) {
        long num = header.getNumber();
        Map<ByteArrayWrapper, HeaderElement<BLK, BHW>> genHeaders = headers.get(num);
        if (genHeaders == null) {
            genHeaders = new HashMap<>();
            putGenHeaders(num, genHeaders);
        }
        ByteArrayWrapper wHash = new ByteArrayWrapper(header.getHash());
        HeaderElement<BLK, BHW> headerElement = genHeaders.get(wHash);
        if (headerElement != null) {
            return false;
        }

        headerElement = new HeaderElement<BLK, BHW>();
        headerElement.header = header;
        genHeaders.put(wHash, headerElement);

        return true;
    }

    public class HeadersRequestImpl implements HeadersRequest {

        private final long start;
        private final int count;
        private final boolean reverse;
        public HeadersRequestImpl(long start, int count, boolean reverse) {
            this.start = start;
            this.count = count;
            this.reverse = reverse;
        }

        @Override
        public String toString() {
            return "HeadersRequest{" + "start=" + getStart() + ", count=" + getCount()
                + ", reverse=" + isReverse()
                + '}';
        }

        @Override
        public long getStart() {
            return start;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public boolean isReverse() {
            return reverse;
        }
    }

    public class HeaderElement<BK extends IBlock<?, ?>, BW extends AbstractBlockHeaderWrapper<?>> {

        public BW header;
        public BK block;
        public boolean exported;
    }

}

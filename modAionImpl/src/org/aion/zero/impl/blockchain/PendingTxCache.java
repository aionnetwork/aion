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

package org.aion.zero.impl.blockchain;

import org.aion.base.type.Address;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PendingTxCache {

    private Map<Address, Map<BigInteger,AionTransaction>> pendingTx;
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());
    private static int cacheMax = 256*100_000; //256MB
    private AtomicInteger currectSize = new AtomicInteger(0);

    public PendingTxCache(final int cacheMax) {
        pendingTx = Collections.synchronizedMap(new LRUMap<>(100_000));
        this.cacheMax = cacheMax *100_000;
    }

    public synchronized List<AionTransaction> getSeqCacheTx(Map<BigInteger, AionTransaction> txmap, Address addr, BigInteger bn) {
        if (addr == null || txmap == null || bn == null) {
            throw new NullPointerException();
        }

        if (isCacheMax(txmap)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("PendingTx reached the max Memory settings");
            }
            return txmap.values().stream().collect(Collectors.toList());
        }

        List<AionTransaction> rtn = new ArrayList<>();
        Map<BigInteger,AionTransaction> accountCacheMap = pendingTx.get(addr);


        if (accountCacheMap != null) {

            int cz = currectSize.get();
            currectSize.set(cz - getAccuTxSize(accountCacheMap.values().stream().collect(Collectors.toList())));

            accountCacheMap.putAll(txmap);
            rtn.addAll(findSeqTx(bn, accountCacheMap));
            pendingTx.put(addr, accountCacheMap);
            currectSize.addAndGet(getAccuTxSize(accountCacheMap.values().stream().collect(Collectors.toList())));
        } else {
            rtn.addAll(findSeqTx(bn, txmap));
            pendingTx.put(addr, txmap);
            currectSize.addAndGet(getAccuTxSize(txmap.values().stream().collect(Collectors.toList())));
        }

        return rtn;
    }

    private int getAccuTxSize(List<AionTransaction> txList) {
        final int[] rtn = { 0 };
        txList.stream().forEach( tx -> {
            rtn[0] += tx.getEncoded().length;
        });

        return rtn[0];
    }

    private boolean isCacheMax(Map<BigInteger, AionTransaction> txmap) {

        if (LOG.isTraceEnabled()) {
            LOG.trace("isCacheMax [{}] [{}]", currectSize.get(), getAccuTxSize(txmap.values().stream().collect(Collectors.toList())));
        }
        return (currectSize.get() + getAccuTxSize(txmap.values().stream().collect(Collectors.toList()))) > cacheMax;
    }

    private synchronized List<AionTransaction> findSeqTx(BigInteger bn, Map<BigInteger, AionTransaction> cacheMap) {
        if (bn == null || cacheMap == null) {
            throw new NullPointerException();
        }

        List<AionTransaction> rtn = new ArrayList<>();
        rtn.add(cacheMap.get(bn));

        boolean foundNext = true;
        while(foundNext) {
            bn = bn.add(BigInteger.ONE);
            AionTransaction nextTx = cacheMap.get(bn);
            if (nextTx == null) {
                foundNext = false;
            } else {
                rtn.add(cacheMap.get(bn));
            }
        }

        return rtn;
    }

    public synchronized List<AionTransaction> addCacheTx(Map<BigInteger, AionTransaction> txmap, Address addr) {
        if (addr == null || txmap == null) {
            throw new NullPointerException();
        }

        if (isCacheMax(txmap)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("PendingTx reached the max Memory settings");
            }
            return txmap.values().stream().collect(Collectors.toList());
        }

        Map<BigInteger, AionTransaction> pendingmap;
        if (pendingTx.get(addr) != null) {
            pendingmap = pendingTx.get(addr);

            int cz = currectSize.get();
            currectSize.set(cz - getAccuTxSize(pendingmap.values().stream().collect(Collectors.toList())));

            List<AionTransaction> newCache = new ArrayList<>();
            for (BigInteger bi : txmap.keySet()) {
                if (pendingmap.get(bi) == null) {
                    newCache.add(txmap.get(bi));
                }
            }

            pendingmap.putAll(txmap);
            pendingTx.put(addr, pendingmap);
            currectSize.addAndGet(getAccuTxSize(pendingmap.values().stream().collect(Collectors.toList())));

            return newCache;
        } else {
            pendingTx.put(addr, txmap);
            currectSize.addAndGet(getAccuTxSize(txmap.values().stream().collect(Collectors.toList())));
            return txmap.values().stream().collect(Collectors.toList());
        }
    }

    public synchronized List<AionTransaction> flush(Map<Address, BigInteger> nonceMap) {
        if (nonceMap == null) {
            throw new NullPointerException();
        }

        List<AionTransaction> processableTx = new ArrayList<>();
        nonceMap.keySet().stream().forEach( addr -> {
            BigInteger bn = nonceMap.get(addr);

            if (LOG.isDebugEnabled()) {
                LOG.debug("cacheTx.flush addr[{}] bn[{}] size[{}]", addr.toString(), bn.toString(), pendingTx.get(addr).size());
            }

            if (LOG.isTraceEnabled()) {
                for (AionTransaction atx : pendingTx.get(addr).values()) {
                    LOG.trace("cacheTx.flush nonce[{}]", new BigInteger(atx.getNonce()).toString());
                }
            }

            Map<BigInteger, AionTransaction> accountCache = pendingTx.get(addr);
            int cz = currectSize.get();
            currectSize.set(cz - getAccuTxSize(accountCache.values().stream().collect(Collectors.toList())));

            Iterator<Map.Entry<BigInteger, AionTransaction>> itr = accountCache.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<BigInteger, AionTransaction> entry = itr.next();
                if (entry.getKey().compareTo(bn) < 0) {
                    itr.remove();
                }
            }

            currectSize.addAndGet(getAccuTxSize(accountCache.values().stream().collect(Collectors.toList())));

            if (LOG.isDebugEnabled()) {
                LOG.debug("cacheTx.flush after addr[{}] size[{}]", addr.toString(), pendingTx.get(addr).size());
            }

            if (accountCache.get(bn) != null) {
                processableTx.addAll(findSeqTx(bn, accountCache));
            }
        });

        return processableTx;
    }

    public synchronized Set<Address> getCacheTxAccount() {
        return new HashSet<>(this.pendingTx.keySet());
    }

    public synchronized Map<BigInteger,AionTransaction> geCacheTx(Address from) {
        if (from == null) {
            throw new NullPointerException();
        }

        return pendingTx.get(from);
    }
}

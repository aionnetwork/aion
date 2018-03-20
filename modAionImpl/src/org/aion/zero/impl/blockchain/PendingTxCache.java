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

    private Map<Address, Map<BigInteger,AionTransaction>> cacheTxMap;
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());
    private static int CacheMax = 256*100_000; //256MB
    private AtomicInteger currentSize = new AtomicInteger(0);

    public PendingTxCache(final int cacheMax) {
        cacheTxMap = Collections.synchronizedMap(new LRUMap<>(100_000));
        PendingTxCache.CacheMax = cacheMax *100_000;
    }

//    public List<AionTransaction> getSeqCacheTx(Map<BigInteger, AionTransaction> txmap, Address addr, BigInteger bn) {
//        if (addr == null || txmap == null || bn == null) {
//            throw new NullPointerException();
//        }
//
//        if (isCacheMax(txmap)) {
//            if (LOG.isTraceEnabled()) {
//                LOG.trace("PendingTx reached the max Memory settings");
//            }
//            return txmap.values().stream().collect(Collectors.toList());
//        }
//
//        List<AionTransaction> rtn = new ArrayList<>();
//        Map<BigInteger,AionTransaction> accountCacheMap = cacheTxMap.get(addr);
//
//
//        if (accountCacheMap != null) {
//
//            int cz = currentSize.get();
//            currentSize.set(cz - getAccuTxSize(accountCacheMap.values().stream().collect(Collectors.toList())));
//
//            accountCacheMap.putAll(txmap);
//            rtn.addAll(findSeqTx(bn, accountCacheMap));
//            cacheTxMap.put(addr, accountCacheMap);
//            currentSize.addAndGet(getAccuTxSize(accountCacheMap.values().stream().collect(Collectors.toList())));
//        } else {
//            rtn.addAll(findSeqTx(bn, txmap));
//            cacheTxMap.put(addr, txmap);
//            currentSize.addAndGet(getAccuTxSize(txmap.values().stream().collect(Collectors.toList())));
//        }
//
//        return rtn;
//    }

    private int getAccuTxSize(Map<BigInteger, AionTransaction> txMap) {

        if (txMap == null) {
            return 0;
        } else {
            final int[] size = { 0 };
            txMap.values().parallelStream().forEach(tx -> {
                size[0] += tx.getEncoded().length;
            });

            return size[0];
        }
    }

    private boolean isCacheMax(int txSize) {

        if (LOG.isTraceEnabled()) {
            LOG.trace("isCacheMax [{}] [{}]", currentSize.get(), txSize);
        }
        return (currentSize.get() + txSize) > CacheMax;
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

    public List<AionTransaction> addCacheTx(AionTransaction tx) {
        if (tx == null) {
            throw new NullPointerException();
        }

        if (cacheTxMap.get(tx.getFrom()) == null) {
            SortedMap<BigInteger, AionTransaction> cacheMap = new TreeMap<>(Collections.reverseOrder());
            cacheTxMap.put(tx.getFrom(), cacheMap);
        }

        int txSize = tx.getEncoded().length;
        if (isCacheMax(txSize)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("PendingTx reached the max Memory settings");
            }

            if (cacheTxMap.get(tx.getFrom()) == null) {
                return Collections.singletonList(tx);
            } else {

                BigInteger nonce = tx.getNonceBI();
                List<BigInteger> removeTx = null;
                boolean findPosition = false;
                for (Map.Entry<BigInteger, AionTransaction> e :  cacheTxMap.get(tx.getFrom()).entrySet()) {
                    if (e.getKey().compareTo(nonce) > -1) {
                        if (removeTx == null) {
                            removeTx = new ArrayList<>();
                        }

                        removeTx.add(e.getKey());
                        currentSize.set(currentSize.get() - e.getValue().getEncoded().length);
                        if (!isCacheMax(tx.getEncoded().length)) {
                            findPosition = true;
                            break;
                        }
                    }
                }

                if (findPosition) {
                    for (BigInteger bi : removeTx) {
                        cacheTxMap.get(tx.getFrom()).remove(bi);
                    }
                    cacheTxMap.get(tx.getFrom()).put(nonce, tx);
                    currentSize.addAndGet(txSize);
                }
            }

        } else {
            cacheTxMap.get(tx.getFrom()).put(tx.getNonceBI(), tx);
        }

        return cacheTxMap.get(tx.getFrom()).values().stream().collect(Collectors.toList());
    }

    public List<AionTransaction> flush(Map<Address, BigInteger> nonceMap) {
        if (nonceMap == null) {
            throw new NullPointerException();
        }

        List<AionTransaction> processableTx = new ArrayList<>();
        

        nonceMap.keySet().stream().forEach( addr -> {
            BigInteger bn = nonceMap.get(addr);

            if (LOG.isDebugEnabled()) {
                LOG.debug("cacheTx.flush addr[{}] bn[{}] size[{}]", addr.toString(), bn.toString(), cacheTxMap.get(addr).size());
            }

            if (LOG.isTraceEnabled()) {
                for (AionTransaction atx : cacheTxMap.get(addr).values()) {
                    LOG.trace("cacheTx.flush nonce[{}]", new BigInteger(atx.getNonce()).toString());
                }
            }

            Map<BigInteger, AionTransaction> accountCache = cacheTxMap.get(addr);
            int cz = currentSize.get();
            currentSize.set(cz - getAccuTxSize(accountCache.values().stream().collect(Collectors.toList())));

            Iterator<Map.Entry<BigInteger, AionTransaction>> itr = accountCache.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<BigInteger, AionTransaction> entry = itr.next();
                if (entry.getKey().compareTo(bn) < 0) {
                    itr.remove();
                }
            }

            currentSize.addAndGet(getAccuTxSize(accountCache.values().stream().collect(Collectors.toList())));

            if (LOG.isDebugEnabled()) {
                LOG.debug("cacheTx.flush after addr[{}] size[{}]", addr.toString(), cacheTxMap.get(addr).size());
            }

            if (accountCache.get(bn) != null) {
                processableTx.addAll(findSeqTx(bn, accountCache));
            }
        });

        return processableTx;
    }

    public Set<Address> getCacheTxAccount() {
        return new HashSet<>(this.cacheTxMap.keySet());
    }

    public Map<BigInteger,AionTransaction> geCacheTx(Address from) {
        if (from == null) {
            throw new NullPointerException();
        }

        if (cacheTxMap.get(from) == null) {

            SortedMap<BigInteger, AionTransaction> cacheMap = Collections.synchronizedSortedMap(new TreeMap<>());

            cacheTxMap.put(from, cacheMap);
        }

        return cacheTxMap.get(from);
    }
}

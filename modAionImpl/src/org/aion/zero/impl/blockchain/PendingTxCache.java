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

public class PendingTxCache {

    private Map<Address, TreeMap<BigInteger,AionTransaction>> cacheTxMap;
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.TX.name());
    private static int CacheMax = 256*100_000; //256MB
    private AtomicInteger currentSize = new AtomicInteger(0);
    private int cacheAccountLimit = 100_000;

    PendingTxCache(final int cacheMax) {
        cacheTxMap = Collections.synchronizedMap(new LRUMap<>(cacheAccountLimit));
        PendingTxCache.CacheMax = cacheMax *100_000;
    }

    private int getAccountSize(Map<BigInteger, AionTransaction> txMap) {

        if (txMap == null) {
            return 0;
        } else {
            final int[] accountSize = { 0 };
            txMap.values().parallelStream().forEach(tx -> accountSize[0] += tx.getEncoded().length);

            return accountSize[0];
        }
    }

    private boolean isCacheMax(int txSize) {

        if (LOG.isTraceEnabled()) {
            LOG.trace("isCacheMax [{}] [{}]", currentSize.get(), txSize);
        }
        return (currentSize.get() + txSize) > CacheMax;
    }

    private List<AionTransaction> findSeqTx(BigInteger bn, Address addr) {

        List<AionTransaction> rtn = new ArrayList<>();
        rtn.add(cacheTxMap.get(addr).get(bn));

        boolean foundNext = true;
        while(foundNext) {
            bn = bn.add(BigInteger.ONE);
            AionTransaction nextTx = cacheTxMap.get(addr).get(bn);
            if (nextTx == null) {
                foundNext = false;
            } else {
                rtn.add(cacheTxMap.get(addr).get(bn));
            }
        }

        return rtn;
    }

    List<AionTransaction> addCacheTx(AionTransaction tx) {
        if (tx == null) {
            throw new NullPointerException();
        }

        int txSize = tx.getEncoded().length;
        if (isCacheMax(txSize)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("PendingTx reached the max Memory settings");
            }

            if (cacheTxMap.get(tx.getFrom()) == null) {
                // no tx belong to the account, return directly
                return Collections.singletonList(tx);
            } else {
                // calculate replaced nonce tx size
                BigInteger nonce = tx.getNonceBI();
                List<BigInteger> removeTx;
                boolean findPosition = false;

                removeTx = new ArrayList<>();
                int tempCacheSize = currentSize.get();
                if (cacheTxMap.get(tx.getFrom()).get(nonce) != null) {
                    // case 1: found tx has same nonce in the cachemap
                    removeTx.add(nonce);
                    int oldTxSize = cacheTxMap.get(tx.getFrom()).get(nonce).getEncoded().length;
                    tempCacheSize -= oldTxSize;
                    if (!isCacheMax( txSize - oldTxSize)) {
                        //case 1a: replace nonce within the cachelimit, replace it
                        findPosition = true;
                    } else {
                        //case 1b: replace nonce still over the cachelimit, replace it and find the best remove list
                        for (Map.Entry<BigInteger, AionTransaction> e :  cacheTxMap.get(tx.getFrom()).descendingMap().entrySet()) {
                            if (e.getKey().compareTo(nonce) > 0) {
                                removeTx.add(e.getKey());
                                tempCacheSize -= e.getValue().getEncoded().length;
                                if (tempCacheSize + txSize < CacheMax) {
                                    findPosition = true;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    // case 2: backward iterate the cache to remove bigger nonce tx until find the enough cache size
                    for (Map.Entry<BigInteger, AionTransaction> e :  cacheTxMap.get(tx.getFrom()).descendingMap().entrySet()) {
                        if (e.getKey().compareTo(nonce) > 0) {
                            removeTx.add(e.getKey());
                            tempCacheSize -= e.getValue().getEncoded().length;
                            if (tempCacheSize + txSize < CacheMax) {
                                findPosition = true;
                                break;
                            }
                        }
                    }
                }

                if (findPosition) {
                    for (BigInteger bi : removeTx) {
                        cacheTxMap.get(tx.getFrom()).remove(bi);
                    }
                    cacheTxMap.get(tx.getFrom()).put(nonce, tx);
                    currentSize.set(tempCacheSize + txSize);
                }
            }

        } else {
            if (cacheTxMap.size() == cacheAccountLimit) {
                //remove firstAccount in pendingTxCache
                Iterator<Map.Entry<Address,TreeMap<BigInteger, AionTransaction>>> it = cacheTxMap.entrySet().iterator();
                if (it.hasNext()) {
                    currentSize.addAndGet( -getAccountSize(it.next().getValue()));
                    it.remove();
                }
            }

            cacheTxMap.computeIfAbsent(tx.getFrom(), k -> new TreeMap<>());

            cacheTxMap.get(tx.getFrom()).put(tx.getNonceBI(), tx);
            currentSize.addAndGet(txSize);
        }

        return new ArrayList<>(cacheTxMap.get(tx.getFrom()).values());
    }

    public List<AionTransaction> flush(Map<Address, BigInteger> nonceMap) {
        if (nonceMap == null) {
            throw new NullPointerException();
        }

        List<AionTransaction> processableTx = new ArrayList<>();

        for (Address addr : nonceMap.keySet()) {
            BigInteger bn = nonceMap.get(addr);
            if (LOG.isDebugEnabled()) {
                LOG.debug("cacheTx.flush addr[{}] bn[{}] size[{}], cache_size[{}]", addr.toString(), bn.toString(), cacheTxMap.get(addr).size(), currentSize.get());
            }

            if (cacheTxMap.get(addr) != null) {
                currentSize.addAndGet(- getAccountSize(cacheTxMap.get(addr)));
                cacheTxMap.get(addr).headMap(bn).clear();
                currentSize.addAndGet(getAccountSize(cacheTxMap.get(addr)));

                if (LOG.isDebugEnabled()) {
                    LOG.debug("cacheTx.flush after addr[{}] size[{}], cache_size[{}]", addr.toString(), cacheTxMap.get(addr).size(), currentSize.get());
                }

                if (cacheTxMap.get(addr).get(bn) != null) {
                    processableTx.addAll(findSeqTx(bn, addr));
                }
            }
        }

        return processableTx;
    }

    Set<Address> getCacheTxAccount() {
        return new HashSet<>(this.cacheTxMap.keySet());
    }

    Map<BigInteger,AionTransaction> geCacheTx(Address from) {
        if (from == null) {
            throw new NullPointerException();
        }

        cacheTxMap.computeIfAbsent(from, k -> new TreeMap<>());

        return cacheTxMap.get(from);
    }

}

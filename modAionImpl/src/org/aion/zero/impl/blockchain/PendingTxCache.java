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

    private Map<Address, TreeMap<BigInteger, AionTransaction>> cacheTxMap;
    private Map<Address, Integer> cachedAccountSize;
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.TX.name());
    private static int CacheMax;
    private AtomicInteger currentSize = new AtomicInteger(0);
    private int cacheAccountLimit = 100_000;

    private Set<byte[]> cacheClearTxHash;
    private boolean isPoolBackup;

    PendingTxCache() {
        CacheMax = 256 * 100_000; //25.6MB
        cacheTxMap = new LRUMap<>(cacheAccountLimit);
        cachedAccountSize = new LRUMap<>(cacheAccountLimit);
        cacheClearTxHash = new HashSet<>();
        this.isPoolBackup = true;
    }

    PendingTxCache(final int cacheMax) {
        cacheTxMap = new LRUMap<>(cacheAccountLimit);
        cachedAccountSize = new LRUMap<>(cacheAccountLimit);
        PendingTxCache.CacheMax = cacheMax * 100_000;
        cacheClearTxHash = new HashSet<>();
        this.isPoolBackup = true;
    }

    PendingTxCache(final int cacheMax, boolean poolBackup) {
        cacheTxMap = new LRUMap<>(cacheAccountLimit);
        cachedAccountSize = new LRUMap<>(cacheAccountLimit);
        PendingTxCache.CacheMax = cacheMax * 100_000;
        this.isPoolBackup = poolBackup;
        if (isPoolBackup) {
            cacheClearTxHash = new HashSet<>();
        }
    }

    private int getAccountSize(Address addr) {
        return cachedAccountSize.get(addr) == null ? 0 : cachedAccountSize.get(addr);
    }

    private boolean isCacheMax(int txSize) {

        if (LOG.isTraceEnabled()) {
            LOG.trace("isCacheMax [{}] [{}]", currentSize.get(), txSize);
        }
        return (currentSize.get() + txSize) > CacheMax;
    }

    void addCacheTx(AionTransaction tx) {
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
                return;
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
                    if (!isCacheMax(txSize - oldTxSize)) {
                        //case 1a: replace nonce within the cachelimit, replace it
                        findPosition = true;
                    } else {
                        //case 1b: replace nonce still over the cachelimit, replace it and find the best remove list
                        for (Map.Entry<BigInteger, AionTransaction> e : cacheTxMap.get(tx.getFrom()).descendingMap()
                                .entrySet()) {
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
                    for (Map.Entry<BigInteger, AionTransaction> e : cacheTxMap.get(tx.getFrom()).descendingMap()
                            .entrySet()) {
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
                        AionTransaction remove = cacheTxMap.get(tx.getFrom()).get(bi);
                        if (isPoolBackup) {
                            cacheClearTxHash.add(remove.getHash().clone());
                        }

                        subAccountSize(remove.getFrom(), remove.getEncoded().length);
                        cacheTxMap.get(tx.getFrom()).remove(bi);
                    }

                    cacheTxMap.get(tx.getFrom()).put(nonce, tx);
                    currentSize.set(tempCacheSize + txSize);
                    addAccountSize(tx.getFrom(), txSize);
                }
            }

        } else {
            if (cacheTxMap.size() == cacheAccountLimit) {
                //remove firstAccount in pendingTxCache
                Iterator<Map.Entry<Address, TreeMap<BigInteger, AionTransaction>>> it = cacheTxMap.entrySet()
                        .iterator();
                if (it.hasNext()) {
                    Map.Entry<Address, TreeMap<BigInteger, AionTransaction>> e = it.next();
                    currentSize.addAndGet(-getAccountSize(e.getKey()));
                    cachedAccountSize.remove(tx.getFrom());
                    if (isPoolBackup) {
                        for (AionTransaction removeTx : e.getValue().values()) {
                            cacheClearTxHash.add(removeTx.getHash().clone());
                        }
                    }
                    it.remove();
                }
            }

            cacheTxMap.computeIfAbsent(tx.getFrom(), k -> new TreeMap<>());

            if (cacheTxMap.get(tx.getFrom()).get(tx.getNonceBI()) != null) {
                int oldTxSize = cacheTxMap.get(tx.getFrom()).get(tx.getNonceBI()).getEncoded().length;
                cacheTxMap.get(tx.getFrom()).put(tx.getNonceBI(), tx);

                int sizeDiff = txSize - oldTxSize;
                currentSize.set(currentSize.get() + sizeDiff);
                addAccountSize(tx.getFrom(), sizeDiff);
            } else {
                cacheTxMap.get(tx.getFrom()).put(tx.getNonceBI(), tx);
                currentSize.addAndGet(txSize);
                addAccountSize(tx.getFrom(), txSize);
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("PendingTx add {}, size{}", tx.toString(), cacheTxMap.get(tx.getFrom()).values().size());
        }
    }

    private void addAccountSize(Address from, int txSize) {
        cachedAccountSize.merge(from, txSize, (a, b) -> (a + b));
    }

    private void subAccountSize(Address from, int txSize) {
        if (cachedAccountSize.get(from) != null)
            cachedAccountSize.put(from, (cachedAccountSize.get(from) - txSize));
    }

    public List<AionTransaction> flush(Map<Address, BigInteger> nonceMap) {
        if (nonceMap == null) {
            throw new NullPointerException();
        }

        if (currentSize.get() == 0) {
            return new ArrayList<>();
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("cacheTx.flush cacheTx# {}", cacheTxSize());
        }

        int cacheTxNumber = 0;
        for (Address addr : nonceMap.keySet()) {
            BigInteger bn = nonceMap.get(addr);
            if (LOG.isDebugEnabled()) {
                LOG.debug("cacheTx.flush addr[{}] bn[{}] tx#[{}] accSize[{}] cacheSize[{}]",
                        addr.toString(),
                        bn.toString(),
                        cacheTxMap.size(),
                        cachedAccountSize.size(),
                        currentSize.get());
            }

            if (cacheTxMap.get(addr) != null) {
                currentSize.addAndGet(-getAccountSize(addr));

                Map<BigInteger, AionTransaction> headmap = cacheTxMap.get(addr).headMap(bn);
                for (AionTransaction tx : headmap.values()) {
                    subAccountSize(addr, tx.getEncoded().length);
                    if (isPoolBackup) {
                        cacheClearTxHash.add(tx.getHash().clone());
                    }
                }

                cacheTxMap.get(addr).headMap(bn).clear();
                currentSize.addAndGet(getAccountSize(addr));

                cacheTxNumber += cacheTxMap.get(addr).size();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("cacheTx.flush after addr[{}] tx#[{}] accSize[{}] cacheSize[{}]", addr.toString(),
                            cacheTxMap.get(addr).size(), cachedAccountSize.get(addr), currentSize.get());
                }
            }
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("cacheTx.flush after cacheTx# {}", cacheTxNumber);
        }

        Map<BigInteger, AionTransaction> timeMap = new LinkedHashMap<>();
        for (TreeMap<BigInteger, AionTransaction> e : cacheTxMap.values()) {
            if (!e.isEmpty()) {
                BigInteger ts = e.firstEntry().getValue().getTimeStampBI();
                while (timeMap.get(ts) != null) {
                    ts = ts.add(BigInteger.ONE);
                }

                timeMap.put(ts, e.firstEntry().getValue());
            }
        }

        return timeMap.values().isEmpty() ? new ArrayList<>() : new ArrayList<>(timeMap.values());
    }

    public boolean isInCache(Address addr, BigInteger nonce) {
        return this.cacheTxMap.get(addr) != null && (this.cacheTxMap.get(addr).get(nonce) != null);
    }

    Set<Address> getCacheTxAccount() {
        Set<Address> acc = new HashSet<>();
        for (Map.Entry<Address, TreeMap<BigInteger, AionTransaction>> e : this.cacheTxMap.entrySet()) {
            if (!e.getValue().isEmpty()) {
                acc.add(e.getKey());
            }
        }

        return acc;
    }

    Map<BigInteger, AionTransaction> geCacheTx(Address from) {
        if (from == null) {
            throw new NullPointerException();
        }

        cacheTxMap.computeIfAbsent(from, k -> new TreeMap<>());

        return cacheTxMap.get(from);
    }

    public int cacheSize() {
        return currentSize.get();
    }

    public int cacheTxSize() {
        AtomicInteger size = new AtomicInteger();
        cacheTxMap.forEach((key, value) -> size.addAndGet(value.size()));
        return size.get();
    }

    public Set<byte[]> getClearTxHash() {
        return cacheClearTxHash;
    }

    public void clearCacheTxHash() {
        cacheClearTxHash.clear();
    }
}

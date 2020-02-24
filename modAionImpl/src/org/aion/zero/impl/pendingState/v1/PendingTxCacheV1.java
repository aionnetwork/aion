package org.aion.zero.impl.pendingState.v1;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.base.AionTransaction;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.types.AionAddress;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

/**
 * The pending tx cache instance for solving the transaction network broadcast order issue to the
 * transaction pool. The pending state will temporary caching the transaction if the transaction has
 * not adding to the pool in order.
 *
 * @author Jay Tseng
 */
public final class PendingTxCacheV1 {

    public static final int ACCOUNT_CACHE_MAX = 2_000;
    public static final int TX_PER_ACCOUNT_MAX = 500;
    public static final int CACHE_TIMEOUT = 3_600;
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.TX.name());
    private final LRUMap<AionAddress, SortedMap<BigInteger, AionTransaction>> cacheTxMap;
    private final SortedMap<Long, Set<AionTransaction>> timeOutMap;
    private final Lock lock = new ReentrantLock();
    private List<AionTransaction> removedTransactionForPoolBackup;

    /** @implNote the default constructor */
    PendingTxCacheV1() {
        cacheTxMap = new LRUMap<>(ACCOUNT_CACHE_MAX);
        timeOutMap = new TreeMap<>();
    }

    /**
     * @implNote the constructor with poolBackup option
     *
     * @param poolBackup the flag to enable/disable the removedTxHash set
     */
    public PendingTxCacheV1(boolean poolBackup) {
        cacheTxMap = new LRUMap<>(ACCOUNT_CACHE_MAX);
        timeOutMap = new TreeMap<>();
        if (poolBackup) {
            removedTransactionForPoolBackup = new ArrayList<>();
        }
    }

    private static long getExpiredTime(long longValue) {
        return TimeUnit.MICROSECONDS.toSeconds(longValue) + CACHE_TIMEOUT;
    }

    /**
     * @implNote add transaction into the cache layer.
     * @param tx the aion transaction.
     * @return return transaction if add success, otherwise, return null.
     */
    public AionTransaction addCacheTx(AionTransaction tx) {
        Objects.requireNonNull(tx);

        long time = getExpiredTime(tx.getTimeStampBI().longValue());

        lock.lock();
        try {
            AionAddress sender = tx.getSenderAddress();
            if (cacheTxMap.isFull() && !cacheTxMap.containsKey(sender)) {
                AionAddress removeAddress = cacheTxMap.firstKey();
                Map<BigInteger, AionTransaction> removedTxMap = cacheTxMap.remove(removeAddress);
                for (AionTransaction removedTx : removedTxMap.values()) {
                    removeTxInTimeoutMap(removedTx);
                    if (removedTransactionForPoolBackup != null) {
                        removedTransactionForPoolBackup.add(removedTx);
                    }
                }
            }

            SortedMap<BigInteger, AionTransaction> cachedTxBySender = cacheTxMap.get(sender);
            if (cachedTxBySender == null) {
                TreeMap<BigInteger, AionTransaction> newMap = new TreeMap<>();
                newMap.put(tx.getNonceBI(), tx);
                cacheTxMap.put(sender, newMap);
            } else {
                if (cachedTxBySender.size() < TX_PER_ACCOUNT_MAX) {
                    cachedTxBySender.put(tx.getNonceBI(), tx);
                    cacheTxMap.put(sender, cachedTxBySender);

                    if (LOG.isTraceEnabled()) {
                        LOG.trace(
                                "PendingTx added {}, cachedTxSize:{} by the sender:{}",
                                tx,
                                cachedTxBySender.size(),
                                sender);
                    }
                } else {
                    LOG.info(
                            "Cannot add tx:{} into the cache, reached the account cached limit.",
                            tx);
                    return null;
                }
            }

            Set<AionTransaction> txSet = timeOutMap.get(time);
            if (txSet == null) {
                Set<AionTransaction> newSet = new HashSet<>();
                newSet.add(tx);
                timeOutMap.put(time, newSet);
            } else {
                txSet.add(tx);
            }

            return tx;
        } finally {
            lock.unlock();
        }
    }

    private void removeTxInTimeoutMap(AionTransaction tx) {
        long expiredTime = getExpiredTime(tx.getTimeStampBI().longValue());
        Set<AionTransaction> set = timeOutMap.get(expiredTime);
        if (set != null) {
            set.remove(tx);
            if (set.isEmpty()) {
                timeOutMap.remove(expiredTime);
            } else {
                timeOutMap.put(expiredTime, set);
            }
        }
    }

    /**
     * @implNote remove the cached transactions base on the account nonce updated by the block import.
     * @param nonceMap The account with the latest nonce.
     * @return The transaction has been removed in the pending tx cache.
     */
    public List<AionTransaction> flush(Map<AionAddress, BigInteger> nonceMap) {
        Objects.requireNonNull(nonceMap);

        List<AionTransaction> txList = new ArrayList<>();
        lock.lock();
        try {
            for (Entry<AionAddress, BigInteger> e : nonceMap.entrySet()) {
                AionAddress address = e.getKey();
                SortedMap<BigInteger, AionTransaction> accountCachedTx = cacheTxMap.get(address);
                if (accountCachedTx != null) {
                    BigInteger nonce = e.getValue();
                    Objects.requireNonNull(nonce);

                    Map<BigInteger, AionTransaction> flushedTxMap = accountCachedTx.headMap(nonce);
                    if (!flushedTxMap.isEmpty()) {
                        for (AionTransaction t : flushedTxMap.values()) {
                            removeTxInTimeoutMap(t);

                            if (removedTransactionForPoolBackup != null) {
                                removedTransactionForPoolBackup.add(t);
                            }
                        }

                        txList.addAll(flushedTxMap.values());
                    }

                    cacheTxMap.get(address).headMap(nonce).clear();
                    if (cacheTxMap.get(address).isEmpty()) {
                        cacheTxMap.remove(address);
                    }
                }
            }

            // Update the timeout cached Tx
            txList.addAll(flushTimeoutTx());
            LOG.info("cacheTx.flush cacheTx# {}", cacheTxSize());

            return txList;
        } finally {
            lock.unlock();
        }
    }

    private List<AionTransaction> flushTimeoutTx() {
        List<AionTransaction> txList = new ArrayList<>();
        long current =  TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        Map<Long, Set<AionTransaction>> timeoutTxs = timeOutMap.headMap(current);
        if (!timeoutTxs.isEmpty()) {
            for (Set<AionTransaction> set : timeoutTxs.values()) {
                for (AionTransaction tx : set) {
                    AionAddress sender = tx.getSenderAddress();
                    if (cacheTxMap.containsKey(sender)) {
                        SortedMap<BigInteger, AionTransaction> map = cacheTxMap.get(sender);
                        BigInteger nonce = tx.getNonceBI();
                        if (map.containsKey(nonce)) {
                            txList.add(map.remove(nonce));
                            cacheTxMap.put(sender, map);
                        }
                    }
                }
            }
        }

        return txList;
    }

    /**
     * @implNote get total transactions have been cached.
     * @return the total transaction numbers.
     */
    public int cacheTxSize() {
        int size = 0;
        lock.lock();
        try {
            for (Map<BigInteger, AionTransaction> accountMap : cacheTxMap.values()) {
                size += accountMap.values().size();
            }

            return size;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote check the transaction is in the pending cache by given the account address and the
     * transaction nonce.
     *
     * @param sender the transaction account relate with the transaction sender.
     * @param nonce the transaction nonce.
     * @return boolean if the transaction matched the given sender and the nonce.
     */
    public boolean isInCache(AionAddress sender, BigInteger nonce) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(nonce);

        lock.lock();
        try {
            return this.cacheTxMap.containsKey(sender)
                    && (this.cacheTxMap.get(sender).containsKey(nonce));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote get how many accounts have been cached in the instance.
     * @return the set of the account address.
     */
    public Set<AionAddress> getCacheTxAccount() {
        lock.lock();
        try {
            return cacheTxMap.keySet();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote get cached transactions relate with the sender.
     * @param sender the transactions in the cache relate with the sender.
     * @return the map of the transaction nonce and the  transaction sent from the given sender address.
     * Return null if cannot find the send address in the cache instance.
     */
    public Map<BigInteger, AionTransaction> getCacheTxBySender(AionAddress sender) {
        Objects.requireNonNull(sender);

        lock.lock();
        try {
            return cacheTxMap.get(sender);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote get the list of the transactions have been removed in the cache instance and clear the
     * removedList
     * @return the list of the transaction
     */
    public List<AionTransaction> getRemovedTransactionForPoolBackup() {
        if (removedTransactionForPoolBackup != null) {
            lock.lock();
            try {
                List<AionTransaction> removedTx = new ArrayList<>(removedTransactionForPoolBackup);
                removedTransactionForPoolBackup.clear();
                return removedTx;
            } finally {
                lock.unlock();
            }
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * @implNote get the pending transactions for adding the transactions into the TxPool
     * @param nonceMap The account with the best pending state nonce.
     * @return The ordering transactions follow by the best nonce of the pending state.
     */
    public List<AionTransaction> getNewPendingTransactions(Map<AionAddress, BigInteger> nonceMap) {
        Objects.requireNonNull(nonceMap);

        List<AionTransaction> txList = new ArrayList<>();
        lock.lock();
        try {
            for (Entry<AionAddress, BigInteger> e : nonceMap.entrySet()) {
                AionAddress address = e.getKey();
                SortedMap<BigInteger, AionTransaction> accountCachedTx = cacheTxMap.get(address);
                if (accountCachedTx != null) {
                    BigInteger nonce = e.getValue();
                    Objects.requireNonNull(nonce);

                    while (accountCachedTx.containsKey(nonce)) {
                        txList.add(accountCachedTx.get(nonce));
                        nonce = nonce.add(BigInteger.ONE);
                    }
                }
            }

            return txList;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote remove specific transaction from the cache.
     * @param sender the sender of the remove transaction
     * @param nonce the nonce of the remove transaction
     */
    public void removeTransaction(AionAddress sender, BigInteger nonce) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(nonce);

        lock.lock();
        try {
            if (cacheTxMap.containsKey(sender)) {
                LOG.debug("remove cachedTransaction: sender:{}, nonce:{}", sender, nonce);
                Map<BigInteger, AionTransaction> accountInfo = cacheTxMap.get(sender);
                AionTransaction removedTx = accountInfo.remove(nonce);
                if (removedTx != null && removedTransactionForPoolBackup != null) {
                    removedTransactionForPoolBackup.add(removedTx);
                }

                if (accountInfo.isEmpty()) {
                    cacheTxMap.remove(sender);
                }
            }
        } finally{
            lock.unlock();
        }

    }
}

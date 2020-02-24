package org.aion.txpool.v1;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.base.AionTransaction;
import org.aion.base.PooledTransaction;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.txpool.Constant;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.slf4j.Logger;

public final class TxPoolV1 {

    /**
     * mainMap : Map<ByteArrayWrapper, TXState> @ByteArrayWrapper transaction hash @TXState
     * transaction data and sort status
     */
    private final Map<ByteArrayWrapper, PooledTransaction> mainMap;
    /**
     * timeView : SortedMap<Long, LinkedHashSet<ByteArrayWrapper>> @Long transaction
     * timestamp @LinkedHashSet<ByteArrayWrapper> the hashSet of the transaction hash*
     */
    private final SortedMap<Long, Set<ByteArrayWrapper>> timeView = new TreeMap<>();
    /**
     * feeView : SortedMap<BigInteger, LinkedHashSet<TxPoolList<ByteArrayWrapper>>> @BigInteger
     * energy cost = energy consumption * energy price @LinkedHashSet<TxPoolList<ByteArrayWrapper>>
     * the TxPoolList of the first transaction hash
     */
    private final SortedMap<Long, Set<ByteArrayWrapper>> feeView =
            new TreeMap<>(Collections.reverseOrder());
    /**
     * accountView : Map<AionAddress, Map<BigInteger, ByteArrayWrapper>> @AionAddress
     * account @BigInteger Transaction nonce @ByteArrayWrapper TransactionHash
     */
    private final Map<AionAddress, SortedMap<BigInteger, ByteArrayWrapper>> accountView = new HashMap<>();

    private final Lock lock = new ReentrantLock();
    private final Logger LOG_TXPOOL;
    private final AtomicLong blockEnergyLimit;
    public final int maxPoolSize;
    public final int transactionTimeout;

    private PooledTransaction droppedPoolTx;

    /**
     * @implNote construct the transaction pool with Java.Properties setup.
     * @param config the pool arguments
     */
    public TxPoolV1(Properties config) {

        if (Optional.ofNullable(config.get(Constant.PROP_TX_TIMEOUT)).isPresent()) {
            transactionTimeout = Math.max(Integer.parseInt(config.get(Constant.PROP_TX_TIMEOUT).toString()), 10);
        } else {
            transactionTimeout = 3600;
        }

        if (Optional.ofNullable(config.get(Constant.PROP_BLOCK_NRG_LIMIT)).isPresent()) {
            blockEnergyLimit =new AtomicLong(
                Math.max(Long.parseLong((String) config.get(Constant.PROP_BLOCK_NRG_LIMIT)), 1_000_000L));
        } else {
            blockEnergyLimit = new AtomicLong(10_000_000L);
        }

        if (Optional.ofNullable(config.get(Constant.PROP_POOL_SIZE_MAX)).isPresent()) {
            maxPoolSize = Math.max(Integer.parseInt((String) config.get(Constant.PROP_POOL_SIZE_MAX)), 1024);
        } else {
            maxPoolSize = 8192;
        }

        mainMap = new HashMap<>();
        LOG_TXPOOL = AionLoggerFactory.getLogger(LogEnum.TXPOOL.toString());
    }

    /**
     * @implNote add transactions into the pool. If the transaction has the same account nonce and
     *     the new transaction has higher energy price. The pool will remove the old transaction and
     *     update to the new one.
     * @param list pool transactions
     * @return the transactions has been added into the pool.
     */
    public List<PooledTransaction> add(List<PooledTransaction> list) {
        Objects.requireNonNull(list);
        if (list.isEmpty()) {
            return new ArrayList<>();
        }

        List<PooledTransaction> addedTransactions = new ArrayList<>();
        lock.lock();
        try {
            for (PooledTransaction poolTx : list) {

                if (mainMap.size() == maxPoolSize) {
                    LOG_TXPOOL.warn("txPool is full. No transaction has been added!");
                    return addedTransactions;
                }

                ByteArrayWrapper repayOldTx = checkRepayTransaction(poolTx.tx);
                if (repayOldTx != null) {
                    if (repayOldTx.equals(ByteArrayWrapper.wrap(poolTx.tx.getTransactionHash()))) {
                        LOG_TXPOOL.debug("skip the transaction add because it's not a valid repay transaction.");
                        continue;
                    } else {
                        LOG_TXPOOL.debug("repay tx found! Remove original tx");
                        droppedPoolTx = poolRemove(repayOldTx);
                    }
                }

                if (poolAdd(poolTx) != null) {
                    addedTransactions.add(poolTx);
                }
            }

            return addedTransactions;
        } finally {
            lock.unlock();
        }
    }

    private PooledTransaction poolAdd(PooledTransaction poolTx) {
        ByteArrayWrapper txHash = ByteArrayWrapper.wrap(poolTx.tx.getTransactionHash());
        PooledTransaction p = mainMap.putIfAbsent(txHash, poolTx);
        if (p != null) {
            LOG_TXPOOL.warn("TxPool has the same transaction");
            return null;
        }

        LOG_TXPOOL.debug("Adding tx[{}]", poolTx.tx);

        AionTransaction tx = poolTx.tx;
        long txTime = TimeUnit.MICROSECONDS.toSeconds(tx.getTimeStampBI().longValue()) + transactionTimeout;
        Set<ByteArrayWrapper> timeSet = timeView.get(txTime);
        if (timeSet == null) {
            timeSet = new LinkedHashSet<>();
        }
        timeSet.add(txHash);
        timeView.put(txTime, timeSet);

        long txEnergyPrice = tx.getEnergyPrice();
        Set<ByteArrayWrapper> feeSet = feeView.get(txEnergyPrice);
        if (feeSet == null) {
            feeSet = new LinkedHashSet<>();
        }
        feeSet.add(txHash);
        feeView.put(txEnergyPrice, feeSet);

        SortedMap<BigInteger, ByteArrayWrapper> accountInfo =
                accountView.get(tx.getSenderAddress());
        if (accountInfo == null) {
            accountInfo = new TreeMap<>();
        }
        accountInfo.put(tx.getNonceBI(), txHash);
        accountView.put(tx.getSenderAddress(), accountInfo);

        LOG_TXPOOL.debug("Added tx[{}]", poolTx.tx);
        return poolTx;
    }

    private PooledTransaction poolRemove(ByteArrayWrapper txHash) {
        PooledTransaction removedTx = mainMap.remove(txHash);
        if (removedTx == null) {
            LOG_TXPOOL.debug("Did not find the transaction hash:{} in the pool", txHash);
            return null;
        }

        LOG_TXPOOL.debug("Removing tx[{}]", removedTx.tx);

        long time = TimeUnit.MICROSECONDS.toSeconds(removedTx.tx.getTimeStampBI().longValue()) + transactionTimeout;
        Set<ByteArrayWrapper> timeSet = timeView.get(time);
        if (timeSet == null) {
            throw new IllegalStateException("the timeView data has broken!, cannot find the data relate with time:" + time);
        } else if (!timeSet.remove(txHash)) {
            throw new IllegalStateException("the timeView data has broken, cannot remove txHash:" + txHash);
        } else {
            if (timeSet.isEmpty()) {
                timeView.remove(time);
            } else {
                timeView.put(time, timeSet);
            }
        }

        Set<ByteArrayWrapper> feeSet = feeView.get(removedTx.tx.getEnergyPrice());
        if (feeSet == null) {
            throw new IllegalStateException("the feeView data has broken!, cannot find the data relate with fee:" + removedTx.tx.getEnergyPrice());
        } else if (!feeSet.remove(txHash)) {
            throw new IllegalStateException("the feeView data has broken, cannot remove txHash:" + txHash);
        } else {
            if (feeSet.isEmpty()) {
                feeView.remove(removedTx.tx.getEnergyPrice());
            } else {
                feeView.put(removedTx.tx.getEnergyPrice(), feeSet);
            }
        }

        SortedMap<BigInteger, ByteArrayWrapper> accountMap =
                accountView.get(removedTx.tx.getSenderAddress());
        if (accountMap == null) {
            throw new IllegalStateException("the accountView data has broken!, cannot find the data relate with the account:" + removedTx.tx.getSenderAddress());
        }

        ByteArrayWrapper removedTxHash = accountMap.remove(removedTx.tx.getNonceBI());
        if (removedTxHash == null) {
            throw new IllegalStateException("the accountView data has broken!, cannot find the tx nonce:" + removedTx.tx.getNonceBI() + " relate with account:" + removedTx.tx.getSenderAddress());
        } else if (!removedTxHash.equals(txHash)) {
            throw new IllegalStateException("the accountView data has broken!, removed hash doesn't match, removedTxHash:" + removedTxHash + " txHash:" + txHash);
        } else {
            if (accountMap.isEmpty()) {
                accountView.remove(removedTx.tx.getSenderAddress());
            } else {
                accountView.put(removedTx.tx.getSenderAddress(), accountMap);
            }
        }

        LOG_TXPOOL.debug("Removed tx[{}]", removedTx.tx);
        return removedTx;
    }

    private ByteArrayWrapper checkRepayTransaction(AionTransaction tx) {
        AionAddress sender = tx.getSenderAddress();
        BigInteger nonce = tx.getNonceBI();
        long price = tx.getEnergyPrice();

        Map<BigInteger, ByteArrayWrapper> accountInfo = accountView.get(sender);
        if (accountInfo != null) {
            ByteArrayWrapper oldTx = accountInfo.get(nonce);
            if (oldTx == null) {
                LOG_TXPOOL.trace("Cannot find the tx has same sender and the nonce in the pool.");
                return null;
            }

            PooledTransaction pTx = mainMap.get(oldTx);
            if (pTx == null) {
                throw new IllegalStateException("Can find the txHash [" + oldTx + "] in the pool, the pool data has broken!");
            }

            LOG_TXPOOL.debug("Original tx[{}], Repay tx[{}]", pTx.tx, tx);

            long pTxPrice = pTx.tx.getEnergyPrice();
            return (price >= pTxPrice * 2) ? oldTx : ByteArrayWrapper.wrap(tx.getTransactionHash());
        }

        LOG_TXPOOL.trace("Cannot find the tx has same sender in the pool.");
        return null;
    }

    /**
     * @implNote add transaction into the pool. If the transaction has the same account nonce and
     *     the new transaction has higher energy price. The pool will remove the old transaction and
     *     update to the new one.
     * @param tx the pool transaction
     * @return the transaction has been added into the pool. Otherwise, return null.
     */
    public PooledTransaction add(PooledTransaction tx) {
        List<PooledTransaction> rtn = this.add(Collections.singletonList(tx));
        return rtn.isEmpty() ? null : rtn.get(0);
    }

    /**
     * @implNote remove transactions into the pool.
     * @param tx pool transactions
     * @return the transactions has been removed from the pool.
     */
    public List<PooledTransaction> remove(List<PooledTransaction> tx) {
        Objects.requireNonNull(tx);

        List<PooledTransaction> removedTx = new ArrayList<>();
        lock.lock();
        try {
            for (PooledTransaction pTx : tx) {
                ByteArrayWrapper txHash = ByteArrayWrapper.wrap(pTx.tx.getTransactionHash());
                PooledTransaction removedPoolTx = poolRemove(txHash);
                if (removedPoolTx != null) {
                    removedTx.add(removedPoolTx);
                }
            }

            return removedTx;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote remove transaction into the pool.
     * @param tx pool transaction
     * @return the transaction has been removed from the pool.
     */
    public PooledTransaction remove(PooledTransaction tx) {
        return remove(Collections.singletonList(tx)).get(0);
    }

    /**
     * @implNote remove transactions relate with the account and the expecting nonce. If the transactions in the pool
     * match the account and less than the expecting nonce. The transactions will be removed.
     * @param accountsWithNonce the map stored accounts with related expecting nonces.
     * @return the pool transactions has been removed from the pool.
     */
    public List<PooledTransaction> removeTxsWithNonceLessThan(Map<AionAddress, BigInteger> accountsWithNonce) {
        Objects.requireNonNull(accountsWithNonce);

        if (accountsWithNonce.isEmpty()) {
            return new ArrayList<>();
        }

        List<PooledTransaction> removedTransaction = new ArrayList<>();
        List<ByteArrayWrapper> removeTxHash = new ArrayList<>();

        lock.lock();
        try {
            for (Map.Entry<AionAddress, BigInteger> account : accountsWithNonce.entrySet()) {
                Objects.requireNonNull(account.getKey());
                Objects.requireNonNull(account.getValue());
                if (accountView.containsKey(account.getKey())) {
                    SortedMap<BigInteger, ByteArrayWrapper> accountInfo = accountView.get(account.getKey());
                    removeTxHash.addAll(accountInfo.headMap(account.getValue()).values());
                }
            }

            for (ByteArrayWrapper hash : removeTxHash) {
                PooledTransaction pTx = poolRemove(hash);
                if (pTx == null) {
                    throw new IllegalStateException("The pool data has broken, cannont find the txHash:" + hash);
                }

                removedTransaction.add(pTx);
            }

            return removedTransaction;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote check the current transactions in the transaction pool.
     * @return the total transaction number in the transaction pool.
     */
    public int size() {
        lock.lock();
        try {
            return mainMap.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote check the transaction pool is full.
     * @return a boolean value represent the pool size reach to the max.
     */
    public boolean isFull() {
        lock.lock();
        try {
            return mainMap.size() >= maxPoolSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote snapshot the transactions for creating new block template.
     * @return the transactions ready to be seal into the new blocks.
     */
    public List<AionTransaction> snapshot() {
        AtomicLong cumulatedTxSize = new AtomicLong();
        AtomicLong cumulatedTxEnergy = new AtomicLong();

        lock.lock();
        try {
            if (mainMap.isEmpty()) {
                return new ArrayList<>();
            }

            Map<AionAddress, BigInteger> accountPickingInfo = new HashMap<>();
            Set<ByteArrayWrapper> pickedTxHash = new HashSet<>();

            // We use the multi rounds picking strategy.
            boolean keepPicking = true;
            List<AionTransaction> pickedTransactions = new ArrayList<>();
            while (keepPicking
                && cumulatedTxSize.get() <= Constant.MAX_BLK_SIZE
                && cumulatedTxEnergy.get() <= blockEnergyLimit.get()) {

                List<AionTransaction> newPicked = pickTransaction(accountPickingInfo, pickedTxHash, cumulatedTxSize, cumulatedTxEnergy);
                if (newPicked.isEmpty()) {
                    keepPicking = false;
                }

                pickedTransactions.addAll(newPicked);
            }

            return pickedTransactions;
        } finally {
            lock.unlock();
        }
    }

    private List<AionTransaction> pickTransaction(
            Map<AionAddress, BigInteger> accountPickingInfo,
            Set<ByteArrayWrapper> pickedTxHash,
            AtomicLong cumulatedTxSize,
            AtomicLong cumulatedTxEnergy) {

        List<AionTransaction> pickedTx = new ArrayList<>();
        for (Set<ByteArrayWrapper> s : feeView.values()) {
            for (ByteArrayWrapper hash : s) {

                if (pickedTxHash.contains(hash)) {
                    continue;
                }

                PooledTransaction pendingTx = mainMap.get(hash);
                if (pendingTx == null) {
                    throw new IllegalStateException("The pool data has broken, cannot find the txHash:" + hash);
                }

                AionAddress sender = pendingTx.tx.getSenderAddress();
                BigInteger bestPickingNonce = accountPickingInfo.get(sender);
                if (bestPickingNonce == null) {
                    accountPickingInfo.put(sender, getAccountFirstPickingNonce(sender));
                }

                BigInteger currentAccountPickingNonce = accountPickingInfo.get(sender);
                if (currentAccountPickingNonce.equals(pendingTx.tx.getNonceBI())) {
                    long txEncodedSize = pendingTx.tx.getEncoded().length;
                    long txEnergyConsumed =
                        Math.max(pendingTx.energyConsumed, (Constant.MIN_ENERGY_CONSUME / 2));

                    cumulatedTxSize.addAndGet(txEncodedSize);
                    cumulatedTxEnergy.addAndGet(txEnergyConsumed);

                    if (cumulatedTxSize.get() <= Constant.MAX_BLK_SIZE && cumulatedTxEnergy.get() <= blockEnergyLimit
                        .get()) {
                        pickedTx.add(pendingTx.tx);
                        pickedTxHash.add(hash);
                        accountPickingInfo.put(sender, currentAccountPickingNonce.add(BigInteger.ONE));
                    } else {
                        return pickedTx;
                    }
                }
            }
        }

        return pickedTx;
    }

    private BigInteger getAccountFirstPickingNonce(AionAddress sender) {
        SortedMap<BigInteger, ByteArrayWrapper> accountInfo = accountView.get(sender);
        if (accountInfo == null) {
            throw new IllegalStateException();
        }

        return accountInfo.firstKey();
    }

    /**
     * @implNote remove out dated transactions in the pool base on the timeout settings.
     * @return removed transactions.
     */
    public List<PooledTransaction> clearOutDateTransaction() {

        List<PooledTransaction> clearedTransactions = new ArrayList<>();
        lock.lock();
        try {
            Map<Long, Set<ByteArrayWrapper>> timeoutTxHashes = timeView.headMap(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

            for (Set<ByteArrayWrapper> hashes : timeoutTxHashes.values()) {
                for (ByteArrayWrapper hash : hashes) {
                    PooledTransaction pTx = poolRemove(hash);
                    if (pTx == null) {
                        throw new IllegalStateException("The pool data has broken, cannot find the txHash:" + hash);
                    }

                    clearedTransactions.add(pTx);
                }
            }

            return clearedTransactions;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote get the current pool best nonce (the largest tx nonce in the pool, continuously transactions)
     * relate with the given account address.
     * @param sender the account address relate with the account in the pool.
     * @return the best nonce in the pool.
     */
    public BigInteger bestPoolNonce(AionAddress sender) {
        Objects.requireNonNull(sender);

        lock.lock();
        try {
            SortedMap<BigInteger, ByteArrayWrapper> accountInfo = accountView.get(sender);
            if (accountInfo == null) {
                return null;
            }

            return accountInfo.lastKey();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote check the transaction already in the pool given sender and the transaction nonce. The repay transaction
     * will return false due to the same sender and nonce. But it will been updated in the following pendingState
     * process.
     * @param sender the transaction sender
     * @param txNonce the transaction nonce
     * @return boolean value to confirm the transaction is in pool.
     */
    public boolean isContained(AionAddress sender, BigInteger txNonce) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(txNonce);

        lock.lock();
        try {
            SortedMap<BigInteger, ByteArrayWrapper> accountInfo = accountView.get(sender);
            if (accountInfo == null) {
                return false;
            } else {
                return accountInfo.containsKey(txNonce);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote update the block energy limit by the block definition.
     * @param nrg the max block energy allowed in the new block.
     */
    public void updateBlkNrgLimit(long nrg) {
        int BLK_NRG_MAX = 100_000_000;
        int BLK_NRG_MIN = 1_000_000;
        if (nrg < BLK_NRG_MIN) {
            blockEnergyLimit.set(BLK_NRG_MIN);
        } else if (nrg > BLK_NRG_MAX) {
            blockEnergyLimit.set(BLK_NRG_MAX);
        } else {
            blockEnergyLimit.set(nrg);
        }

        if (LOG_TXPOOL.isDebugEnabled()) {
            LOG_TXPOOL
                .debug("TxPoolA1.updateBlkNrgLimit nrg[{}] blkNrgLimit[{}]", nrg, blockEnergyLimit.get());
        }
    }

    /**
     * @implNote get the transaction pool version.
     * @return the transaction pool version.
     */
    public String getVersion() {
        return "1.0";
    }

    /**
     * @implNote snapshot all transactions in the pool by account nonce order.
     * @return a list of the AionTransaction in the current transaction pool.
     * @exception IllegalStateException if cannot find the transaction hash in the mainMap.
     */
    public List<AionTransaction> snapshotAll() {
        List<AionTransaction> allPoolTransactions = new ArrayList<>();

        lock.lock();
        try {
            for (SortedMap<BigInteger, ByteArrayWrapper> txHashes : accountView.values()) {
                for (ByteArrayWrapper hash : txHashes.values()) {
                    PooledTransaction pTx = mainMap.get(hash);
                    if (pTx == null) {
                        throw new IllegalStateException("The pool data has broken, cannot find the txHash:" + hash);
                    }
                    allPoolTransactions.add(pTx.tx);
                }
            }

            LOG_TXPOOL.info("snapshotAll: tx#[{}]", allPoolTransactions.size());

            return allPoolTransactions;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote get pool transaction by given the transaction sender address and the transaction nonce.
     * @param sender the transaction sender address.
     * @param nonce the transaction nonce.
     * @return the pooledTransaction when the arguments matched. Otherwise, return null.
     */
    public PooledTransaction getPoolTx(AionAddress sender, BigInteger nonce) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(nonce);

        lock.lock();
        try {
            Map<BigInteger, ByteArrayWrapper> accountInfo = accountView.get(sender);
            if (accountInfo == null) {
                return null;
            }

            ByteArrayWrapper txHash = accountInfo.get(nonce);
            if (txHash == null) {
                return null;
            }

            return mainMap.get(txHash);
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting
    public List<BigInteger> getNonceList(AionAddress acc) {
        lock.lock();
        try {
            Map<BigInteger, ByteArrayWrapper> accountInfo = accountView.get(acc);
            return new ArrayList<>(accountInfo.keySet());
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting
    public List<Long> getFeeList() {
        lock.lock();
        try {
            return new ArrayList<>(feeView.keySet());
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting
    public List<AionTransaction> snapshot(long outDateTime) {
        lock.lock();
        try {
            Map<Long, Set<ByteArrayWrapper>> timeoutTxHashes = timeView.headMap(outDateTime);

            for (Set<ByteArrayWrapper> hashes : timeoutTxHashes.values()) {
                for (ByteArrayWrapper hash : hashes) {
                    PooledTransaction pTx = poolRemove(hash);
                    if (pTx == null) {
                        throw new IllegalStateException("The pool data has broken, cannot find the txHash:" + hash);
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        return snapshot();
    }

    /**
     * @implNote Update pool transaction for updating the correct energy consumption for repay transaction.
     * @param pooledTransaction update the pooedTransaction
     */
    public void updatePoolTransaction(PooledTransaction pooledTransaction) {
        Objects.requireNonNull(pooledTransaction);

        lock.lock();
        try {
            ByteArrayWrapper txHash = ByteArrayWrapper.wrap(pooledTransaction.tx.getTransactionHash());
            mainMap.put(txHash, pooledTransaction);
        } finally{
            lock.unlock();
        }
    }

    public PooledTransaction getDroppedPoolTx() {
        lock.lock();
        try {
            return droppedPoolTx;
        } finally{
            lock.unlock();
        }
    }
}

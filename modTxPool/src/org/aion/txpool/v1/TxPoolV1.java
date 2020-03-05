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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.base.AionTransaction;
import org.aion.base.PooledTransaction;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.txpool.Constant;
import org.aion.txpool.Constant.TXPOOL_PROPERTY;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;

public final class TxPoolV1 {

    /**
     * poolTransactions : Map<ByteArrayWrapper, PooledTransaction>
     *     @ByteArrayWrapper transaction hash
     *     @PooledTransaction transaction data with the actual energy consume
     */
    private final Map<ByteArrayWrapper, PooledTransaction> poolTransactions;
    /**
     * timeView : SortedMap<Long, LinkedHashSet<ByteArrayWrapper>>
     *     @Long the transaction timeout in the pool by the second unit.
     *     @LinkedHashSet<ByteArrayWrapper> the hashSet of the transaction hash
     */
    private final SortedMap<Long, Set<ByteArrayWrapper>> timeView = new TreeMap<>();
    /**
     * feeView : SortedMap<Long, LinkedHashSet<ByteArrayWrapper>>
     *     @Long energy price
     *     @LinkedHashSet<ByteArrayWrapper> the transaction hash set relate with the transaction has the energy price
     */
    private final SortedMap<Long, Set<ByteArrayWrapper>> feeView =
            new TreeMap<>(Collections.reverseOrder());
    /**
     * accountView : Map<AionAddress, SortedMap<BigInteger, ByteArrayWrapper>>
     *     @AionAddress account
     *     @BigInteger transaction nonce
     *     @ByteArrayWrapper TransactionHash
     */
    private final Map<AionAddress, SortedMap<BigInteger, ByteArrayWrapper>> accountView = new HashMap<>();

    private final Lock lock = new ReentrantLock();
    private final Logger LOG_TXPOOL;
    private long blockEnergyLimit;
    public final int maxPoolSize;
    public final int transactionTimeout;

    private PooledTransaction droppedPoolTx;

    /**
     * @implNote construct the transaction pool with Java.Properties setup.
     * @param config the pool arguments
     */
    public TxPoolV1(Properties config) {

        if (Optional.ofNullable(config.get(TXPOOL_PROPERTY.PROP_TX_TIMEOUT)).isPresent()) {
            transactionTimeout = Math.max(Integer.parseInt(config.get(TXPOOL_PROPERTY.PROP_TX_TIMEOUT).toString()), Constant.TRANSACTION_TIMEOUT_MIN);
        } else {
            transactionTimeout = Constant.TRANSACTION_TIMEOUT_DEFAULT;
        }

        if (Optional.ofNullable(config.get(TXPOOL_PROPERTY.PROP_BLOCK_NRG_LIMIT)).isPresent()) {
            blockEnergyLimit = Math.max(Long.parseLong((String) config.get(TXPOOL_PROPERTY.PROP_BLOCK_NRG_LIMIT)), Constant.BLOCK_ENERGY_LIMIT_MIN);
        } else {
            blockEnergyLimit = Constant.BLOCK_ENERGY_LIMIT_DEFAULT;
        }

        if (Optional.ofNullable(config.get(TXPOOL_PROPERTY.PROP_POOL_SIZE_MAX)).isPresent()) {
            maxPoolSize = Math.max(Integer.parseInt((String) config.get(TXPOOL_PROPERTY.PROP_POOL_SIZE_MAX)), Constant.TXPOOL_SIZE_MIN);
        } else {
            maxPoolSize = Constant.TXPOOL_SIZE_DEFAULT;
        }

        poolTransactions = new HashMap<>();
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
            return Collections.emptyList();
        }

        lock.lock();
        try {
            List<PooledTransaction> addedTransactions = new ArrayList<>();

            for (PooledTransaction poolTx : list) {

                if (poolTransactions.size() == maxPoolSize) {
                    LOG_TXPOOL.warn("txPool is full. No transaction has been added!");
                    return addedTransactions;
                }

                ByteArrayWrapper repayOldTx = checkRepayTransaction(poolTx.tx);
                ByteArrayWrapper poolTxHash = ByteArrayWrapper.wrap(poolTx.tx.getTransactionHash());
                if (repayOldTx != null) {
                    if (repayOldTx.equals(poolTxHash)) {
                        LOG_TXPOOL.debug("skip adding the tx [{}] because it's not a valid repay transaction.", poolTx.tx);
                        continue;
                    } else {
                        LOG_TXPOOL.debug("repay tx found! Remove original tx");
                        droppedPoolTx = poolRemove(repayOldTx);
                    }
                }

                poolAdd(poolTxHash, poolTx);
                addedTransactions.add(poolTx);
            }

            return addedTransactions;
        } finally {
            lock.unlock();
        }
    }

    private void poolAdd(ByteArrayWrapper txHash, PooledTransaction poolTx) {

        LOG_TXPOOL.debug("Adding tx[{}]", poolTx.tx);

        poolTransactions.put(txHash, poolTx);
        long txTime = TimeUnit.MICROSECONDS.toSeconds(poolTx.tx.getTimeStampBI().longValue()) + transactionTimeout;
        Set<ByteArrayWrapper> timeSet = timeView.getOrDefault(txTime, new LinkedHashSet<>());
        timeView.putIfAbsent(txTime, timeSet);
        timeSet.add(txHash);

        long txEnergyPrice = poolTx.tx.getEnergyPrice();
        Set<ByteArrayWrapper> feeSet = feeView.getOrDefault(txEnergyPrice, new LinkedHashSet<>());
        feeView.putIfAbsent(txEnergyPrice, feeSet);
        feeSet.add(txHash);

        SortedMap<BigInteger, ByteArrayWrapper> accountInfo = accountView.getOrDefault(poolTx.tx.getSenderAddress(), new TreeMap<>());
        accountView.putIfAbsent(poolTx.tx.getSenderAddress(), accountInfo);
        accountInfo.put(poolTx.tx.getNonceBI(), txHash);

        LOG_TXPOOL.debug("Added tx[{}]", poolTx.tx);
    }

    private PooledTransaction poolRemove(ByteArrayWrapper txHash) {
        PooledTransaction removedTx = poolTransactions.remove(txHash);
        if (removedTx == null) {
            LOG_TXPOOL.debug("Did not find the transaction hash:{} in the pool", txHash);
            return null;
        }

        LOG_TXPOOL.debug("Removing tx[{}]", removedTx.tx);

        long time = TimeUnit.MICROSECONDS.toSeconds(removedTx.tx.getTimeStampBI().longValue()) + transactionTimeout;
        Set<ByteArrayWrapper> timeSet = timeView.get(time);
        timeSet.remove(txHash);
        if (timeSet.isEmpty()) {
            timeView.remove(time);
        }

        Set<ByteArrayWrapper> feeSet = feeView.get(removedTx.tx.getEnergyPrice());
        feeSet.remove(txHash);
        if (feeSet.isEmpty()) {
            feeView.remove(removedTx.tx.getEnergyPrice());
        }

        SortedMap<BigInteger, ByteArrayWrapper> accountInfo =
                accountView.get(removedTx.tx.getSenderAddress());

        accountInfo.remove(removedTx.tx.getNonceBI());
        if (accountInfo.isEmpty()) {
            accountView.remove(removedTx.tx.getSenderAddress());
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
                LOG_TXPOOL.trace("Cannot find the tx has same sender and the nonce in the pool. {}", tx);
                return null;
            }

            PooledTransaction pTx = poolTransactions.get(oldTx);
            LOG_TXPOOL.debug("Original tx[{}], Repay tx[{}]", pTx.tx, tx);

            long pTxPrice = pTx.tx.getEnergyPrice();
            return (price >= pTxPrice * 2) ? oldTx : ByteArrayWrapper.wrap(tx.getTransactionHash());
        } else {
            LOG_TXPOOL.trace("Cannot find the tx has same sender in the pool. {}", tx);
            return null;
        }
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

        lock.lock();
        try {
            List<PooledTransaction> removedTx = new ArrayList<>();

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
        Objects.requireNonNull(accountsWithNonce.entrySet());

        if (accountsWithNonce.isEmpty()) {
            return Collections.emptyList();
        }

        lock.lock();
        try {
            List<PooledTransaction> removedTransaction = new ArrayList<>();
            List<ByteArrayWrapper> removeTxHash = new ArrayList<>();
            for (Map.Entry<AionAddress, BigInteger> account : accountsWithNonce.entrySet()) {
                if (accountView.containsKey(account.getKey())) {
                    SortedMap<BigInteger, ByteArrayWrapper> accountInfo = accountView.get(account.getKey());
                    removeTxHash.addAll(accountInfo.headMap(account.getValue()).values());
                }
            }

            for (ByteArrayWrapper hash : removeTxHash) {
                PooledTransaction pTx = poolRemove(hash);
                if (pTx != null) {
                    removedTransaction.add(pTx);
                }
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
            return poolTransactions.size();
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
            return poolTransactions.size() >= maxPoolSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote snapshot the transactions for creating new block template.
     * @return the transactions ready to be seal into the new blocks.
     */
    public List<AionTransaction> snapshot() {

        lock.lock();
        try {
            if (poolTransactions.isEmpty()) {
                return Collections.emptyList();
            }

            Map<AionAddress, BigInteger> accountPickingInfo = new HashMap<>();
            Set<ByteArrayWrapper> pickedTxHash = new HashSet<>();

            // We use the multi rounds picking strategy.
            List<AionTransaction> pickedTransactions = new ArrayList<>();
            int totalPicked;
            long cumulatedTxEncodedSize = 0;
            long cumulatedTxEnergyConsumed = 0;
            LOG_TXPOOL.info("Start to pick transaction");
            do {
                totalPicked = pickedTransactions.size();

                Triple<List<AionTransaction>, Long, Long> newPicked =
                        pickTransaction(
                                accountPickingInfo,
                                pickedTxHash,
                                cumulatedTxEncodedSize,
                                cumulatedTxEnergyConsumed);
                cumulatedTxEncodedSize += newPicked.getMiddle();
                cumulatedTxEnergyConsumed += newPicked.getRight();
                LOG_TXPOOL.debug(
                        "transaction picked: {}, newPickedEncodedSize: {}, newPickedEnergyConsumed: {}",
                        newPicked.getLeft().size(),
                        newPicked.getMiddle(),
                        newPicked.getRight());
                pickedTransactions.addAll(newPicked.getLeft());

            } while (totalPicked < pickedTransactions.size());

            LOG_TXPOOL.info(
                    "snapshot {} tx, totalEncodedSize: {}, totalEnergyConsumed: {}",
                    pickedTransactions.size(),
                    cumulatedTxEncodedSize,
                    cumulatedTxEnergyConsumed);
            return pickedTransactions;
        } finally {
            lock.unlock();
        }
    }

    private Triple<List<AionTransaction>, Long, Long> pickTransaction(
            Map<AionAddress, BigInteger> accountPickingInfo,
            Set<ByteArrayWrapper> pickedTxHash,
            long cumulatedTxEncodedSize,
            long cumulatedTxEnergy) {

        List<AionTransaction> pickedTx = new ArrayList<>();
        long pickedTxEncodedSize = 0;
        long pickedEnergyConsumed = 0;
        for (Set<ByteArrayWrapper> s : feeView.values()) {
            for (ByteArrayWrapper hash : s) {

                if (!pickedTxHash.contains(hash)) {
                    PooledTransaction pendingTx = poolTransactions.get(hash);

                    AionAddress sender = pendingTx.tx.getSenderAddress();
                    BigInteger currentAccountPickingNonce =
                        accountPickingInfo.getOrDefault(sender, getAccountFirstPickingNonce(sender));

                    if (currentAccountPickingNonce.equals(pendingTx.tx.getNonceBI())) {
                        long txEncodedSize = pendingTx.tx.getEncoded().length;
                        long txEnergyConsumed = Math.max(pendingTx.energyConsumed, (Constant.MIN_ENERGY_CONSUME / 2));

                        if ((cumulatedTxEncodedSize + pickedTxEncodedSize + txEncodedSize) <= Constant.MAX_BLK_SIZE
                            && (cumulatedTxEnergy + pickedEnergyConsumed + txEncodedSize) <= blockEnergyLimit) {
                            LOG_TXPOOL.trace("Transaction picked: [{}]", pendingTx.tx);
                            pickedTx.add(pendingTx.tx);
                            pickedTxHash.add(hash);

                            currentAccountPickingNonce = currentAccountPickingNonce.add(BigInteger.ONE);
                            accountPickingInfo.put(sender, currentAccountPickingNonce);

                            pickedTxEncodedSize += txEncodedSize;
                            pickedEnergyConsumed += txEnergyConsumed;
                        } else {
                            return Triple.of(pickedTx, pickedTxEncodedSize, pickedEnergyConsumed);
                        }
                    }
                }
            }
        }

        return Triple.of(pickedTx, pickedTxEncodedSize, pickedEnergyConsumed);
    }

    private BigInteger getAccountFirstPickingNonce(AionAddress sender) {
        SortedMap<BigInteger, ByteArrayWrapper> accountInfo = accountView.get(sender);
        if (accountInfo == null) {
            throw new IllegalStateException("Can't find the account info relate with sender: " + sender);
        }

        return accountInfo.firstKey();
    }

    /**
     * @implNote remove out dated transactions in the pool base on the timeout settings.
     * @return removed transactions.
     */
    public List<PooledTransaction> clearOutDateTransaction() {
        lock.lock();
        try {
            return clearOutDateTransaction(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        } finally {
            lock.unlock();
        }
    }

    List<PooledTransaction> clearOutDateTransaction(long outDateTime) {
        List<PooledTransaction> clearedTransactions = new ArrayList<>();

        for (Set<ByteArrayWrapper> set : timeView.headMap(outDateTime).values()) {
            for (ByteArrayWrapper txHash : set) {
                PooledTransaction removedTx = poolTransactions.remove(txHash);
                if (removedTx == null) {
                    LOG_TXPOOL.debug("Did not find the transaction hash:{} in the pool", txHash);
                    continue;
                }

                LOG_TXPOOL.debug("Removing tx[{}]", removedTx.tx);

                Set<ByteArrayWrapper> feeSet = feeView.get(removedTx.tx.getEnergyPrice());
                feeSet.remove(txHash);
                if (feeSet.isEmpty()) {
                    feeView.remove(removedTx.tx.getEnergyPrice());
                }

                SortedMap<BigInteger, ByteArrayWrapper> accountInfo =
                    accountView.get(removedTx.tx.getSenderAddress());

                accountInfo.remove(removedTx.tx.getNonceBI());
                if (accountInfo.isEmpty()) {
                    accountView.remove(removedTx.tx.getSenderAddress());
                }

                clearedTransactions.add(removedTx);
                LOG_TXPOOL.debug("Removed tx[{}]", removedTx.tx);
            }
        }
        timeView.headMap(outDateTime).clear();

        return clearedTransactions;
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

        lock.lock();
        try {
            if (nrg < BLK_NRG_MIN) {
                blockEnergyLimit = BLK_NRG_MIN;
            } else if (nrg > BLK_NRG_MAX) {
                blockEnergyLimit = BLK_NRG_MAX;
            } else {
                blockEnergyLimit = nrg;
            }

            LOG_TXPOOL.debug(
                    "TxPoolA1.updateBlkNrgLimit nrg[{}] blkNrgLimit[{}]", nrg, blockEnergyLimit);
        } finally {
            lock.unlock();
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

        lock.lock();
        try {
            List<AionTransaction> allPoolTransactions = new ArrayList<>();
            for (SortedMap<BigInteger, ByteArrayWrapper> txHashes : accountView.values()) {
                for (ByteArrayWrapper hash : txHashes.values()) {
                    PooledTransaction pTx = poolTransactions.get(hash);
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

            return poolTransactions.get(txHash);
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
            for (Set<ByteArrayWrapper> hashes : new ArrayList<>(timeView.headMap(outDateTime).values())) {
                for (ByteArrayWrapper hash : new ArrayList<>(hashes)) {
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
            if (poolTransactions.containsKey(txHash)) {
                poolTransactions.put(txHash, pooledTransaction);
            }
        } finally{
            lock.unlock();
        }
    }

    /**
     * @implNote when the transaction has been removed due to the repay transaction. It will be assigned
     * to the droppedPoolTx. And then the pendingState will retrieve the dropped transaction by this call.
     * @return the original transaction has been dropped by new repay transaction.
     */
    public PooledTransaction getDroppedPoolTx() {
        lock.lock();
        try {
            return droppedPoolTx;
        } finally{
            lock.unlock();
        }
    }
}

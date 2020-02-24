package org.aion.zero.impl.pendingState;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aion.txpool.v1.TxPoolV1;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.blockchain.AionImpl.NetworkBestBlockCallback;
import org.aion.zero.impl.blockchain.AionImpl.PendingTxCallback;
import org.aion.zero.impl.blockchain.AionImpl.TransactionBroadcastCallback;
import org.aion.zero.impl.pendingState.v1.PendingTxCacheV1;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.PendingTxDetails;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.aion.base.AionTransaction;
import org.aion.base.PooledTransaction;
import org.aion.base.TxUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.types.TxResponse;
import org.aion.base.AccountState;
import org.aion.mcf.db.RepositoryCache;
import org.aion.txpool.Constant;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.valid.TXValidator;
import org.aion.zero.impl.valid.TransactionTypeValidator;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.slf4j.Logger;

public final class AionPendingStateImpl implements IPendingState {

    private final Logger LOGGER_TX = AionLoggerFactory.getLogger(LogEnum.TX.toString());
    private final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());

    private final AionBlockchainImpl blockchain;
    private final TxPoolV1 txPool;
    private final AtomicReference<Block> currentBestBlock;
    private final PendingTxCacheV1 pendingTxCache;

    private RepositoryCache<AccountState> pendingState;

    /**
     * This buffer stores txs that come in with double the energy price as an existing tx with the same nonce
     *  They will be applied between blocks so it is easier for us to manage the state of the repo.
     */
    private final Set<AionTransaction> repayTransaction;

    private boolean testingMode;

    private boolean poolDumpEnable;

    private boolean isSeedMode;

    private boolean poolBackUpEnable;

    private Map<byte[], byte[]> backupPendingPoolAdd;
    private Map<byte[], byte[]> backupPendingCacheAdd;
    private List<byte[]> backupPendingPoolRemove;
    private List<byte[]> backupPendingCacheRemove;


    private boolean closeToNetworkBest = true;

    private final AtomicBoolean pendingTxReceivedforMining;
    private final PendingTxCallback pendingTxCallback;
    private final NetworkBestBlockCallback networkBestBlockCallback;
    private final TransactionBroadcastCallback transactionBroadcastCallback;

    private void backupPendingTx() {
        if (!poolBackUpEnable) {
            return;
        }

        long t1 = System.currentTimeMillis();

        if (!backupPendingPoolAdd.isEmpty()) {
            blockchain.getRepository().addTxBatch(backupPendingPoolAdd, true);
        }

        if (!backupPendingCacheAdd.isEmpty()) {
            blockchain.getRepository().addTxBatch(backupPendingCacheAdd, false);
        }

        if (!backupPendingPoolRemove.isEmpty()) {
            blockchain.getRepository().removeTxBatch(backupPendingPoolRemove, true);
        }

        for (AionTransaction tx : pendingTxCache.getRemovedTransactionForPoolBackup()) {
            backupPendingCacheRemove.add(tx.getTransactionHash());
        }

        if (!backupPendingCacheRemove.isEmpty()) {
            blockchain.getRepository().removeTxBatch(backupPendingCacheRemove, false);
        }

        blockchain.getRepository().flush();

        backupPendingPoolAdd.clear();
        backupPendingCacheAdd.clear();
        backupPendingPoolRemove.clear();
        backupPendingCacheRemove.clear();

        LOGGER_TX.debug("Pending state backupPending took {} ms", System.currentTimeMillis() - t1);
    }

    public AionPendingStateImpl(
            AionBlockchainImpl blockchain,
            long energyUpperBound,
            int txPendingTimeout,
            int maxTxCacheSize,
            boolean seedMode,
            boolean poolBackup,
            boolean poolDump,
            PendingTxCallback pendingTxCallback,
            NetworkBestBlockCallback networkBestBlockCallback,
            TransactionBroadcastCallback transactionBroadcastCallback,
            boolean forTest) {

        this.testingMode = forTest;
        this.isSeedMode = seedMode;
        this.blockchain = blockchain;
        this.currentBestBlock = new AtomicReference<>(blockchain.getBestBlock());

        if (isSeedMode) {
            // seedMode has no txpool setup.
            txPool = null;
            LOGGER_TX.info("Seed mode is enabled");
        } else {
            Properties prop = new Properties();
            // The BlockEnergyLimit will be updated when the best block found.
            prop.put(Constant.PROP_BLOCK_NRG_LIMIT, String.valueOf(energyUpperBound));
            prop.put(Constant.PROP_TX_TIMEOUT, String.valueOf(txPendingTimeout));
            this.txPool = new TxPoolV1(prop);
        }

        this.pendingTxCallback = pendingTxCallback;
        this.networkBestBlockCallback = networkBestBlockCallback;
        this.transactionBroadcastCallback = transactionBroadcastCallback;
        this.pendingTxReceivedforMining = new AtomicBoolean();

        // seedMode has no pool.
        this.poolDumpEnable = poolDump && !seedMode;
        this.poolBackUpEnable = poolBackup && !seedMode;
        this.repayTransaction = new LinkedHashSet<>();
        this.pendingState = blockchain.getRepository().startTracking();
        this.pendingTxCache = new PendingTxCacheV1(poolBackUpEnable);

        if (poolBackUpEnable) {
            this.backupPendingPoolAdd = new HashMap<>();
            this.backupPendingCacheAdd = new HashMap<>();
            this.backupPendingPoolRemove = new ArrayList<>();
            this.backupPendingCacheRemove = new ArrayList<>();

            // Trying to recover the pool backup first.
            recoverPoolnCache();
        }
    }

    public synchronized RepositoryCache<?> getRepository() {
        // Todo : no class use this method.
        return pendingState;
    }

    public int getPendingTxSize() {
        return isSeedMode ? 0 : this.txPool.size();
    }

    @Override
    public synchronized List<AionTransaction> getPendingTransactions() {
        return isSeedMode ? new ArrayList<>() : this.txPool.snapshot();
    }

    /**
     * Transaction comes from the ApiServer. Validate it first then add into the pendingPool.
     * Synchronized it because multiple Api interfaces call this method.
     * @param tx transaction comes from the ApiServer.
     * @return the TxResponse.
     */
    public synchronized TxResponse addTransactionFromApiServer(AionTransaction tx) {

        TxResponse response = validateTx(tx);
        if (response.isFail()) {
            LOGGER_TX.info("tx is not valid - status: {} tx: {}", response.name(), tx);
            return response;
        }

        // SeedMode or the syncing status will just broadcast the transaction to the network.
        if (isSeedMode || !closeToNetworkBest) {
            transactionBroadcastCallback.broadcastTransactions(Collections.singletonList(tx));
            return TxResponse.SUCCESS;
        }

        return addPendingTransactions(Collections.singletonList(tx)).get(0);
    }

    /**
     * The transactions come from the p2p network. We validate it first then add into the pendingPool.
     * @param transactions transaction list come from the network.
     */
    public synchronized void addTransactionsFromNetwork(List<AionTransaction> transactions) {
        List<AionTransaction> validTransactions = new ArrayList<>();

        for (AionTransaction tx : transactions) {
            if (!TXValidator.isInCache(ByteArrayWrapper.wrap(tx.getTransactionHash())) && !validateTx(tx).isFail()) {
                validTransactions.add(tx);
            }
        }

        // SeedMode or the syncing status will just broadcast the transaction to the network.
        if (isSeedMode || !closeToNetworkBest) {
            transactionBroadcastCallback.broadcastTransactions(validTransactions);
        } else {
            addPendingTransactions(validTransactions);
        }
    }

    private TxResponse validateTx(AionTransaction tx) {
        TxResponse response = TXValidator.validateTx(tx, blockchain.isUnityForkEnabledAtNextBlock());
        if (response.isFail()) {
            return response;
        }

        if (!TransactionTypeValidator.isValid(tx)) {
            return TxResponse.INVALID_TX_TYPE;
        }

        if (!blockchain.beaconHashValidator.validateTxForPendingState(tx)) {
            return TxResponse.INVALID_TX_BEACONHASH;
        }

        return TxResponse.SUCCESS;
    }

    /**
     * For the transactions come from the cache or backup, we can just verify the beaconHash. And skip
     * the transactions broadcast.
     * @param transactions transaction list come from the cache or backup.
     */
    private void addTransactionsFromCacheOrBackup(List<AionTransaction> transactions) {
        List<AionTransaction> validTransactions = new ArrayList<>();

        for (AionTransaction tx : transactions) {
            if (blockchain.beaconHashValidator.validateTxForPendingState(tx)) {
                validTransactions.add(tx);
            } else {
                fireDroppedTx(tx, TxResponse.INVALID_TX_BEACONHASH.name());
            }
        }

        addPendingTransactions(validTransactions);
    }

    /**
     * Tries to add the given transactions to the PendingState
     *
     * @param transactions, the list of AionTransactions to be added
     * @return a list of TxResponses of the same size as the input param transactions The entries in
     *     the returned list of responses correspond one-to-one with the input txs
     */
    private List<TxResponse> addPendingTransactions(
            List<AionTransaction> transactions) {

        List<AionTransaction> newPending = new ArrayList<>();
        List<AionTransaction> newLargeNonceTx = new ArrayList<>();
        List<TxResponse> txResponses = new ArrayList<>();

        for (AionTransaction tx : transactions) {
            BigInteger bestPendingStateNonce = bestPendingStateNonce(tx.getSenderAddress());
            int cmp = tx.getNonceBI().compareTo(bestPendingStateNonce);

            if (pendingTxCache.isInCache(tx.getSenderAddress(), tx.getNonceBI())) {
                txResponses.add(TxResponse.ALREADY_CACHED);
            } else {
                // This case happens when we have already received a tx with a larger nonce
                // from the address txFrom
                if (cmp > 0) {
                    TxResponse response = addTransactionToCache(tx, false);
                    txResponses.add(response);

                    if (response.equals(TxResponse.CACHED_NONCE)) {
                        newLargeNonceTx.add(tx);
                    }
                }
                // This case happens when this transaction has been received before, but was
                // cached due to the non large nonce or the transaction is temporary full.
                else if (cmp == 0) {
                    if (txPool.isFull()) {
                        TxResponse response =  addTransactionToCache(tx, true);
                        txResponses.add(response);

                        if (response.equals(TxResponse.CACHED_POOLMAX)) {
                            newLargeNonceTx.add(tx);
                        }
                    } else {
                        Map<BigInteger, AionTransaction> cachedTxWithSender = pendingTxCache.getCacheTxBySender(tx.getSenderAddress());

                        if (cachedTxWithSender == null) {
                            TxResponse response = addPendingTransactionInner(tx);
                            txResponses.add(response);

                            if (response.equals(TxResponse.SUCCESS)) {
                                newPending.add(tx);
                                addPendingTxToBackupDatabase(tx);
                            }
                        } else {
                            LOGGER_TX.debug(
                                "add Transaction from cache, sender: {}, size: {}",
                                tx.getSenderAddress(),
                                cachedTxWithSender.size());

                            TxResponse response = addPendingTransactionInner(tx);
                            txResponses.add(response);

                            if (response.equals(TxResponse.SUCCESS)) {
                                newPending.add(tx);
                                addPendingTxToBackupDatabase(tx);

                                int limit = calculateTxFetchNumberLimit();
                                BigInteger newCachedTxNonce = tx.getNonceBI().add(BigInteger.ONE);
                                AionTransaction newCachedTx = cachedTxWithSender.get(newCachedTxNonce);
                                while (response.equals(TxResponse.SUCCESS)
                                    && newCachedTx != null
                                    && limit-- > 0
                                    && !txPool.isFull()) {

                                    LOGGER_TX.debug("add Transaction from cache, sender: {}, nonce: {}", tx.getSenderAddress(), newCachedTxNonce);

                                    response = addPendingTransactionInner(tx);
                                    if (response.equals(TxResponse.SUCCESS)) {
                                        newPending.add(tx);
                                        addPendingTxToBackupDatabase(tx);
                                        newCachedTxNonce = newCachedTxNonce.add(BigInteger.ONE);
                                        newCachedTx = cachedTxWithSender.get(newCachedTxNonce);
                                    }
                                }
                            }
                        }
                    }
                }
                else if (tx.getNonceBI().compareTo(bestRepoNonce(tx.getSenderAddress())) < 0) {
                    // This should mean that the transaction has already been sealed in the repo
                    txResponses.add(TxResponse.ALREADY_SEALED);
                } else {
                    // This case happens when this tx was received before, but never sealed,
                    // typically because of low energy
                    TxResponse implResponse = addPendingTransactionInner(tx);
                    if (implResponse.equals(TxResponse.SUCCESS)) {
                        newPending.add(tx);
                        txResponses.add(TxResponse.REPAID);
                        addPendingTxToBackupDatabase(tx);
                    } else {
                        txResponses.add(implResponse);
                    }
                }
            }
        }

        LOGGER_TX.info(
                "Wire transaction list added: total: {}, newPending: {}, cached: {}, txPool size: {}",
                transactions.size(),
                newPending,
                newLargeNonceTx.size(),
                txPool.size());

        if (!newPending.isEmpty()) {
            pendingTxCallback.pendingTxReceivedCallback(newPending);
            pendingTxReceivedforMining.set(true);
        }

        if (!testingMode && (!newPending.isEmpty() || !newLargeNonceTx.isEmpty())) {
            transactionBroadcastCallback.broadcastTransactions(
                    Stream.concat(newPending.stream(), newLargeNonceTx.stream())
                            .collect(Collectors.toList()));
        }

        return txResponses;
    }

    private void addPendingTxToBackupDatabase(AionTransaction tx) {
        if (poolBackUpEnable) {
            backupPendingPoolAdd.put(tx.getTransactionHash(), tx.getEncoded());
        }
    }

    private void removeBackupDBPendingTx(byte[] txHash) {
        if (poolBackUpEnable) {
            backupPendingPoolRemove.add(txHash);
        }
    }

    private int calculateTxFetchNumberLimit() {
        int cachedAccount = pendingTxCache.getCacheTxAccount().size();
        return cachedAccount == 0 ? 1 : Math.max((txPool.maxPoolSize / 4) / cachedAccount, 1);
    }

    private TxResponse addTransactionToCache(AionTransaction tx, boolean transactionPoolIsFull) {

        if (pendingTxCache.addCacheTx(tx) == null) {
            LOGGER_TX.debug(
                "addPendingTransactions failed due to the account cache is full: from: {}, nonce: {}",
                tx.getSenderAddress(),
                tx.getNonceBI());

            return TxResponse.CACHED_ACCOUNTMAX;
        } else {
            if (poolBackUpEnable) {
                backupPendingCacheAdd.put(tx.getTransactionHash(), tx.getEncoded());
            }

            if (transactionPoolIsFull) {
                LOGGER_TX.debug(
                    "addPendingTransactions addToCache due to poolMax: from: {}, nonce: {}",
                    tx.getSenderAddress(),
                    tx.getNonceBI());

                // Transaction cached because the pool is full
                return TxResponse.CACHED_POOLMAX;
            } else {
                LOGGER_TX.debug(
                    "addPendingTransactions addToCache due to largeNonce: from: {}, nonce: {}",
                    tx.getSenderAddress(),
                    tx.getNonceBI());

                // Transaction cached due to large nonce
                return TxResponse.CACHED_NONCE;
            }
        }
    }

    private void fireTxUpdate(AionTxReceipt txReceipt, PendingTransactionState state, Block block) {
        LOGGER_TX.info(
                String.format(
                        "PendingTransactionUpdate: (Tot: %3s) %12s : %s %8s %s [%s]",
                        getPendingTxSize(),
                        state,
                        txReceipt
                                .getTransaction()
                                .getSenderAddress()
                                .toString()
                                .substring(0, 8),
                        ByteUtil.byteArrayToLong(txReceipt.getTransaction().getNonce()),
                        block.getShortDescr(),
                        txReceipt.getError()));

        pendingTxCallback.pendingTxStateUpdateCallback(new PendingTxDetails(state.getValue(), txReceipt, block.getNumber()));
    }

    /**
     * Executes pending tx on the latest best block Fires pending state update
     *
     * @param tx transaction come from API or P2P
     * @return SUCCESS if transaction gets NEW_PENDING state, else appropriate message such as
     *     DROPPED, INVALID_TX, etc.
     */
    private TxResponse addPendingTransactionInner(final AionTransaction tx) {

        if (txPool.isContained(tx.getSenderAddress(), tx.getNonceBI())) {
            // check energy usage
            PooledTransaction poolTx = txPool.getPoolTx(tx.getSenderAddress(), tx.getNonceBI());
            if (poolTx == null) {
                LOGGER_TX.error(
                    "The pool data has broken, missing the transaction! sender: {}, nonce: {}",
                    tx.getSenderAddress(),
                    tx.getNonceBI());
                throw new IllegalStateException();
            } else {
                //Use BigInteger to avoid the overflow
                BigInteger repayValidPrice = BigInteger.valueOf(poolTx.tx.getEnergyPrice()).multiply(BigInteger.TWO);
                if (BigInteger.valueOf(tx.getEnergyPrice()).compareTo(repayValidPrice) >= 0) {
                    if (repayTransaction.size() < (txPool.maxPoolSize / 4)) {
                        repayTransaction.add(tx);
                        return TxResponse.REPAID;
                    } else {
                        fireDroppedTx(tx, TxResponse.REPAYTX_BUFFER_FULL.name());
                        return TxResponse.DROPPED;
                    }
                } else {
                    fireDroppedTx(tx, TxResponse.REPAYTX_LOWPRICE.name());
                    return TxResponse.REPAYTX_LOWPRICE;
                }
            }
        } else {
            AionTxExecSummary txSum = executeTx(tx);
            if (txSum.isRejected()) {
                LOGGER_TX.debug(
                    "addPendingTransactionImpl tx: {} is rejected due to: {}",
                    Hex.toHexString(tx.getTransactionHash()),
                    txSum.getReceipt().getError());

                fireTxUpdate(txSum.getReceipt(), PendingTransactionState.DROPPED, currentBestBlock.get());
                return TxResponse.DROPPED;
            } else {
                PooledTransaction pendingTx = new PooledTransaction(tx, txSum.getReceipt().getEnergyUsed());
                LOGGER_TX.debug("addPendingTransactionImpl validTx: {}", tx);

                PooledTransaction rtn = this.txPool.add(pendingTx);
                if (rtn == null|| rtn != pendingTx) {
                    // Replay tx case should not happen in this check.
                    LOGGER_TX.debug(
                        "The pool data has broken, missing the transaction! sender: {}, nonce: {}",
                        tx.getSenderAddress(),
                        tx.getNonceBI());
                    throw new IllegalStateException();
                } else {
                    fireTxUpdate(txSum.getReceipt(), PendingTransactionState.NEW_PENDING, currentBestBlock.get());
                    return TxResponse.SUCCESS;
                }
            }
        }
    }

    private void fireDroppedTx(AionTransaction tx, String error) {
        AionTxReceipt rp = new AionTxReceipt();
        rp.setTransaction(tx);
        rp.setError(error);
        fireTxUpdate(rp, PendingTransactionState.DROPPED, currentBestBlock.get());
    }

    private AionTxReceipt createDroppedReceipt(AionTransaction tx, String error) {
        AionTxReceipt txReceipt = new AionTxReceipt();
        txReceipt.setTransaction(tx);
        txReceipt.setError(error);
        return txReceipt;
    }

    private Block findCommonAncestor(Block b1, Block b2) {
        while (!b1.isEqual(b2)) {
            if (b1.getNumber() >= b2.getNumber()) {
                b1 = blockchain.getBlockByHash(b1.getParentHash());
            }

            if (b1.getNumber() < b2.getNumber()) {
                b2 = blockchain.getBlockByHash(b2.getParentHash());
            }
            if (b2 == null) {
                // shouldn't happen
                LOGGER_TX.error("Pending state can't find common ancestor: one of blocks has a gap");
                throw new RuntimeException(
                        "Pending state can't find common ancestor: one of blocks has a gap");
            }
        }
        return b1;
    }

    /**
     * AKI-608
     * The method called by the AionblockchainImpl through callback, currently it will block the block import.
     * @param newBlock new applied block.
     * @param receipts the transaction receipts relate with the transactions of the new block.
     */
    @Override
    public synchronized void applyBlockUpdate(Block newBlock, List<AionTxReceipt> receipts) {

        if (isSeedMode) {
            // seed mode doesn't need to update the pendingState
            return;
        }

        if (currentBestBlock.get().isParentOf(newBlock)) {
            LOGGER_TX.info("PendingStateImpl.processBest: {}", newBlock.getShortDescr());
            processBestInternal(newBlock, receipts);
        } else {
            // need to switch the state to another fork
            Block commonAncestor = findCommonAncestor(currentBestBlock.get(), newBlock);

            LOGGER_TX.info(
                "New best block from another fork: {}, old best: {}, ancestor: {}",
                newBlock.getShortDescr(),
                currentBestBlock.get().getShortDescr(),
                commonAncestor.getShortDescr());

            processRollbackTransactions(commonAncestor);

            processMainChainBlocks(commonAncestor, newBlock);
        }

        currentBestBlock.set(newBlock);
        txPool.updateBlkNrgLimit(currentBestBlock.get().getNrgLimit());

        checkNetworkFullSynced();
        checkCloseToNetworkBest();

        // Should update the pendingState before re run the transactions in txPool.
        pendingState = blockchain.getRepository().startTracking();
        rerunTxsInPool(currentBestBlock.get());

        flushCachedTx();
        backupPendingTx();

        // This is for debug purpose, do not use in the regular kernel running.
        dumpPool();
    }

    private void checkCloseToNetworkBest() {
        int networkSyncingGap = 128;
        closeToNetworkBest =
            currentBestBlock.get().getNumber() >= (networkBestBlockCallback.getNetworkBestBlockNumber() - networkSyncingGap);
        LOGGER_TX.debug(
            "PendingStateImpl.processBest: close to the network best: {}",
            closeToNetworkBest ? "true" : "false");
    }

    /**
     * For Cli full-sync check feature
      */
    private void checkNetworkFullSynced() {
        if (AionBlockchainImpl.enableFullSyncCheck) {
            AionBlockchainImpl.reachedFullSync =
                currentBestBlock.get().getNumber() >= networkBestBlockCallback.getNetworkBestBlockNumber();
        }
    }

    private void processMainChainBlocks(Block commonAncestor, Block mainChainBest) {
        Block mainChainBlock = mainChainBest;
        Stack<Block> stack = new Stack<>();
        while (!mainChainBlock.isEqual(commonAncestor)) {
            LOGGER_TX.debug("mainChain: {}", mainChainBlock.getShortDescr());
            stack.push(mainChainBlock);
            mainChainBlock = blockchain.getBlockByHash(mainChainBlock.getParentHash());
        }

        // processing blocks from ancestor to new block
        while (!stack.isEmpty()) {
            processBestInternal(stack.pop(), null);
        }
    }

    private void processRollbackTransactions(Block commonAncestor) {

        // first return back the transactions from forked blocks
        Block rollback = currentBestBlock.get();
        Stack<List<AionTransaction>> stack = new Stack<>();
        while (!rollback.isEqual(commonAncestor)) {
            LOGGER_TX.debug("Rollback: {}", rollback.getShortDescr());
            stack.push(rollback.getTransactionsList());
            rollback = blockchain.getBlockByHash(rollback.getParentHash());
        }

        while (!stack.isEmpty()) {
            List<AionTransaction> transactions = stack.pop();
            for (AionTransaction tx : transactions) {
                    /* We can add the Tx directly to the pool with the energy value
                     because all txs in the pool are going to be re-run in rerunTxsInPool(best.get()) */
                txPool.add(new PooledTransaction(tx, tx.getEnergyLimit()));
            }
        }
    }

    @Override
    public void setNewPendingReceiveForMining(boolean newPendingTxReceived) {
        pendingTxReceivedforMining.set(newPendingTxReceived);
    }

    private void flushCachedTx() {
        Set<AionAddress> cacheTxAccount = this.pendingTxCache.getCacheTxAccount();

        if (cacheTxAccount.isEmpty()) {
            return;
        }

        LOGGER_TX.debug("PendingStateImpl.flushCachePendingTx: acc#[{}]", cacheTxAccount.size());

        Map<AionAddress, BigInteger> nonceMap = new HashMap<>();
        for (AionAddress addr : cacheTxAccount) {
            nonceMap.put(addr, bestPendingStateNonce(addr));
        }

        updateCachedTxToTxPool(nonceMap);

        List<AionTransaction> outdatedTransaction = this.pendingTxCache.flush(nonceMap);
        LOGGER_TX.debug("PendingStateImpl.flushCachePendingTx: outdatedTransaction#[{}]", outdatedTransaction.size());

        if (!outdatedTransaction.isEmpty()) {
            for (AionTransaction tx : outdatedTransaction) {
                fireDroppedTx(tx, "Dropped due to a new block import");
                removeBackupDBCachedTx(tx.getTransactionHash());
            }
        }
    }

    private void updateCachedTxToTxPool(Map<AionAddress, BigInteger> nonceMap) {

        List<AionTransaction> newPendingTransactions = pendingTxCache.getNewPendingTransactions(nonceMap);
        LOGGER_TX.debug("flushCachedTx - newPendingTxs#[{}]", newPendingTransactions.size());
        Set<AionAddress> updatedAddress = new HashSet<>();
        for (AionTransaction tx : newPendingTransactions) {
            if (txPool.isFull()) {
                LOGGER_TX.debug("flushCachedTx txPool is full, cannot add new pending transactions from the cachedPool");
                break;
            }

            LOGGER_TX.debug("flushCachedTx - loop: {}", tx);
            AionTxExecSummary txSum = executeTx(tx);
            AionTxReceipt receipt = txSum.getReceipt();
            receipt.setTransaction(tx);

            if (txSum.isRejected()) {
                LOGGER_TX.debug("Invalid transaction in cachedPool: {}", tx);
                fireTxUpdate(receipt, PendingTransactionState.DROPPED, currentBestBlock.get());
                pendingTxCache.removeTransaction(tx.getSenderAddress(), tx.getNonceBI());
            } else {
                PooledTransaction pTx = txPool.add(new PooledTransaction(tx, receipt.getEnergyUsed()));
                if (pTx != null) {
                    fireTxUpdate(receipt, PendingTransactionState.PENDING, currentBestBlock.get());
                    updatedAddress.add(tx.getSenderAddress());
                    pendingTxCache.removeTransaction(tx.getSenderAddress(), tx.getNonceBI());
                }
            }
        }

        for (AionAddress addr : updatedAddress) {
            nonceMap.put(addr, bestPendingStateNonce(addr));
        }
    }

    private void processBestInternal(Block block, List<AionTxReceipt> receipts) {
        clearPending(block, receipts);
        clearOutdated(block.getNumber());
    }

    private void clearOutdated(final long blockNumber) {

        List<PooledTransaction> clearedTxFromTxPool = txPool.clearOutDateTransaction();
        for (PooledTransaction pTx : clearedTxFromTxPool) {
            removeBackupDBPendingTx(pTx.tx.getTransactionHash());
            fireTxUpdate(
                createDroppedReceipt(
                    pTx.tx, "Tx was not included into last " + txPool.transactionTimeout + " seconds"),
                PendingTransactionState.DROPPED,
                currentBestBlock.get());
        }

        List<AionTransaction> clearedTxFromCache = pendingTxCache.getRemovedTransactionForPoolBackup();
        for (AionTransaction tx : clearedTxFromCache) {
            removeBackupDBCachedTx(tx.getTransactionHash());
            fireTxUpdate(
                createDroppedReceipt(
                    tx, "Tx was not included into last " + PendingTxCacheV1.CACHE_TIMEOUT + " seconds"),
                    PendingTransactionState.DROPPED,
                    currentBestBlock.get());
        }

        LOGGER_TX.info(
                "clearOutdated block#[{}] tx#[{}]",
                blockNumber,
                clearedTxFromTxPool.size() + clearedTxFromCache.size());
    }

    private void removeBackupDBCachedTx(byte[] hash) {
        if (poolBackUpEnable) {
            backupPendingCacheRemove.add(hash);
        }
    }

    private void clearPending(Block block, List<AionTxReceipt> receipts) {

        List<AionTransaction> txList = block.getTransactionsList();
        LOGGER_TX.info("clearPending block#[{}] tx#[{}]", block.getNumber(), txList.size());

        if (!txList.isEmpty()) {
            Map<AionAddress, BigInteger> accountNonce = new HashMap<>();
            int cnt = 0;
            for (AionTransaction tx : txList) {
                accountNonce.computeIfAbsent(tx.getSenderAddress(), this::bestRepoNonce);

                LOGGER_TX.debug(
                    "Clear pending transaction, addr: {} hash: {}",
                    tx.getSenderAddress().toString(),
                    Hex.toHexString(tx.getTransactionHash()));

                AionTxReceipt receipt;
                if (receipts != null) {
                    receipt = receipts.get(cnt);
                } else {
                    AionTxInfo info = getTransactionInfo(tx.getTransactionHash(), block.getHash());
                    receipt = info.getReceipt();
                }

                removeBackupDBPendingTx(tx.getTransactionHash());
                fireTxUpdate(receipt, PendingTransactionState.INCLUDED, block);

                cnt++;
            }

            if (!accountNonce.isEmpty()) {
                txPool.removeTxsWithNonceLessThan(accountNonce);
            }
        }
    }

    private AionTxInfo getTransactionInfo(byte[] txHash, byte[] blockHash) {
        AionTxInfo info = blockchain.getTransactionStore().getTxInfo(txHash, blockHash);
        AionTransaction tx =
                blockchain
                        .getBlockByHash(info.getBlockHash())
                        .getTransactionsList()
                        .get(info.getIndex());
        info.setTransaction(tx);
        return info;
    }

    private void rerunTxsInPool(Block block) {

        addRepayTxToTxPool();

        List<AionTransaction> pendingTxl = txPool.snapshotAll();
        LOGGER_TX.info("rerunTxsInPool - snapshotAll tx[{}]", pendingTxl.size());

        if (!pendingTxl.isEmpty()) {
            for (AionTransaction tx : pendingTxl) {
                LOGGER_TX.debug("rerunTxsInPool - loop: {}", tx);

                AionTxExecSummary txSum = executeTx(tx);
                AionTxReceipt receipt = txSum.getReceipt();
                receipt.setTransaction(tx);

                if (txSum.isRejected()) {
                    LOGGER_TX.debug("Invalid transaction in txPool: {}", tx);

                    txPool.remove(new PooledTransaction(tx, receipt.getEnergyUsed()));
                    removeBackupDBPendingTx(tx.getTransactionHash());
                    fireTxUpdate(receipt, PendingTransactionState.DROPPED, block);
                } else {
                    if (repayTransaction.contains(tx)) {
                        txPool.updatePoolTransaction(new PooledTransaction(tx, receipt.getEnergyUsed()));
                    }

                    fireTxUpdate(receipt, PendingTransactionState.PENDING, block);
                }
            }
        }

        repayTransaction.clear();
    }

    private void addRepayTxToTxPool() {
        for (AionTransaction tx : repayTransaction) {
            // Add the energy limit value because it will get rerun soon after it is added
            PooledTransaction ptx = txPool.add(new PooledTransaction(tx, tx.getEnergyLimit()));
            if (ptx != null && ptx.tx.equals(tx)) {
                addPendingTxToBackupDatabase(tx);
            } else {
                // lowPriceTransaction been dropped!
                PooledTransaction droppedPtx = txPool.getDroppedPoolTx();
                if (droppedPtx != null) {
                    removeBackupDBPendingTx(droppedPtx.tx.getTransactionHash());
                    fireDroppedTx(droppedPtx.tx, TxResponse.DROPPED.name());
                }
            }
        }
    }

    private AionTxExecSummary executeTx(AionTransaction tx) {

        Block bestBlk = currentBestBlock.get();
        LOGGER_TX.debug("executeTx: {}", Hex.toHexString(tx.getTransactionHash()));

        try {
            // Booleans moved out here so their meaning is explicit.
            boolean isLocalCall = false;
            boolean incrementSenderNonce = true;
            boolean checkBlockEnergyLimit = false;

            // this parameter should not be relevant to execution
            byte[] difficulty = bestBlk.getDifficulty();
            // the pending state is executed on top of the best block
            long currentBlockNumber = bestBlk.getNumber() + 1;
            // simulating future block
            long timestamp = bestBlk.getTimestamp() + 1;
            // the limit is not checked so making it unlimited
            long blockNrgLimit = Long.MAX_VALUE;
            // assuming same person will mine the future block
            AionAddress miner = bestBlk.getCoinbase();

            return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                    difficulty,
                    currentBlockNumber,
                    timestamp,
                    blockNrgLimit,
                    miner,
                    tx,
                    pendingState,
                    isLocalCall,
                    incrementSenderNonce,
                    blockchain.forkUtility.is040ForkActive(currentBlockNumber),
                    checkBlockEnergyLimit,
                    LOGGER_VM,
                    BlockCachingContext.PENDING,
                    bestBlk.getNumber(),
                    blockchain.forkUtility.isUnityForkActive(currentBlockNumber));
        } catch (VmFatalException e) {
            LOGGER_VM.error("Shutdown due to a VM fatal error.", e);
            System.exit(SystemExitCodes.FATAL_VM_ERROR);
            return null;
        }
    }

    public synchronized BigInteger bestPendingStateNonce(AionAddress addr) {
        // Because the seedmode has no pendingPool concept, it only pass the transaction to the network directly.
        // So we will return the chainRepo nonce instead of pendingState nonce.
        return isSeedMode ? bestRepoNonce(addr) : pendingState.getNonce(addr);
    }

    private BigInteger bestRepoNonce(AionAddress addr) {
        return this.blockchain.getRepository().getNonce(addr);
    }

    private void dumpPool() {
        if (!poolDumpEnable) {
            return;
        }

        List<AionTransaction> txn = txPool.snapshotAll();
        Set<AionAddress> addrs = new HashSet<>();
        LOGGER_TX.info("");
        LOGGER_TX.info("=========== SnapshotAll");
        for (AionTransaction tx : txn) {
            addrs.add(tx.getSenderAddress());
            LOGGER_TX.info("{}", tx.toString());
        }

        txn = txPool.snapshot();
        LOGGER_TX.info("");
        LOGGER_TX.info("=========== Snapshot");
        for (AionTransaction tx : txn) {
            LOGGER_TX.info("{}", tx.toString());
        }

        LOGGER_TX.info("");
        LOGGER_TX.info("=========== Pool best nonce");
        for (AionAddress addr : addrs) {
            LOGGER_TX.info("{} {}", addr.toString(), txPool.bestPoolNonce(addr));
        }

        LOGGER_TX.info("");
        LOGGER_TX.info("=========== Cache pending tx");
        Set<AionAddress> cacheAddr = pendingTxCache.getCacheTxAccount();
        for (AionAddress addr : cacheAddr) {
            Map<BigInteger, AionTransaction> cacheMap = pendingTxCache.getCacheTxBySender(addr);
            if (cacheMap != null) {
                for (AionTransaction tx : cacheMap.values()) {
                    LOGGER_TX.info("{}", tx.toString());
                }
            }
        }

        LOGGER_TX.info("");
        LOGGER_TX.info("=========== db nonce");
        addrs.addAll(cacheAddr);
        for (AionAddress addr : addrs) {
            LOGGER_TX.info("{} {}", addr.toString(), bestRepoNonce(addr));
        }

        LOGGER_TX.info("");
        LOGGER_TX.info("=========== ps nonce");
        addrs.addAll(cacheAddr);
        for (AionAddress addr : addrs) {
            LOGGER_TX.info("{} {}", addr.toString(), bestPendingStateNonce(addr));
        }
    }

    private void recoverPoolnCache() {
        recoverPool();
        recoverCache();
    }

    private void recoverCache() {

        LOGGER_TX.info("pendingCacheTx loading from DB");
        long t1 = System.currentTimeMillis();
        List<byte[]> pendingCacheTxBytes = blockchain.getRepository().getCacheTx();

        List<AionTransaction> pendingTx = new ArrayList<>();
        for (byte[] b : pendingCacheTxBytes) {
            try {
                pendingTx.add(TxUtil.decode(b));
            } catch (Exception e) {
                LOGGER_TX.error("loadingPendingCacheTx error ", e);
            }
        }

        Map<AionAddress, SortedMap<BigInteger, AionTransaction>> sortedMap = new HashMap<>();
        for (AionTransaction tx : pendingTx) {
            if (sortedMap.get(tx.getSenderAddress()) == null) {
                SortedMap<BigInteger, AionTransaction> accountSortedMap = new TreeMap<>();
                accountSortedMap.put(tx.getNonceBI(), tx);

                sortedMap.put(tx.getSenderAddress(), accountSortedMap);
            } else {
                sortedMap.get(tx.getSenderAddress()).put(tx.getNonceBI(), tx);
            }
        }

        int cnt = 0;
        for (Map.Entry<AionAddress, SortedMap<BigInteger, AionTransaction>> e :
                sortedMap.entrySet()) {
            for (AionTransaction tx : e.getValue().values()) {
                pendingTxCache.addCacheTx(tx);
                cnt++;
            }
        }

        long t2 = System.currentTimeMillis() - t1;
        LOGGER_TX.info("{} pendingCacheTx loaded from DB into the pendingCache, {} ms", cnt, t2);
    }

    private void recoverPool() {

        LOGGER_TX.info("pendingPoolTx loading from DB");
        long t1 = System.currentTimeMillis();
        List<byte[]> pendingPoolTxBytes = blockchain.getRepository().getPoolTx();

        List<AionTransaction> pendingTx = new ArrayList<>();
        for (byte[] b : pendingPoolTxBytes) {
            try {
                pendingTx.add(TxUtil.decode(b));
            } catch (Exception e) {
                LOGGER_TX.error("loadingCachePendingTx error ", e);
            }
        }

        Map<AionAddress, SortedMap<BigInteger, AionTransaction>> sortedMap = new HashMap<>();
        for (AionTransaction tx : pendingTx) {
            if (sortedMap.get(tx.getSenderAddress()) == null) {
                SortedMap<BigInteger, AionTransaction> accountSortedMap = new TreeMap<>();
                accountSortedMap.put(tx.getNonceBI(), tx);

                sortedMap.put(tx.getSenderAddress(), accountSortedMap);
            } else {
                sortedMap.get(tx.getSenderAddress()).put(tx.getNonceBI(), tx);
            }
        }

        List<AionTransaction> pendingPoolTx = new ArrayList<>();

        for (Map.Entry<AionAddress, SortedMap<BigInteger, AionTransaction>> e :
                sortedMap.entrySet()) {
            pendingPoolTx.addAll(e.getValue().values());
        }

        addTransactionsFromCacheOrBackup(pendingPoolTx);
        long t2 = System.currentTimeMillis() - t1;
        LOGGER_TX.info(
                "{} pendingPoolTx loaded from DB loaded into the txpool, {} ms",
                pendingPoolTx.size(),
                t2);
    }

    public String getVersion() {
        return isSeedMode ? "0" : this.txPool.getVersion();
    }
}

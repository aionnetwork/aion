package org.aion.zero.impl.pendingState;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.aion.base.AionTransaction;
import org.aion.base.PooledTransaction;
import org.aion.base.TxUtil;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.txpool.TxPoolA0;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.types.TxResponse;
import org.aion.zero.impl.config.CfgFork;
import org.aion.base.AccountState;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.zero.impl.db.TransactionStore;
import org.aion.base.TransactionTypeRule;
import org.aion.zero.impl.vm.common.TxNrgRule;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.txpool.Constant;
import org.aion.txpool.ITxPool;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.valid.BeaconHashValidator;
import org.aion.zero.impl.valid.TXValidator;
import org.aion.zero.impl.valid.TransactionTypeValidator;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.slf4j.Logger;

public class AionPendingStateImpl implements IPendingState {

    private static final Logger LOGGER_TX = AionLoggerFactory.getLogger(LogEnum.TX.toString());
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());

    private IP2pMgr p2pMgr;

    public static class TransactionSortedSet extends TreeSet<AionTransaction> {

        private static final long serialVersionUID = 4941385879122799663L;

        public TransactionSortedSet() {
            super(
                    (tx1, tx2) -> {
                        long nonceDiff =
                                ByteUtil.byteArrayToLong(tx1.getNonce())
                                        - ByteUtil.byteArrayToLong(tx2.getNonce());

                        if (nonceDiff != 0) {
                            return nonceDiff > 0 ? 1 : -1;
                        }
                        return Arrays.compare(tx1.getTransactionHash(), tx2.getTransactionHash());
                    });
        }
    }

    private static final int MAX_VALIDATED_PENDING_TXS = 8192;

    private final int MAX_TXCACHE_FLUSH_SIZE = MAX_VALIDATED_PENDING_TXS >> 2;

    private final int MAX_REPLAY_TX_BUFFER_SIZE = MAX_VALIDATED_PENDING_TXS >> 2;

    private AionBlockchainImpl blockchain;

    private TransactionStore transactionStore;

    private Repository repository;

    private final ITxPool txPool;

    private IEventMgr evtMgr = null;

    private RepositoryCache<AccountState> pendingState;

    private AtomicReference<Block> best;

    private PendingTxCache pendingTxCache;

    private EventExecuteService ees;

    private List<AionTxExecSummary> txBuffer;

    /**
     * This buffer stores txs that come in with double the energy price as an existing tx with the same nonce
     *  They will be applied between blocks so it is easier for us to manage the state of the repo.
     */
    private List<AionTransaction> replayTxBuffer;

    private boolean bufferEnable;

    private boolean test;

    private boolean dumpPool;

    private boolean isSeed;

    private boolean loadPendingTx;

    private boolean poolBackUpEnable;

    private Map<byte[], byte[]> backupPendingPoolAdd;
    private Map<byte[], byte[]> backupPendingCacheAdd;
    private Set<byte[]> backupPendingPoolRemove;

    private ScheduledExecutorService ex;

    private boolean closeToNetworkBest = true;

    private BeaconHashValidator beaconHashValidator;

    private long fork040Block = -1;
    private boolean fork040Enable = false;

    class TxBuffTask implements Runnable {

        @Override
        public void run() {
            processTxBuffer();
        }
    }

    private synchronized void processTxBuffer() {
        if (bufferEnable && !txBuffer.isEmpty()) {

            List<PooledTransaction> txs = new ArrayList<>();
            try {
                for (AionTxExecSummary summary : txBuffer) {
                    txs.add(
                            new PooledTransaction(
                                    summary.getTransaction(),
                                    summary.getReceipt().getEnergyUsed()));
                }

                List<PooledTransaction> newPending = txPool.add(txs);

                if (LOGGER_TX.isTraceEnabled()) {
                    LOGGER_TX.trace(
                            "processTxBuffer buffer#{} poolNewTx#{}",
                            txs.size(),
                            newPending.size());
                }

                int cnt = 0;
                for (AionTxExecSummary summary : txBuffer) {
                    if (newPending.get(cnt) != null
                            && !newPending
                                    .get(cnt)
                                    .tx
                                    .equals(summary.getTransaction())) {
                        AionTxReceipt rp = new AionTxReceipt();
                        rp.setTransaction(newPending.get(cnt).tx);
                        fireTxUpdate(rp, PendingTransactionState.DROPPED, best.get());
                    }
                    cnt++;

                    fireTxUpdate(
                            summary.getReceipt(), PendingTransactionState.NEW_PENDING, best.get());
                }

                if (!txs.isEmpty() && !loadPendingTx) {
                    if (LOGGER_TX.isDebugEnabled()) {
                        LOGGER_TX.debug("processTxBuffer tx#{}", txs.size());
                    }
                    List<AionTransaction> aionTransactions = new ArrayList<>();
                    for (PooledTransaction pooledTransaction : txs) {
                        aionTransactions.add(pooledTransaction.tx);
                    }
                    AionImpl.inst().broadcastTransactions(aionTransactions);
                }
            } catch (Exception e) {
                LOGGER_TX.error("processTxBuffer throw ", e);
            }

            txBuffer.clear();
        }
    }

    private final class EpPS implements Runnable {

        boolean go = true;

        /**
         * When an object implementing interface <code>Runnable</code> is used to create a thread,
         * starting the thread causes the object's <code>run</code> method to be called in that
         * separately executing thread.
         *
         * <p>The general contract of the method <code>run</code> is that it may take any action
         * whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {
            while (go) {
                IEvent e = ees.take();

                if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()) {
                    go = false;
                }
            }
        }
    }

    private synchronized void backupPendingTx() {

        if (!backupPendingPoolAdd.isEmpty()) {
            repository.addTxBatch(backupPendingPoolAdd, true);
        }

        if (!backupPendingCacheAdd.isEmpty()) {
            repository.addTxBatch(backupPendingCacheAdd, false);
        }

        if (!backupPendingPoolRemove.isEmpty()) {
            repository.removeTxBatch(backupPendingPoolRemove, true);
        }

        repository.removeTxBatch(pendingTxCache.getClearTxHash(), false);
        repository.flush();

        backupPendingPoolAdd.clear();
        backupPendingCacheAdd.clear();
        backupPendingPoolRemove.clear();
        pendingTxCache.clearCacheTxHash();
    }

    public static AionPendingStateImpl create(CfgAion cfgAion, AionBlockchainImpl blockchain, AionRepositoryImpl repository, boolean forTest) {
        AionPendingStateImpl ps = new AionPendingStateImpl(cfgAion, repository);
        ps.init(blockchain, forTest);
        return ps;
    }

    private AionPendingStateImpl(CfgAion _cfgAion, AionRepositoryImpl _repository) {

        this.repository = _repository;

        this.isSeed = _cfgAion.getConsensus().isSeed();

        if (!isSeed) {

            Properties prop = new Properties();

            // The BlockEnergyLimit will be updated when the best block found.
            prop.put(
                    ITxPool.PROP_BLOCK_NRG_LIMIT,
                    String.valueOf(
                            CfgAion.inst().getConsensus().getEnergyStrategy().getUpperBound()));
            prop.put(ITxPool.PROP_BLOCK_SIZE_LIMIT, String.valueOf(Constant.MAX_BLK_SIZE));
            prop.put(
                    ITxPool.PROP_TX_TIMEOUT,
                    String.valueOf(CfgAion.inst().getTx().getTxPendingTimeout()));

            this.txPool = new TxPoolA0(prop);

        } else {
            txPool = null;
            LOGGER_TX.info("Seed mode is enable");
        }

        CfgFork cfg = _cfgAion.getFork();
        String fork040 = cfg.getProperties().getProperty("fork0.4.0");
        if (fork040 != null) {
            fork040Block = Long.valueOf(fork040);
        }
    }

    public void init(final AionBlockchainImpl blockchain, boolean test) {

        this.blockchain = blockchain;
        this.beaconHashValidator = blockchain.beaconHashValidator;

        this.best = new AtomicReference<>();
        this.test = test;

        if (!this.isSeed) {
            this.transactionStore = blockchain.getTransactionStore();

            this.evtMgr = blockchain.getEventMgr();
            this.poolBackUpEnable = CfgAion.inst().getTx().getPoolBackup();
            this.replayTxBuffer = new ArrayList<>();
            this.pendingTxCache =
                    new PendingTxCache(CfgAion.inst().getTx().getCacheMax(), poolBackUpEnable);
            this.pendingState = repository.startTracking();

            this.dumpPool = test || CfgAion.inst().getTx().getPoolDump();

            ees = new EventExecuteService(1000, "EpPS", Thread.MAX_PRIORITY, LOGGER_TX);
            ees.setFilter(setEvtFilter());

            IHandler blkHandler = this.evtMgr.getHandler(IHandler.TYPE.BLOCK0.getValue());
            if (blkHandler != null) {
                blkHandler.eventCallback(new EventCallback(ees, LOGGER_TX));
            }

            if (poolBackUpEnable) {
                this.backupPendingPoolAdd = new HashMap<>();
                this.backupPendingCacheAdd = new HashMap<>();
                this.backupPendingPoolRemove = new HashSet<>();
            }

            this.bufferEnable = !test && CfgAion.inst().getTx().getBuffer();
            if (bufferEnable) {
                LOGGER_TX.info("TxBuf enable!");
                this.ex = Executors.newSingleThreadScheduledExecutor();
                this.ex.scheduleWithFixedDelay(new TxBuffTask(), 5000, 500, TimeUnit.MILLISECONDS);

                this.txBuffer = Collections.synchronizedList(new ArrayList<>());
            }

            if (!test) {
                ees.start(new EpPS());
            }
        }
    }

    private Set<Integer> setEvtFilter() {
        Set<Integer> eventSN = new HashSet<>();

        int sn = IHandler.TYPE.BLOCK0.getValue() << 8;
        eventSN.add(sn + EventBlock.CALLBACK.ONBEST0.getValue());

        if (poolBackUpEnable) {
            sn = IHandler.TYPE.TX0.getValue() << 8;
            eventSN.add(sn + EventTx.CALLBACK.TXBACKUP0.getValue());
        }

        return eventSN;
    }

    public synchronized RepositoryCache<?> getRepository() {
        // Todo : no class use this method.
        return pendingState;
    }

    public int getPendingTxSize() {
        return isSeed ? 0 : this.txPool.size();
    }

    @Override
    public synchronized List<AionTransaction> getPendingTransactions() {
        return isSeed ? new ArrayList<>() : this.txPool.snapshot();
    }

    public synchronized Block getBestBlock() {
        best.set(blockchain.getBestBlock());
        return best.get();
    }

    /**
     * TODO: when we removed libNc, timers were not introduced yet, we must rework the model that
     * libAion uses to work with timers
     */
    public synchronized TxResponse addPendingTransaction(AionTransaction tx) {
        return addPendingTransactions(Collections.singletonList(tx)).get(0);
    }

    public boolean isValid(AionTransaction tx) {
        return (TXValidator.isValid(tx, blockchain.isUnityForkEnabledAtNextBlock()))
                && TransactionTypeValidator.isValid(tx)
                && beaconHashValidator.validateTxForPendingState(tx);
    }

    /**
     * Tries to add the given transactions to the PendingState
     *
     * @param transactions, the list of AionTransactions to be added
     * @return a list of TxResponses of the same size as the input param transactions The entries in
     *     the returned list of responses correspond one-to-one with the input txs
     */
    public synchronized List<TxResponse> addPendingTransactions(
            List<AionTransaction> transactions) {

        if ((isSeed || !closeToNetworkBest) && !loadPendingTx) {
            return seedProcess(transactions);
        }

        List<AionTransaction> newPending = new ArrayList<>();
        List<AionTransaction> newLargeNonceTx = new ArrayList<>();
        List<TxResponse> txResponses = new ArrayList<>();

        for (AionTransaction tx : transactions) {
            BigInteger txNonce = tx.getNonceBI();
            BigInteger bestPSNonce = bestPendingStateNonce(tx.getSenderAddress());
            AionAddress txFrom = tx.getSenderAddress();

            int cmp = txNonce.compareTo(bestPSNonce);

            // This case happens when we have already received a tx with a larger nonce
            // from the address txFrom
            if (cmp > 0) {
                if (isInTxCache(txFrom, txNonce)) {
                    txResponses.add(TxResponse.ALREADY_CACHED);
                } else {
                    newLargeNonceTx.add(tx);
                    addToTxCache(tx);

                    if (poolBackUpEnable) {
                        backupPendingCacheAdd.put(tx.getTransactionHash(), tx.getEncoded());
                    }

                    if (LOGGER_TX.isTraceEnabled()) {
                        LOGGER_TX.trace(
                                "addPendingTransactions addToCache due to largeNonce: from = {}, nonce = {}",
                                txFrom,
                                txNonce);
                    }

                    // Transaction cached due to large nonce
                    txResponses.add(TxResponse.CACHED_NONCE);
                }
            }
            // This case happens when this transaction has been received before, but was
            // cached for some reason
            else if (cmp == 0) {
                if (txPool.size() > MAX_VALIDATED_PENDING_TXS) {
                    if (isInTxCache(txFrom, txNonce)) {
                        txResponses.add(TxResponse.ALREADY_CACHED);
                    } else {
                        newLargeNonceTx.add(tx);
                        addToTxCache(tx);

                        if (poolBackUpEnable) {
                            backupPendingCacheAdd.put(tx.getTransactionHash(), tx.getEncoded());
                        }

                        if (LOGGER_TX.isTraceEnabled()) {
                            LOGGER_TX.trace(
                                    "addPendingTransactions addToCache due to poolMax: from = {}, nonce = {}",
                                    txFrom,
                                    txNonce);
                        }

                        // Transaction cached because the pool is full
                        txResponses.add(TxResponse.CACHED_POOLMAX);
                    }
                } else {
                    // TODO: need to implement better cache return Strategy
                    Map<BigInteger, AionTransaction> cache = pendingTxCache.getCacheTx(txFrom);

                    int limit = 0;
                    Set<AionAddress> addr = pendingTxCache.getCacheTxAccount();
                    if (!addr.isEmpty()) {
                        limit = MAX_TXCACHE_FLUSH_SIZE / addr.size();

                        if (limit == 0) {
                            limit = 1;
                        }
                    }

                    if (LOGGER_TX.isTraceEnabled()) {
                        LOGGER_TX.trace(
                                "addPendingTransactions from cache: from {}, size {}",
                                txFrom,
                                cache.size());
                    }

                    boolean added = false;

                    do {
                        TxResponse implResponse = addPendingTransactionImpl(tx);
                        if (!added) {
                            txResponses.add(implResponse);
                            added = true;
                        }
                        if (implResponse.equals(TxResponse.SUCCESS)) {
                            newPending.add(tx);

                            if (poolBackUpEnable) {
                                backupPendingPoolAdd.put(tx.getTransactionHash(), tx.getEncoded());
                            }
                        } else {
                            break;
                        }

                        if (LOGGER_TX.isTraceEnabled()) {
                            LOGGER_TX.trace("cache: from {}, nonce {}", txFrom, txNonce.toString());
                        }

                        txNonce = txNonce.add(BigInteger.ONE);
                    } while (cache != null
                            && (tx = cache.get(txNonce)) != null
                            && (limit-- > 0)
                            && (txBuffer == null ? txPool.size() : txPool.size() + txBuffer.size())
                                    < MAX_VALIDATED_PENDING_TXS);
                }
            }
            // This case happens when this tx was received before, but never sealed,
            // typically because of low energy
            else if (bestRepoNonce(txFrom).compareTo(txNonce) < 1) {
                // repay Tx
                TxResponse implResponse = addPendingTransactionImpl(tx);
                if (implResponse.equals(TxResponse.SUCCESS)) {
                    newPending.add(tx);
                    txResponses.add(TxResponse.REPAID);

                    if (poolBackUpEnable) {
                        backupPendingPoolAdd.put(tx.getTransactionHash(), tx.getEncoded());
                    }
                } else {
                    txResponses.add(implResponse);
                }
            }
            // This should mean that the transaction has already been sealed in the repo
            else {
                txResponses.add(TxResponse.ALREADY_SEALED);
            }
        }

        if (LOGGER_TX.isTraceEnabled()) {
            LOGGER_TX.trace(
                    "Wire transaction list added: total: {}, newPending: {}, cached: {}, valid (added to pending): {} pool_size:{}",
                    transactions.size(),
                    newPending,
                    newLargeNonceTx.size(),
                    txPool.size());
        }

        if (!newPending.isEmpty()) {
            IEvent evtRecv = new EventTx(EventTx.CALLBACK.PENDINGTXRECEIVED0);
            evtRecv.setFuncArgs(Collections.singletonList(newPending));
            this.evtMgr.newEvent(evtRecv);

            IEvent evtChange = new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0);
            this.evtMgr.newEvent(evtChange);
        }

        if (!loadPendingTx) {
            if (bufferEnable) {
                if (!newLargeNonceTx.isEmpty()) {
                    AionImpl.inst().broadcastTransactions(newLargeNonceTx);
                }
            } else if (!test && (!newPending.isEmpty() || !newLargeNonceTx.isEmpty())) {
                AionImpl.inst()
                        .broadcastTransactions(
                                Stream.concat(newPending.stream(), newLargeNonceTx.stream())
                                        .collect(Collectors.toList()));
            }
        }

        return txResponses;
    }

    private List<TxResponse> seedProcess(List<AionTransaction> transactions) {
        List<AionTransaction> newTx = new ArrayList<>();
        List<TxResponse> txResponses = new ArrayList<>();
        for (AionTransaction tx : transactions) {
            if (isValid(tx)) {
                newTx.add(tx);
                txResponses.add(TxResponse.SUCCESS);
            } else {
                LOGGER_TX.error(
                        "tx is not valid: tx[{}]", tx.toString());
                txResponses.add(TxResponse.INVALID_TX);
            }
        }

        if (!newTx.isEmpty()) {
            AionImpl.inst().broadcastTransactions(newTx);
        }

        return txResponses;
    }

    private boolean inPool(BigInteger txNonce, AionAddress from) {
        return (this.txPool.bestPoolNonce(from).compareTo(txNonce) > -1);
    }

    private void fireTxUpdate(
            AionTxReceipt txReceipt, PendingTransactionState state, Block block) {
        if (LOGGER_TX.isTraceEnabled()) {
            LOGGER_TX.trace(
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
        }

        IEvent evt = new EventTx(EventTx.CALLBACK.PENDINGTXUPDATE0);
        List<Object> args = new ArrayList<>();
        args.add(txReceipt);
        args.add(state.getValue());
        args.add(block);
        evt.setFuncArgs(args);
        this.evtMgr.newEvent(evt);
    }

    /**
     * Executes pending tx on the latest best block Fires pending state update
     *
     * @param tx transaction come from API or P2P
     * @return SUCCESS if transaction gets NEW_PENDING state, else appropriate message such as
     *     DROPPED, INVALID_TX, etc.
     */
    private TxResponse addPendingTransactionImpl(final AionTransaction tx) {

        if (!isValid(tx)) {
            LOGGER_TX.error("invalid Tx [{}]", tx.toString());
            fireDroppedTx(tx, "INVALID_TX");
            return TxResponse.INVALID_TX;
        }

        if (!TxNrgRule.isValidTxNrgPrice(tx.getEnergyPrice())) {
            LOGGER_TX.error("invalid Tx Nrg price [{}]", tx.toString());
            fireDroppedTx(tx, "INVALID_TX_NRG_PRICE");
            return TxResponse.INVALID_TX_NRG_PRICE;
        }

        AionTxExecSummary txSum;
        boolean ip = inPool(tx.getNonceBI(), tx.getSenderAddress());
        if (ip) {
            // check energy usage
            PooledTransaction poolTx = txPool.getPoolTx(tx.getSenderAddress(), tx.getNonceBI());
            if (poolTx == null) {
                LOGGER_TX.error(
                        "addPendingTransactionImpl no same tx nonce in the pool {}", tx.toString());
                fireDroppedTx(tx, "REPAYTX_POOL_EXCEPTION");
                return TxResponse.REPAYTX_POOL_EXCEPTION;
            } else {
                long price = (poolTx.tx.getEnergyPrice() << 1);
                if (price > 0 && price <= tx.getEnergyPrice()) {
                    if (replayTxBuffer.size() < MAX_REPLAY_TX_BUFFER_SIZE) {
                        replayTxBuffer.add(tx);
                        return TxResponse.REPAID;
                    } else {
                        return TxResponse.DROPPED;
                    }
                } else {
                    fireDroppedTx(tx, "REPAYTX_LOWPRICE");
                    return TxResponse.REPAYTX_LOWPRICE;
                }
            }
        } else {
            txSum = executeTx(tx, false);
        }

        if (txSum.isRejected()) {
            if (LOGGER_TX.isTraceEnabled()) {
                LOGGER_TX.trace(
                        "addPendingTransactionImpl tx "
                                + Hex.toHexString(tx.getTransactionHash())
                                + " is rejected due to: {}",
                        txSum.getReceipt().getError());
            }
            fireTxUpdate(txSum.getReceipt(), PendingTransactionState.DROPPED, best.get());
            return TxResponse.DROPPED;
        } else {
            PooledTransaction pendingTx = new PooledTransaction(tx, txSum.getReceipt().getEnergyUsed());

            if (LOGGER_TX.isTraceEnabled()) {
                LOGGER_TX.trace("addPendingTransactionImpl validTx {}", tx.toString());
            }

            if (bufferEnable) {
                txBuffer.add(txSum);
            } else {
                PooledTransaction rtn = this.txPool.add(pendingTx);
                if (rtn != null && !rtn.equals(pendingTx)) {
                    AionTxReceipt rp = new AionTxReceipt();
                    rp.setTransaction(rtn.tx);

                    if (poolBackUpEnable) {
                        backupPendingPoolRemove.add(tx.getTransactionHash().clone());
                    }
                    fireTxUpdate(rp, PendingTransactionState.DROPPED, best.get());
                }

                fireTxUpdate(txSum.getReceipt(), PendingTransactionState.NEW_PENDING, best.get());
            }

            return TxResponse.SUCCESS;
        }
    }

    private void fireDroppedTx(AionTransaction tx, String error) {

        if (LOGGER_TX.isErrorEnabled()) {
            LOGGER_TX.error("Tx dropped {} [{}]", error, tx.toString());
        }

        AionTxReceipt rp = new AionTxReceipt();
        rp.setTransaction(tx);
        rp.setError(error);
        fireTxUpdate(rp, PendingTransactionState.DROPPED, best.get());
    }

    private AionTxReceipt createDroppedReceipt(PooledTransaction pooledTx, String error) {
        AionTxReceipt txReceipt = new AionTxReceipt();
        txReceipt.setTransaction(pooledTx.tx);
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
                throw new RuntimeException(
                        "Pending state can't find common ancestor: one of blocks has a gap");
            }
        }
        return b1;
    }

    /**
     * AKI-608
     * The method called by the AionblockchainImpl through callback, currently it will block the block import.
     * TODO :  Sync or Async from the callback.
     * @param newBlock
     * @param receipts
     */
    @Override
    public synchronized void applyBlockUpdate(Block newBlock, List<AionTxReceipt> receipts) {

        if (isSeed) {
            // seed mode doesn't need to update the pendingState
            return;
        }

        if (best.get() != null && !best.get().isParentOf(newBlock)) {

            // need to switch the state to another fork

            Block commonAncestor = findCommonAncestor(best.get(), newBlock);

            if (LOGGER_TX.isDebugEnabled()) {
                LOGGER_TX.debug(
                        "New best block from another fork: "
                                + newBlock.getShortDescr()
                                + ", old best: "
                                + best.get().getShortDescr()
                                + ", ancestor: "
                                + commonAncestor.getShortDescr());
            }

            // first return back the transactions from forked blocks
            Block rollback = best.get();
            while (!rollback.isEqual(commonAncestor)) {
                if (LOGGER_TX.isDebugEnabled()) {
                    LOGGER_TX.debug("Rollback: {}", rollback.getShortDescr());
                }
                List<AionTransaction> atl = rollback.getTransactionsList();
                for (AionTransaction atx : atl) {
                    /* We can add the Tx directly to the pool with a junk energyConsumed value
                     because all txs in the pool are going to be re-run in rerunTxsInPool(best.get()) */
                    txPool.add(new PooledTransaction(atx, 1));
                }
                rollback = blockchain.getBlockByHash(rollback.getParentHash());
            }

            // rollback the state snapshot to the ancestor
            pendingState = repository.getSnapshotTo(commonAncestor.getStateRoot()).startTracking();

            // next process blocks from new fork
            Block main = newBlock;
            List<Block> mainFork = new ArrayList<>();
            while (!main.isEqual(commonAncestor)) {
                if (LOGGER_TX.isDebugEnabled()) {
                    LOGGER_TX.debug("Mainfork: {}", main.getShortDescr());
                }

                mainFork.add(main);
                main = blockchain.getBlockByHash(main.getParentHash());
            }

            // processing blocks from ancestor to new block
            for (int i = mainFork.size() - 1; i >= 0; i--) {
                processBestInternal(mainFork.get(i), null);
            }
        } else {
            if (LOGGER_TX.isDebugEnabled()) {
                LOGGER_TX.debug("PendingStateImpl.processBest: " + newBlock.getShortDescr());
            }
            //noinspection unchecked
            processBestInternal(newBlock, receipts);
        }

        best.set(newBlock);

        closeToNetworkBest = best.get().getNumber() + 128 >= getPeersBestBlk13();

        if (LOGGER_TX.isDebugEnabled()) {
            LOGGER_TX.debug(
                    "PendingStateImpl.processBest: closeToNetworkBest[{}]", closeToNetworkBest);
        }

        rerunTxsInPool(best.get());

        txPool.updateBlkNrgLimit(best.get().getNrgLimit());

        flushCachePendingTx();

        List<IEvent> events = new ArrayList<>();
        events.add(new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0));

        if (poolBackUpEnable) {
            long t1 = System.currentTimeMillis();
            backupPendingTx();
            long t2 = System.currentTimeMillis();
            LOGGER_TX.debug("Pending state backupPending took {} ms", t2 - t1);
        }

        this.evtMgr.newEvents(events);

        // This is for debug purpose, do not use in the regular kernel running.
        if (this.dumpPool) {
            DumpPool();
        }
    }

    private void flushCachePendingTx() {
        Set<AionAddress> cacheTxAccount = this.pendingTxCache.getCacheTxAccount();

        if (cacheTxAccount.isEmpty()) {
            return;
        }

        if (LOGGER_TX.isDebugEnabled()) {
            LOGGER_TX.debug(
                    "PendingStateImpl.flushCachePendingTx: acc#[{}]", cacheTxAccount.size());
        }

        Map<AionAddress, BigInteger> nonceMap = new HashMap<>();
        for (AionAddress addr : cacheTxAccount) {
            nonceMap.put(addr, bestPendingStateNonce(addr));
        }

        List<AionTransaction> newPendingTx = this.pendingTxCache.flush(nonceMap);

        if (LOGGER_TX.isDebugEnabled()) {
            LOGGER_TX.debug(
                    "PendingStateImpl.flushCachePendingTx: newPendingTx_size[{}]",
                    newPendingTx.size());
        }

        if (!newPendingTx.isEmpty()) {
            addPendingTransactions(newPendingTx);
        }
    }

    private void processBestInternal(Block block, List<AionTxReceipt> receipts) {

        clearPending(block, receipts);

        clearOutdated(block.getNumber());
    }

    private void clearOutdated(final long blockNumber) {

        List<PooledTransaction> outdated = new ArrayList<>();

        final long timeout = this.txPool.getOutDateTime();
        for (PooledTransaction pooledTx : this.txPool.getOutdatedList()) {
            outdated.add(pooledTx);

            if (poolBackUpEnable) {
                backupPendingPoolRemove.add(pooledTx.tx.getTransactionHash().clone());
            }
            // @Jay
            // TODO : considering add new state - TIMEOUT
            fireTxUpdate(
                    createDroppedReceipt(
                            pooledTx, "Tx was not included into last " + timeout + " seconds"),
                    PendingTransactionState.DROPPED,
                    best.get());
        }

        if (LOGGER_TX.isDebugEnabled()) {
            LOGGER_TX.debug("clearOutdated block#[{}] tx#[{}]", blockNumber, outdated.size());
        }

        if (outdated.isEmpty()) {
            return;
        }

        txPool.remove(outdated);
    }

    @SuppressWarnings("unchecked")
    private void clearPending(Block block, List<AionTxReceipt> receipts) {
        List<AionTransaction> txsInBlock = block.getTransactionsList();

        if (txsInBlock == null) return;

        if (LOGGER_TX.isDebugEnabled()) {
            LOGGER_TX.debug(
                    "clearPending block#[{}] tx#[{}]",
                    block.getNumber(),
                    txsInBlock.size());
        }

        Map<AionAddress, BigInteger> accountNonce = new HashMap<>();
        int cnt = 0;
        for (AionTransaction tx : block.getTransactionsList()) {
            accountNonce.computeIfAbsent(
                    tx.getSenderAddress(),
                    k -> this.repository.getNonce(tx.getSenderAddress()));

            if (LOGGER_TX.isTraceEnabled()) {
                LOGGER_TX.trace(
                        "Clear pending transaction, addr: {} hash: {}",
                        tx.getSenderAddress().toString(),
                        Hex.toHexString(tx.getTransactionHash()));
            }

            AionTxReceipt receipt;
            if (receipts != null) {
                receipt = receipts.get(cnt);
            } else {
                AionTxInfo info = getTransactionInfo(tx.getTransactionHash(), block.getHash());
                receipt = info.getReceipt();
            }

            if (poolBackUpEnable) {
                backupPendingPoolRemove.add(tx.getTransactionHash().clone());
            }
            fireTxUpdate(receipt, PendingTransactionState.INCLUDED, block);

            cnt++;
        }

        if (!accountNonce.isEmpty()) {
            this.txPool.removeTxsWithNonceLessThan(accountNonce);
        }
    }

    private AionTxInfo getTransactionInfo(byte[] txHash, byte[] blockHash) {
        AionTxInfo info = transactionStore.getTxInfo(txHash, blockHash);
        AionTransaction tx =
                blockchain
                        .getBlockByHash(info.getBlockHash())
                        .getTransactionsList()
                        .get(info.getIndex());
        info.setTransaction(tx);
        return info;
    }

    @SuppressWarnings("UnusedReturnValue")
    private List<AionTransaction> rerunTxsInPool(Block block) {

        pendingState = repository.startTracking();

        for (AionTransaction tx : replayTxBuffer) {
            // Add a junk energyConsumed value because it will get rerun soon after it is added
            txPool.add(new PooledTransaction(tx, tx.getEnergyLimit()));
        }
        replayTxBuffer.clear();

        processTxBuffer();
        List<AionTransaction> pendingTxl = this.txPool.snapshotAll();
        List<AionTransaction> rtn = new ArrayList<>();
        if (LOGGER_TX.isInfoEnabled()) {
            LOGGER_TX.info("rerunTxsInPool - snapshotAll tx[{}]", pendingTxl.size());
        }
        for (AionTransaction tx : pendingTxl) {
            if (LOGGER_TX.isTraceEnabled()) {
                LOGGER_TX.trace("rerunTxsInPool - loop: " + tx.toString());
            }

            AionTxExecSummary txSum = executeTx(tx, false);
            AionTxReceipt receipt = txSum.getReceipt();
            receipt.setTransaction(tx);

            if (txSum.isRejected()) {
                if (LOGGER_TX.isDebugEnabled()) {
                    LOGGER_TX.debug("Invalid transaction in txpool: {}", tx);
                }
                txPool.remove(new PooledTransaction(tx, receipt.getEnergyUsed()));

                if (poolBackUpEnable) {
                    backupPendingPoolRemove.add(tx.getTransactionHash().clone());
                }
                fireTxUpdate(receipt, PendingTransactionState.DROPPED, block);
            } else {
                fireTxUpdate(receipt, PendingTransactionState.PENDING, block);
                rtn.add(tx);
            }
        }

        return rtn;
    }

    private Set<AionAddress> getTxsAccounts(List<AionTransaction> txn) {
        Set<AionAddress> rtn = new HashSet<>();
        for (AionTransaction tx : txn) {
            rtn.add(tx.getSenderAddress());
        }
        return rtn;
    }

    private AionTxExecSummary executeTx(AionTransaction tx, boolean inPool) {

        Block bestBlk = best.get();
        if (LOGGER_TX.isTraceEnabled()) {
            LOGGER_TX.trace("executeTx: {}", Hex.toHexString(tx.getTransactionHash()));
        }

        if (fork040Block > -1 && !fork040Enable) {
            fork040Enable = bestBlk.getNumber() >= fork040Block;
        }

        try {
            // Booleans moved out here so their meaning is explicit.
            boolean isLocalCall = false;
            boolean incrementSenderNonce = !inPool;
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
                    fork040Enable,
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
        return isSeed ? BigInteger.ZERO : this.pendingState.getNonce(addr);
    }

    private BigInteger bestRepoNonce(AionAddress addr) {
        return this.repository.getNonce(addr);
    }

    private void addToTxCache(AionTransaction tx) {
        this.pendingTxCache.addCacheTx(tx);
    }

    private boolean isInTxCache(AionAddress addr, BigInteger nonce) {
        return this.pendingTxCache.isInCache(addr, nonce);
    }

    public void shutDown() {
        if (this.bufferEnable) {
            ex.shutdown();
        }

        if (ees != null) {
            ees.shutdown();
        }
    }

    public synchronized void DumpPool() {
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
            Map<BigInteger, AionTransaction> cacheMap = pendingTxCache.getCacheTx(addr);
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

    public void loadPendingTx() {

        loadPendingTx = true;
        recoverPool();
        recoverCache();
        loadPendingTx = false;
    }

    public void checkAvmFlag() {

        long bestBlockNumber = getBestBlock().getNumber();

        if (fork040Block != -1 && bestBlockNumber >= fork040Block) {
            TransactionTypeRule.allowAVMContractTransaction();
        }
    }

    public void setP2pMgr(final IP2pMgr p2pMgr) {
        if (!this.isSeed) {
            this.p2pMgr = p2pMgr;
        }
    }

    private long getPeersBestBlk13() {
        if (this.p2pMgr == null) {
            return 0;
        }

        List<Long> peersBest = new ArrayList<>();
        for (INode node : p2pMgr.getActiveNodes().values()) {
            peersBest.add(node.getBestBlockNumber());
        }

        if (peersBest.isEmpty()) {
            return 0;
        }

        peersBest.sort(Comparator.reverseOrder());

        int position = peersBest.size() / 3;
        if (position > 3) {
            position -= 1;
        }

        if (LOGGER_TX.isDebugEnabled()) {
            StringBuilder blk = new StringBuilder();
            for (Long l : peersBest) {
                blk.append(l.toString()).append(" ");
            }

            LOGGER_TX.debug(
                    "getPeersBestBlk13 peers[{}] 1/3[{}] PeersBest[{}]",
                    peersBest.size(),
                    peersBest.get(position),
                    blk.toString());
        }

        return peersBest.get(position);
    }

    private void recoverCache() {

        LOGGER_TX.info("pendingCacheTx loading from DB");
        long t1 = System.currentTimeMillis();
        //noinspection unchecked
        List<byte[]> pendingCacheTxBytes = repository.getCacheTx();

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
        //noinspection unchecked
        List<byte[]> pendingPoolTxBytes = repository.getPoolTx();

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

        addPendingTransactions(pendingPoolTx);
        long t2 = System.currentTimeMillis() - t1;
        LOGGER_TX.info(
                "{} pendingPoolTx loaded from DB loaded into the txpool, {} ms",
                pendingPoolTx.size(),
                t2);
    }

    public String getVersion() {
        return isSeed ? "0" : this.txPool.getVersion();
    }

    public void updateBest() {
        getBestBlock();
    }
}

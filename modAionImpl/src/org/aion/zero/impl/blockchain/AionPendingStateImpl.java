package org.aion.zero.impl.blockchain;

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
import java.util.ServiceLoader;
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
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.mcf.blockchain.TxResponse;
import org.aion.mcf.config.CfgFork;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.db.TransactionStore;
import org.aion.mcf.evt.IListenerBase.PendingTransactionState;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.txpool.Constant;
import org.aion.txpool.ITxPool;
import org.aion.txpool.TxPoolModule;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.vm.BulkExecutor;
import org.aion.vm.exception.VMException;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.valid.TXValidator;
import org.aion.zero.impl.valid.TransactionTypeValidator;
import org.aion.base.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

public class AionPendingStateImpl implements IPendingStateInternal<AionBlock, AionTransaction> {

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

    private IAionBlockchain blockchain;

    private TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> transactionStore;

    private Repository repository;

    private ITxPool<AionTransaction> txPool;

    private IEventMgr evtMgr = null;

    private RepositoryCache<AccountState, IBlockStoreBase<?, ?>> pendingState;

    private AtomicReference<AionBlock> best;

    private PendingTxCache pendingTxCache;

    private EventExecuteService ees;

    private List<AionTxExecSummary> txBuffer;

    private boolean bufferEnable;

    private boolean dumpPool;

    private boolean isSeed;

    private boolean loadPendingTx;

    private boolean poolBackUp;

    private Map<byte[], byte[]> backupPendingPoolAdd;
    private Map<byte[], byte[]> backupPendingCacheAdd;
    private Set<byte[]> backupPendingPoolRemove;

    private ScheduledExecutorService ex;

    private boolean closeToNetworkBest = true;

    private static long NRGPRICE_MIN = 10_000_000_000L; // 10 PLAT  (10 * 10 ^ -9 AION)
    private static long NRGPRICE_MAX = 9_000_000_000_000_000_000L; //  9 AION

    private long fork040Block = -1;
    private boolean fork040Enable = false;

    class TxBuffTask implements Runnable {

        @Override
        public void run() {
            processTxBuffer();
        }
    }

    private synchronized void processTxBuffer() {
        if (!txBuffer.isEmpty()) {
            List<AionTransaction> txs = new ArrayList<>();
            try {
                for (AionTxExecSummary s : txBuffer) {
                    txs.add(s.getTransaction());
                }

                List<AionTransaction> newPending = txPool.add(txs);

                if (LOGGER_TX.isTraceEnabled()) {
                    LOGGER_TX.trace(
                            "processTxBuffer buffer#{} poolNewTx#{}",
                            txs.size(),
                            newPending.size());
                }

                int cnt = 0;
                for (AionTxExecSummary summary : txBuffer) {
                    if (newPending.get(cnt) != null
                            && !newPending.get(cnt).equals(summary.getTransaction())) {
                        AionTxReceipt rp = new AionTxReceipt();
                        rp.setTransaction(newPending.get(cnt));
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
                    AionImpl.inst().broadcastTransactions(txs);
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

                if (e.getEventType() == IHandler.TYPE.BLOCK0.getValue()
                        && e.getCallbackType() == EventBlock.CALLBACK.ONBEST0.getValue()) {
                    long t1 = System.currentTimeMillis();
                    processBest((AionBlock) e.getFuncArgs().get(0), (List) e.getFuncArgs().get(1));

                    if (LOGGER_TX.isDebugEnabled()) {
                        long t2 = System.currentTimeMillis();
                        LOGGER_TX.debug("Pending state update took {} ms", t2 - t1);
                    }
                } else if (e.getEventType() == IHandler.TYPE.TX0.getValue()
                        && e.getCallbackType() == EventTx.CALLBACK.TXBACKUP0.getValue()) {
                    long t1 = System.currentTimeMillis();
                    backupPendingTx();

                    if (LOGGER_TX.isDebugEnabled()) {
                        long t2 = System.currentTimeMillis();
                        LOGGER_TX.debug("Pending state backupPending took {} ms", t2 - t1);
                    }
                } else if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()) {
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

    private static AionPendingStateImpl initializeAionPendingState(
            CfgAion _cfgAion,
            AionRepositoryImpl _repository,
            AionBlockchainImpl _blockchain,
            boolean test) {
        AionPendingStateImpl ps = new AionPendingStateImpl(_cfgAion, _repository);
        ps.init(_blockchain, test);
        return ps;
    }

    private static class Holder {

        static final AionPendingStateImpl INSTANCE =
                initializeAionPendingState(
                        CfgAion.inst(),
                        AionRepositoryImpl.inst(),
                        AionBlockchainImpl.inst(),
                        false);
    }

    public static AionPendingStateImpl inst() {
        return Holder.INSTANCE;
    }

    public static AionPendingStateImpl createForTesting(
            CfgAion _cfgAion, AionBlockchainImpl _blockchain, AionRepositoryImpl _repository) {
        return initializeAionPendingState(_cfgAion, _repository, _blockchain, true);
    }

    private AionPendingStateImpl(CfgAion _cfgAion, AionRepositoryImpl _repository) {

        this.repository = _repository;

        this.isSeed = _cfgAion.getConsensus().isSeed();

        if (!isSeed) {

            try {
                ServiceLoader.load(TxPoolModule.class);
            } catch (Exception e) {
                LOGGER_TX.error("load TxPoolModule service fail!", e);
                throw e;
            }

            Properties prop = new Properties();

            prop.put(TxPoolModule.MODULENAME, "org.aion.txpool.zero.TxPoolA0");
            // The BlockEnergyLimit will be updated when the best block found.
            prop.put(
                    ITxPool.PROP_BLOCK_NRG_LIMIT,
                    String.valueOf(
                            CfgAion.inst().getConsensus().getEnergyStrategy().getUpperBound()));
            prop.put(ITxPool.PROP_BLOCK_SIZE_LIMIT, String.valueOf(Constant.MAX_BLK_SIZE));
            prop.put(ITxPool.PROP_TX_TIMEOUT, "86400");
            TxPoolModule txPoolModule;
            try {
                txPoolModule = TxPoolModule.getSingleton(prop);
                //noinspection unchecked
                this.txPool = (ITxPool<AionTransaction>) txPoolModule.getTxPool();
            } catch (Exception e) {
                LOGGER_TX.error("TxPoolModule getTxPool fail!", e);
            }

        } else {
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
        this.best = new AtomicReference<>();

        if (!this.isSeed) {
            this.transactionStore = blockchain.getTransactionStore();

            this.evtMgr = blockchain.getEventMgr();
            this.poolBackUp = CfgAion.inst().getTx().getPoolBackup();
            this.pendingTxCache =
                    new PendingTxCache(CfgAion.inst().getTx().getCacheMax(), poolBackUp);
            this.pendingState = repository.startTracking();

            this.dumpPool = CfgAion.inst().getTx().getPoolDump();

            ees = new EventExecuteService(1000, "EpPS", Thread.MAX_PRIORITY, LOGGER_TX);
            ees.setFilter(setEvtFilter());

            regBlockEvents();

            IHandler blkHandler = this.evtMgr.getHandler(IHandler.TYPE.BLOCK0.getValue());
            if (blkHandler != null) {
                blkHandler.eventCallback(new EventCallback(ees, LOGGER_TX));
            }

            if (poolBackUp) {
                this.backupPendingPoolAdd = new HashMap<>();
                this.backupPendingCacheAdd = new HashMap<>();
                this.backupPendingPoolRemove = new HashSet<>();

                regTxEvents();
                IHandler txHandler = this.evtMgr.getHandler(IHandler.TYPE.TX0.getValue());
                if (txHandler != null) {
                    txHandler.eventCallback(new EventCallback(ees, LOGGER_TX));
                }
            }

            this.bufferEnable = CfgAion.inst().getTx().getBuffer();
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

        if (poolBackUp) {
            sn = IHandler.TYPE.TX0.getValue() << 8;
            eventSN.add(sn + EventTx.CALLBACK.TXBACKUP0.getValue());
        }

        return eventSN;
    }

    private void regBlockEvents() {
        List<IEvent> evts = new ArrayList<>();
        evts.add(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
        evts.add(new EventBlock(EventBlock.CALLBACK.ONBEST0));

        this.evtMgr.registerEvent(evts);
    }

    private void regTxEvents() {
        List<IEvent> evts = new ArrayList<>();
        evts.add(new EventTx(EventTx.CALLBACK.TXBACKUP0));

        this.evtMgr.registerEvent(evts);
    }

    @Override
    public synchronized RepositoryCache<?, ?> getRepository() {
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

    public synchronized AionBlock getBestBlock() {
        best.set(blockchain.getBestBlock());
        return best.get();
    }

    /**
     * TODO: when we removed libNc, timers were not introduced yet, we must rework the model that
     * libAion uses to work with timers
     */
    @Override
    public synchronized TxResponse addPendingTransaction(AionTransaction tx) {
        return addPendingTransactions(Collections.singletonList(tx)).get(0);
    }

    public boolean isValid(AionTransaction tx) {
        return TXValidator.isValid(tx) && TransactionTypeValidator.isValid(tx);
    }

    /**
     * Tries to add the given transactions to the PendingState
     *
     * @param transactions, the list of AionTransactions to be added
     * @return a list of TxResponses of the same size as the input param transactions The entries in
     *     the returned list of responses correspond one-to-one with the input txs
     */
    @Override
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

                    if (poolBackUp) {
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

                        if (poolBackUp) {
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
                        TxResponse implResponse = addPendingTransactionImpl(tx, txNonce);
                        if (!added) {
                            txResponses.add(implResponse);
                            added = true;
                        }
                        if (implResponse.equals(TxResponse.SUCCESS)) {
                            newPending.add(tx);

                            if (poolBackUp) {
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
                TxResponse implResponse = addPendingTransactionImpl(tx, txNonce);
                if (implResponse.equals(TxResponse.SUCCESS)) {
                    newPending.add(tx);
                    txResponses.add(TxResponse.REPAID);

                    if (poolBackUp) {
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
            } else if (!newPending.isEmpty() || !newLargeNonceTx.isEmpty()) {
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
                        "tx sig does not match with the tx raw data, tx[{}]", tx.toString());
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
            AionTxReceipt txReceipt, PendingTransactionState state, IAionBlock block) {
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
     * @param txNonce nonce of the transaction.
     * @return SUCCESS if transaction gets NEW_PENDING state, else appropriate message such as
     *     DROPPED, INVALID_TX, etc.
     */
    private TxResponse addPendingTransactionImpl(final AionTransaction tx, BigInteger txNonce) {

        if (!isValid(tx)) {
            LOGGER_TX.error("invalid Tx [{}]", tx.toString());
            fireDroppedTx(tx, "INVALID_TX");
            return TxResponse.INVALID_TX;
        }

        if (inValidTxNrgPrice(tx)) {
            LOGGER_TX.error("invalid Tx Nrg price [{}]", tx.toString());
            fireDroppedTx(tx, "INVALID_TX_NRG_PRICE");
            return TxResponse.INVALID_TX_NRG_PRICE;
        }

        AionTxExecSummary txSum;
        boolean ip = inPool(txNonce, tx.getSenderAddress());
        if (ip) {
            // check energy usage
            AionTransaction poolTx = txPool.getPoolTx(tx.getSenderAddress(), txNonce);
            if (poolTx == null) {
                LOGGER_TX.error(
                        "addPendingTransactionImpl no same tx nonce in the pool {}", tx.toString());
                fireDroppedTx(tx, "REPAYTX_POOL_EXCEPTION");
                return TxResponse.REPAYTX_POOL_EXCEPTION;
            } else {
                long price = (poolTx.getEnergyPrice() << 1);
                if (price > 0 && price <= tx.getEnergyPrice()) {
                    txSum = executeTx(tx, true);
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
            tx.setNrgConsume(txSum.getReceipt().getEnergyUsed());

            if (LOGGER_TX.isTraceEnabled()) {
                LOGGER_TX.trace("addPendingTransactionImpl validTx {}", tx.toString());
            }

            if (bufferEnable) {
                txBuffer.add(txSum);
            } else {
                AionTransaction rtn = this.txPool.add(tx);
                if (rtn != null && !rtn.equals(tx)) {
                    AionTxReceipt rp = new AionTxReceipt();
                    rp.setTransaction(rtn);

                    if (poolBackUp) {
                        backupPendingPoolRemove.add(tx.getTransactionHash().clone());
                    }
                    fireTxUpdate(rp, PendingTransactionState.DROPPED, best.get());
                }

                fireTxUpdate(txSum.getReceipt(), PendingTransactionState.NEW_PENDING, best.get());
            }

            return TxResponse.SUCCESS;
        }
    }

    private boolean inValidTxNrgPrice(AionTransaction tx) {
        return tx.getEnergyPrice() < NRGPRICE_MIN || tx.getEnergyPrice() > NRGPRICE_MAX;
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

    private AionTxReceipt createDroppedReceipt(AionTransaction tx, String error) {
        AionTxReceipt txReceipt = new AionTxReceipt();
        txReceipt.setTransaction(tx);
        txReceipt.setError(error);
        return txReceipt;
    }

    private IAionBlock findCommonAncestor(IAionBlock b1, IAionBlock b2) {
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

    @Override
    public synchronized void processBest(AionBlock newBlock, List receipts) {

        if (isSeed) {
            // seed mode doesn't need to update the pendingState
            return;
        }

        if (best.get() != null && !best.get().isParentOf(newBlock)) {

            // need to switch the state to another fork

            IAionBlock commonAncestor = findCommonAncestor(best.get(), newBlock);

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
            IAionBlock rollback = best.get();
            while (!rollback.isEqual(commonAncestor)) {
                if (LOGGER_TX.isDebugEnabled()) {
                    LOGGER_TX.debug("Rollback: {}", rollback.getShortDescr());
                }
                List<AionTransaction> atl = rollback.getTransactionsList();
                if (!atl.isEmpty()) {
                    this.txPool.add(atl);
                }
                rollback = blockchain.getBlockByHash(rollback.getParentHash());
            }

            // rollback the state snapshot to the ancestor
            pendingState = repository.getSnapshotTo(commonAncestor.getStateRoot()).startTracking();

            // next process blocks from new fork
            IAionBlock main = newBlock;
            List<IAionBlock> mainFork = new ArrayList<>();
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

        updateState(best.get());

        txPool.updateBlkNrgLimit(best.get().getNrgLimit());

        flushCachePendingTx();

        List<IEvent> events = new ArrayList<>();
        events.add(new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0));

        if (poolBackUp) {
            events.add(new EventTx(EventTx.CALLBACK.TXBACKUP0));
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

    private void processBestInternal(IAionBlock block, List<AionTxReceipt> receipts) {

        clearPending(block, receipts);

        clearOutdated(block.getNumber());
    }

    private void clearOutdated(final long blockNumber) {

        List<AionTransaction> outdated = new ArrayList<>();

        final long timeout = this.txPool.getOutDateTime();
        for (AionTransaction tx : this.txPool.getOutdatedList()) {
            outdated.add(tx);

            if (poolBackUp) {
                backupPendingPoolRemove.add(tx.getTransactionHash().clone());
            }
            // @Jay
            // TODO : considering add new state - TIMEOUT
            fireTxUpdate(
                    createDroppedReceipt(
                            tx, "Tx was not included into last " + timeout + " seconds"),
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
    private void clearPending(IAionBlock block, List<AionTxReceipt> receipts) {

        if (block.getTransactionsList() != null) {
            if (LOGGER_TX.isDebugEnabled()) {
                LOGGER_TX.debug(
                        "clearPending block#[{}] tx#[{}]",
                        block.getNumber(),
                        block.getTransactionsList().size());
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

                if (poolBackUp) {
                    backupPendingPoolRemove.add(tx.getTransactionHash().clone());
                }
                fireTxUpdate(receipt, PendingTransactionState.INCLUDED, block);
                cnt++;
            }

            if (!accountNonce.isEmpty()) {
                this.txPool.remove(accountNonce);
            }
        }
    }

    private AionTxInfo getTransactionInfo(byte[] txHash, byte[] blockHash) {
        AionTxInfo info = transactionStore.get(txHash, blockHash);
        AionTransaction tx =
                blockchain
                        .getBlockByHash(info.getBlockHash())
                        .getTransactionsList()
                        .get(info.getIndex());
        info.getReceipt().setTransaction(tx);
        return info;
    }

    @SuppressWarnings("UnusedReturnValue")
    private List<AionTransaction> updateState(IAionBlock block) {

        pendingState = repository.startTracking();

        processTxBuffer();
        List<AionTransaction> pendingTxl = this.txPool.snapshotAll();
        List<AionTransaction> rtn = new ArrayList<>();
        if (LOGGER_TX.isInfoEnabled()) {
            LOGGER_TX.info("updateState - snapshotAll tx[{}]", pendingTxl.size());
        }
        for (AionTransaction tx : pendingTxl) {
            if (LOGGER_TX.isTraceEnabled()) {
                LOGGER_TX.trace("updateState - loop: " + tx.toString());
            }

            AionTxExecSummary txSum = executeTx(tx, false);
            AionTxReceipt receipt = txSum.getReceipt();
            receipt.setTransaction(tx);

            if (txSum.isRejected()) {
                if (LOGGER_TX.isDebugEnabled()) {
                    LOGGER_TX.debug("Invalid transaction in txpool: {}", tx);
                }
                txPool.remove(Collections.singletonList(tx));

                if (poolBackUp) {
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

        IAionBlock bestBlk = best.get();
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

            return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                    bestBlk,
                    tx,
                    pendingState,
                    isLocalCall,
                    incrementSenderNonce,
                    fork040Enable,
                    checkBlockEnergyLimit,
                    LOGGER_VM);
        } catch (VMException e) {
            LOGGER_VM.error("Shutdown due to a VM fatal error.", e);
            System.exit(-1);
            return null;
        }
    }

    @Override
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

    @Override
    public void shutDown() {
        if (this.bufferEnable) {
            ex.shutdown();
        }

        if (ees != null) {
            ees.shutdown();
        }
    }

    @Override
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

    @Override
    public void loadPendingTx() {

        loadPendingTx = true;
        recoverPool();
        recoverCache();
        loadPendingTx = false;
    }

    @Override
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
                pendingTx.add(new AionTransaction(b));
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
                pendingTx.add(new AionTransaction(b));
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

    @Override
    public String getVersion() {
        return isSeed ? "0" : this.txPool.getVersion();
    }

    @Override
    public void updateBest() {
        getBestBlock();
    }
}

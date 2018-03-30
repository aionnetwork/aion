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

import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.type.Hash256;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.FastByteComparisons;
import org.aion.base.util.Hex;
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
import org.aion.mcf.db.TransactionStore;
import org.aion.mcf.evt.IListenerBase.PendingTransactionState;
import org.aion.txpool.ITxPool;
import org.aion.txpool.TxPoolModule;
import org.aion.vm.TransactionExecutor;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.valid.TXValidator;
import org.aion.zero.types.*;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class AionPendingStateImpl
        implements IPendingStateInternal<org.aion.zero.impl.types.AionBlock, AionTransaction> {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.TX.name());

    public static class TransactionSortedSet extends TreeSet<AionTransaction> {

        private static final long serialVersionUID = 4941385879122799663L;

        public TransactionSortedSet() {
            super((tx1, tx2) -> {
                long nonceDiff = ByteUtil.byteArrayToLong(tx1.getNonce())
                        - ByteUtil.byteArrayToLong(tx2.getNonce());
                if (nonceDiff != 0) {
                    return nonceDiff > 0 ? 1 : -1;
                }
                return FastByteComparisons.compareTo(tx1.getHash(), 0, 32, tx2.getHash(), 0, 32);
            });
        }
    }

    private static final int MAX_VALIDATED_PENDING_TXS = 8192;

    private IAionBlockchain blockchain;

    private TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> transactionStore;

    private IRepository repository;

    private ITxPool<AionTransaction> txPool;

    private IEventMgr evtMgr = null;

    // to filter out the transactions we have already processed
    // transactions could be sent by peers even if they were already included
    // into blocks
    private final Map<ByteArrayWrapper, Object> receivedTxs = Collections.synchronizedMap(new LRUMap<>(100000));
    private final Object dummyObject = new Object();

    private IRepositoryCache pendingState;

    private AtomicReference<AionBlock> best;

    static private AionPendingStateImpl inst;

    private PendingTxCache pendingTxCache;

    private EventExecuteService ees;

    private List<AionTxExecSummary> txBuffer;

    private boolean bufferEnable;

    private boolean dumpPool;

    private Timer timer;

    class TxBuffTask extends TimerTask {
        @Override
        public void run() {
            processTxBuffer();
        }
    }

    private synchronized void processTxBuffer() {

        List<AionTransaction> txs = new ArrayList<>();
        for (AionTxExecSummary s : txBuffer) {
            txs.add(s.getTransaction());
        }

        List<AionTransaction> newPending = txPool.add(txs);

        if (LOG.isInfoEnabled()) {
            LOG.info("txBufferSize {} return size {}", txs.size(), newPending.size());
        }

        int cnt = 0;
        Iterator<AionTxExecSummary> it = txBuffer.iterator();
        while (it.hasNext()) {
            AionTxExecSummary summary = it.next();
            if (newPending.get(cnt) != null && !newPending.get(cnt).equals(summary.getTransaction())) {
                AionTxReceipt rp = new AionTxReceipt();
                rp.setTransaction(newPending.get(cnt));
                receivedTxs.remove(ByteArrayWrapper.wrap(newPending.get(cnt).getHash()));
                fireTxUpdate(rp, PendingTransactionState.DROPPED, best.get());
            }
            cnt++;

            fireTxUpdate(summary.getReceipt(), PendingTransactionState.NEW_PENDING, best.get());
        }

        AionImpl.inst().broadcastTransactions(txs);

        txBuffer.clear();
    }

    private final class EpPS implements Runnable {
        boolean go = true;
        /**
         * When an object implementing interface <code>Runnable</code> is used
         * to create a thread, starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method <code>run</code> is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {
            while (go) {
                IEvent e = ees.take();

                if (e.getEventType() == IHandler.TYPE.BLOCK0.getValue() && e.getCallbackType() == EventBlock.CALLBACK.ONBEST0.getValue()) {
                    long t1 = System.currentTimeMillis();
                    processBest((AionBlock) e.getFuncArgs().get(0), (List) e.getFuncArgs().get(1));
                    long t2 = System.currentTimeMillis();

                    LOG.info("Pending state update took {} ms", t2 - t1);
                } else if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()) {
                    go = false;
                }
            }
        }
    }

    public synchronized static AionPendingStateImpl inst() {
        if (inst == null) {
            inst = new AionPendingStateImpl(AionRepositoryImpl.inst());
            inst.init(AionBlockchainImpl.inst());
        }
        return inst;
    }

    private AionPendingStateImpl(AionRepositoryImpl repository) {
        this.repository = repository;

        try {
            ServiceLoader.load(TxPoolModule.class);
        } catch (Exception e) {
            System.out.println("load TxPoolModule service fail!" + e.toString());
            throw e;
        }

        Properties prop = new Properties();

        prop.put(TxPoolModule.MODULENAME, "org.aion.txpool.zero.TxPoolA0");
        // The BlockEnergyLimit will be updated when the best block found.
        prop.put(ITxPool.PROP_BLOCK_NRG_LIMIT, String.valueOf(CfgAion.inst().getConsensus().getEnergyStrategy().getUpperBound()));
        prop.put(ITxPool.PROP_BLOCK_SIZE_LIMIT, "16000000");
        prop.put(ITxPool.PROP_TXN_TIMEOUT, "86400");
        TxPoolModule txPoolModule = null;
        try {
            txPoolModule = TxPoolModule.getSingleton(prop);
            this.txPool = (ITxPool<AionTransaction>) txPoolModule.getTxPool();
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            // log here!
            e.printStackTrace();
        }
    }

    public AionPendingStateImpl() {
        super();
    }

    public void init(final AionBlockchainImpl blockchain) {
        this.blockchain = blockchain;
        this.transactionStore = blockchain.getTransactionStore();
        this.evtMgr = blockchain.getEventMgr();
        this.pendingTxCache = new PendingTxCache(CfgAion.inst().getTx().getCacheMax());
        this.pendingState = repository.startTracking();
        this.txBuffer = new CopyOnWriteArrayList();

        this.bufferEnable = CfgAion.inst().getTx().getBuffer();
        this.dumpPool = CfgAion.inst().getTx().getPoolDump();

        this.best = new AtomicReference<>();

        ees = new EventExecuteService(1000, "EpPS", Thread.MAX_PRIORITY, LOG);
        ees.setFilter(setEvtFilter());

        regBlockEvents();
        IHandler blkHandler = this.evtMgr.getHandler(2);
        if (blkHandler != null) {
            blkHandler.eventCallback(new EventCallback(ees, LOG));
        }


        if (bufferEnable) {
            timer = new Timer("TxBuf");
            timer.schedule(new TxBuffTask(), 10000, 200);
        }

        ees.start(new EpPS());

    }

    private Set<Integer> setEvtFilter() {
        Set<Integer> eventSN = new HashSet<>();

        int sn = IHandler.TYPE.BLOCK0.getValue() << 8;
        eventSN.add(sn + EventBlock.CALLBACK.ONBEST0.getValue());

        return eventSN;
    }

    private void regBlockEvents() {
        List<IEvent> evts = new ArrayList<>();
        evts.add(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
        evts.add(new EventBlock(EventBlock.CALLBACK.ONBEST0));

        this.evtMgr.registerEvent(evts);
    }

    @Override
    public synchronized IRepositoryCache<?, ?, ?> getRepository() {
        // Todo : no class use this method.
        return pendingState;
    }

    private int getPendingTxSize() {
        return this.txPool.size();
    }

    @Override
    public synchronized List<AionTransaction> getPendingTransactions() {
            return this.txPool.snapshot();
    }

    public synchronized AionBlock getBestBlock() {
        best.set(blockchain.getBestBlock());
        return best.get();
    }

    private boolean addNewTxIfNotExist(AionTransaction tx) {
        return receivedTxs.put(new ByteArrayWrapper(tx.getHash()), dummyObject) == null;
    }

    /**
     * TODO: when we removed libNc, timers were not introduced yet, we must
     * rework the model that libAion uses to work with timers
     */
    @Override
    public synchronized List<AionTransaction> addPendingTransaction(AionTransaction tx) {

        return addPendingTransactions(Collections.singletonList(tx));
    }

    @Override
    public synchronized List<AionTransaction> addPendingTransactions(List<AionTransaction> transactions) {
        int unknownTx = 0;
        List<AionTransaction> newPending = new ArrayList<>();

        for (AionTransaction tx : transactions) {
            BigInteger txNonce = tx.getNonceBI();
            BigInteger bestPSNonce = bestPendingStateNonce(tx.getFrom());

            if (txNonce.compareTo(bestPSNonce) > 0) {
                if (!isInTxCache(tx.getFrom(), tx.getNonceBI())) {
                    AionImpl.inst().broadcastTransactions(Collections.singletonList(tx));
                }

                addToTxCache(tx);

                LOG.debug("Adding transaction to cache: from = {}, nonce = {}", tx.getFrom(), txNonce);
            } else if (txNonce.equals(bestPSNonce)) {
                if (txPool.size() >= MAX_VALIDATED_PENDING_TXS) {

                    if (!isInTxCache(tx.getFrom(), tx.getNonceBI())) {
                        AionImpl.inst().broadcastTransactions(Collections.singletonList(tx));
                    }
                    addToTxCache(tx);
                    continue;
                }

                Map<BigInteger,AionTransaction> cache = pendingTxCache.geCacheTx(tx.getFrom());

                do {
                    if (addNewTxIfNotExist(tx)) {
                        unknownTx++;
                        if (addPendingTransactionImpl(tx, txNonce)) {
                            newPending.add(tx);
                        } else {
                            break;
                        }
                    }

                    txNonce = txNonce.add(BigInteger.ONE);
                } while (cache != null && (tx = cache.get(txNonce)) != null && txPool.size() < MAX_VALIDATED_PENDING_TXS);
            }  else if (bestRepoNonce(tx.getFrom()).compareTo(txNonce) < 1) {
                // repay Tx
                if (addNewTxIfNotExist(tx)) {
                    unknownTx++;
                    if (addPendingTransactionImpl(tx, txNonce)) {
                        newPending.add(tx);
                    }
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Wire transaction list added: total: {}, new: {}, valid (added to pending): {} (current #of known txs: {})",
                    transactions.size(), unknownTx, newPending, receivedTxs.size());
        }

        if (!newPending.isEmpty()) {
            IEvent evtRecv = new EventTx(EventTx.CALLBACK.PENDINGTXRECEIVED0);
            evtRecv.setFuncArgs(Collections.singletonList(newPending));
            this.evtMgr.newEvent(evtRecv);

            IEvent evtChange = new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0);
            this.evtMgr.newEvent(evtChange);
        }

        return newPending;
    }

    private boolean inPool(BigInteger txNonce, Address from) {
        return (this.txPool.bestPoolNonce(from).compareTo(txNonce) > -1);
    }


    private void fireTxUpdate(AionTxReceipt txReceipt, PendingTransactionState state, IAionBlock block) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("PendingTransactionUpdate: (Tot: %3s) %12s : %s %8s %s [%s]", getPendingTxSize(),
                    state, txReceipt.getTransaction().getFrom().toString().substring(0, 8),
                    ByteUtil.byteArrayToLong(txReceipt.getTransaction().getNonce()), block.getShortDescr(),
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
     * @param txNonce
     * @return True if transaction gets NEW_PENDING state, False if DROPPED
     */
    private boolean addPendingTransactionImpl(final AionTransaction tx, BigInteger txNonce) {

        if (!TXValidator.isValid(tx)) {
            LOG.error("tx sig does not match with the tx raw data, tx[{}]", tx.toString());
            return false;
        }

        AionTxExecSummary txSum;
        boolean ip = inPool(txNonce, tx.getFrom());
        if (ip) {
            // check energy usage
            AionTransaction poolTx = txPool.getPoolTx(tx.getFrom(), txNonce);
            if (poolTx == null) {
                LOG.error("addPendingTransactionImpl no same tx nonce in the pool addr[{}] nonce[{}], hash[{}]", tx.getFrom().toString(), txNonce.toString(), Hash256.wrap(tx.getHash()).toString());
                return false;
            } else {
                long price = (poolTx.getNrgPrice() << 1);
                if (price > 0 && price <= tx.getNrgPrice()) {
                    txSum = executeTx(tx, true);
                } else {
                    return false;
                }
            }
        } else {
            txSum = executeTx(tx, false);
        }

        if (txSum.isRejected()) {
            if (LOG.isErrorEnabled()) {
                LOG.error("addPendingTransactionImpl tx is rejected due to: {}", txSum.getReceipt().getError());
            }
            fireTxUpdate(txSum.getReceipt(), PendingTransactionState.DROPPED, best.get());
            return false;
        } else {
            tx.setNrgConsume(txSum.getReceipt().getEnergyUsed());

            if (LOG.isTraceEnabled()) {
                LOG.trace("addPendingTransactionImpl: [{}]", tx.toString());
            }

            if (bufferEnable) {
                txBuffer.add(txSum);
            } else {
                AionTransaction rtn = this.txPool.add(tx);
                if (rtn != null && !rtn.equals(tx)) {
                    AionTxReceipt rp = new AionTxReceipt();
                    rp.setTransaction(rtn);
                    receivedTxs.remove(ByteArrayWrapper.wrap(rtn.getHash()));
                    fireTxUpdate(rp, PendingTransactionState.DROPPED, best.get());
                }

                fireTxUpdate(txSum.getReceipt(), PendingTransactionState.NEW_PENDING, best.get());
            }

            return true;
        }
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
                throw new RuntimeException("Pending state can't find common ancestor: one of blocks has a gap");
            }
        }
        return b1;
    }

    @Override
    public synchronized void processBest(AionBlock newBlock, List receipts) {
        if (best.get() != null && !best.get().isParentOf(newBlock)) {

            // need to switch the state to another fork

            IAionBlock commonAncestor = findCommonAncestor(best.get(), newBlock);

            if (LOG.isDebugEnabled()) {
                LOG.debug("New best block from another fork: " + newBlock.getShortDescr() + ", old best: " + best.get().getShortDescr() + ", ancestor: " + commonAncestor.getShortDescr());
            }

            // first return back the transactions from forked blocks
            IAionBlock rollback = best.get();
            while (!rollback.isEqual(commonAncestor)) {

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
                mainFork.add(main);
                main = blockchain.getBlockByHash(main.getParentHash());
            }

            // processing blocks from ancestor to new block
            for (int i = mainFork.size() - 1; i >= 0; i--) {
                processBestInternal(mainFork.get(i), null);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("PendingStateImpl.processBest: " + newBlock.getShortDescr());
            }
            processBestInternal(newBlock, receipts);
        }

        best.set(newBlock);

        if (LOG.isTraceEnabled()) {
            LOG.trace("PendingStateImpl.processBest: updateState");
        }
        updateState(best.get());

        if (LOG.isTraceEnabled()) {
            LOG.trace("PendingStateImpl.processBest: txPool.updateBlkNrgLimit");
        }
        txPool.updateBlkNrgLimit(best.get().getNrgLimit());

        if (LOG.isTraceEnabled()) {
            LOG.trace("PendingStateImpl.processBest: flushCachePendingTx");
        }
        flushCachePendingTx();

        IEvent evtChange = new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0);
        this.evtMgr.newEvent(evtChange);

        if (CfgAion.inst().getTx().getPoolDump()) {
            DumpPool();
        }
    }

    private void flushCachePendingTx() {
        Set<Address> cacheTxAccount = this.pendingTxCache.getCacheTxAccount();

        if (LOG.isDebugEnabled()) {
            LOG.debug("PendingStateImpl.flushCachePendingTx: acc#[{}]", cacheTxAccount.size());
        }

        Map<Address, BigInteger> nonceMap = new HashMap<>();
        for (Address addr : cacheTxAccount) {
            nonceMap.put(addr, bestPendingStateNonce(addr));
        }

        List<AionTransaction> newPendingTx = this.pendingTxCache.flush(nonceMap);

        if (LOG.isTraceEnabled()) {
            LOG.trace("PendingStateImpl.flushCachePendingTx: newPendingTx_size[{}]", newPendingTx.size());
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

        List<AionPendingTx> outdated = new ArrayList<>();

        final long timeout = this.txPool.getOutDateTime();
        final long bestNumber = best.get().getNumber();
        for (AionTransaction tx : this.txPool.getOutdatedList()) {
            outdated.add(new AionPendingTx(tx, bestNumber));

            // @Jay
            // TODO : considering add new state - TIMEOUT
            fireTxUpdate(createDroppedReceipt(tx, "Tx was not included into last " + timeout + " seconds"),
                    PendingTransactionState.DROPPED, best.get());
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("clearOutdated block#[{}] tx#[{}]", blockNumber, outdated.size());
        }

        if (outdated.isEmpty()) {
            return;
        }

        if (LOG.isDebugEnabled()) {
            for (AionPendingTx tx : outdated) {
                LOG.debug("Clear outdated pending transaction, block.number: [{}] hash: [{}]", tx.getBlockNumber(),
                        Hex.toHexString(tx.getHash()));
            }
        }
    }

//    @SuppressWarnings("unchecked")
//    @Deprecated
//    private void clearPending(IAionBlock block, List<AionTxReceipt> receipts) {
//
//        List<AionTransaction> txList = block.getTransactionsList();
//
//        if (LOG.isDebugEnabled()) {
//            LOG.debug("clearPending block#[{}] tx#[{}]", block.getNumber(), txList.size());
//        }
//
//        if (!txList.isEmpty()) {
//            List<AionTransaction> txn = this.txPool.remove(txList);
//
//            int cnt = 0;
//            for (AionTransaction tx : txn) {
//                if (LOG.isTraceEnabled()) {
//                    LOG.trace("Clear pending transaction, hash: [{}]", Hex.toHexString(tx.getHash()));
//                }
//
//                AionTxReceipt receipt;
//                if (receipts != null) {
//                    receipt = receipts.get(cnt);
//                } else {
//                    AionTxInfo info = getTransactionInfo(tx.getHash(), block.getHash());
//                    receipt = info.getReceipt();
//                }
//                fireTxUpdate(receipt, PendingTransactionState.INCLUDED, block);
//                cnt++;
//            }
//        }
//    }

    @SuppressWarnings("unchecked")
    private void clearPending(IAionBlock block, List<AionTxReceipt> receipts) {

        List<AionTransaction> txList = block.getTransactionsList();
        Map<Address, BigInteger> accountNonce = new HashMap<>();
        if (txList != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("clearPending block#[{}] tx#[{}]", block.getNumber(), txList.size());
            }

            for (AionTransaction tx  : txList) {
                if (accountNonce.get(tx.getFrom()) != null) {
                    continue;
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("clear address {}", tx.getFrom().toString());
                }

                accountNonce.put(tx.getFrom(), this.repository.getNonce(tx.getFrom()));
            }
        }

        if (!accountNonce.isEmpty()) {
            this.txPool.remove(accountNonce);

            int cnt = 0;
            for (AionTransaction tx : txList) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Clear pending transaction, hash: [{}]", Hex.toHexString(tx.getHash()));
                }

                AionTxReceipt receipt;
                if (receipts != null) {
                    receipt = receipts.get(cnt);
                } else {
                    AionTxInfo info = getTransactionInfo(tx.getHash(), block.getHash());
                    receipt = info.getReceipt();
                }
                fireTxUpdate(receipt, PendingTransactionState.INCLUDED, block);
                cnt++;
            }
        }
    }

    private AionTxInfo getTransactionInfo(byte[] txHash, byte[] blockHash) {
        AionTxInfo info = transactionStore.get(txHash, blockHash);
        AionTransaction tx = blockchain.getBlockByHash(info.getBlockHash()).getTransactionsList().get(info.getIndex());
        info.getReceipt().setTransaction(tx);
        return info;
    }

    private void updateState(IAionBlock block) {

        pendingState = repository.startTracking();
        processTxBuffer();
        List<AionTransaction> pendingTxl = this.txPool.snapshotAll();

        if (LOG.isInfoEnabled()) {
            LOG.info("updateState - snapshotAll tx[{}]", pendingTxl.size());
        }
        for (AionTransaction tx : pendingTxl) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("updateState - loop: " + tx.toString());
            }

            AionTxExecSummary txSum = executeTx(tx, false);
            AionTxReceipt receipt = txSum.getReceipt();
            receipt.setTransaction(tx);

            if (txSum.isRejected()) {
                LOG.warn("Invalid transaction in txpool: {}", tx);
                txPool.remove(Collections.singletonList(tx));

                fireTxUpdate(receipt, PendingTransactionState.DROPPED, block);
            } else {
                fireTxUpdate(receipt, PendingTransactionState.PENDING, block);
            }
        }
    }

    private Set<Address> getTxsAccounts(List<AionTransaction> txn) {
        Set<Address> rtn = new HashSet<>();
        for (AionTransaction tx : txn) {
            if (!rtn.contains(tx.getFrom())) {
                rtn.add(tx.getFrom());
            }
        }
        return rtn;
    }
    
    private AionTxExecSummary executeTx(AionTransaction tx, boolean inPool) {

        IAionBlock bestBlk = best.get();

        if (LOG.isTraceEnabled()) {
            LOG.trace("executeTx: {}", Hex.toHexString(tx.getHash()));
        }

        TransactionExecutor executor = new TransactionExecutor(tx, bestBlk, pendingState);

        if (inPool) {
            executor.setBypassNonce(true);
        }

        return executor.execute();
    }

    @Override
    public synchronized BigInteger bestPendingStateNonce(Address addr) {
        return this.pendingState.getNonce(addr);
    }


    private BigInteger bestRepoNonce(Address addr) {
        return this.repository.getNonce(addr);
    }

    private List<AionTransaction> addToTxCache(AionTransaction tx) {
        return this.pendingTxCache.addCacheTx(tx);
    }

    private boolean isInTxCache(Address addr, BigInteger nonce) {
        return this.pendingTxCache.isInCache(addr, nonce);
    }

    @Override
    public void shutDown() {
        if (this.bufferEnable) {
            timer.cancel();
        }
        ees.shutdown();
    }

    @Override
    public synchronized void DumpPool() {
        List<AionTransaction> txn = txPool.snapshotAll();
        Set<Address> addrs = new HashSet<>();
        LOG.info("");
        LOG.info("=========== SnapshotAll");
        for (AionTransaction tx : txn) {
            addrs.add(tx.getFrom());
            LOG.info("{}", tx.toString());
        }

        txn = txPool.snapshot();
        LOG.info("");
        LOG.info("=========== Snapshot");
        for (AionTransaction tx : txn) {
            LOG.info("{}", tx.toString());
        }

        LOG.info("");
        LOG.info("=========== Pool best nonce");
        for (Address addr : addrs) {
            LOG.info("{} {}", addr.toString(), txPool.bestPoolNonce(addr));
        }

        LOG.info("");
        LOG.info("=========== Cache pending tx");
        Set<Address> cacheAddr = pendingTxCache.getCacheTxAccount();
        for(Address addr : cacheAddr) {
            Map<BigInteger, AionTransaction> cacheMap = pendingTxCache.geCacheTx(addr);
            if (cacheMap != null) {
                for (AionTransaction tx : cacheMap.values()) {
                    LOG.info("{}", tx.toString());
                }
            }
        }

        LOG.info("");
        LOG.info("=========== db nonce");
        addrs.addAll(cacheAddr);
        for (Address addr : addrs) {
            LOG.info("{} {}", addr.toString(), bestRepoNonce(addr));
        }

        LOG.info("");
        LOG.info("=========== ps nonce");
        addrs.addAll(cacheAddr);
        for (Address addr : addrs) {
            LOG.info("{} {}", addr.toString(), bestPendingStateNonce(addr));
        }
    }

    @Override
    public String getVersion() {
        return this.txPool.getVersion();
    }

    @Override
    public void updateBest() {
        getBestBlock();
    }
}

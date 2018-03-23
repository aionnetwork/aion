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
import org.aion.base.type.*;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.FastByteComparisons;
import org.aion.base.util.Hex;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallbackA0;
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

public class AionPendingStateImpl
        implements IPendingStateInternal<org.aion.zero.impl.types.AionBlock, AionTransaction> {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());

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

    private AionBlock best = null;

    static private AionPendingStateImpl inst;

    private PendingTxCache pendingTxCache;

    private final Map<Address, BigInteger> cachePoolNonce = new LRUMap<>(1000);

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
        prop.put(ITxPool.PROP_BLOCK_NRG_LIMIT, "10000000");
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

        CfgAion cfg = CfgAion.inst();
        this.pendingTxCache = new PendingTxCache(cfg.getTx().getCacheMax());
    }

    public void init(final AionBlockchainImpl blockchain) {
        this.blockchain = blockchain;
        this.transactionStore = blockchain.getTransactionStore();
        this.evtMgr = blockchain.getEventMgr();

        this.pendingState = repository.startTracking();

        regBlockEvents();
        IHandler blkHandler = this.evtMgr.getHandler(2);
        if (blkHandler != null) {
            blkHandler.eventCallback(
                    new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                        public void onBest(IBlock _blk, List<?> _receipts) {
                            processBest((AionBlock) _blk, _receipts);
                        }
                    });
        }
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
        if (best == null) {
            best = blockchain.getBestBlock();
        }
        return best;
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

        Map<Address, BigInteger> dbNonce = new HashMap<>();
        for (AionTransaction tx : transactions) {
            BigInteger txNonce = new BigInteger(1, tx.getNonce());
            BigInteger bestNonce = bestNonce(tx.getFrom());

            if (txNonce.compareTo(bestNonce) > 0) {
                addToTxCache(tx);

                LOG.debug("Adding transaction to cache: from = {}, nonce = {}", tx.getFrom(), txNonce);
            } else if (txNonce.equals(bestNonce)) {
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
                } while (cache != null && (tx = cache.get(txNonce)) != null);
            } /* else {
                // check repay tx
                if (dbNonce.get(tx.getFrom()) == null) {
                    dbNonce.put(tx.getFrom(), this.repository.getNonce(tx.getFrom()));
                }

                if (dbNonce.get(tx.getFrom()).compareTo(txNonce) < 1) {
                    if (addNewTxIfNotExist(tx)) {
                        unknownTx++;
                        if (addPendingTransactionImpl(tx, txNonce)) {
                            newPending.add(tx);
                        }
                    }
                }
            } */
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

        // Broadcast new pending transactions
        AionImpl.inst().broadcastTransactions(newPending);

        return newPending;
    }

    private boolean inPool(BigInteger txNonce, Address from) {
        return false;
        /*BigInteger bn = this.txPool.bestNonce(from);
        return bn != null && (bn.compareTo(txNonce) > -1);*/
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
            fireTxUpdate(txSum.getReceipt(), PendingTransactionState.DROPPED, getBestBlock());
            return false;
        } else {
            tx.setNrgConsume(txSum.getReceipt().getEnergyUsed());

            if (LOG.isTraceEnabled()) {
                LOG.trace("addPendingTransactionImpl: [{}]", tx.toString());
            }

            AionTransaction rtn = this.txPool.add(tx);
            if (rtn != null && !rtn.equals(tx)) {
                AionTxReceipt rp = new AionTxReceipt();
                rp.setTransaction(rtn);
                receivedTxs.remove(ByteArrayWrapper.wrap(rtn.getHash()));
                fireTxUpdate(rp, PendingTransactionState.DROPPED, getBestBlock());
            }

            fireTxUpdate(txSum.getReceipt(), PendingTransactionState.NEW_PENDING, getBestBlock());
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
        synchronized (txPool) {
            if (getBestBlock() != null && !getBestBlock().isParentOf(newBlock)) {

                // need to switch the state to another fork

                IAionBlock commonAncestor = findCommonAncestor(getBestBlock(), newBlock);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("New best block from another fork: " + newBlock.getShortDescr() + ", old best: "
                            + getBestBlock().getShortDescr() + ", ancestor: " + commonAncestor.getShortDescr());
                }

                // first return back the transactions from forked blocks
                IAionBlock rollback = getBestBlock();
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

            best = newBlock;

            if (LOG.isTraceEnabled()) {
                LOG.trace("PendingStateImpl.processBest: updateState");
            }
            updateState(best);

            if (LOG.isTraceEnabled()) {
                LOG.trace("PendingStateImpl.processBest: txPool.updateBlkNrgLimit");
            }
            txPool.updateBlkNrgLimit(best.getNrgLimit());

            if (LOG.isTraceEnabled()) {
                LOG.trace("PendingStateImpl.processBest: flushCachePendingTx");
            }
            flushCachePendingTx();


            IEvent evtChange = new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0);
            this.evtMgr.newEvent(evtChange);
        }
    }

    private void flushCachePendingTx() {
        Set<Address> cacheTxAccount = this.pendingTxCache.getCacheTxAccount();

        if (LOG.isDebugEnabled()) {
            LOG.debug("PendingStateImpl.flushCachePendingTx: acc#[{}]", cacheTxAccount.size());
        }

        Map<Address, BigInteger> nonceMap = new HashMap<>();
        for (Address addr : cacheTxAccount) {
            nonceMap.put(addr, bestNonce(addr));
        }

        List<AionTransaction> newPendingTx = this.pendingTxCache.flush(nonceMap);

        if (LOG.isTraceEnabled()) {
            LOG.trace("PendingStateImpl.flushCachePendingTx: newPendingTx_size[{}]", newPendingTx.size());
        }

        if (newPendingTx != null && !newPendingTx.isEmpty()) {
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
        final long best = getBestBlock().getNumber();
        for (AionTransaction tx : this.txPool.getOutdatedList()) {
            outdated.add(new AionPendingTx(tx, best));

            // @Jay
            // TODO : considering add new state - TIMEOUT
            fireTxUpdate(createDroppedReceipt(tx, "Tx was not included into last " + timeout + " seconds"),
                    PendingTransactionState.DROPPED, getBestBlock());
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

    @SuppressWarnings("unchecked")
    private void clearPending(IAionBlock block, List<AionTxReceipt> receipts) {

        List<AionTransaction> txList = block.getTransactionsList();

        if (LOG.isDebugEnabled()) {
            LOG.debug("clearPending block#[{}] tx#[{}]", block.getNumber(), txList.size());
        }

        if (!txList.isEmpty()) {
            List<AionTransaction> txn = this.txPool.remove(txList);

            int cnt = 0;
            for (AionTransaction tx : txn) {
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

        List<AionTransaction> pendingTxl = this.txPool.snapshotAll();

        if (LOG.isDebugEnabled()) {
            LOG.debug("updateState - snapshotAll tx[{}]", pendingTxl.size());
        }
        for (AionTransaction tx : pendingTxl) {
            if (LOG.isTraceEnabled()) {
                LOG.debug("updateState - loop: " + tx.toString());
            }

            AionTxExecSummary txSum = executeTx(tx, false);
            AionTxReceipt receipt = txSum.getReceipt();
            receipt.setTransaction(tx);
            fireTxUpdate(receipt, PendingTransactionState.PENDING, block);
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

        IAionBlock best = getBestBlock();

        if (LOG.isTraceEnabled()) {
            LOG.trace("executeTx: {}", Hex.toHexString(tx.getHash()));
        }

        TransactionExecutor executor = new TransactionExecutor(tx, best, pendingState);
        if (inPool) {
            executor.setBypassNonce(true);
        }
        return executor.execute();
    }

    @Override
    public synchronized BigInteger bestNonce(Address addr) {
        return this.pendingState.getNonce(addr);
    }

    private List<AionTransaction> addToTxCache(AionTransaction tx) {
        return this.pendingTxCache.addCacheTx(tx);
    }

    @Override
    public String getVersion() {
        return this.txPool.getVersion();
    }
}
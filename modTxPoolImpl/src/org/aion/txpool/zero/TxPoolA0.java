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

package org.aion.txpool.zero;

import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.TimeInstant;
import org.aion.txpool.ITxPool;
import org.aion.txpool.common.AbstractTxPool;
import org.aion.txpool.common.AccountState;
import org.aion.txpool.common.TxDependList;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.math.BigInteger;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class TxPoolA0<TX extends ITransaction> extends AbstractTxPool<TX> implements ITxPool<TX> {

    public TxPoolA0() {
        super();
    }

    public TxPoolA0(Properties config) {
        super();
        setPoolArgs(config);
    }

    private void setPoolArgs(Properties config) {
        if (Optional.ofNullable(config.get(PROP_TX_TIMEOUT)).isPresent()) {
            txn_timeout = Integer.valueOf(config.get(PROP_TX_TIMEOUT).toString());
            if (txn_timeout < TXN_TIMEOUT_MIN) {
                txn_timeout = TXN_TIMEOUT_MIN;
            } else if (txn_timeout > TXN_TIMEOUT_MAX) {
                txn_timeout = TXN_TIMEOUT_MAX;
            }
        }

        txn_timeout--; // final timeout value sub -1 sec

        if (Optional.ofNullable(config.get(PROP_BLOCK_SIZE_LIMIT)).isPresent()) {
            blkSizeLimit = Integer.valueOf(config.get(PROP_BLOCK_SIZE_LIMIT).toString());
            if (blkSizeLimit < BLK_SIZE_MIN) {
                blkSizeLimit = BLK_SIZE_MIN;
            } else if (blkSizeLimit > BLK_SIZE_MAX) {
                blkSizeLimit = BLK_SIZE_MAX;
            }
        }

        if (Optional.ofNullable(config.get(PROP_BLOCK_NRG_LIMIT)).isPresent()) {
            updateBlkNrgLimit(Long.valueOf((String) config.get(PROP_BLOCK_NRG_LIMIT)));
        }

        if (Optional.ofNullable(config.get(PROP_TX_SEQ_MAX)).isPresent()) {
            seqTxCountMax = Integer.valueOf(config.get(PROP_TX_SEQ_MAX).toString());
            if (seqTxCountMax < SEQ_TX_MIN) {
                seqTxCountMax = SEQ_TX_MIN;
            } else if (seqTxCountMax > SEQ_TX_MAX) {
                seqTxCountMax = SEQ_TX_MAX;
            }
        }
    }

    /**
     * This function is a test function
     *
     * @param acc
     * @return
     */
    public List<BigInteger> getNonceList(Address acc) {

        List<BigInteger> nl = Collections.synchronizedList(new ArrayList<>());
        lock.readLock().lock();
        this.getAccView(acc).getMap().entrySet().parallelStream().forEach(e -> nl.add(e.getKey()));
        lock.readLock().unlock();

        return nl.parallelStream().sorted().collect(Collectors.toList());
    }

    @Override
    public TX add(TX tx) {
        List<TX> rtn = this.add(Collections.singletonList(tx)) ;
        return  rtn.isEmpty() ? null : rtn.get(0);
    }

    /**
     * this is a test function
     *
     * @return
     */
    public List<BigInteger> getFeeList() {
        List<BigInteger> nl = Collections.synchronizedList(new ArrayList<>());

        this.getFeeView().entrySet().parallelStream().forEach(e -> nl.add(e.getKey()));

        return nl.parallelStream().sorted(Collections.reverseOrder()).collect(Collectors.toList());
    }

    @Override
    public List<TX> add(List<TX> txl) {

        List<TX> newPendingTx = new ArrayList<>();
        Map<ByteArrayWrapper, TXState> mainMap = new HashMap<>();
        for (TX tx : txl) {

            ByteArrayWrapper bw = ByteArrayWrapper.wrap(tx.getHash());
            if (this.getMainMap().get(bw) != null) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("The tx hash existed in the pool! [{}]", ByteUtils.toHexString(bw.getData()));
                }
                continue;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Put tx into mainMap: hash:[{}] tx:[{}]", ByteUtils.toHexString(bw.getData()), tx.toString());
            }

            mainMap.put(bw, new TXState(tx));

            BigInteger txNonce = tx.getNonceBI();
            BigInteger bn = getBestNonce(tx.getFrom());

            if (bn != null && txNonce.compareTo(bn) < 1) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("repay tx, do snapshot!");
                }
                snapshot();
            }

            AbstractMap.SimpleEntry<ByteArrayWrapper, BigInteger> entry = this.getAccView(tx.getFrom()).getMap().get(txNonce);
            if (entry != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("repay tx, remove previous tx!");
                }
                List oldTx = remove(Collections.singletonList(this.getMainMap().get(entry.getKey()).getTx()));

                if (oldTx != null && !oldTx.isEmpty()) {
                    newPendingTx.add((TX) oldTx.get(0));
                }
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("new tx! n[{}]", tx.getNonceBI().toString());
                }
                newPendingTx.add(tx);
            }

            setBestNonce(tx.getFrom(), txNonce);
        }

        this.getMainMap().putAll(mainMap);

        if (LOG.isTraceEnabled()) {
            LOG.trace("new add tx! np[{}] tx[{}]", newPendingTx.size(), txl.size());
        }

        if (newPendingTx.size() != txl.size()) {
            LOG.error("error");
        }

        return newPendingTx;
    }

    public List<TX> getOutdatedList() {
        return this.getOutdatedListImpl();
    }

    @Override
    public List<TX> remove(Map<Address, BigInteger> accNonce) {

        List<ByteArrayWrapper> bwList = new ArrayList<>();
        for (Map.Entry<Address, BigInteger> en1 : accNonce.entrySet()) {
            AccountState as = this.getAccView(en1.getKey());
            lock.writeLock().lock();
            Iterator<Map.Entry<BigInteger, AbstractMap.SimpleEntry<ByteArrayWrapper, BigInteger>>> it = as.getMap().entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<BigInteger, AbstractMap.SimpleEntry<ByteArrayWrapper, BigInteger>> en = it.next();
                if (en1.getValue().compareTo(en.getKey()) > 0) {
                    bwList.add(en.getValue().getKey());
                    it.remove();
                } else {
                    break;
                }
            }
            lock.writeLock().unlock();

            Set<BigInteger> fee = Collections.synchronizedSet(new HashSet<>());
            if (this.getPoolStateView(en1.getKey()) != null) {
                this.getPoolStateView(en1.getKey()).parallelStream().forEach(ps -> fee.add(ps.getFee()));
            }

            fee.parallelStream().forEach(bi -> {
                if (this.getFeeView().get(bi) != null) {
                    this.getFeeView().get(bi).entrySet().removeIf(
                            byteArrayWrapperTxDependListEntry -> byteArrayWrapperTxDependListEntry.getValue().getAddress().equals(en1.getKey()));

                    if (this.getFeeView().get(bi).isEmpty()) {
                        this.getFeeView().remove(bi);
                    }
                }
            });

            as.setDirty();
        }

        List<TX> removedTxl = Collections.synchronizedList(new ArrayList<>());
        bwList.parallelStream().forEach( bw -> {
            if (this.getMainMap().get(bw) != null) {
                ITransaction tx = this.getMainMap().get(bw).getTx().clone();
                removedTxl.add((TX)tx);

                long timestamp = tx.getTimeStampBI().longValue()/ multiplyM;
                synchronized (this.getTimeView().get(timestamp)) {
                    if (this.getTimeView().get(timestamp) == null) {
                        LOG.error("Txpool.remove can't find the timestamp in the map [{}]", tx.toString());
                        return;
                    }

                    this.getTimeView().get(timestamp).remove(bw);
                    if (this.getTimeView().get(timestamp).isEmpty()) {
                        this.getTimeView().remove(timestamp);
                    }
                }

                lock.writeLock().lock();
                this.getMainMap().remove(bw);
                lock.writeLock().unlock();
            }
        });

        this.updateAccPoolState();
        this.updateFeeMap();

        if (LOG.isInfoEnabled()) {
            LOG.info("TxPoolA0.remove {} TX", removedTxl.size());
        }

        return removedTxl;
    }


    @Override
    @Deprecated
    public List<TX> remove(List<TX> txs) {

        List<TX> removedTxl = Collections.synchronizedList(new ArrayList<>());
        Set<Address> checkedAddress = Collections.synchronizedSet(new HashSet<>());

        for (TX tx : txs) {
            ByteArrayWrapper bw = ByteArrayWrapper.wrap(tx.getHash());
            lock.writeLock().lock();
            try {
                if (this.getMainMap().remove(bw) == null) {
                    continue;
                }
            } finally {
                lock.writeLock().unlock();
            }

            //noinspection unchecked
            removedTxl.add((TX) tx.clone());

            if (LOG.isTraceEnabled()) {
                LOG.trace("TxPoolA0.remove:[{}] nonce:[{}]", ByteUtils.toHexString(tx.getHash()),
                        tx.getNonceBI().toString());
            }

            long timestamp = tx.getTimeStampBI().longValue()/ multiplyM;
            if (this.getTimeView().get(timestamp) != null) {
                if (this.getTimeView().get(timestamp).remove(bw)) {
                    if (this.getTimeView().get(timestamp).isEmpty()) {
                        this.getTimeView().remove(timestamp);
                    }
                }
            }

            // remove the all transactions belong to the given address in the feeView
            Address address = tx.getFrom();
            Set<BigInteger> fee = Collections.synchronizedSet(new HashSet<>());
            if (!checkedAddress.contains(address)) {

                if (this.getPoolStateView(tx.getFrom()) != null) {
                    this.getPoolStateView(tx.getFrom()).parallelStream().forEach(ps -> fee.add(ps.getFee()));
                }

                fee.parallelStream().forEach(bi -> {
                    if (this.getFeeView().get(bi) != null) {
                        this.getFeeView().get(bi).entrySet().removeIf(
                                byteArrayWrapperTxDependListEntry -> byteArrayWrapperTxDependListEntry.getValue().getAddress().equals(address));

                        if (this.getFeeView().get(bi).isEmpty()) {
                            this.getFeeView().remove(bi);
                        }
                    }
                });

                checkedAddress.add(address);
            }

            AccountState as = this.getAccView(tx.getFrom());

            lock.writeLock().lock();
            as.getMap().remove(tx.getNonceBI());
            lock.writeLock().unlock();

            as.setDirty();
        }


        this.updateAccPoolState();
        this.updateFeeMap();

        if (LOG.isDebugEnabled()) {
            LOG.debug("TxPoolA0.remove TX remove [{}] removed [{}]", txs.size(), removedTxl.size());
        }

        return removedTxl;
    }

    public long getOutDateTime() {
        return txn_timeout;
    }

    @Override
    public int size() {
        return this.getMainMap().size();
    }

    @Override
    public void updateBlkNrgLimit(long nrg) {
        if (nrg < BLK_NRG_MIN) {
            blkNrgLimit.set(BLK_NRG_MIN);
        } else if (nrg > BLK_NRG_MAX) {
            blkNrgLimit.set(BLK_NRG_MAX);
        } else {
            blkNrgLimit.set(nrg);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("TxPoolA0.updateBlkNrgLimit nrg[{}] blkNrgLimit[{}]", nrg, blkNrgLimit.get());
        }
    }

    @Override
    public TX getPoolTx(Address from, BigInteger txNonce) {
        if (from == null || txNonce == null) {
            LOG.error("TxPoolA0.getPoolTx null args");
            return null;
        }

        sortTxn();

        lock.readLock().lock();
        try {
            AbstractMap.SimpleEntry<ByteArrayWrapper, BigInteger> entry = this.getAccView(from).getMap().get(txNonce);
            return (entry == null ? null : this.getMainMap().get(entry.getKey()).getTx());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public List<TX> snapshotAll() {

        sortTxn();
        removeTimeoutTxn();

        List<TX> rtn = new ArrayList<>();
        for (Map.Entry<Address, AccountState>  as : this.getFullAcc().entrySet()) {
            for (Map.Entry<ByteArrayWrapper, BigInteger> txMap : as.getValue().getMap().values()) {
                if (this.getMainMap().get(txMap.getKey()) == null) {
                    LOG.error("can't find the tx in the mainMap");
                    continue;
                }

                rtn.add((TX)this.getMainMap().get(txMap.getKey()).getTx().clone());
            }
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("TxPoolA0.snapshot All return [{}] TX, poolSize[{}]", rtn.size(), getMainMap().size());
        }

        if (rtn.size() != getMainMap().size()) {
            LOG.error("size does not match!");
        }

        return rtn;
    }

    public List<TX> snapshot() {

        sortTxn();
        removeTimeoutTxn();

        int cnt_txSz = 0;
        long cnt_nrg = 0;
        List<TX> rtn = new ArrayList<>();
        Set<ByteArrayWrapper> snapshotSet = new HashSet<>();
        Map<ByteArrayWrapper, Entry<ByteArrayWrapper, TxDependList<ByteArrayWrapper>>> nonPickedTx = new HashMap<>();
        for (Entry<BigInteger, Map<ByteArrayWrapper, TxDependList<ByteArrayWrapper>>> e : this.getFeeView()
                .entrySet()) {

            if (LOG.isTraceEnabled()) {
                LOG.trace("snapshot  fee[{}]", e.getKey().toString());
            }

            SortedMap<BigInteger, Entry<ByteArrayWrapper, TxDependList<ByteArrayWrapper>>> timeTxDep = Collections
                    .synchronizedSortedMap(new TreeMap<>());
            for (Entry<ByteArrayWrapper, TxDependList<ByteArrayWrapper>> pair : e.getValue().entrySet()) {
                BigInteger ts = pair.getValue().getTimeStamp();
                while (timeTxDep.get(ts.add(BigInteger.ONE)) != null) {
                    ts = ts.add(BigInteger.ONE);
                }
                timeTxDep.put(ts, pair);
            }

            for(Entry<ByteArrayWrapper, TxDependList<ByteArrayWrapper>> pair : timeTxDep.values()) {
                // Check the small nonce tx must been picked before put the high nonce tx
                ByteArrayWrapper dependTx = pair.getValue().getDependTx();
                if (dependTx == null || snapshotSet.contains(dependTx)) {
                    boolean firstTx = true;
                    for (ByteArrayWrapper bw : pair.getValue().getTxList()) {
                        ITransaction itx = this.getMainMap().get(bw).getTx();

                        cnt_txSz += itx.getEncoded().length;
                        cnt_nrg += itx.getNrgConsume();
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("from:[{}] nonce:[{}] txSize: txSize[{}] nrgConsume[{}]",
                                    itx.getFrom().toString(), itx.getNonceBI().toString(),
                                    itx.getEncoded().length, itx.getNrgConsume());
                        }

                        if (cnt_txSz < blkSizeLimit && cnt_nrg < blkNrgLimit.get()) {
                            try {
                                rtn.add((TX) itx.clone());
                                if (firstTx) {
                                    snapshotSet.add(bw);
                                    firstTx = false;
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();

                                if (LOG.isErrorEnabled()) {
                                    LOG.error("TxPoolA0.snapshot  exception[{}], return [{}] TX", ex.toString(), rtn.size());
                                }
                                return rtn;
                            }
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Reach blockLimit: txSize[{}], nrgConsume[{}], tx#[{}]", cnt_txSz,
                                        cnt_nrg, rtn.size());
                            }

                            return rtn;
                        }
                    }

                    ByteArrayWrapper ancestor = pair.getKey();
                    while (nonPickedTx.get(ancestor)!= null) {
                        firstTx = true;
                        for (ByteArrayWrapper bw : nonPickedTx.get(ancestor).getValue().getTxList()) {
                            ITransaction itx = this.getMainMap().get(bw).getTx();

                            cnt_txSz += itx.getEncoded().length;
                            cnt_nrg += itx.getNrgConsume();
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("from:[{}] nonce:[{}] txSize: txSize[{}] nrgConsume[{}]",
                                        itx.getFrom().toString(), itx.getNonceBI().toString(), itx.getEncoded().length,
                                        itx.getNrgConsume());
                            }

                            if (cnt_txSz < blkSizeLimit && cnt_nrg < blkNrgLimit.get()) {
                                try {
                                    rtn.add((TX) itx.clone());
                                    if (firstTx) {
                                        snapshotSet.add(bw);
                                        firstTx = false;
                                    }
                                } catch (Exception ex) {
                                    ex.printStackTrace();

                                    if (LOG.isErrorEnabled()) {
                                        LOG.error("TxPoolA0.snapshot  exception[{}], return [{}] TX", ex.toString(), rtn.size());
                                    }
                                    return rtn;
                                }
                            } else {
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("Reach blockLimit: txSize[{}], nrgConsume[{}], tx#[{}]", cnt_txSz, cnt_nrg,
                                            rtn.size());
                                }

                                return rtn;
                            }
                        }

                        ancestor = nonPickedTx.get(ancestor).getKey();
                    }
                } else {
                    // one low fee small nonce tx has been picked,and then search from this map.
                    nonPickedTx.put(pair.getValue().getDependTx(), pair);
                }
            }
        }


        if (LOG.isInfoEnabled()) {
            LOG.info("TxPoolA0.snapshot return [{}] TX, poolSize[{}]", rtn.size(), getMainMap().size());
        }

        return rtn;
    }

    @Override
    public String getVersion() {
        return "0.1.0";
    }

    public BigInteger bestPoolNonce(Address addr) {
        return getBestNonce(addr);
    }

    private void removeTimeoutTxn() {

        long ts = TimeInstant.now().toEpochSec() - txn_timeout;
        List<TX> txl = Collections.synchronizedList(new ArrayList<>());

        this.getTimeView().entrySet().parallelStream().forEach(e -> {
            if (e.getKey() < ts) {
                for (ByteArrayWrapper bw : e.getValue()) {
                    txl.add(this.getMainMap().get(bw).getTx());
                }
            }
        });

        if (txl.isEmpty()) {
            return;
        }

        this.addOutDatedList(txl);
        this.remove(txl);

        if (LOG.isDebugEnabled()) {
            LOG.debug("TxPoolA0.remove return [{}] TX, poolSize[{}]", txl.size(), getMainMap().size());
        }

    }

}

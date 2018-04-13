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

package org.aion.txpool.common;

import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.base.Constant;
import org.slf4j.Logger;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class AbstractTxPool<TX extends ITransaction> {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.TXPOOL.toString());

    protected int seqTxCountMax = 16;
    protected int txn_timeout = 86_400; // 1 day by seconds
    protected int blkSizeLimit = Constant.MAX_BLK_SIZE; // 2MB

    protected final AtomicLong blkNrgLimit = new AtomicLong(10_000_000L);
    protected final int multiplyM = 1_000_000;
    protected final int TXN_TIMEOUT_MIN = 10; // 10s
    protected final int TXN_TIMEOUT_MAX = 86_400; // 1 day

    protected final int BLK_SIZE_MAX = 16 * 1024 * 1024; // 16MB
    protected final int BLK_SIZE_MIN = 1024 * 1024; // 1MB

    protected final int BLK_NRG_MAX = 100_000_000;
    protected final int BLK_NRG_MIN = 1_000_000;
    protected final int SEQ_TX_MAX = 25;
    protected final int SEQ_TX_MIN = 5;
    /**
     * mainMap : Map<ByteArrayWrapper, TXState>
     *
     * @ByteArrayWrapper transaction hash
     * @TXState transaction data and sort status
     */
    // TODO : should limit size
    private final Map<ByteArrayWrapper, TXState> mainMap = new ConcurrentHashMap<>();
    /**
     * timeView : SortedMap<Long, LinkedHashSet<ByteArrayWrapper>>
     *
     * @Long transaction timestamp
     * @LinkedHashSet<ByteArrayWrapper> the hashSet of the transaction hash*
     */
    private final SortedMap<Long, LinkedHashSet<ByteArrayWrapper>> timeView = Collections
            .synchronizedSortedMap(new TreeMap<>());
    /**
     * feeView : SortedMap<BigInteger,
     * LinkedHashSet<TxPoolList<ByteArrayWrapper>>>
     *
     * @BigInteger energy cost = energy consumption * energy price
     * @LinkedHashSet<TxPoolList<ByteArrayWrapper>> the TxPoolList of the first
     *                                              transaction hash
     */
    private final SortedMap<BigInteger, Map<ByteArrayWrapper, TxDependList<ByteArrayWrapper>>> feeView = Collections
            .synchronizedSortedMap(new TreeMap<>(Collections.reverseOrder()));
    /**
     * accountView : Map<ByteArrayWrapper, AccountState>
     *
     * @ByteArrayWrapper account address
     * @AccountState
     */
    private final Map<Address, AccountState> accountView = new ConcurrentHashMap<>();
    /**
     * poolStateView : Map<ByteArrayWrapper, List<PoolState>>
     *
     * @ByteArrayWrapper account address
     * @PoolState continuous transaction state including starting nonce
     */
    private final Map<Address, List<PoolState>> poolStateView = new ConcurrentHashMap<>();
    private final List<TX> outDated = new ArrayList<>();

    private final Map<Address, BigInteger> bestNonce = new ConcurrentHashMap<>();

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public abstract List<TX> add(List<TX> txl);

    public abstract TX add(TX tx);

    public abstract List<TX> remove(List<TX> txl);

    public abstract int size();

    public abstract List<TX> snapshot();

    protected Map<ByteArrayWrapper, TXState> getMainMap() {
        return this.mainMap;
    }

    protected SortedMap<BigInteger, Map<ByteArrayWrapper, TxDependList<ByteArrayWrapper>>> getFeeView() {
        return this.feeView;
    }

    protected AccountState getAccView(Address acc) {

        this.accountView.computeIfAbsent(acc, k -> new AccountState());
        return this.accountView.get(acc);
    }

    protected Map<Address, AccountState> getFullAcc() {
        return this.accountView;
    }

    protected List<PoolState> getPoolStateView(Address acc) {

        if (this.accountView.get(acc) == null) {
            this.poolStateView.put(acc, new LinkedList<>());
        }
        return this.poolStateView.get(acc);
    }

    protected List<TX> getOutdatedListImpl() {
        List<TX> rtn = new ArrayList<>(this.outDated);
        this.outDated.clear();

        return rtn;
    }

    protected void addOutDatedList(List<TX> txl) {
        this.outDated.addAll(txl);
    }

    public void clear() {
        this.mainMap.clear();
        this.timeView.clear();
        this.feeView.clear();
        this.accountView.clear();
        this.poolStateView.clear();
        this.outDated.clear();
    }

    protected void sortTxn() {

        Map<Address, Map<BigInteger, SimpleEntry<ByteArrayWrapper, BigInteger>>> accMap = new ConcurrentHashMap<>();
        SortedMap<Long, LinkedHashSet<ByteArrayWrapper>> timeMap = Collections.synchronizedSortedMap(new TreeMap<>());

        Map<ITransaction, Long> updatedTx = new HashMap<>();
        this.mainMap.entrySet().parallelStream().forEach(e -> {

            TXState ts = e.getValue();
            if (ts.sorted()) {
                return;
            }

            ITransaction tx = ts.getTx();

            // Gen temp timeMap
            long timestamp = tx.getTimeStampBI().longValue() / multiplyM;

            Map<BigInteger, SimpleEntry<ByteArrayWrapper, BigInteger>> nonceMap;
            ITransaction replacedTx = null;
            synchronized (accMap) {
                if (accMap.get(tx.getFrom()) != null) {
                    nonceMap = accMap.get(tx.getFrom());
                } else {
                    nonceMap = Collections.synchronizedSortedMap(new TreeMap<>());
                }

                // considering refactor later
                BigInteger nonce = tx.getNonceBI();

                BigInteger nrgCharge = BigInteger.valueOf(tx.getNrgPrice())
                        .multiply(BigInteger.valueOf(tx.getNrgConsume()));

                if (LOG.isTraceEnabled()) {
                    LOG.trace("AbstractTxPool.sortTxn Put tx into nonceMap: nonce:[{}] ts:[{}] nrgCharge:[{}]", nonce,
                            ByteUtils.toHexString(e.getKey().getData()), nrgCharge.toString());
                }

                // considering same nonce tx, only put the latest tx.
                if (nonceMap.get(nonce) != null) {
                    try {
                        if (this.mainMap.get(nonceMap.get(nonce).getKey()).getTx().getTimeStampBI()
                                .compareTo(tx.getTimeStampBI()) < 1) {
                            replacedTx = this.mainMap.get(nonceMap.get(nonce).getKey()).getTx();
                            updatedTx.put(replacedTx, timestamp);
                            nonceMap.put(nonce, new SimpleEntry<>(e.getKey(), nrgCharge));

                        }
                    } catch (Exception ex) {
                        LOG.error("AbsTxPool.sortTxn {} [{}]", ex.toString(), tx.toString());
                    }
                } else {
                    nonceMap.put(nonce, new SimpleEntry<>(e.getKey(), nrgCharge));
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("AbstractTxPool.sortTxn Put tx into accMap: acc:[{}] mapSize[{}] ",
                            tx.getFrom().toString(), nonceMap.size());
                }

                accMap.put(tx.getFrom(), nonceMap);
            }

            LinkedHashSet<ByteArrayWrapper> lhs;
            synchronized (timeMap) {
                if (timeMap.get(timestamp) != null) {
                    lhs = timeMap.get(timestamp);
                } else {
                    lhs = new LinkedHashSet<>();
                }

                lhs.add(e.getKey());

                if (LOG.isTraceEnabled()) {
                    LOG.trace("AbstractTxPool.sortTxn Put txHash into timeMap: ts:[{}] size:[{}]", timestamp,
                            lhs.size());
                }

                timeMap.put(timestamp, lhs);

                if (replacedTx != null) {
                    long t = replacedTx.getTimeStampBI().longValue() / multiplyM;
                    if (timeMap.get(t) != null) {
                        timeMap.get(t).remove(ByteArrayWrapper.wrap(replacedTx.getHash()));
                    }
                }
            }

            ts.setSorted();
        });

        if (!updatedTx.isEmpty()) {
            for (Map.Entry<ITransaction, Long> en : updatedTx.entrySet()) {
                ByteArrayWrapper bw = ByteArrayWrapper.wrap(en.getKey().getHash());
                if (this.timeView.get(en.getValue()) != null) {
                    this.timeView.get(en.getValue()).remove(bw);
                }

                lock.writeLock().lock();
                this.mainMap.remove(bw);
                lock.writeLock().unlock();
            }
        }

        if (!accMap.isEmpty()) {

            timeMap.entrySet().parallelStream().forEach(e -> {
                if (this.timeView.get(e.getKey()) == null) {
                    this.timeView.put(e.getKey(), e.getValue());
                } else {
                    this.timeView.get(e.getKey()).addAll(e.getValue());
                }
            });

            accMap.entrySet().parallelStream().forEach(e -> {
                lock.writeLock().lock();
                this.accountView.computeIfAbsent(e.getKey(), k -> new AccountState());
                this.accountView.get(e.getKey()).updateMap(e.getValue());
                lock.writeLock().unlock();
            });

            updateAccPoolState();
            updateFeeMap();
        }
    }

    protected SortedMap<Long, LinkedHashSet<ByteArrayWrapper>> getTimeView() {
        return this.timeView;
    }

    protected void updateAccPoolState() {

        // iterate tx by account
        List<Address> clearAddr = new ArrayList<>();
        for (Entry<Address, AccountState> e : this.accountView.entrySet()) {
            AccountState as = e.getValue();
            if (as.isDirty()) {

                if (as.getMap().isEmpty()) {
                    this.poolStateView.remove(e.getKey());
                    clearAddr.add(e.getKey());
                } else {
                    // checking AccountState given by account
                    List<PoolState> psl = this.poolStateView.get(e.getKey());
                    if (psl == null) {
                        psl = new LinkedList<>();
                    }

                    List<PoolState> newPoolState = new LinkedList<>();
                    // Checking new tx has been include into old pools.
                    BigInteger txNonceStart = as.getFirstNonce();

                    if (txNonceStart != null) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("AbstractTxPool.updateAccPoolState fn [{}]", txNonceStart.toString());
                        }
                        for (PoolState ps : psl) {
                            // check the previous txn status in the old
                            // PoolState
                            if (isClean(ps, as) && ps.firstNonce.equals(txNonceStart) && ps.combo == seqTxCountMax) {
                                ps.resetInFeePool();
                                newPoolState.add(ps);

                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("AbstractTxPool.updateAccPoolState add fn [{}]",
                                            ps.firstNonce.toString());
                                }

                                txNonceStart = txNonceStart.add(BigInteger.valueOf(seqTxCountMax));
                            } else {
                                // remove old poolState in the feeMap
                                if (this.feeView.get(ps.getFee()) != null) {

                                    if (e.getValue().getMap().get(ps.firstNonce) != null) {
                                        this.feeView.get(ps.getFee())
                                                .remove(e.getValue().getMap().get(ps.firstNonce).getKey());
                                    }

                                    if (LOG.isTraceEnabled()) {
                                        LOG.trace("AbstractTxPool.updateAccPoolState remove fn [{}]",
                                                ps.firstNonce.toString());
                                    }

                                    if (this.feeView.get(ps.getFee()).isEmpty()) {
                                        this.feeView.remove(ps.getFee());
                                    }
                                }
                            }
                        }
                    }

                    int cnt = 0;
                    BigInteger fee = BigInteger.ZERO;
                    BigInteger totalFee = BigInteger.ZERO;

                    for (Entry<BigInteger, SimpleEntry<ByteArrayWrapper, BigInteger>> en : as.getMap().entrySet()) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace(
                                    "AbstractTxPool.updateAccPoolState mapsize[{}] nonce:[{}] cnt[{}] txNonceStart[{}]",
                                    as.getMap().size(), en.getKey().toString(), cnt,
                                    txNonceStart != null ? txNonceStart.toString() : null);
                        }
                        if (en.getKey()
                                .equals(txNonceStart != null ? txNonceStart.add(BigInteger.valueOf(cnt)) : null)) {
                            if (en.getValue().getValue().compareTo(fee) > -1) {
                                fee = en.getValue().getValue();
                                totalFee = totalFee.add(fee);

                                if (++cnt == seqTxCountMax) {
                                    if (LOG.isTraceEnabled()) {
                                        LOG.trace(
                                                "AbstractTxPool.updateAccPoolState case1 - nonce:[{}] totalFee:[{}] cnt:[{}]",
                                                txNonceStart, totalFee.toString(), cnt);
                                    }
                                    newPoolState.add(
                                            new PoolState(txNonceStart, totalFee.divide(BigInteger.valueOf(cnt)), cnt));

                                    txNonceStart = en.getKey().add(BigInteger.ONE);
                                    totalFee = BigInteger.ZERO;
                                    fee = BigInteger.ZERO;
                                    cnt = 0;
                                }
                            } else {
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace(
                                            "AbstractTxPool.updateAccPoolState case2 - nonce:[{}] totalFee:[{}] cnt:[{}]",
                                            txNonceStart, totalFee.toString(), cnt);
                                }
                                newPoolState.add(
                                        new PoolState(txNonceStart, totalFee.divide(BigInteger.valueOf(cnt)), cnt));

                                // next PoolState
                                txNonceStart = en.getKey();
                                fee = en.getValue().getValue();
                                totalFee = fee;
                                cnt = 1;
                            }
                        }
                    }

                    if (totalFee.signum() == 1) {

                        if (LOG.isTraceEnabled()) {
                            LOG.trace(
                                    "AbstractTxPool.updateAccPoolState case3 - nonce:[{}] totalFee:[{}] cnt:[{}] bw:[{}]",
                                    txNonceStart, totalFee.toString(), cnt, e.getKey().toString());
                        }

                        newPoolState.add(new PoolState(txNonceStart, totalFee.divide(BigInteger.valueOf(cnt)), cnt));
                    }

                    this.poolStateView.put(e.getKey(), newPoolState);

                    if (LOG.isTraceEnabled()) {
                        this.poolStateView.forEach((k, v) -> v.forEach(l -> {
                            LOG.trace("AbstractTxPool.updateAccPoolState - the first nonce of the poolState list:[{}]",
                                    l.firstNonce);
                        }));
                    }
                    as.sorted();
                }
            }
        }

        if (!clearAddr.isEmpty()) {
            clearAddr.forEach(addr -> {
                lock.writeLock().lock();
                this.accountView.remove(addr);
                lock.writeLock().unlock();
                this.bestNonce.remove(addr);
            });
        }
    }

    private boolean isClean(PoolState ps, AccountState as) {
        if (ps == null || as == null) {
            throw new NullPointerException();
        }

        for (BigInteger bi = ps.getFirstNonce(); bi
                .compareTo(ps.firstNonce.add(BigInteger.valueOf(ps.getCombo()))) < 0; bi = bi.add(BigInteger.ONE)) {
            if (!as.getMap().containsKey(bi)) {
                return false;
            }
        }

        return true;
    }

    protected void updateFeeMap() {
        for (Entry<Address, List<PoolState>> e : this.poolStateView.entrySet()) {
            ByteArrayWrapper dependTx = null;
            for (PoolState ps : e.getValue()) {

                if (LOG.isTraceEnabled()) {
                    LOG.trace("updateFeeMap addr[{}] inFp[{}] fn[{}] cb[{}] fee[{}]", e.getKey().toString(),
                            ps.isInFeePool(), ps.getFirstNonce().toString(), ps.getCombo(), ps.getFee().toString());
                }

                if (ps.isInFeePool()) {
                    dependTx = this.accountView.get(e.getKey()).getMap().get(ps.getFirstNonce()).getKey();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("updateFeeMap isInFeePool [{}]", dependTx.toString());
                    }
                } else {

                    TxDependList<ByteArrayWrapper> txl = new TxDependList<>();
                    BigInteger timestamp = BigInteger.ZERO;
                    for (BigInteger i = ps.firstNonce; i.compareTo(
                            ps.firstNonce.add(BigInteger.valueOf(ps.combo))) < 0; i = i.add(BigInteger.ONE)) {

                        ByteArrayWrapper bw = this.accountView.get(e.getKey()).getMap().get(i).getKey();
                        if (i.equals(ps.firstNonce)) {
                            timestamp = this.mainMap.get(bw).getTx().getTimeStampBI();
                        }

                        txl.addTx(bw);
                    }

                    if (!txl.isEmpty()) {
                        txl.setDependTx(dependTx);
                        dependTx = txl.getTxList().get(0);
                        txl.setAddress(e.getKey());
                        txl.setTimeStamp(timestamp);
                    }

                    if (this.feeView.get(ps.fee) == null) {
                        Map<ByteArrayWrapper, TxDependList<ByteArrayWrapper>> set = new LinkedHashMap<>();
                        set.put(txl.getTxList().get(0), txl);

                        if (LOG.isTraceEnabled()) {
                            LOG.trace("updateFeeMap new feeView put fee[{}]", ps.fee);
                        }

                        this.feeView.put(ps.fee, set);
                    } else {

                        if (LOG.isTraceEnabled()) {
                            LOG.trace("updateFeeMap update feeView put fee[{}]", ps.fee);
                        }

                        this.feeView.get(ps.fee).put(txl.getTxList().get(0), txl);
                    }

                    ps.setInFeePool();
                }
            }
        }
    }

    protected void setBestNonce(Address addr, BigInteger bn) {
        if (addr == null || bn == null) {
            throw new NullPointerException();
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("addr[{}] bn[{}] txnonce[{}]", addr.toString(),
                    bestNonce.get(addr) == null ? "-1" : bestNonce.get(addr).toString(), bn.toString());
        }

        if (bestNonce.get(addr) == null || bestNonce.get(addr).compareTo(bn) < 0) {
            bestNonce.put(addr, bn);
        }
    }

    protected BigInteger getBestNonce(Address addr) {
        if (addr == null || bestNonce.get(addr) == null) {
            return BigInteger.ONE.negate();
        }

        return bestNonce.get(addr);
    }

    protected class TXState {
        private boolean sorted = false;
        private TX tx;

        public TXState(TX tx) {
            this.tx = tx;
        }

        public TX getTx() {
            return this.tx;
        }

        boolean sorted() {
            return this.sorted;
        }

        void setSorted() {
            this.sorted = true;
        }
    }

    protected class PoolState {
        private final AtomicBoolean inFeePool = new AtomicBoolean(false);
        private BigInteger fee;
        private BigInteger firstNonce;
        private int combo;

        PoolState(BigInteger nonce, BigInteger fee, int combo) {
            this.firstNonce = nonce;
            this.combo = combo;
            this.fee = fee;
        }

        public boolean contains(BigInteger bi) {
            return (bi.compareTo(firstNonce) > -1) && (bi.compareTo(firstNonce.add(BigInteger.valueOf(combo))) < 0);
        }

        public BigInteger getFee() {
            return fee;
        }

        BigInteger getFirstNonce() {
            return firstNonce;
        }

        int getCombo() {
            return combo;
        }

        boolean isInFeePool() {
            return inFeePool.get();
        }

        void setInFeePool() {
            inFeePool.set(true);
        }

        void resetInFeePool() {
            inFeePool.set(false);
        }
    }
}

package org.aion.txpool.common;

import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.base.AionTransaction;
import org.aion.base.PooledTransaction;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.txpool.Constant;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.ByteArrayWrapper;
import org.slf4j.Logger;

public abstract class AbstractTxPool {

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
     * mainMap : Map<ByteArrayWrapper, TXState> @ByteArrayWrapper transaction hash @TXState
     * transaction data and sort status
     */
    // TODO : should limit size
    private final Map<ByteArrayWrapper, TXState> mainMap = new ConcurrentHashMap<>();
    /**
     * timeView : SortedMap<Long, LinkedHashSet<ByteArrayWrapper>> @Long transaction
     * timestamp @LinkedHashSet<ByteArrayWrapper> the hashSet of the transaction hash*
     */
    private final SortedMap<Long, LinkedHashSet<ByteArrayWrapper>> timeView =
            Collections.synchronizedSortedMap(new TreeMap<>());
    /**
     * feeView : SortedMap<BigInteger, LinkedHashSet<TxPoolList<ByteArrayWrapper>>> @BigInteger
     * energy cost = energy consumption * energy price @LinkedHashSet<TxPoolList<ByteArrayWrapper>>
     * the TxPoolList of the first transaction hash
     */
    private final SortedMap<BigInteger, Map<ByteArrayWrapper, TxDependList<ByteArrayWrapper>>>
            feeView = Collections.synchronizedSortedMap(new TreeMap<>(Collections.reverseOrder()));
    /**
     * accountView : Map<ByteArrayWrapper, AccountState> @ByteArrayWrapper account
     * address @AccountState
     */
    private final Map<AionAddress, AccountState> accountView = new ConcurrentHashMap<>();
    /**
     * poolStateView : Map<ByteArrayWrapper, List<PoolState>> @ByteArrayWrapper account
     * address @PoolState continuous transaction state including starting nonce
     */
    private final Map<AionAddress, List<PoolState>> poolStateView = new ConcurrentHashMap<>();

    private final List<PooledTransaction> outDated = new ArrayList<>();

    private final Map<AionAddress, BigInteger> bestNonce = new ConcurrentHashMap<>();

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public abstract List<PooledTransaction> add(List<PooledTransaction> txl);

    public abstract PooledTransaction add(PooledTransaction tx);

    public abstract List<PooledTransaction> remove(List<PooledTransaction> txl);

    public abstract int size();

    public abstract List<AionTransaction> snapshot();

    protected Map<ByteArrayWrapper, TXState> getMainMap() {
        return this.mainMap;
    }

    protected SortedMap<BigInteger, Map<ByteArrayWrapper, TxDependList<ByteArrayWrapper>>>
            getFeeView() {
        return this.feeView;
    }

    protected AccountState getAccView(AionAddress acc) {

        this.accountView.computeIfAbsent(acc, k -> new AccountState());
        return this.accountView.get(acc);
    }

    protected Map<AionAddress, AccountState> getFullAcc() {
        return this.accountView;
    }

    protected List<PoolState> getPoolStateView(AionAddress acc) {

        if (this.accountView.get(acc) == null) {
            this.poolStateView.put(acc, new LinkedList<>());
        }
        return this.poolStateView.get(acc);
    }

    protected List<PooledTransaction> getOutdatedListImpl() {
        List<PooledTransaction> rtn = new ArrayList<>(this.outDated);
        this.outDated.clear();

        return rtn;
    }

    protected void addOutDatedList(List<PooledTransaction> txl) {
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

        Map<AionAddress, Map<BigInteger, SimpleEntry<ByteArrayWrapper, BigInteger>>> accMap =
                new ConcurrentHashMap<>();
        SortedMap<Long, LinkedHashSet<ByteArrayWrapper>> timeMap =
                Collections.synchronizedSortedMap(new TreeMap<>());

        Map<PooledTransaction, Long> updatedTx = new HashMap<>();
        this.mainMap
                .entrySet()
                .parallelStream()
                .forEach(
                        e -> {
                            TXState ts = e.getValue();
                            if (ts.sorted()) {
                                return;
                            }

                            PooledTransaction pooledTx = ts.getTx();

                            // Gen temp timeMap
                            long timestamp = pooledTx.tx.getTimeStampBI().longValue() / multiplyM;

                            Map<BigInteger, SimpleEntry<ByteArrayWrapper, BigInteger>> nonceMap;
                            PooledTransaction replacedTx = null;
                            synchronized (accMap) {
                                if (accMap.get(pooledTx.tx.getSenderAddress()) != null) {
                                    nonceMap = accMap.get(pooledTx.tx.getSenderAddress());
                                } else {
                                    nonceMap = Collections.synchronizedSortedMap(new TreeMap<>());
                                }

                                // considering refactor later
                                BigInteger nonce = pooledTx.tx.getNonceBI();

                                BigInteger nrgCharge =
                                        BigInteger.valueOf(pooledTx.tx.getEnergyPrice())
                                                .multiply(BigInteger.valueOf(pooledTx.energyConsumed));

                                if (LOG.isTraceEnabled()) {
                                    LOG.trace(
                                            "AbstractTxPool.sortTxn Put tx into nonceMap: nonce:[{}] ts:[{}] nrgCharge:[{}]",
                                            nonce,
                                            ByteUtil.toHexString(e.getKey().getData()),
                                            nrgCharge.toString());
                                }

                                // considering same nonce tx, only put the latest tx.
                                if (nonceMap.get(nonce) != null) {
                                    try {
                                        if (this.mainMap
                                                        .get(nonceMap.get(nonce).getKey())
                                                        .getTx()
                                                        .tx
                                                        .getTimeStampBI()
                                                        .compareTo(pooledTx.tx.getTimeStampBI())
                                                < 1) {
                                            replacedTx =
                                                    this.mainMap
                                                            .get(nonceMap.get(nonce).getKey())
                                                            .getTx();
                                            updatedTx.put(replacedTx, timestamp);
                                            nonceMap.put(
                                                    nonce,
                                                    new SimpleEntry<>(e.getKey(), nrgCharge));
                                        }
                                    } catch (Exception ex) {
                                        LOG.error(
                                                "AbsTxPool.sortTxn {} [{}]",
                                                ex.toString(),
                                                pooledTx.toString());
                                    }
                                } else {
                                    nonceMap.put(nonce, new SimpleEntry<>(e.getKey(), nrgCharge));
                                }

                                if (LOG.isTraceEnabled()) {
                                    LOG.trace(
                                            "AbstractTxPool.sortTxn Put tx into accMap: acc:[{}] mapSize[{}] ",
                                            pooledTx.tx.getSenderAddress().toString(),
                                            nonceMap.size());
                                }

                                accMap.put(pooledTx.tx.getSenderAddress(), nonceMap);
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
                                    LOG.trace(
                                            "AbstractTxPool.sortTxn Put txHash into timeMap: ts:[{}] size:[{}]",
                                            timestamp,
                                            lhs.size());
                                }

                                timeMap.put(timestamp, lhs);

                                if (replacedTx != null) {
                                    long t = replacedTx.tx.getTimeStampBI().longValue() / multiplyM;
                                    if (timeMap.get(t) != null) {
                                        timeMap.get(t)
                                                .remove(
                                                        ByteArrayWrapper.wrap(
                                                                replacedTx.tx.getTransactionHash()));
                                    }
                                }
                            }

                            ts.setSorted();
                        });

        if (!updatedTx.isEmpty()) {
            for (Map.Entry<PooledTransaction, Long> en : updatedTx.entrySet()) {
                ByteArrayWrapper bw = ByteArrayWrapper.wrap(en.getKey().tx.getTransactionHash());
                if (this.timeView.get(en.getValue()) != null) {
                    this.timeView.get(en.getValue()).remove(bw);
                }

                lock.writeLock().lock();
                this.mainMap.remove(bw);
                lock.writeLock().unlock();
            }
        }

        if (!accMap.isEmpty()) {

            timeMap.entrySet()
                    .parallelStream()
                    .forEach(
                            e -> {
                                if (this.timeView.get(e.getKey()) == null) {
                                    this.timeView.put(e.getKey(), e.getValue());
                                } else {
                                    this.timeView.get(e.getKey()).addAll(e.getValue());
                                }
                            });

            accMap.entrySet()
                    .parallelStream()
                    .forEach(
                            e -> {
                                lock.writeLock().lock();
                                this.accountView.computeIfAbsent(
                                        e.getKey(), k -> new AccountState());
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
        List<AionAddress> clearAddr = new ArrayList<>();
        for (Entry<AionAddress, AccountState> e : this.accountView.entrySet()) {
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
                            LOG.trace(
                                    "AbstractTxPool.updateAccPoolState fn [{}]",
                                    txNonceStart.toString());
                        }
                        for (PoolState ps : psl) {
                            // check the previous txn status in the old
                            // PoolState
                            if (isClean(ps, as)
                                    && ps.firstNonce.equals(txNonceStart)
                                    && ps.combo == seqTxCountMax) {
                                ps.resetInFeePool();
                                newPoolState.add(ps);

                                if (LOG.isTraceEnabled()) {
                                    LOG.trace(
                                            "AbstractTxPool.updateAccPoolState add fn [{}]",
                                            ps.firstNonce.toString());
                                }

                                txNonceStart = txNonceStart.add(BigInteger.valueOf(seqTxCountMax));
                            } else {
                                // remove old poolState in the feeMap
                                if (this.feeView.get(ps.getFee()) != null) {

                                    if (e.getValue().getMap().get(ps.firstNonce) != null) {
                                        this.feeView
                                                .get(ps.getFee())
                                                .remove(
                                                        e.getValue()
                                                                .getMap()
                                                                .get(ps.firstNonce)
                                                                .getKey());
                                    }

                                    if (LOG.isTraceEnabled()) {
                                        LOG.trace(
                                                "AbstractTxPool.updateAccPoolState remove fn [{}]",
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

                    for (Entry<BigInteger, SimpleEntry<ByteArrayWrapper, BigInteger>> en :
                            as.getMap().entrySet()) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace(
                                    "AbstractTxPool.updateAccPoolState mapsize[{}] nonce:[{}] cnt[{}] txNonceStart[{}]",
                                    as.getMap().size(),
                                    en.getKey().toString(),
                                    cnt,
                                    txNonceStart != null ? txNonceStart.toString() : null);
                        }
                        if (en.getKey()
                                .equals(
                                        txNonceStart != null
                                                ? txNonceStart.add(BigInteger.valueOf(cnt))
                                                : null)) {
                            if (en.getValue().getValue().compareTo(fee) > -1) {
                                fee = en.getValue().getValue();
                                totalFee = totalFee.add(fee);

                                if (++cnt == seqTxCountMax) {
                                    if (LOG.isTraceEnabled()) {
                                        LOG.trace(
                                                "AbstractTxPool.updateAccPoolState case1 - nonce:[{}] totalFee:[{}] cnt:[{}]",
                                                txNonceStart,
                                                totalFee.toString(),
                                                cnt);
                                    }
                                    newPoolState.add(
                                            new PoolState(
                                                    txNonceStart,
                                                    totalFee.divide(BigInteger.valueOf(cnt)),
                                                    cnt));

                                    txNonceStart = en.getKey().add(BigInteger.ONE);
                                    totalFee = BigInteger.ZERO;
                                    fee = BigInteger.ZERO;
                                    cnt = 0;
                                }
                            } else {
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace(
                                            "AbstractTxPool.updateAccPoolState case2 - nonce:[{}] totalFee:[{}] cnt:[{}]",
                                            txNonceStart,
                                            totalFee.toString(),
                                            cnt);
                                }
                                newPoolState.add(
                                        new PoolState(
                                                txNonceStart,
                                                totalFee.divide(BigInteger.valueOf(cnt)),
                                                cnt));

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
                                    txNonceStart,
                                    totalFee.toString(),
                                    cnt,
                                    e.getKey().toString());
                        }

                        newPoolState.add(
                                new PoolState(
                                        txNonceStart,
                                        totalFee.divide(BigInteger.valueOf(cnt)),
                                        cnt));
                    }

                    this.poolStateView.put(e.getKey(), newPoolState);

                    if (LOG.isTraceEnabled()) {
                        this.poolStateView.forEach(
                                (k, v) ->
                                        v.forEach(
                                                l -> {
                                                    LOG.trace(
                                                            "AbstractTxPool.updateAccPoolState - the first nonce of the poolState list:[{}]",
                                                            l.firstNonce);
                                                }));
                    }
                    as.sorted();
                }
            }
        }

        if (!clearAddr.isEmpty()) {
            clearAddr.forEach(
                    addr -> {
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

        for (BigInteger bi = ps.getFirstNonce();
                bi.compareTo(ps.firstNonce.add(BigInteger.valueOf(ps.getCombo()))) < 0;
                bi = bi.add(BigInteger.ONE)) {
            if (!as.getMap().containsKey(bi)) {
                return false;
            }
        }

        return true;
    }

    protected void updateFeeMap() {
        for (Entry<AionAddress, List<PoolState>> e : this.poolStateView.entrySet()) {
            ByteArrayWrapper dependTx = null;
            for (PoolState ps : e.getValue()) {

                if (LOG.isTraceEnabled()) {
                    LOG.trace(
                            "updateFeeMap addr[{}] inFp[{}] fn[{}] cb[{}] fee[{}]",
                            e.getKey().toString(),
                            ps.isInFeePool(),
                            ps.getFirstNonce().toString(),
                            ps.getCombo(),
                            ps.getFee().toString());
                }

                if (ps.isInFeePool()) {
                    dependTx =
                            this.accountView
                                    .get(e.getKey())
                                    .getMap()
                                    .get(ps.getFirstNonce())
                                    .getKey();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("updateFeeMap isInFeePool [{}]", dependTx.toString());
                    }
                } else {

                    TxDependList<ByteArrayWrapper> txl = new TxDependList<>();
                    BigInteger timestamp = BigInteger.ZERO;
                    for (BigInteger i = ps.firstNonce;
                            i.compareTo(ps.firstNonce.add(BigInteger.valueOf(ps.combo))) < 0;
                            i = i.add(BigInteger.ONE)) {

                        ByteArrayWrapper bw =
                                this.accountView.get(e.getKey()).getMap().get(i).getKey();
                        if (i.equals(ps.firstNonce)) {
                            timestamp = this.mainMap.get(bw).getTx().tx.getTimeStampBI();
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
                        Map<ByteArrayWrapper, TxDependList<ByteArrayWrapper>> set =
                                new LinkedHashMap<>();
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

    protected void setBestNonce(AionAddress addr, BigInteger bn) {
        if (addr == null || bn == null) {
            throw new NullPointerException();
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace(
                    "addr[{}] bn[{}] txnonce[{}]",
                    addr.toString(),
                    bestNonce.get(addr) == null ? "-1" : bestNonce.get(addr).toString(),
                    bn.toString());
        }

        if (bestNonce.get(addr) == null || bestNonce.get(addr).compareTo(bn) < 0) {
            bestNonce.put(addr, bn);
        }
    }

    protected BigInteger getBestNonce(AionAddress addr) {
        if (addr == null || bestNonce.get(addr) == null) {
            return BigInteger.ONE.negate();
        }

        return bestNonce.get(addr);
    }

    protected class TXState {
        private boolean sorted = false;
        private PooledTransaction tx;

        public TXState(PooledTransaction tx) {
            this.tx = tx;
        }

        public PooledTransaction getTx() {
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
            return (bi.compareTo(firstNonce) > -1)
                    && (bi.compareTo(firstNonce.add(BigInteger.valueOf(combo))) < 0);
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

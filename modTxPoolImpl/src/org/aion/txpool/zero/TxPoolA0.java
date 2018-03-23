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
        if (Optional.ofNullable(config.get(PROP_TXN_TIMEOUT)).isPresent()) {
            txn_timeout = Integer.valueOf(config.get(PROP_TXN_TIMEOUT).toString());
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
            long sz_limit = Long.valueOf((String) config.get(PROP_BLOCK_NRG_LIMIT));
            updateBlkNrgLimit(sz_limit);
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
        synchronized (this) {
            this.getAccView(acc).getMap().entrySet().parallelStream().forEach(e -> nl.add(e.getKey()));
        }
        return nl.parallelStream().sorted().collect(Collectors.toList());
    }

    @Override
    public synchronized TX add(TX tx) {
        List<TX> rtn = this.add(Collections.singletonList(tx)) ;
        return  rtn.isEmpty() ? null : rtn.get(0);
    }

    /**
     * this is a test function
     *
     * @return
     */
    public synchronized List<BigInteger> getFeeList() {
        List<BigInteger> nl = Collections.synchronizedList(new ArrayList<>());

        this.getFeeView().entrySet().parallelStream().forEach(e -> nl.add(e.getKey()));

        return nl.parallelStream().sorted(Collections.reverseOrder()).collect(Collectors.toList());
    }

    @Override
    public synchronized List<TX> add(List<TX> txl) {

        List<TX> newPendingTx = new ArrayList<>();
        Map<ByteArrayWrapper, TXState> mainMap = new HashMap<>();
        for (TX tx : txl) {
            // Gen temp mainTxMap
            byte[] hash = tx.getHash();

            if (hash == null) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("the tx hash is empty skip this tx [{}]!", tx);
                }
                continue;
            }

            ByteArrayWrapper bw = ByteArrayWrapper.wrap(hash);
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

            BigInteger txNonce = new BigInteger(1, tx.getNonce());
            BigInteger bn = getBestNonce(tx.getFrom());

            if (bn != null && txNonce.compareTo(bn) < 1) {
                snapshot();
            }

            AbstractMap.SimpleEntry<ByteArrayWrapper, BigInteger> entry = this.getAccView(tx.getFrom()).getMap().get(txNonce);
            if (entry != null) {
                List oldTx = remove(Collections.singletonList(this.getMainMap().get(entry.getKey()).getTx()));

                if (oldTx != null && !oldTx.isEmpty()) {
                    newPendingTx.add((TX) oldTx.get(0));
                }
            } else {
                newPendingTx.add(tx);
            }

            setBestNonce(tx.getFrom(), txNonce);
        }

        this.getMainMap().putAll(mainMap);

        return newPendingTx;
    }

    public List<TX> getOutdatedList() {
        return this.getOutdatedListImpl();
    }

    @Override
    public synchronized List<TX> remove(List<TX> txs) {

        List<TX> removedTxl = Collections.synchronizedList(new ArrayList<>());
        Set<Address> checkedAddress = Collections.synchronizedSet(new HashSet<>());

        for (TX tx : txs) {
            ByteArrayWrapper bw = ByteArrayWrapper.wrap(tx.getHash());
            if (this.getMainMap().remove(bw) == null) {
                continue;
            }

            //noinspection unchecked
            removedTxl.add((TX) tx.clone());

            if (LOG.isTraceEnabled()) {
                LOG.trace("TxPoolA0.remove:[{}] nonce:[{}]", ByteUtils.toHexString(tx.getHash()),
                        new BigInteger(1, tx.getNonce()).toString());
            }

            long timestamp = new BigInteger(1, tx.getTimeStamp()).longValue()/ multiplyM;
            if (this.getTimeView().get(timestamp) == null) {
                continue;
            }

            this.getTimeView().get(timestamp).remove(bw);
            if (this.getTimeView().get(timestamp).isEmpty()) {
                this.getTimeView().remove(timestamp);
            }

            // remove the all transactions belong to the given address in the feeView
            Address address = tx.getFrom();
            Set<BigInteger> fee = Collections.synchronizedSet(new HashSet<>());
            if (!checkedAddress.contains(address)) {

                this.getPoolStateView(tx.getFrom()).parallelStream().forEach(ps -> fee.add(ps.getFee()));

                fee.parallelStream().forEach(bi -> {
                    this.getFeeView().get(bi).entrySet().removeIf(
                            byteArrayWrapperTxDependListEntry -> byteArrayWrapperTxDependListEntry.getValue()
                                    .getAddress().equals(address));

                    if (this.getFeeView().get(bi).isEmpty()) {
                        this.getFeeView().remove(bi);
                    }
                });

                checkedAddress.add(address);
            }

            AccountState as = this.getAccView(tx.getFrom());
            as.getMap().remove(new BigInteger(1, tx.getNonce()));
            as.setDirty();
        }


        this.updateAccPoolState();
        this.updateFeeMap();

        if (LOG.isInfoEnabled()) {
            LOG.info("TxPoolA0.remove TX remove [{}] removed [{}]", txs.size(), removedTxl.size());
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
        AbstractMap.SimpleEntry<ByteArrayWrapper, BigInteger> entry = this.getAccView(from).getMap().get(txNonce);
        return entry == null ? null : this.getMainMap().get(entry.getKey()).getTx();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<TX> snapshot() {
        return snapshot(false);
    }

    @Override
    public synchronized List<TX> snapshotAll() {
        return snapshot(true);
    }

    private synchronized List<TX> snapshot(boolean getAll) {
        List<TX> rtn = new ArrayList<>();

        sortTxn();
        removeTimeoutTxn();

        int cnt_txSz = 0;
        long cnt_nrg = 0;
        Set<ByteArrayWrapper> snapshotSet = new HashSet<>();
        for (Entry<BigInteger, Map<ByteArrayWrapper, TxDependList<ByteArrayWrapper>>> e : this.getFeeView()
                .entrySet()) {

            if (LOG.isTraceEnabled()) {
                LOG.trace("snapshot {} fee[{}]", getAll ? "All" : "", e.getKey().toString());
            }

            Map<ByteArrayWrapper, TxDependList<ByteArrayWrapper>> tpl = e.getValue();
            for (Entry<ByteArrayWrapper, TxDependList<ByteArrayWrapper>> pair : tpl.entrySet()) {
                // Check the small nonce tx must been picked before put the high nonce tx
                ByteArrayWrapper dependTx = pair.getValue().getDependTx();
                if (dependTx == null || snapshotSet.contains(dependTx)) {
                    boolean firstTx = true;
                    for (ByteArrayWrapper bw : pair.getValue().getTxList()) {
                        ITransaction itx = this.getMainMap().get(bw).getTx();

                        if (!getAll) {
                            cnt_txSz += itx.getEncoded().length;
                            cnt_nrg += itx.getNrgConsume();
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("from:[{}] nonce:[{}] txSize: txSize[{}] nrgConsume[{}]",
                                        itx.getFrom().toString(), new BigInteger(1, itx.getNonce()).toString(),
                                        itx.getEncoded().length, itx.getNrgConsume());
                            }
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
                                    LOG.error("TxPoolA0.snapshot {} exception[{}], return [{}] TX", getAll ? "All" : "", ex.toString(), rtn.size());
                                }
                                return rtn;
                            }
                        } else {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Reach blockLimit: txSize[{}], nrgConsume[{}], tx#[{}]", cnt_txSz,
                                        cnt_nrg, rtn.size());
                            }

                            return rtn;
                        }
                    }
                }
            }
        }


        if (LOG.isInfoEnabled()) {
            LOG.info("TxPoolA0.snapshot {} return [{}] TX, poolSize[{}]", getAll ? "All" : "", rtn.size(), getMainMap().size());
        }

        return rtn;
    }

    @Override
    public String getVersion() {
        return "0.1.0";
    }

    @Override
    public synchronized Map.Entry<BigInteger, BigInteger> bestNonceSet(Address address) {

        List<BigInteger> nonceList = new ArrayList<>();
        List<PoolState> psl = this.getPoolStateView(address);
        if (!psl.isEmpty()) {
            boolean firstPS = true;
            int combo = 0;
            BigInteger nextNonce = BigInteger.ZERO;

            for (PoolState ps : psl) {
                if (firstPS) {
                    nextNonce = ps.getFirstNonce();
                    nonceList.add(nextNonce);
                    nextNonce = nextNonce.add(BigInteger.valueOf(ps.getCombo()));
                    combo += ps.getCombo();
                    firstPS = false;
                } else {
                    if (ps.getFirstNonce().equals(nextNonce)) {
                        nextNonce = nextNonce.add(BigInteger.valueOf(ps.getCombo()));
                        combo += ps.getCombo();
                    } else {
                        break;
                    }
                }
            }

            nonceList.add(nonceList.get(0).add(BigInteger.valueOf(combo - 1)));
        }

        return nonceList.size() == 2 ? new AbstractMap.SimpleEntry<>(nonceList.get(0), nonceList.get(1)) : null;
    }

    public BigInteger bestNonce(Address addr) {
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

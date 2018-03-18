/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 * <ether.camp> team through the ethereumJ library.
 * Ether.Camp Inc. (US) team through Ethereum Harmony.
 * John Tromp through the Equihash solver.
 * Samuel Neves through the BLAKE2 implementation.
 * Zcash project team.
 * Bitcoinj team.
 */

package org.aion.zero.impl.sync.handler;

import java.math.BigInteger;
import java.util.*;

import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.p2p.*;
import org.aion.zero.impl.blockchain.NonceMgr;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.BroadcastTx;
import org.aion.zero.impl.valid.TXValidator;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;

/**
 * @author chris
 * handler for new transaction broadcasted from network
 */
public final class BroadcastTxHandler extends Handler {

    private final Logger log;

    private final IPendingStateInternal pendingState;

    private final IP2pMgr p2pMgr;

    private final NonceMgr nonceMgr;

    /*
     * (non-Javadoc)
     *
     * @see org.aion.net.nio.ICallback#getCtrl() change param
     * IPendingStateInternal later
     */
    public BroadcastTxHandler(final Logger _log, final IPendingStateInternal _pendingState, final IP2pMgr _p2pMgr, final
            NonceMgr _nonceMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_TX);
        this.log = _log;
        this.pendingState = _pendingState;
        this.p2pMgr = _p2pMgr;
        this.nonceMgr = _nonceMgr;
    }

    @Override
    public final void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length == 0)
            return;

        List<byte[]> broadCastTx = BroadcastTx.decode(_msgBytes);
        if (broadCastTx.isEmpty()) {
            return;
        }

        Map<Address, Map<BigInteger,ITransaction>> txn = castRawTx(broadCastTx);
        List<ITransaction> cacheTxn = Collections.synchronizedList(new ArrayList<>());

        if (log.isTraceEnabled()) {
            log.trace("receive txn size[{}]", txn.size());
        }

        List<ITransaction> newCache = new ArrayList<>();

        txn.keySet().parallelStream().forEach( addr -> {
            Map<BigInteger,ITransaction> tmpTxMap = txn.get(addr);

            if (log.isTraceEnabled()) {
                for (BigInteger bi : tmpTxMap.keySet()) {
                    log.trace("receive txn bi[{}]", bi.toString());
                }
            }

            BigInteger bn = expectNonce(tmpTxMap, addr);
            if (bn != null) {
                List<ITransaction> seqTx = this.pendingState.getSeqCacheTx(tmpTxMap, addr, bn);

                if (log.isTraceEnabled()) {
                    log.trace("addr[{}] bn[{}] seqTx size[{}]", addr.toString(), bn.toString(), seqTx.size());
                }

                cacheTxn.addAll(seqTx);
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("addToTxCache addr[{}] size[{}]", addr.toString(), tmpTxMap.size());
                }
                newCache.addAll(this.pendingState.addToTxCache(tmpTxMap, addr));
            }
        });

        List<ITransaction> newPendingTx = new ArrayList<>();
        if (!cacheTxn.isEmpty()) {
            newPendingTx = this.pendingState.addPendingTransactions(cacheTxn);

            if (log.isTraceEnabled()) {
                log.trace("cacheTxn size[{}] newPendingTx size[{}]", cacheTxn.size(), newPendingTx.size());
            }

        }

        if (!newCache.isEmpty()) {
            newPendingTx.addAll(newCache);
        }

        // new pending tx, broadcast out to the active nodes
        if (!newPendingTx.isEmpty()) {
            this.log.debug("<broadcast-txs txs={} from-node={}>", newPendingTx.size(), _displayId);

            Map<Integer, INode> activeNodes = this.p2pMgr.getActiveNodes();

            List<ITransaction> finalNewPendingTx = newPendingTx;
            activeNodes.forEach((k, v) -> {
                this.p2pMgr.send(v.getIdHash(), new BroadcastTx(finalNewPendingTx));
            });
        }
    }

    private synchronized BigInteger expectNonce(Map<BigInteger, ITransaction> tmpTx, Address from) {

        BigInteger bestNonce = this.nonceMgr.getNonce(from);

        if (tmpTx.get(bestNonce) == null) {

            Map<BigInteger, ITransaction> cachetx = this.pendingState.getCacheTx(from);
            if (cachetx == null || cachetx.get(bestNonce) == null) {
                return null;
            }
        }

        return bestNonce;
    }

    private Map<Address, Map<BigInteger, ITransaction>> castRawTx(List<byte[]> broadCastTx) {

        Map<Address, Map<BigInteger, ITransaction>> rtn = Collections.synchronizedMap(new HashMap<>());

        for (byte[] raw : broadCastTx) {
            try {
                AionTransaction tx = new AionTransaction(raw);
                if (TXValidator.isValid(tx)) {
                    Address from = tx.getFrom();

                    if (rtn.get(from) == null) {
                        rtn.put(from, new HashMap<>());
                    }

                    rtn.get(from).put(new BigInteger(tx.getNonce()), tx);
                }

            } catch (Exception e){
                log.error("rawdata -> AionTransaction cast exception[{}]", e.toString());
            }
        }
        return rtn;
    }
}

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

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.BroadcastTx;
import org.aion.zero.impl.valid.TXValidator;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author chris
 * handler for new transaction broadcasted from network
 */
public final class BroadcastTxHandler extends Handler {

    private final Logger log;

    private final IPendingStateInternal pendingState;

    private final IP2pMgr p2pMgr;

    private final Timer timer;

    private LinkedBlockingQueue<AionTransaction> txQueue;

    private final boolean buffer;

    public BroadcastTxHandler(final Logger _log, final IPendingStateInternal _pendingState, final IP2pMgr _p2pMgr, final boolean enableBuffer) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_TX);
        this.log = _log;
        this.pendingState = _pendingState;
        this.p2pMgr = _p2pMgr;
        this.txQueue = new LinkedBlockingQueue<>(50_000);
        this.buffer = enableBuffer;

        if (this.buffer) {
            log.info("BufferTask buffer enable!");
            this.timer = new Timer("TimerBC");
            this.timer.scheduleAtFixedRate(new BufferTask(), 5000, 500);
        } else {
            timer = null;
        }
    }

    private class BufferTask extends TimerTask {
        @Override
        public void run() {
            if (!txQueue.isEmpty()) {
                List<AionTransaction> txs = new ArrayList<>();
                try {
                    txQueue.drainTo(txs);
                } catch (Throwable e) {
                    log.error("BufferTask throw{}", e.toString());
                }
                if (!txs.isEmpty()) {
                    if (log.isTraceEnabled()) {
                        log.trace("BufferTask add txs into pendingState:{}", txs.size());
                    }

                    pendingState.addPendingTransactions(txs);
                }
            }
        }
    }

    @Override
    public final void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length == 0)
            return;

        List<byte[]> broadCastTx = BroadcastTx.decode(_msgBytes);

        if (broadCastTx == null) {
            log.error("<BroadcastTxHandler decode-error unable to decode tx-list from {}, len: {]>", _displayId, _msgBytes.length);
            if (log.isTraceEnabled()) {
                log.trace("BroadcastTxHandler dump: {}", ByteUtil.toHexString(_msgBytes));
            }
            return;
        } else if (broadCastTx.isEmpty()) {
            p2pMgr.errCheck(_nodeIdHashcode, _displayId);

            if (log.isTraceEnabled()) {
                log.trace("<BroadcastTxHandler from: {} empty {}>", _displayId);

            }
            return;
        }

        if (this.buffer) {
            try {
                for (AionTransaction tx : castRawTx(broadCastTx)) {
                    if (!txQueue.offer(tx)) {
                        if (log.isTraceEnabled()) {
                            log.trace("<BroadcastTxHandler txQueue full! {}>", _displayId);
                        }
                        break;
                    }
                }

            } catch (Throwable e) {
                log.error("BroadcastTxHandler throw {}", e.toString());
            }
        } else {
            pendingState.addPendingTransactions(castRawTx(broadCastTx));
        }
    }

    private List<AionTransaction> castRawTx(List<byte[]> broadCastTx) {
        List<AionTransaction> rtn = new ArrayList<>();

        for (byte[] raw : broadCastTx) {
            try {
                AionTransaction tx = new AionTransaction(raw);
                if (tx.getHash() != null) {
                    if (!TXValidator.isInCache(ByteArrayWrapper.wrap(tx.getHash()))) {
                        if (TXValidator.isValid(tx)) {
                            rtn.add(tx);
                        }
                    }
                }
            } catch (Exception e) {
                // do nothing, invalid transaction from bad peer
            }
        }

        return rtn;
    }

    @Override
    public void shutDown() {
        log.info("BroadcastTxHandler shutdown!");
        if (timer != null) {
            timer.cancel();
        }
    }
}

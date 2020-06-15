package org.aion.zero.impl.sync.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.aion.base.AionTransaction;
import org.aion.base.TxUtil;
import org.aion.rlp.RLP;
import org.aion.rlp.SharedRLPList;
import org.aion.zero.impl.pendingState.AionPendingStateImpl;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.BroadcastTx;
import org.slf4j.Logger;

/** @author chris handler for new transaction broadcasted from network */
public final class BroadcastTxHandler extends Handler {

    private final Logger log;

    private final AionPendingStateImpl pendingState;

    private final IP2pMgr p2pMgr;

    private LinkedBlockingQueue<AionTransaction> txQueue;

    private ScheduledExecutorService ex;

    private final boolean isSyncOnlyNode;

    public BroadcastTxHandler(
            final Logger _log,
            final AionPendingStateImpl _pendingState,
            final IP2pMgr _p2pMgr,
            final boolean isSyncOnlyNode) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_TX);
        this.log = _log;
        this.pendingState = _pendingState;
        this.p2pMgr = _p2pMgr;
        this.txQueue = new LinkedBlockingQueue<>(50_000);
        this.isSyncOnlyNode = isSyncOnlyNode;

        if (isSyncOnlyNode) return;
        // don't run the buffertask in sync-node mode

        this.ex = Executors.newSingleThreadScheduledExecutor();
        this.ex.scheduleWithFixedDelay(new BufferTask(), 5000, 500, TimeUnit.MILLISECONDS);
    }

    private class BufferTask implements Runnable {
        @Override
        public void run() {
            if (!txQueue.isEmpty()) {
                List<AionTransaction> txs = new ArrayList<>();
                try {
                    txQueue.drainTo(txs);
                    log.trace("BufferTask add txs into pendingState:{}", txs.size());
                    pendingState.addTransactionsFromNetwork(txs);
                } catch (Exception e) {
                    log.error("BufferTask throw ", e);
                }
            }
        }
    }

    @Override
    public final void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (isSyncOnlyNode) return;

        if (_msgBytes == null || _msgBytes.length == 0) return;

        try {
            List<byte[]> broadCastTx = BroadcastTx.decode(_msgBytes);
            if (broadCastTx.isEmpty()) {
                p2pMgr.errCheck(_nodeIdHashcode, _displayId);
                log.debug("<BroadcastTxHandler from: {} empty>", _displayId);
            }

            for (AionTransaction tx : castRawTx(broadCastTx)) {
                if (!txQueue.offer(tx)) {
                    log.debug("<BroadcastTxHandler txQueue full! {}>", _displayId);
                    break;
                }
            }

        } catch (Exception e) {
            p2pMgr.errCheck(_nodeIdHashcode, _displayId);
            log.error("BroadcastTxHandler exception!", e);
        }
    }

    private List<AionTransaction> castRawTx(List<byte[]> broadCastTx) {
        List<AionTransaction> rtn = new ArrayList<>();

        for (byte[] raw : broadCastTx) {
            try {
                rtn.add(TxUtil.decodeUsingRlpSharedList(raw));
            } catch (Exception e) {
                // do nothing, invalid transaction from bad peer
                log.debug("castRawTx exception!" + e);
            }
        }

        log.trace("BroadcastTxHandler.castRawTx Tx#{} validTx#{}", broadCastTx.size(), rtn.size());

        return rtn;
    }

    @Override
    public void shutDown() {
        log.info("BroadcastTxHandler shutting down!");
        if (ex != null) {
            ex.shutdown();
        }
    }
}

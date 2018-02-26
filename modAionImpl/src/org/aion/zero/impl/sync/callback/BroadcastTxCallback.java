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

package org.aion.zero.impl.sync.callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aion.base.timer.StackTimer;
import org.aion.base.type.ITransaction;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.p2p.CTRL;
import org.aion.p2p.ICallback;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.ACT;
import org.aion.zero.impl.sync.msg.BroadcastTx;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;

/**
 * @author jay TODO: move to consensus package later
 */
public final class BroadcastTxCallback implements ICallback {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.BROADCAST_TX;

    private final Logger log;

    private final IPendingStateInternal pendingState;

    private final IP2pMgr p2pMgr;

    /*
     * (non-Javadoc)
     * 
     * @see org.aion.net.nio.ICallback#getCtrl() change param
     * IPendingStateInternal later
     */
    public BroadcastTxCallback(final Logger _log, final IPendingStateInternal _pendingState, final IP2pMgr _p2pMgr) {
        this.log = _log;
        this.pendingState = _pendingState;
        this.p2pMgr = _p2pMgr;
    }

    @Override
    public byte getCtrl() {
        return ctrl;
    }

    @Override
    public byte getAct() {
        return act;
    }

    @Override
    public void receive(final byte[] _nodeId, final byte[] _msgBytes) {

        // the length of nodeId?
        if (_nodeId == null || _msgBytes == null) {
            return;
        }

        List<byte[]> broadCastTx = BroadcastTx.decode(_msgBytes);
        if (broadCastTx.isEmpty()) {
            return;
        }

        List<ITransaction> txn = new ArrayList<>();
        for (byte[] rawdata : broadCastTx) {
            AionTransaction tx = new AionTransaction(rawdata);
            txn.add(tx);
        }

        List<ITransaction> newPendingTx = this.pendingState.addPendingTransactions(txn, new StackTimer());

        // new pending tx, broadcast out to the active nodes
        if (newPendingTx != null && !newPendingTx.isEmpty()) {
            this.log.debug("<broadcast-txs txs={} from-node={}>", newPendingTx.size(),
                    java.util.Arrays.hashCode(_nodeId));

            Map<Integer, INode> activeNodes = this.p2pMgr.getActiveNodes();

            activeNodes.forEach((k, v) -> {
                this.p2pMgr.send(v.getId(), new org.aion.zero.impl.sync.msg.BroadcastTx(newPendingTx));
            });
        }
    }
}

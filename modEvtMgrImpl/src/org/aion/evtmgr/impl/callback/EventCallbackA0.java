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

package org.aion.evtmgr.impl.callback;

import java.util.List;

import org.aion.evtmgr.IEventCallback;
import org.aion.evtmgr.impl.evt.EventTx;

/**
 * @author jay
 *
 */
@SuppressWarnings("hiding")
public class EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>
        implements IEventCallback {

    // Block events
    public void onBlock(final IBlockSummary _bs) {
    }

    public void onBest(IBlock _blk, List<?> _receipts) {
    }

    // Tx events
    public void onPendingTxStateChange() {
    }

    public void onPendingTxReceived(ITransaction _tx) {
    }

    public void onPendingTxUpdate(ITxReceipt _txRcpt, EventTx.STATE _state, IBlock _blk) {
    }

    public void onTxExecuted(ITxExecSummary _summary) {
    }

    // Miner events
    public void onMiningStarted() {
    }

    public void onMiningStopped() {
    }

    public void onBlockMiningStarted(IBlock _blk) {
    }

    public void onBlockMined(IBlock _blk) {
    }

    public void onBlockMiningCanceled(IBlock _blk) {
    }

    // Consensus
    public void onSyncDone() {
    }

    public void onBlockTemplate(IBlock block) {
    }

    public void onSolution(ISolution solution) {
    }

    // VM
    public void onVMTraceCreated(String _txHash, String _trace) {
    }

    public void onTrace(String _trace) {
    }

}

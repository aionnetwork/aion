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
 *
 * Contributors:
 *     Aion foundation.

 ******************************************************************************/
package org.aion.zero.evt;

import java.util.List;

import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxExecSummary;

import org.aion.mcf.evt.IListenerBase;
import org.aion.mcf.types.AbstractBlockSummary;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * POW listener interface.
 *
 * @param <BLK>
 * @param <TX>
 * @param <TXR>
 * @param <BS>
 */
public interface IPowListener<BLK extends IBlock<?, ?>, TX extends ITransaction, TXR extends AbstractTxReceipt<?>, BS extends AbstractBlockSummary<?, ?, ?, ?>>
        extends IListenerBase<BLK, TX, TXR, BS> {
    void onBlock(BS blockSummary);

    void onPeerDisconnect(String host, long port);

    void onPendingTransactionsReceived(List<TX> transactions);

    void onSyncDone();

    void onNoConnections();

    void onVMTraceCreated(String transactionHash, String trace);

    void onTransactionExecuted(ITxExecSummary summary);

}

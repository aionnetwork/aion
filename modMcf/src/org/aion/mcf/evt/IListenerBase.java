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
 ******************************************************************************/
package org.aion.mcf.evt;

import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.mcf.types.AbstractBlockSummary;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * Listener base interface.
 */
public interface IListenerBase<BLK extends IBlock<?, ?>, TX extends ITransaction, TXR extends AbstractTxReceipt<?>, BS extends AbstractBlockSummary<?, ?, ?, ?>> {

    /**
     * PendingState changes on either new pending transaction or new best block receive When a new
     * transaction arrives it is executed on top of the current pending state When a new best block
     * arrives the PendingState is adjusted to the new Repository state and all transactions which
     * remain pending are executed on top of the new PendingState
     */
    void onPendingStateChanged(IPendingStateInternal<BLK, TX> pendingState);

    /**
     * Is called when PendingTransaction arrives, executed or dropped and included to a block
     *
     * @param txReceipt Receipt of the tx execution on the current PendingState
     * @param state Current state of pending tx
     * @param block The block which the current pending state is based on (for PENDING tx state) or
     * the block which tx was included to (for INCLUDED state)
     */
    void onPendingTransactionUpdate(TXR txReceipt, PendingTransactionState state, BLK block);

    void trace(String output);

    enum PendingTransactionState {
        /**
         * Transaction may be dropped due to: - Invalid transaction (invalid nonce, low nrg price,
         * insufficient account funds, invalid signature) - Timeout (when pending transaction is not
         * included to any block for last [transaction.outdated.threshold] blocks This is the final
         * state
         */
        DROPPED(0),
        /**
         * The same as PENDING when transaction is just arrived Next state can be either PENDING or
         * INCLUDED
         */
        NEW_PENDING(1),
        /**
         * State when transaction is not included to any blocks (on the main chain), and was
         * executed on the last best block. The repository state is reflected in the PendingState
         * Next state can be either INCLUDED, DROPPED (due to timeout) or again PENDING when a new
         * block (without this transaction) arrives
         */
        PENDING(2),
        /**
         * State when the transaction is included to a block. This could be the final state, however
         * next state could also be PENDING: when a fork became the main chain but doesn't include
         * this tx INCLUDED: when a fork became the main chain and tx is included into another block
         * from the new main chain DROPPED: If switched to a new (long enough) main chain without
         * this Tx
         */
        INCLUDED(3);

        private final int value;

        PendingTransactionState(int value) {
            this.value = value;
        }

        public boolean isPending() {
            return this == NEW_PENDING || this == PENDING;
        }

        public boolean notFinished() {
            return this.isPending();
        }

        public int getValue() {
            return value;
        }
    }

}

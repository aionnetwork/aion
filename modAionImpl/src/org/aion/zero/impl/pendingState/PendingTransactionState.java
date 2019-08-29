package org.aion.zero.impl.pendingState;

public enum PendingTransactionState {
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
     * State when transaction is not included to any blocks (on the main chain), and was executed on
     * the last best block. The repository state is reflected in the PendingState Next state can be
     * either INCLUDED, DROPPED (due to timeout) or again PENDING when a new block (without this
     * transaction) arrives
     */
    PENDING(2),
    /**
     * State when the transaction is included to a block. This could be the final state, however
     * next state could also be PENDING: when a fork became the main chain but doesn't include this
     * tx INCLUDED: when a fork became the main chain and tx is included into another block from the
     * new main chain DROPPED: If switched to a new (long enough) main chain without this Tx
     */
    INCLUDED(3);

    PendingTransactionState(int value) {
        this.value = value;
    }

    public boolean isPending() {
        return this == NEW_PENDING || this == PENDING;
    }

    public boolean notFinished() {
        return this.isPending();
    }

    private final int value;

    public int getValue() {
        return value;
    }
}

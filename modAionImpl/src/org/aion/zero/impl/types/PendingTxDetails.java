package org.aion.zero.impl.types;

import org.aion.base.AionTxReceipt;

public class PendingTxDetails {
    public final int state;
    public final AionTxReceipt receipt;
    public final long blockNumber;

    public PendingTxDetails(int pendingState, AionTxReceipt receipt, long blockNumber) {
        state = pendingState;
        this.receipt = receipt;
        this.blockNumber = blockNumber;
    }
}

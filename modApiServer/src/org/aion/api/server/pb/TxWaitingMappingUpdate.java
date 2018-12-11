package org.aion.api.server.pb;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.zero.types.AionTxReceipt;

public class TxWaitingMappingUpdate {
    ByteArrayWrapper txHash;
    AionTxReceipt txReceipt;
    int pState;

    public TxWaitingMappingUpdate(ByteArrayWrapper txHashW, int state, AionTxReceipt txReceipt) {
        this.txHash = txHashW;
        this.pState = state;
        this.txReceipt = txReceipt;
    }

    public ByteArrayWrapper getTxHash() {
        return txHash;
    }

    public AionTxReceipt getTxReceipt() {
        return txReceipt;
    }

    public ByteArrayWrapper getTxResult() {
        return ByteArrayWrapper.wrap(txReceipt.getExecutionResult());
    }

    public int getState() {
        return pState;
    }

    public boolean isDummy() {
        return txHash == null && pState == 0 && txReceipt == null;
    }
}

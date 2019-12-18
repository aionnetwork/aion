package org.aion.zero.impl.pendingState;

import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.mcf.blockchain.Block;

public interface IPendingState {
    List<AionTransaction> getPendingTransactions();

    void applyBlockUpdate(Block newBlock, List<AionTxReceipt> receipts);
}

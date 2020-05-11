package org.aion.zero.impl.pendingState;

import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.blockchain.RepoState;

public interface IPendingState extends RepoState {
    List<AionTransaction> getPendingTransactions();

    void applyBlockUpdate(Block newBlock, List<AionTxReceipt> receipts);

    void setNewPendingReceiveForMining(boolean newPendingTxReceived);
}

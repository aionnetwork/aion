package org.aion.mcf.blockchain;

import java.util.List;
import org.aion.base.AionTransaction;

public interface IPendingState {
    List<AionTransaction> getPendingTransactions();
}

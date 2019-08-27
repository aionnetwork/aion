package org.aion.zero.impl.blockchain;

import java.util.List;
import org.aion.base.AionTransaction;

public interface IPendingState {
    List<AionTransaction> getPendingTransactions();
}

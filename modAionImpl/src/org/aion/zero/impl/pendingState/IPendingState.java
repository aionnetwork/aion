package org.aion.zero.impl.pendingState;

import java.util.List;
import org.aion.base.AionTransaction;

public interface IPendingState {
    List<AionTransaction> getPendingTransactions();
}

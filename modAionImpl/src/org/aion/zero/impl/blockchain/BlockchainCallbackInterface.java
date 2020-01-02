package org.aion.zero.impl.blockchain;

import org.aion.base.AionTransaction;
import org.aion.zero.impl.types.PendingTxDetails;

public interface BlockchainCallbackInterface {

    boolean isForApiServer();

    void pendingTxReceived(AionTransaction tx);

    void pendingTxUpdated(PendingTxDetails txDetails);
}

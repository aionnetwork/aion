package org.aion.api.server;

import org.aion.base.AionTransaction;
import org.aion.zero.impl.blockchain.BlockchainCallbackInterface;
import org.aion.zero.impl.types.PendingTxDetails;

public class BlockchainCallbackForApiServer implements BlockchainCallbackInterface {
    private ApiAion apiService;

    public BlockchainCallbackForApiServer(ApiAion apiService) {
        if (apiService == null) {
            throw new NullPointerException();
        }

        this.apiService = apiService;
    }

    @Override
    public boolean isForApiServer() {
        return true;
    }

    @Override
    public void pendingTxReceived(AionTransaction tx) {
        apiService.pendingTxReceived(tx);
    }

    @Override
    public void pendingTxUpdated(PendingTxDetails txDetails) {
        apiService.pendingTxUpdate(txDetails.receipt, txDetails.state);
    }
}

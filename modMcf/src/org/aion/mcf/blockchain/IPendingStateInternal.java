package org.aion.mcf.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;

/**
 * Internal pending state interface.
 */
public interface IPendingStateInternal extends IPendingState {

    void shutDown();

    int getPendingTxSize();

    void updateBest();

    void DumpPool();

    void loadPendingTx();

    void checkAvmFlag();

    BigInteger bestPendingStateNonce(AionAddress addr);

    String getVersion();

    List<TxResponse> addPendingTransactions(List<AionTransaction> transactions);

    TxResponse addPendingTransaction(AionTransaction tx);

    boolean isValid(AionTransaction tx);

    RepositoryCache<?, ?> getRepository();
}

package org.aion.mcf.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;

public interface IPendingState {

    List<TxResponse> addPendingTransactions(List<AionTransaction> transactions);

    TxResponse addPendingTransaction(AionTransaction tx);

    boolean isValid(AionTransaction tx);

    RepositoryCache<?, ?> getRepository();

    List<AionTransaction> getPendingTransactions();

    BigInteger bestPendingStateNonce(AionAddress addr);

    String getVersion();
}

package org.aion.mcf.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.Transaction;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;

public interface IPendingState<TX extends Transaction> {

    List<TxResponse> addPendingTransactions(List<TX> transactions);

    TxResponse addPendingTransaction(TX tx);

    boolean isValid(TX tx);

    RepositoryCache<?, ?> getRepository();

    List<TX> getPendingTransactions();

    BigInteger bestPendingStateNonce(AionAddress addr);

    String getVersion();
}

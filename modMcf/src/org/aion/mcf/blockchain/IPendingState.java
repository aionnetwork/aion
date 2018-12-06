package org.aion.mcf.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.type.ITransaction;

public interface IPendingState<TX extends ITransaction> {

    List<TxResponse> addPendingTransactions(List<TX> transactions);

    TxResponse addPendingTransaction(TX tx);

    IRepositoryCache<?, ?, ?> getRepository();

    List<TX> getPendingTransactions();

    BigInteger bestPendingStateNonce(AionAddress addr);

    String getVersion();
}

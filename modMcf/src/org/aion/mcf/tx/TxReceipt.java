package org.aion.mcf.tx;

import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.base.Transaction;

/** @author jay */
public interface TxReceipt<LOG> {
    void setTransaction(AionTransaction tx);

    void setLogs(List<LOG> logs);

    void setNrgUsed(long nrg);

    void setExecutionResult(byte[] result);

    void setError(String error);
}

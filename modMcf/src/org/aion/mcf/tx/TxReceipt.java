package org.aion.mcf.tx;

import java.util.List;
import org.aion.base.Transaction;

/** @author jay */
public interface TxReceipt<TX extends Transaction, LOG> {
    void setTransaction(TX tx);

    void setLogs(List<LOG> logs);

    void setNrgUsed(long nrg);

    void setExecutionResult(byte[] result);

    void setError(String error);
}

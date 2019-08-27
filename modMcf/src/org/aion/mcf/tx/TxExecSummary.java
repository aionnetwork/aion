package org.aion.mcf.tx;

import java.math.BigInteger;
import java.util.List;
import org.aion.mcf.types.AionTxReceipt;

public interface TxExecSummary {

    Object getBuilder(AionTxReceipt receipt);

    boolean isRejected();

    BigInteger getRefund();

    BigInteger getFee();

    AionTxReceipt getReceipt();

    List getLogs();
}

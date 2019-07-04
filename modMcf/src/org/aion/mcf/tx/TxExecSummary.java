package org.aion.mcf.tx;

import java.math.BigInteger;
import java.util.List;

public interface TxExecSummary {

    Object getBuilder(TxReceipt receipt);

    boolean isRejected();

    BigInteger getRefund();

    BigInteger getFee();

    TxReceipt getReceipt();

    List getLogs();
}

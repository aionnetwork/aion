package org.aion.base.type;

import java.math.BigInteger;
import java.util.List;

public interface ITxExecSummary {

    Object getBuilder(ITxReceipt receipt);

    boolean isRejected();

    BigInteger getRefund();

    BigInteger getFee();

    ITxReceipt getReceipt();

    List getLogs();
}

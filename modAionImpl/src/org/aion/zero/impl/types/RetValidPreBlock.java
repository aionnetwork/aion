package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.types.AionAddress;
import org.aion.base.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;

public class RetValidPreBlock {

    public final List<AionTransaction> txs;
    public final Map<AionAddress, BigInteger> rewards;
    public final List<AionTxReceipt> receipts;
    public final List<AionTxExecSummary> summaries;

    public RetValidPreBlock(
            List<AionTransaction> txs,
            Map<AionAddress, BigInteger> rewards,
            List<AionTxReceipt> receipts,
            List<AionTxExecSummary> summaries) {
        this.txs = txs;
        this.rewards = rewards;
        this.receipts = receipts;
        this.summaries = summaries;
    }
}

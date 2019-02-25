package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;

public class RetValidPreBlock {

    public final List<AionTransaction> txs;
    public final Map<Address, BigInteger> rewards;
    public final List<AionTxReceipt> receipts;
    public final List<AionTxExecSummary> summaries;

    public RetValidPreBlock(
            List<AionTransaction> txs,
            Map<Address, BigInteger> rewards,
            List<AionTxReceipt> receipts,
            List<AionTxExecSummary> summaries) {
        this.txs = txs;
        this.rewards = rewards;
        this.receipts = receipts;
        this.summaries = summaries;
    }
}

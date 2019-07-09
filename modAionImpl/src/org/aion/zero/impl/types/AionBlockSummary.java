package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.aion.mcf.types.AbstractBlockSummary;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.BlockSummary;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;

/**
 * Modified to add transactions
 *
 * @author yao
 */
public class AionBlockSummary
        extends AbstractBlockSummary<IAionBlock, AionTxReceipt, AionTxExecSummary>
        implements BlockSummary {

    public AionBlockSummary(
            IAionBlock block,
            Map<AionAddress, BigInteger> rewards,
            List<AionTxReceipt> receipts,
            List<AionTxExecSummary> summaries) {
        this.block = block;
        this.rewards = rewards;
        this.receipts = receipts;
        this.summaries = summaries;
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("rewards [\n");
        for (Entry e : rewards.entrySet()) {
            s.append("  ")
                    .append(e.getKey().toString())
                    .append(" ")
                    .append(e.getValue().toString())
                    .append("\n");
        }
        s.append("]\n\n");

        // Ignore print receipt cause the summary already has.

        int index = 0;
        s.append("summaries [\n");
        for (AionTxExecSummary sum : summaries) {
            s.append("tx index ").append(index++).append("\n").append(sum.toString()).append("\n");
        }
        s.append("]\n");

        return s.toString();
    }
}

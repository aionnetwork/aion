package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.types.AionAddress;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.slf4j.Logger;

/**
 * Modified to add transactions
 *
 * @author yao
 */
public class AionBlockSummary implements BlockSummary {

    /* TODO: the getters for these parameters could be removed since they are final; requires removing the BlockSummary interface */
    private final Block block;
    private final Map<AionAddress, BigInteger> rewards;
    private final List<AionTxReceipt> receipts;
    private final List<AionTxExecSummary> summaries;
    private BigInteger totalDifficulty = BigInteger.ZERO;

    private final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.toString());

    public AionBlockSummary(
            Block block,
            Map<AionAddress, BigInteger> rewards,
            List<AionTxReceipt> receipts,
            List<AionTxExecSummary> summaries) {
        this.block = block;
        this.rewards = rewards;
        this.receipts = receipts;
        this.summaries = summaries;
    }

    public Block getBlock() {
        return block;
    }

    public List<AionTxReceipt> getReceipts() {
        return receipts;
    }

    public List<AionTxExecSummary> getSummaries() {
        return summaries;
    }

    /**
     * All the mining rewards paid out for this block, including the main block rewards, uncle
     * rewards, and transaction fees.
     */
    public Map<AionAddress, BigInteger> getRewards() {
        return rewards;
    }

    public void setTotalDifficulty(BigInteger totalDifficulty) {
        this.totalDifficulty = totalDifficulty;

        if (LOG.isTraceEnabled()) {
            LOG.trace("The current total difficulty is: {}", totalDifficulty.toString());
        }
    }

    public BigInteger getTotalDifficulty() {
        return totalDifficulty;
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

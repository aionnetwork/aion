package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.base.type.AionAddress;
import org.aion.base.type.IBlockSummary;
import org.aion.mcf.types.AbstractBlockSummary;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;

/**
 * Modified to add transactions
 *
 * @author yao
 */
public class AionBlockSummary
        extends AbstractBlockSummary<IAionBlock, AionTransaction, AionTxReceipt, AionTxExecSummary>
        implements IBlockSummary {

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
}

package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;

/**
 * Rule for checking that energyConsumed does not exceed energyLimit:
 * assert(blockHeader.energyConsumed <= blockHeader.energyLimit)
 */
public class EnergyConsumedRule extends BlockHeaderRule<A0BlockHeader> {

    @Override
    public boolean validate(A0BlockHeader blockHeader, List<RuleError> error) {
        if (blockHeader.getEnergyConsumed() > blockHeader.getEnergyLimit()) {
            addError(
                    formatError(blockHeader.getEnergyConsumed(), blockHeader.getEnergyLimit()),
                    error);
            return false;
        }
        return true;
    }

    private static String formatError(long energyConsumed, long energyLimit) {
        return "energyConsumed (" + energyConsumed + ") > energyLimit(" + energyLimit + ")";
    }
}

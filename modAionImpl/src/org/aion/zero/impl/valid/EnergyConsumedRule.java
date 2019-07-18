package org.aion.zero.impl.valid;

import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;

/**
 * Rule for checking that energyConsumed does not exceed energyLimit:
 * assert(blockHeader.energyConsumed <= blockHeader.energyLimit)
 */
public class EnergyConsumedRule extends BlockHeaderRule {

    public boolean validate(
            BlockHeader blockHeader, List<RuleError> error) {
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

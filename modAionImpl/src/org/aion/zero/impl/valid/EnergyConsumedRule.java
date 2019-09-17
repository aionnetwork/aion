package org.aion.zero.impl.valid;

import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;

/**
 * Rule for checking that energyConsumed does not exceed energyLimit:
 * assert(blockHeader.energyConsumed <= blockHeader.energyLimit)
 */
public class EnergyConsumedRule implements BlockHeaderRule {

    @Override
    public boolean validate(BlockHeader blockHeader, List<RuleError> error) {
        if (blockHeader.getEnergyConsumed() > blockHeader.getEnergyLimit()) {
            BlockHeaderValidatorUtil.addError(
                    formatError(blockHeader.getEnergyConsumed(), blockHeader.getEnergyLimit()),
                    this.getClass(),
                    error);
            return false;
        }
        return true;
    }

    private static String formatError(long energyConsumed, long energyLimit) {
        return "energyConsumed (" + energyConsumed + ") > energyLimit(" + energyLimit + ")";
    }
}

package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.equihash.OptimizedEquiValidator;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.zero.impl.types.MiningBlockHeader;

/** Checks if {@link MiningBlockHeader#getSolution()} is a valid Equihash solution. */
public class EquihashSolutionRule implements BlockHeaderRule {

    private OptimizedEquiValidator validator;

    public EquihashSolutionRule(OptimizedEquiValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean validate(BlockHeader header, List<RuleError> errors) {
        MiningBlockHeader minedHeader = (MiningBlockHeader) header;
        return (validate(minedHeader, errors));
    }

    public boolean validate(MiningBlockHeader header, List<RuleError> errors) {
        if (!validator.isValidSolutionNative(
                header.getSolution(), header.getMineHash(), header.getNonce())) {
            BlockHeaderValidatorUtil.addError("Invalid solution", this.getClass(), errors);
            return false;
        }
        return true;
    }
}

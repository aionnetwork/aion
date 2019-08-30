package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.equihash.OptimizedEquiValidator;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.zero.impl.types.A0BlockHeader;

/** Checks if {@link A0BlockHeader#getSolution()} is a valid Equihash solution. */
public class EquihashSolutionRule extends BlockHeaderRule {

    private OptimizedEquiValidator validator;

    public EquihashSolutionRule(OptimizedEquiValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean validate(BlockHeader header, List<RuleError> errors) {
        A0BlockHeader minedHeader = (A0BlockHeader) header;
        return (validate(minedHeader, errors));
    }

    public boolean validate(A0BlockHeader header, List<RuleError> errors) {
        if (!validator.isValidSolutionNative(
                header.getSolution(), header.getMineHash(), header.getNonce())) {
            addError("Invalid solution", errors);
            return false;
        }
        return true;
    }
}

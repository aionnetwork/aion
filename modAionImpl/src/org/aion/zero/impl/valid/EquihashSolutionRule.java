package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.equihash.OptimizedEquiValidator;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;

/** Checks if {@link A0BlockHeader#solution} is a valid Equihash solution. */
public class EquihashSolutionRule extends BlockHeaderRule<A0BlockHeader> {

    private OptimizedEquiValidator validator;

    public EquihashSolutionRule(OptimizedEquiValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean validate(A0BlockHeader header, List<RuleError> errors) {
        if (!validator.isValidSolutionNative(
                header.getSolution(), header.getMineHash(), header.getNonce())) {
            addError("Invalid solution", errors);
            return false;
        }
        return true;
    }
}

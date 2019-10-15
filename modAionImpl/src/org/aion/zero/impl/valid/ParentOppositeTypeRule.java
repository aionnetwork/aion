package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;

public class ParentOppositeTypeRule implements DependentBlockHeaderRule {

    private boolean validateInner(BlockHeader header, BlockHeader parent, List<RuleError> errors) {
        if (header.getSealType() == parent.getSealType()) {
            BlockHeaderValidatorUtil.addError(
                    formatError(header.getNumber()), this.getClass(), errors);
            return false;
        }
        return true;
    }

    @Override
    public boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors) {
        return validateInner(header, dependency, errors);
    }

    @Override
    public boolean validate(
            BlockHeader header, BlockHeader dependency, List<RuleError> errors, Object arg) {
        return validateInner(header, dependency, errors);
    }

    private static String formatError(long headerNumber) {
        return "blockNumber ("
                + headerNumber
                + ") is of the same type as its parent";
    }
}

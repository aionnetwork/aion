package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.zero.impl.types.BlockHeader;

public class BlockNumberRule implements DependentBlockHeaderRule {

    private boolean validateInner(BlockHeader header, BlockHeader parent, List<RuleError> errors) {
        if (header.getNumber() != (parent.getNumber() + 1)) {
            BlockHeaderValidatorUtil.addError(
                    formatError(header.getNumber(), parent.getNumber()), this.getClass(), errors);
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

    private static String formatError(long headerNumber, long parentNumber) {
        return "blockNumber ("
                + headerNumber
                + ") is not equal to parentBlock number + 1 ("
                + parentNumber
                + ")";
    }
}

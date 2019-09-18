package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;

/** Validates whether the timestamp of the current block is > the timestamp of the parent block */
public class TimeStampRule implements DependentBlockHeaderRule {

    private boolean validateInner(BlockHeader header, BlockHeader dependency, List<RuleError> errors) {
        if (header.getTimestamp() <= dependency.getTimestamp()) {
            BlockHeaderValidatorUtil.addError(
                    "timestamp ("
                            + header.getTimestamp()
                            + ") is not greater than parent timestamp ("
                            + dependency.getTimestamp()
                            + ")",
                    this.getClass(),
                    errors);
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
}

package org.aion.mcf.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;

/** Validates whether the timestamp of the current block is > the timestamp of the parent block */
public class TimeStampRule extends DependentBlockHeaderRule {

    @Override
    public boolean validate(
            BlockHeader header,
            BlockHeader dependency,
            List<RuleError> errors,
            Object... extraValidationArg) {
        if (header.getTimestamp() <= dependency.getTimestamp()) {
            addError(
                    "timestamp ("
                            + header.getTimestamp()
                            + ") is not greater than parent timestamp ("
                            + dependency.getTimestamp()
                            + ")",
                    errors);
            return false;
        }
        return true;
    }
}

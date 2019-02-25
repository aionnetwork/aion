package org.aion.mcf.valid;

import java.util.List;
import org.aion.base.type.IBlockHeader;

/** Validates whether the timestamp of the current block is > the timestamp of the parent block */
public class TimeStampRule<BH extends IBlockHeader> extends DependentBlockHeaderRule<BH> {

    @Override
    public boolean validate(BH header, BH dependency, List<RuleError> errors) {
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

package org.aion.mcf.valid;

import java.util.List;
import org.aion.base.type.IBlockHeader;

public class BlockNumberRule<BH extends IBlockHeader> extends DependentBlockHeaderRule<BH> {

    @Override
    public boolean validate(BH header, BH parent, List<RuleError> errors) {
        if (header.getNumber() != (parent.getNumber() + 1)) {
            addError(formatError(header.getNumber(), parent.getNumber()), errors);
            return false;
        }
        return true;
    }

    private static String formatError(long headerNumber, long parentNumber) {
        return "blockNumber ("
                + headerNumber
                + ") is not equal to parentBlock number + 1 ("
                + parentNumber
                + ")";
    }
}

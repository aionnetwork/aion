package org.aion.mcf.valid;

import java.util.List;
import org.aion.interfaces.block.BlockHeader;

public class BlockNumberRule<BH extends BlockHeader> extends DependentBlockHeaderRule<BH> {

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

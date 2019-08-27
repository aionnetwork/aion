package org.aion.mcf.valid;

import java.util.List;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.RuleError;

public class BlockNumberRule extends DependentBlockHeaderRule {

    @Override
    public boolean validate(BlockHeader header, BlockHeader parent, List<RuleError> errors) {
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

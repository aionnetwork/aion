package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.zero.impl.types.BlockHeader;

/**
 * Rule for checking that blockTimestamp does not exceed the currentTimestamp plus clock drift tolerance:
 */
public class FutureBlockRule implements BlockHeaderRule {

    @Override
    public boolean validate(BlockHeader blockHeader, List<RuleError> error) {
        // We allow one second clock drift, see CLOCK_DRIFT_BUFFER_TIME in BlockConstants
        long validBlockTimeStamp = System.currentTimeMillis() / 1000 + 1;

        if (blockHeader.getTimestamp() > validBlockTimeStamp) {
            BlockHeaderValidatorUtil.addError(
                    formatError(blockHeader.getTimestamp(), validBlockTimeStamp),
                    this.getClass(),
                    error);
            return false;
        }

        return true;
    }

    private static String formatError(long blockTimestamp, long validBlockTimeStamp) {
        return "blockTimestamp (" + blockTimestamp + ") > validBlockTimeStamp(" + validBlockTimeStamp + ")";
    }
}
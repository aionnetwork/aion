package org.aion.zero.impl.valid;

import static java.lang.Long.max;

import java.math.BigInteger;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.valid.DependentBlockHeaderRuleWithArg;
import org.aion.zero.types.StakedBlockHeader;

public class StakingBlockTimeStampRule extends DependentBlockHeaderRuleWithArg {

    private static BigInteger boundry = BigInteger.TWO.pow(256);

    @Override
    public boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors, Object _stake) {

        if (!(header instanceof StakedBlockHeader)) {
            throw new IllegalStateException("Invalid header input");
        }

        if (!(dependency instanceof StakedBlockHeader)) {
            throw new IllegalStateException("Invalid parent header input");
        }

        if (_stake == null) {
            throw  new IllegalStateException("Invalid arg input");
        }

        long parentTimeStamp = dependency.getTimestamp();

        BigInteger stake = (BigInteger) _stake;

        if (stake.compareTo(BigInteger.ONE) < 0) {
            return false;
        }

        long timeStamp = header.getTimestamp();
        BigInteger blockDifficulty = header.getDifficultyBI();

        BigInteger dividend =
                new BigInteger(1, HashUtil.h256(((StakedBlockHeader) header).getSeed()));

        double delta =
                blockDifficulty.doubleValue()
                        * (Math.log(boundry.doubleValue()) - Math.log(dividend.doubleValue()))
                        / stake.doubleValue();

        long offset = max((long) delta, 1);

        if (timeStamp < (parentTimeStamp + offset)) {
            addError(formatError(timeStamp, parentTimeStamp, delta), errors);
            return false;
        }

        return true;
    }

    private static String formatError(long timeStamp, long parantTimeStamp, double delta) {
        return "block timestamp output ("
                + timeStamp
                + ") violates boundary condition ( parentTimeStamp:"
                + parantTimeStamp
                + " delta:"
                + delta
                + ")";
    }
}

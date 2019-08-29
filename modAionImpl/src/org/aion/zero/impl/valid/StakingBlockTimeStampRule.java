package org.aion.zero.impl.valid;

import static java.lang.Long.max;

import java.math.BigInteger;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.zero.impl.types.StakingBlockHeader;
import org.aion.util.math.FixedPoint;
import org.aion.util.math.LogApproximator;

public class StakingBlockTimeStampRule implements DependentBlockHeaderRule {

    private static final BigInteger boundary = BigInteger.ONE.shiftLeft(256);
    private static final FixedPoint logBoundary = LogApproximator.log(boundary);

    @Override
    public boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors) {
        BlockHeaderValidatorUtil.addError("Invalid validate method call", this.getClass(), errors);
        return false;
    }

    @Override
    public boolean validate(
            BlockHeader header, BlockHeader dependency, List<RuleError> errors, Object stake) {
        if (!(header instanceof StakingBlockHeader)) {
            BlockHeaderValidatorUtil.addError("Invalid header type", this.getClass(), errors);
            return false;
        }

        if (!(dependency instanceof StakingBlockHeader)) {
            BlockHeaderValidatorUtil.addError(
                    "Invalid parent header type", this.getClass(), errors);
            return false;
        }

        if (stake == null) {
            BlockHeaderValidatorUtil.addError("The stake can not be null", this.getClass(), errors);
            return false;
        }

        if (!(stake instanceof BigInteger)) {
            BlockHeaderValidatorUtil.addError("Invalid stake object type", this.getClass(), errors);
            return false;
        }

        long parentTimeStamp = dependency.getTimestamp();

        BigInteger stakes = (BigInteger) stake;

        if (stakes.signum() < 1) {
            return false;
        }

        long timeStamp = header.getTimestamp();
        BigInteger blockDifficulty = header.getDifficultyBI();

        BigInteger dividend = new BigInteger(1, HashUtil.h256(((StakingBlockHeader) header).getSeed()));
        
        FixedPoint logDifference = logBoundary.subtract(LogApproximator.log(dividend));

        BigInteger delta = logDifference.multiplyInteger(blockDifficulty).toBigInteger().divide(stakes);

        long offset = max(delta.longValueExact(), 1);

        if (timeStamp < (parentTimeStamp + offset)) {
            BlockHeaderValidatorUtil.addError(
                    formatError(timeStamp, parentTimeStamp, delta), this.getClass(), errors);
            return false;
        }

        return true;
    }

    private static String formatError(long timeStamp, long parantTimeStamp, BigInteger delta) {
        return "block timestamp output ("
                + timeStamp
                + ") violates boundary condition ( parentTimeStamp:"
                + parantTimeStamp
                + " delta:"
                + delta
                + ")";
    }
}

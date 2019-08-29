package org.aion.zero.impl.valid;

import static java.lang.Long.max;

import java.math.BigInteger;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.valid.DependentBlockHeaderRule;
import org.aion.util.math.FixedPoint;
import org.aion.util.math.LogApproximator;
import org.aion.zero.impl.types.StakedBlockHeader;
public class StakingBlockTimeStampRule extends DependentBlockHeaderRule {

    private static final BigInteger boundary = BigInteger.ONE.shiftLeft(256);
    private static final FixedPoint logBoundary = LogApproximator.log(boundary);

    @Override
    public boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors) {
        throw new IllegalStateException("Incorrect validate method call!");
    }

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
        
        FixedPoint logDifference = logBoundary.subtract(LogApproximator.log(dividend));

        BigInteger delta = logDifference.multiplyInteger(blockDifficulty).toBigInteger().divide(stake);

        long offset = max(delta.longValueExact(), 1);

        if (timeStamp < (parentTimeStamp + offset)) {
            addError(formatError(timeStamp, parentTimeStamp, delta), errors);
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

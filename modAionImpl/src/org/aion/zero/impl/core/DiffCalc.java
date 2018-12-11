package org.aion.zero.impl.core;

import static org.aion.base.util.BIUtil.max;
import static org.aion.base.util.BIUtil.min;

import java.math.BigInteger;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.zero.api.BlockConstants;

public class DiffCalc {

    private static BigInteger LOWER_BOUND = BigInteger.valueOf(99);

    private IBlockConstants constants;

    public DiffCalc(IBlockConstants constants) {
        this.constants = constants;
    }

    public BigInteger calcDifficulty(AbstractBlockHeader curBlock, AbstractBlockHeader parent) {
        BigInteger pd = parent.getDifficultyBI();
        BigInteger quotient = pd.divide(this.constants.getDifficultyBoundDivisor());

        BigInteger sign = getCalcDifficultyMultiplier(curBlock, parent);

        BigInteger fromParent = pd.add(quotient.multiply(sign));
        BigInteger difficulty = max(this.constants.getMinimumDifficulty(), fromParent);

        return difficulty;
    }

    private static BigInteger getCalcDifficultyMultiplier(
            AbstractBlockHeader curBlock, AbstractBlockHeader parent) {
        return BigInteger.valueOf(
                curBlock.getTimestamp() >= (parent.getTimestamp() + BlockConstants.DURATION_LIMIT)
                        ? -1
                        : 1);
    }

    public BigInteger calcDifficultyAlt(
            BigInteger currentTimestamp, BigInteger parentTimestamp, BigInteger parentDifficulty) {
        BigInteger diffBase = parentDifficulty.divide(this.constants.getDifficultyBoundDivisor());
        BigInteger diffMultiplier =
                max(
                        BigInteger.ONE.subtract(
                                currentTimestamp.subtract(parentTimestamp).divide(BigInteger.TEN)),
                        LOWER_BOUND);
        return parentDifficulty.add(diffBase.multiply(diffMultiplier));
    }

    public BigInteger calcDifficultyTarget(
            BigInteger currentTimestamp, BigInteger parentTimestamp, BigInteger parentDifficulty) {
        BigInteger diffBase = parentDifficulty.divide(this.constants.getDifficultyBoundDivisor());

        // if smaller than our bound divisor, always round up
        if (diffBase.signum() == 0) {
            diffBase = BigInteger.ONE;
        }

        // use of longValueExact() means this function can throw
        long delta = currentTimestamp.subtract(parentTimestamp).longValueExact();
        final long boundDomain = 10;

        // split into our ranges 0 <= x <= min_block_time, min_block_time < x <
        // max_block_time, max_block_time < x
        BigInteger outputDifficulty = null;
        if (delta <= this.constants.getBlockTimeLowerBound()) {
            outputDifficulty = parentDifficulty.add(diffBase);
        } else if (this.constants.getBlockTimeLowerBound() < delta
                && delta < this.constants.getBlockTimeUpperBound()) {
            outputDifficulty = parentDifficulty;
        } else {

            BigInteger boundQuotient =
                    BigInteger.valueOf(
                            ((delta - this.constants.getBlockTimeUpperBound()) / boundDomain) + 1);
            BigInteger multiplier = min(boundQuotient, LOWER_BOUND);
            outputDifficulty = parentDifficulty.subtract(multiplier.multiply(diffBase));
        }
        // minimum lower bound difficulty requirement
        outputDifficulty = max(this.constants.getMinimumDifficulty(), outputDifficulty);
        return outputDifficulty;
    }
}

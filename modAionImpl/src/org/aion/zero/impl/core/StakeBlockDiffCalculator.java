package org.aion.zero.impl.core;

import static org.aion.util.biginteger.BIUtil.max;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.IBlockConstants;

public class StakeBlockDiffCalculator {
    private static double controlRate = 1.0 + 0.05;

    // choise barrier = 14 because lambda =~ −13.862943611
    private static long barrier = 14;

    private IBlockConstants constants;

    public StakeBlockDiffCalculator(IBlockConstants _constants) {
        constants = _constants;
    }

    public BigInteger calcDifficulty(BlockHeader parent, BlockHeader grandParent) {

        // If not parent pos block, return the initial difficulty
        if (parent == null) {
            return BigInteger.ONE;
        }

        BigInteger pd = parent.getDifficultyBI();

        if (grandParent == null) {
            return pd;
        }

        long timeDelta = parent.getTimestamp() - grandParent.getTimestamp();
        if (timeDelta < 1) {
            throw new IllegalStateException("Invalid parent timestamp & grandparent timestamp diff!");
        }

        BigInteger newDiff;
        if (timeDelta >= barrier) {
            newDiff = BigDecimal.valueOf(pd.doubleValue() / controlRate).toBigInteger();
        } else {
            newDiff = BigDecimal.valueOf(pd.doubleValue() * controlRate).toBigInteger();
        }

        return max(constants.getMinimumDifficulty(), newDiff);
    }
}

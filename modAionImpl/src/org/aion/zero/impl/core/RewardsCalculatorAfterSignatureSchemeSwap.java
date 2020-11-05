package org.aion.zero.impl.core;


import java.math.BigInteger;
import org.aion.zero.impl.api.BlockConstants;

/**
 * The calculator returns the variable block rewards to the block after signatureSchemeSwap fork
 *
 * Adjusted_block_rewards = block_rewards_after_unity * (slope * time-span / block target time + 1 - slope)
 */
public final class RewardsCalculatorAfterSignatureSchemeSwap {

    final static BigInteger[] rewardsAdjustTable;
    // 95% of the mining block time-span will be in 70 secs
    final static int capping = 70;

    static {
        rewardsAdjustTable = new BigInteger[capping];

        // we use rewardsSlope divided by the divisor to represent the floating point
        long rewardsSlope = 4;
        long divisor = 10;

        // baseline rewards
        BigInteger baseline = IRewardsCalculator.fixedRewardsAfterUnity.multiply(BigInteger.valueOf(
            divisor - rewardsSlope)).divide(BigInteger.valueOf(divisor));

        for (int i = 0; i < capping; i++) {
            rewardsAdjustTable[i] =
                IRewardsCalculator.fixedRewardsAfterUnity
                    .multiply(BigInteger.valueOf(i + 1))
                    .multiply(BigInteger.valueOf(rewardsSlope))
                    .divide(BigInteger.valueOf(divisor))
                    .divide(BigInteger.valueOf(BlockConstants.getExpectedBlockTime()))
                    .add(baseline);
        }
    }

    public static BigInteger calculateReward(long timeSpan) {
        if (timeSpan <= 0) {
            return BigInteger.ZERO;
        }

        return rewardsAdjustTable[timeSpan > capping ? capping-1: (int) (timeSpan - 1)];
    }
}

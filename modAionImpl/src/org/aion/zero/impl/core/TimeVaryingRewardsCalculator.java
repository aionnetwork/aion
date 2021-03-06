package org.aion.zero.impl.core;


import java.math.BigInteger;
import org.aion.zero.impl.api.BlockConstants;

/**
 * The calculator returns the variable block rewards to the block after signatureSchemeSwap fork
 *
 * Adjusted_block_rewards = block_rewards_after_unity * (slope * time-span / block target time + 1 - slope)
 */
public final class TimeVaryingRewardsCalculator {

    final static BigInteger[] rewardsAdjustTable;
    // 99% of the mining block time-span will fall into 125 secs under the time-span attack under 0.4 hashing power
    final static int capping = 125;

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
            throw new IllegalStateException("The block timespan should be at least 1 sec.");
        }

        return rewardsAdjustTable[timeSpan > capping ? capping-1: (int) (timeSpan - 1)];
    }
}

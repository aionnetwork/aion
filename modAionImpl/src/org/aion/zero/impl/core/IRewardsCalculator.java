package org.aion.zero.impl.core;

import java.math.BigInteger;

/**
 * Calculates the rewards given for sealing a particular block, depending on the implementation we
 * may be able to swap different difficulty implementations.
 *
 * @author yao
 */
@FunctionalInterface
public interface IRewardsCalculator {
    BigInteger fixedRewardsAfterUnity = BigInteger.valueOf(4_500_000_000_000_000_000L);

    BigInteger calculateReward(long blkNumOrTimeSpan);
}

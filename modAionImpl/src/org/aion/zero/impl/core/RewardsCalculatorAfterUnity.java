package org.aion.zero.impl.core;

import java.math.BigInteger;

/**
 * The unity protocol block rewards is a fixed rewards equal to 4.5 AION.
 */
public class RewardsCalculatorAfterUnity {

    private static final BigInteger fixedBlockRewards = BigInteger.valueOf(4_500_000_000_000_000_000L);

    public static BigInteger calculateReward(long number) {
        return fixedBlockRewards;
    }
}

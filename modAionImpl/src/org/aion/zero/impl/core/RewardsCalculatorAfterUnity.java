package org.aion.zero.impl.core;

import static org.aion.zero.impl.core.IRewardsCalculator.fixedRewardsAfterUnity;

import java.math.BigInteger;

/**
 * The unity protocol block rewards is a fixed rewards equal to 4.5 AION.
 */
public class RewardsCalculatorAfterUnity {
    public static BigInteger calculateReward(long number) {
        return fixedRewardsAfterUnity;
    }
}

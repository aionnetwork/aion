package org.aion.zero.impl.core;

import org.junit.Assert;
import org.junit.Test;

public class RewardsCalculatorAfterSignatureSchemeSwapTest {

    @Test (expected = IllegalStateException.class)
    public void RewardsCalculatorTimespanMinusOneTest() {
        RewardsCalculatorAfterSignatureSchemeSwap.calculateReward(-1);
    }

    @Test (expected = IllegalStateException.class)
    public void RewardsCalculatorTimespanZeroTest() {
        RewardsCalculatorAfterSignatureSchemeSwap.calculateReward(0);
    }

    @Test
    public void RewardsCalculatorTimespanOneTest() {
        Assert.assertEquals(RewardsCalculatorAfterSignatureSchemeSwap.rewardsAdjustTable[0],
            RewardsCalculatorAfterSignatureSchemeSwap.calculateReward(1));
    }

    @Test
    public void RewardsCalculatorTimespanTenTest() {
        Assert.assertEquals(IRewardsCalculator.fixedRewardsAfterUnity,
            RewardsCalculatorAfterSignatureSchemeSwap.calculateReward(10));
    }

    @Test
    public void RewardsCalculatorTimespanCapTest() {
        Assert.assertEquals(RewardsCalculatorAfterSignatureSchemeSwap.rewardsAdjustTable[RewardsCalculatorAfterSignatureSchemeSwap.capping - 1],
            RewardsCalculatorAfterSignatureSchemeSwap.calculateReward(RewardsCalculatorAfterSignatureSchemeSwap.capping));
    }

    @Test
    public void RewardsCalculatorTimespanCapPlus1Test() {
        Assert.assertEquals(RewardsCalculatorAfterSignatureSchemeSwap.rewardsAdjustTable[RewardsCalculatorAfterSignatureSchemeSwap.capping - 1],
            RewardsCalculatorAfterSignatureSchemeSwap.calculateReward(RewardsCalculatorAfterSignatureSchemeSwap.capping + 1));
    }
}

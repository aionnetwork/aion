package org.aion.zero.impl.core;

import org.junit.Assert;
import org.junit.Test;

public class TimeVaryingRewardsCalculatorTest {

    @Test (expected = IllegalStateException.class)
    public void RewardsCalculatorTimespanMinusOneTest() {
        TimeVaryingRewardsCalculator.calculateReward(-1);
    }

    @Test (expected = IllegalStateException.class)
    public void RewardsCalculatorTimespanZeroTest() {
        TimeVaryingRewardsCalculator.calculateReward(0);
    }

    @Test
    public void RewardsCalculatorTimespanOneTest() {
        Assert.assertEquals(TimeVaryingRewardsCalculator.rewardsAdjustTable[0],
            TimeVaryingRewardsCalculator.calculateReward(1));
    }

    @Test
    public void RewardsCalculatorTimespanTenTest() {
        Assert.assertEquals(IRewardsCalculator.fixedRewardsAfterUnity,
            TimeVaryingRewardsCalculator.calculateReward(10));
    }

    @Test
    public void RewardsCalculatorTimespanCapTest() {
        Assert.assertEquals(
            TimeVaryingRewardsCalculator.rewardsAdjustTable[TimeVaryingRewardsCalculator.capping - 1],
            TimeVaryingRewardsCalculator.calculateReward(TimeVaryingRewardsCalculator.capping));
    }

    @Test
    public void RewardsCalculatorTimespanCapPlus1Test() {
        Assert.assertEquals(
            TimeVaryingRewardsCalculator.rewardsAdjustTable[TimeVaryingRewardsCalculator.capping - 1],
            TimeVaryingRewardsCalculator.calculateReward(TimeVaryingRewardsCalculator.capping + 1));
    }
}

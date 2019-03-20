package org.aion.zero.api;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class MonetaryCalculatorTest {

    private BigInteger totalSupplyAfterMainnetAVMHF;
    private BigInteger initialSupply = new BigInteger("465934586660000000000000000");
    private long blockAnnum = 3110400;

    @Before
    public void setup() {
        totalSupplyAfterMainnetAVMHF = calculateAVMHFSupply(blockAnnum);
    }

    @After
    public void teardown() {
        MonetaryCalculator.destory();
    }

    @Test
    public void testCalculateCompound() {

        MonetaryCalculator mc = MonetaryCalculator.init(BigInteger.valueOf(100000000), 100, 1, 0);
        assertEquals(BigInteger.ZERO, mc.testCalculateCompound(0));
        assertEquals(BigInteger.valueOf(1000000), mc.testCalculateCompound(1));
        assertEquals(BigInteger.valueOf(1010000), mc.testCalculateCompound(2));
        assertEquals(BigInteger.valueOf(1020100), mc.testCalculateCompound(3));
        assertEquals(BigInteger.valueOf(1030301), mc.testCalculateCompound(4));
    }

    @Test
    public void testCalculateCompoundwithBlockAnnumChange() {
        MonetaryCalculator mc = MonetaryCalculator.init(BigInteger.valueOf(100000000), 100, 100, 0);
        assertEquals(BigInteger.ZERO, mc.testCalculateCompound(0));
        assertEquals(BigInteger.valueOf(10000), mc.testCalculateCompound(1));
        assertEquals(BigInteger.valueOf(10100), mc.testCalculateCompound(2));
        assertEquals(BigInteger.valueOf(10201), mc.testCalculateCompound(3));
    }

    @Test
    public void testCalculateCompoundwithBlockBasePointChange() {
        MonetaryCalculator mc = MonetaryCalculator.init(BigInteger.valueOf(100000000), 200, 1, 0);
        assertEquals(BigInteger.ZERO, mc.testCalculateCompound(0));
        assertEquals(BigInteger.valueOf(2000000), mc.testCalculateCompound(1));
        assertEquals(BigInteger.valueOf(2040000), mc.testCalculateCompound(2));
        assertEquals(BigInteger.valueOf(2080800), mc.testCalculateCompound(3));
        assertEquals(BigInteger.valueOf(2122416), mc.testCalculateCompound(4));
    }

    @Test
    public void testCalculateCompoundMainnet() {

        MonetaryCalculator mc =
                MonetaryCalculator.init(totalSupplyAfterMainnetAVMHF, 100, blockAnnum, 0);
        assertEquals(BigInteger.ZERO, mc.testCalculateCompound(0));
        assertEquals(new BigInteger("1512657096179086739"), mc.testCalculateCompound(1));
        assertEquals(new BigInteger("1527783667140877607"), mc.testCalculateCompound(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetMainetRewardException() {
        MonetaryCalculator.init(totalSupplyAfterMainnetAVMHF, 100, blockAnnum, blockAnnum);
        MonetaryCalculator.getCurrentReward(blockAnnum);
    }

    @Test
    public void testGetMainetReward() {
        MonetaryCalculator.init(totalSupplyAfterMainnetAVMHF, 100, blockAnnum, blockAnnum);

        assertEquals(
                new BigInteger("1512657096179086739"),
                MonetaryCalculator.getCurrentReward(blockAnnum + 1));
        assertEquals(
                new BigInteger("1512657096179086739"),
                MonetaryCalculator.getCurrentReward(blockAnnum + blockAnnum));
        assertEquals(
                new BigInteger("1527783667140877607"),
                MonetaryCalculator.getCurrentReward(blockAnnum + blockAnnum + 1));
        assertEquals(
                new BigInteger("1527783667140877607"),
                MonetaryCalculator.getCurrentReward(blockAnnum * 3));
    }

    @Test
    public void testGetAvmTestnetReward() {
        MonetaryCalculator.init(initialSupply, 100, blockAnnum, 0);
        assertEquals(new BigInteger("1497989283243312757"), MonetaryCalculator.getCurrentReward(1));
        assertEquals(
                new BigInteger("1497989283243312757"),
                MonetaryCalculator.getCurrentReward(blockAnnum));
        assertEquals(
                new BigInteger("1512969176075745884"),
                MonetaryCalculator.getCurrentReward(blockAnnum + 1));
        assertEquals(
                new BigInteger("1512969176075745884"),
                MonetaryCalculator.getCurrentReward(blockAnnum + blockAnnum));
    }

    private BigInteger calculateAVMHFSupply(long blockAnnum) {
        BlockConstants bc = new BlockConstants();
        BigInteger ts = BigInteger.ZERO;

        // pre-calculate the desired increment
        long delta = bc.getRampUpUpperBound() - bc.getRampUpLowerBound();
        assert (delta > 0);

        BigInteger m =
                bc.getRampUpEndValue()
                        .subtract(bc.getRampUpStartValue())
                        .divide(BigInteger.valueOf(delta));

        // Calculate total supply after one year.
        for (long i = 1; i <= blockAnnum; i++) {
            if (i <= bc.getRampUpUpperBound()) {
                ts = ts.add(BigInteger.valueOf(i).multiply(m).add(bc.getRampUpStartValue()));
            } else {
                ts = ts.add(bc.getBlockReward());
            }
        }

        return ts.add(initialSupply);
    }
}

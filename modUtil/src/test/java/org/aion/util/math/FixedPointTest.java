package org.aion.util.math;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

public class FixedPointTest {

    private static FixedPoint f1 = FixedPoint.fromString("1.6875");
    private static FixedPoint f2 = FixedPoint.fromString("3.078125");
    private static FixedPoint f3 = FixedPoint.fromString("0.84375");

    @Test
    public void testSimpleAddition() {
        BigDecimal expectedSum = new BigDecimal("4.765625");
        Assert.assertEquals(expectedSum, f1.add(f2).toBigDecimal());
    }


    @Test
    public void testSimpleSubtraction() {
        BigDecimal expectedDifference = new BigDecimal("1.390625");
        Assert.assertEquals(expectedDifference, f2.subtract(f1).toBigDecimal());
    }

    @Test
    public void testSimpleMultiplication() {
        BigDecimal expectedProduct = new BigDecimal("3.375");
        Assert.assertEquals(expectedProduct, f1.multiplyInteger(BigInteger.TWO).toBigDecimal());
    }

    @Test
    public void testShiftByOne() {
        BigDecimal expectedResult = new BigDecimal("0.421875");
        Assert.assertEquals(expectedResult, f3.divideByPowerOfTwo(1).toBigDecimal());
    }

    @Test
    public void testShiftByTwo() {
        BigDecimal expectedResult = new BigDecimal("0.2109375");
        Assert.assertEquals(expectedResult, f3.divideByPowerOfTwo(2).toBigDecimal());
    }

    @Test
    public void testShiftByTen() {
        BigDecimal expectedResult = new BigDecimal("0.000823974609375");
        Assert.assertEquals(expectedResult, f3.divideByPowerOfTwo(10).toBigDecimal());
    }
    
    @Test
    public void testComparator() {
        Assert.assertEquals(f1.compareTo(f2), -1);
        Assert.assertEquals(f2.compareTo(f1), 1);
        Assert.assertEquals(f1.compareTo(f1), 0);
    }

    @Test
    public void testToString() {
        FixedPoint fixedPoint = FixedPoint.fromString("5.4375");
        Assert.assertEquals("5.4375", fixedPoint.toString());
    }

    @Test
    public void testEquals() {
        FixedPoint f1Copy = FixedPoint.fromString("1.6875");
        Assert.assertNotEquals(f1, f2);
        Assert.assertEquals(f1, f1Copy);
        Assert.assertEquals(f1, f1);
    }
}
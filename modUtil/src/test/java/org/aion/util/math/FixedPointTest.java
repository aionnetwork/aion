// This test needs to be added, but it is very sensitive to the PRECISION
// It's commented out until we 

//package org.aion.util.math;
//
//import org.junit.Assert;
//import org.junit.Test;
//
//import java.math.BigDecimal;
//import java.math.BigInteger;
//
//public class FixedPointTest {
//
//    private static FixedPoint f1 = new FixedPoint(new BigDecimal("1.6875"));
//    private static FixedPoint f2 = new FixedPoint(new BigDecimal("3.55"));
//    private static FixedPoint f3 = new FixedPoint(new BigDecimal("0.84375"));
//
//    @Test
//    public void testSimpleAddition() {
//        BigDecimal expectedSum = new BigDecimal("5.2375");
//        Assert.assertEquals(expectedSum, f1.add(f2).toBigDecimal());
//    }
//
//
//    @Test
//    public void testSimpleSubstraction() {
//        BigDecimal expectedDifference = new BigDecimal("1.8625");
//        Assert.assertEquals(expectedDifference, f2.subtract(f1).toBigDecimal());
//    }
//
//    @Test
//    public void testSimpleMultiplication() {
//        BigDecimal expectedProduct = new BigDecimal("3.375");
//        Assert.assertEquals(expectedProduct, f1.multiplyInteger(BigInteger.TWO).toBigDecimal());
//    }
//
//    @Test
//    public void testShiftByOne() {
//        BigDecimal expectedResult = new BigDecimal("0.421875");
//        Assert.assertEquals(expectedResult, f3.divideByPowerOfTwo(1).toBigDecimal());
//    }
//
//    @Test
//    public void testShiftByTwo() {
//        BigDecimal expectedResult = new BigDecimal("0.2109375");
//        Assert.assertEquals(expectedResult, f3.divideByPowerOfTwo(2).toBigDecimal());
//    }
//
//    @Test
//    public void testShiftByTen() {
//        BigDecimal expectedResult = new BigDecimal("0.000823974609375");
//        Assert.assertEquals(expectedResult, f3.divideByPowerOfTwo(10).toBigDecimal());
//    }
//
//    @Test
//    public void testShiftByTwentyAndLosePrecision() {
//        BigDecimal expectedResult = new BigDecimal("0.00000080466270446777");
//        Assert.assertEquals(expectedResult, f3.divideByPowerOfTwo(20).toBigDecimal());
//    }
//
//    @Test
//    public void testDivideByTen() {
//        BigDecimal expectedResult = new BigDecimal("0.084375");
//        Assert.assertEquals(expectedResult, f3.divideInteger(BigInteger.TEN).toBigDecimal());
//    }
//
//    @Test
//    public void testDivideAndLosePrecision() {
//        BigDecimal expectedResult = new BigDecimal("0.00000000000000084375");
//        FixedPoint result = f3.divideInteger(new BigInteger("1000000000000000"));
//        Assert.assertEquals(expectedResult, result.toBigDecimal());
//        
//        // Dividing further causes a loss of precision
//        expectedResult = new BigDecimal("0.00000000000000042187");
//        result = result.divideInteger(BigInteger.TWO);
//        Assert.assertEquals(expectedResult, result.toBigDecimal());
//        
//        expectedResult = new BigDecimal("0.00000000000000004218");
//        result = result.divideInteger(BigInteger.TEN);
//        Assert.assertEquals(expectedResult, result.toBigDecimal());
//    }
//
//    @Test
//    public void testHighPrecisionAddition() {
//        FixedPoint f1 = new FixedPoint(new BigDecimal("1.12345678900123456789"));
//        FixedPoint f2 = new FixedPoint(new BigDecimal("3.98765432187123456789"));
//        BigDecimal expectedSum = new BigDecimal("5.11111111087246913578");
//        Assert.assertEquals(expectedSum, f1.add(f2).toBigDecimal());
//    }
//
//    @Test
//    public void testComparator() {
//        Assert.assertEquals(f1.compareTo(f2), -1);
//        Assert.assertEquals(f2.compareTo(f1), 1);
//        Assert.assertEquals(f1.compareTo(f1), 0);
//    }
//    
//    @Test
//    public void testToString() {
//        FixedPoint fixedPoint = new FixedPoint(new BigDecimal("5.4375"));
//        Assert.assertEquals("5.4375", fixedPoint.toString());
//    }
//
//    @Test
//    public void testEquals() {
//        FixedPoint f1Copy = new FixedPoint(new BigDecimal("1.6875"));
//        Assert.assertNotEquals(f1, f2);
//        Assert.assertEquals(f1, f1Copy);
//        Assert.assertEquals(f1, f1);
//    }
//}
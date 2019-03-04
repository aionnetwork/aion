package org.aion.util.bytes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import org.aion.util.biginteger.BIUtil;
import org.junit.Assert;
import org.junit.Test;

public class BIUtilTest {

    private final BigInteger[][] bigInt = {
        {new BigInteger("-00000000000000000000"), new BigInteger("00000000000000000000")},
        {new BigInteger("-00000000000000000001"), new BigInteger("00000000000000000001")},
        {new BigInteger("-10000000000000000000"), new BigInteger("10000000000000000000")},
        {new BigInteger("-20000000000000000000"), new BigInteger("20000000000000000000")},
        {new BigInteger("-30000000000000000000"), new BigInteger("30000000000000000000")},
        {new BigInteger("-99999999999999999999"), new BigInteger("99999999999999999999")},
    };

    @Test
    public void testIntegrity() {

        // isZero && isPositive
        assertTrue(BIUtil.isZero(bigInt[0][0]));
        assertTrue(BIUtil.isZero(bigInt[0][1]));

        assertFalse(BIUtil.isPositive(bigInt[0][0]));
        assertFalse(BIUtil.isPositive(bigInt[0][1]));

        // isEqual && isNotEqual
        assertTrue(BIUtil.isEqual(bigInt[0][0], bigInt[0][1]));
        assertFalse(BIUtil.isNotEqual(bigInt[0][0], bigInt[0][1]));

        // isLessThan && isMoreThan
        assertFalse(BIUtil.isLessThan(bigInt[0][0], bigInt[0][1]));
        assertFalse(BIUtil.isMoreThan(bigInt[0][0], bigInt[0][1]));

        for (int a = 1; a < bigInt.length; a++) {

            assertFalse(BIUtil.isPositive(bigInt[a][0]));
            assertTrue(BIUtil.isPositive(bigInt[a][1]));

            assertFalse(BIUtil.isEqual(bigInt[a][0], bigInt[a][1]));
            assertTrue(BIUtil.isNotEqual(bigInt[a][0], bigInt[a][1]));

            assertTrue(BIUtil.isLessThan(bigInt[a][0], bigInt[a][1]));
            assertFalse(BIUtil.isMoreThan(bigInt[a][0], bigInt[a][1]));
        }

        // isCovers && isNotCovers
        for (int a = 1; a < bigInt.length; a++) {
            assertTrue(BIUtil.isNotCovers(bigInt[a - 1][1], bigInt[a][1]));
        }
        for (int a = 1; a < bigInt.length; a++) {
            assertTrue(BIUtil.isNotCovers(bigInt[a][0], bigInt[a - 1][0]));
        }
        for (int a = bigInt.length - 1; a > 0; a--) {
            assertTrue(BIUtil.isCovers(bigInt[a][1], bigInt[a - 1][1]));
        }

        for (int a = bigInt.length - 1; a > 0; a--) {
            assertTrue(BIUtil.isCovers(bigInt[a - 1][0], bigInt[a][0]));
        }

        // isIn20PercentRange
    }

    @Test
    public void testType() {

        // toBI(byte), toBI(long)
        final long[] testLong = {
            0L, 1L, 1000000000000000000L, 9223372036854775807L,
        };

        final byte[][] testByte = {
            ByteUtil.longToBytes(testLong[0]),
            ByteUtil.longToBytes(testLong[1]),
            ByteUtil.longToBytes(testLong[2]),
            ByteUtil.longToBytes(testLong[3]),
        };

        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(BIUtil.toBI(testLong[i]), BIUtil.toBI(testByte[i]));
        }

        // exitLong
    }

    @Test
    public void testSum() {

        // sum
        for (BigInteger[] bigIntegers : bigInt) {
            Assert.assertEquals(new BigInteger("0"), BIUtil.sum(bigIntegers[0], bigIntegers[1]));
        }

        for (int b = 0; b < 2; b++) {
            for (BigInteger[] bigIntegers : bigInt) {
                Assert.assertEquals(bigIntegers[b], BIUtil.sum(bigInt[0][b], bigIntegers[b]));
            }
        }

        Assert.assertEquals(
                new BigInteger("-160000000000000000000"),
                BIUtil.sum(
                        bigInt[0][0],
                        BIUtil.sum(
                                bigInt[1][0],
                                BIUtil.sum(
                                        bigInt[2][0],
                                        BIUtil.sum(bigInt[3][0], BIUtil.sum(bigInt[4][0], bigInt[5][0]))))));

        Assert.assertEquals(
                new BigInteger("160000000000000000000"),
                BIUtil.sum(
                        bigInt[0][1],
                        BIUtil.sum(
                                bigInt[1][1],
                                BIUtil.sum(
                                        bigInt[2][1],
                                        BIUtil.sum(bigInt[3][1], BIUtil.sum(bigInt[4][1], bigInt[5][1]))))));
    }

    @Test
    public void testMinMax() {
        // min && max
        for (BigInteger[] bigIntegers : bigInt) {
            Assert.assertEquals(bigIntegers[0], BIUtil.min(bigIntegers[0], bigIntegers[1]));
            Assert.assertEquals(bigIntegers[1], BIUtil.max(bigIntegers[0], bigIntegers[1]));
        }

        Assert.assertEquals(
                bigInt[bigInt.length - 1][0],
                BIUtil.min(
                        bigInt[0][0],
                        BIUtil.min(
                                bigInt[1][0],
                                BIUtil.min(
                                        bigInt[2][0],
                                        BIUtil.min(bigInt[3][0], BIUtil.min(bigInt[4][0], bigInt[5][0]))))));

        Assert.assertEquals(
                bigInt[bigInt.length - 1][1],
                BIUtil.max(
                        bigInt[0][1],
                        BIUtil.max(
                                bigInt[1][1],
                                BIUtil.max(
                                        bigInt[2][1],
                                        BIUtil.max(bigInt[3][1], BIUtil.max(bigInt[4][1], bigInt[5][1]))))));
    }
}

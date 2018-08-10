package org.aion.base.util;

import org.junit.Test;

import java.math.BigInteger;

import static org.aion.base.util.BIUtil.*;
import static org.junit.Assert.*;

public class BIUtilTest {

    private final BigInteger[][] bigInt ={
            { new BigInteger("-00000000000000000000"), new BigInteger("00000000000000000000")},
            { new BigInteger("-00000000000000000001"), new BigInteger("00000000000000000001")},
            { new BigInteger("-10000000000000000000"), new BigInteger("10000000000000000000")},
            { new BigInteger("-20000000000000000000"), new BigInteger("20000000000000000000")},
            { new BigInteger("-30000000000000000000"), new BigInteger("30000000000000000000")},
            { new BigInteger("-99999999999999999999"), new BigInteger("99999999999999999999")},
    };

    @Test
    public void testIntegrity() {

        // isZero && isPositive
        assertTrue(isZero(bigInt[0][0]));
        assertTrue(isZero(bigInt[0][1]));

        assertFalse(isPositive(bigInt[0][0]));
        assertFalse(isPositive(bigInt[0][1]));

        // isEqual && isNotEqual
        assertTrue(isEqual(bigInt[0][0], bigInt[0][1]));
        assertFalse(isNotEqual(bigInt[0][0], bigInt[0][1]));

        // isLessThan && isMoreThan
        assertFalse(isLessThan(bigInt[0][0], bigInt[0][1]));
        assertFalse(isMoreThan(bigInt[0][0], bigInt[0][1]));

        for (int a = 1; a < bigInt.length; a++) {

            assertFalse(isPositive(bigInt[a][0]));
            assertTrue(isPositive(bigInt[a][1]));

            assertFalse(isEqual(bigInt[a][0], bigInt[a][1]));
            assertTrue(isNotEqual(bigInt[a][0], bigInt[a][1]));

            assertTrue(isLessThan(bigInt[a][0], bigInt[a][1]));
            assertFalse(isMoreThan(bigInt[a][0], bigInt[a][1]));
        }

        // isCovers && isNotCovers
        for (int a = 1; a < bigInt.length; a++) {
            assertTrue(isNotCovers(bigInt[a - 1][1], bigInt[a][1]));
        }
        for (int a = 1; a < bigInt.length; a++) {
            assertTrue(isNotCovers(bigInt[a][0], bigInt[a - 1][0]));
        }
        for (int a = bigInt.length - 1; a > 0; a--) {
            assertTrue(isCovers(bigInt[a][1], bigInt[a - 1][1]));
        }

        for (int a = bigInt.length - 1; a > 0; a--) {
            assertTrue(isCovers(bigInt[a - 1][0], bigInt[a][0]));
        }

        // isIn20PercentRange
    }

    @Test
    public void testType() {

        // toBI(byte), toBI(long)
        final long[] testLong = {
                0L,
                1L,
                1000000000000000000L,
                9223372036854775807L,
        };

        final byte[][] testByte = {
                ByteUtil.longToBytes(testLong[0]),
                ByteUtil.longToBytes(testLong[1]),
                ByteUtil.longToBytes(testLong[2]),
                ByteUtil.longToBytes(testLong[3]),
        };

        for(int i = 0; i < 4; i++) {
            assertEquals( toBI(testLong[i]), toBI(testByte[i]));
        }

        // exitLong
    }

    @Test
    public void testSum() {

        // sum
        for (int a = 0; a < bigInt.length; a++) {
            assertEquals(new BigInteger("0"), sum(bigInt[a][0], bigInt[a][1]));
        }

        for (int b = 0; b < 2; b++) {
            for (int a = 0; a < bigInt.length; a++) {
                assertEquals(bigInt[a][b], sum(bigInt[0][b], bigInt[a][b]));
            }
        }

        assertEquals(new BigInteger("-160000000000000000000"),
                sum(bigInt[0][0],
                        sum(bigInt[1][0],
                                sum(bigInt[2][0],
                                        sum(bigInt[3][0],
                                                sum(bigInt[4][0], bigInt[5][0]))))));

        assertEquals(new BigInteger("160000000000000000000"),
                sum(bigInt[0][1],
                        sum(bigInt[1][1],
                                sum(bigInt[2][1],
                                        sum(bigInt[3][1],
                                                sum(bigInt[4][1], bigInt[5][1]))))));
    }

    @Test
    public void testMinMax() {
        // min && max
        for(int c = 0; c < bigInt.length; c++) {
            assertEquals(bigInt[c][0], min(bigInt[c][0], bigInt[c][1]));
            assertEquals(bigInt[c][1], max(bigInt[c][0], bigInt[c][1]));
        }

        assertEquals(bigInt[bigInt.length-1][0],
                min(bigInt[0][0],
                        min(bigInt[1][0],
                                min(bigInt[2][0],
                                        min(bigInt[3][0],
                                                min(bigInt[4][0], bigInt[5][0]))))));

        assertEquals(bigInt[bigInt.length-1][1],
                max(bigInt[0][1],
                        max(bigInt[1][1],
                                max(bigInt[2][1],
                                        max(bigInt[3][1],
                                                max(bigInt[4][1], bigInt[5][1]))))));
    }
}
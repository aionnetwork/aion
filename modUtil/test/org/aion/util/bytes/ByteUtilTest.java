package org.aion.util.bytes;

import static org.aion.util.bytes.ByteUtil.and;
import static org.aion.util.bytes.ByteUtil.appendByte;
import static org.aion.util.bytes.ByteUtil.bigEndianToShort;
import static org.aion.util.bytes.ByteUtil.bigIntegerToBytes;
import static org.aion.util.bytes.ByteUtil.bigIntegerToBytesSigned;
import static org.aion.util.bytes.ByteUtil.byteArrayToInt;
import static org.aion.util.bytes.ByteUtil.byteArrayToLong;
import static org.aion.util.bytes.ByteUtil.bytesToBigInteger;
import static org.aion.util.bytes.ByteUtil.copyToArray;
import static org.aion.util.bytes.ByteUtil.encodeValFor32Bits;
import static org.aion.util.bytes.ByteUtil.firstNonZeroByte;
import static org.aion.util.bytes.ByteUtil.getBit;
import static org.aion.util.bytes.ByteUtil.hexStringToBytes;
import static org.aion.util.bytes.ByteUtil.increment;
import static org.aion.util.bytes.ByteUtil.intToBytes;
import static org.aion.util.bytes.ByteUtil.intToBytesBE;
import static org.aion.util.bytes.ByteUtil.intToBytesLE;
import static org.aion.util.bytes.ByteUtil.intToBytesNoLeadZeroes;
import static org.aion.util.bytes.ByteUtil.isNullOrZeroArray;
import static org.aion.util.bytes.ByteUtil.isSingleZero;
import static org.aion.util.bytes.ByteUtil.length;
import static org.aion.util.bytes.ByteUtil.longToBytes;
import static org.aion.util.bytes.ByteUtil.longToBytesLE;
import static org.aion.util.bytes.ByteUtil.longToBytesNoLeadZeroes;
import static org.aion.util.bytes.ByteUtil.numBytes;
import static org.aion.util.bytes.ByteUtil.oneByteToHexString;
import static org.aion.util.bytes.ByteUtil.or;
import static org.aion.util.bytes.ByteUtil.setBit;
import static org.aion.util.bytes.ByteUtil.shortToBytes;
import static org.aion.util.bytes.ByteUtil.stripLeadingZeroes;
import static org.aion.util.bytes.ByteUtil.toByte;
import static org.aion.util.bytes.ByteUtil.toHexString;
import static org.aion.util.bytes.ByteUtil.toHexStringWithPrefix;
import static org.aion.util.bytes.ByteUtil.toLEByteArray;
import static org.aion.util.bytes.ByteUtil.xor;
import static org.aion.util.bytes.ByteUtil.xorAlignRight;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.util.conversions.Hex;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongycastle.util.BigIntegers;

@RunWith(JUnitParamsRunner.class)
public class ByteUtilTest {

    private static final Random random = new Random();

    private final String[] testHex = {
        null, // 0 - Null
        "", // 1 - Empty
        "eF", // 2 - One Byte
        "aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44", // 3 - Upper/Lower
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", // 4 - Negative (-1)
        "0000000000000000000000000000000000000000000000000000000000000000", // 5 - Zeroes
        "0000000000000000000000000000000000000000000000000000000000000001", // 6 - Positive (+1)
    }; // 1byte

    private final byte[][] testByte = {
        null,
        hexStringToBytes(testHex[1]),
        hexStringToBytes(testHex[2]),
        hexStringToBytes(testHex[3]),
        hexStringToBytes(testHex[4]),
        hexStringToBytes(testHex[5]),
        hexStringToBytes(testHex[6]),
    };

    private final String[][] testNum = {
        {null, null},
        {"-00000000000000000000", "00000000000000000000"},
        {"-00000000000000000001", "00000000000000000001"},
        {"-10000000000000000000", "10000000000000000000"},
        {"-20000000000000000000", "20000000000000000000"},
        {"-30000000000000000000", "30000000000000000000"},
        {"-99999999999999999999", "99999999999999999999"},
    };

    private final BigInteger[][] testBigInt = {
        {null, null},
        {new BigInteger(testNum[1][0]), new BigInteger(testNum[1][1])},
        {new BigInteger(testNum[2][0]), new BigInteger(testNum[2][1])},
        {new BigInteger(testNum[3][0]), new BigInteger(testNum[3][1])},
        {new BigInteger(testNum[4][0]), new BigInteger(testNum[4][1])},
        {new BigInteger(testNum[5][0]), new BigInteger(testNum[5][1])},
        {new BigInteger(testNum[6][0]), new BigInteger(testNum[6][1])},
    };

    /** @return input values for {@link #longTest(long)} */
    @SuppressWarnings("unused")
    private Object longValues() {

        List<Object> parameters = new ArrayList<>();

        // longs similar to integer values
        parameters.add(0L);
        parameters.add(1L);
        parameters.add(10L);
        parameters.add((long) random.nextInt(Integer.MAX_VALUE));
        parameters.add((long) Integer.MAX_VALUE);

        // additional long values
        parameters.add((long) Integer.MAX_VALUE + random.nextInt(Integer.MAX_VALUE));
        parameters.add(10L * (long) Integer.MAX_VALUE);
        parameters.add(Long.MAX_VALUE - 1L);

        return parameters.toArray();
    }

    /** @return input values for {@link #intTest(int)} */
    @SuppressWarnings("unused")
    private Object intValues() {

        List<Object> parameters = new ArrayList<>();

        // integer values
        parameters.add(0);
        parameters.add(1);
        parameters.add(10);
        parameters.add(15);
        parameters.add(20);
        parameters.add(random.nextInt(Integer.MAX_VALUE));
        parameters.add(Integer.MAX_VALUE);

        return parameters.toArray();
    }

    /** @return input values for {@link #shortTest(short)} */
    @SuppressWarnings("unused")
    private Object shortValues() {

        Short[] temp = {
            0, 1, 10, 15, 20, (short) random.nextInt(Integer.MAX_VALUE), (short) Integer.MAX_VALUE
        };

        return temp;
    }

    /**
     * TEST: BI <--> Bytes (+ve) bigIntegerToBytes(BI) bigIntegerToBytes(BI, num)
     * bigIntegerToBytesSigned(BI, num) bytesToBigInteger(byte) {@link #intValues()}.
     */
    @Test
    public void bigIntegerTest() {
        for (int b = 1; b < 2; b++) {
            for (int a = 0; a < testBigInt.length; a++) {
                try {
                    byte[] temp1 = bigIntegerToBytes(testBigInt[a][b]);
                    byte[] temp2 = bigIntegerToBytes(testBigInt[a][b], temp1.length);
                    byte[] temp3 = bigIntegerToBytesSigned(testBigInt[a][b], temp1.length);
                    byte[] temp4 = encodeValFor32Bits(testNum[a][b]);

                    assertEquals(testBigInt[a][b], bytesToBigInteger(temp1));
                    assertEquals(testBigInt[a][b], bytesToBigInteger(temp2));
                    assertEquals(testBigInt[a][b], bytesToBigInteger(temp3));
                    assertEquals(bytesToBigInteger(temp1), bytesToBigInteger(temp4));
                } catch (NullPointerException e) {
                    System.out.println(b + " " + a);
                    System.out.println("\nNull Big Integer Test!");
                }
            }
        }
    }

    // TODO: Object --> Bytes
    // encodeValFor32Bits(Object)
    // encodeDataList(Object)
    @Test
    public void objectTest() {
        for (int a = 0; a < testNum.length; a++) {
            try {
                byte[] temp1 = encodeValFor32Bits(testNum[a][1]);
            } catch (NullPointerException e) {
                System.out.println("\nNull Object Test!");
            }
        }
    }

    /** TEST: hex <--> Bytes hexStringToBytes(hex) toHexString(byte) toHexStringWithPrefix(byte) */
    @Test
    public void hexTest() {
        byte[] temp0 = hexStringToBytes(testHex[0]);
        assertEquals("", toHexString(temp0));
        assertEquals("0x", toHexStringWithPrefix(temp0));

        for (int a = 1; a < testHex.length; a++) {
            byte[] temp1 = hexStringToBytes(testHex[a]);
            assertEquals(testHex[a].toLowerCase(), toHexString(temp1));
            assertEquals("0x" + testHex[a].toLowerCase(), toHexStringWithPrefix(temp1));
        }
    }

    /**
     * TEST: long <--> Bytes longToBytes(long) longToBytesNoLeadZeroes(long) byteArrayToLong(byte)
     * longToBytesLE(long)
     */
    @Test
    @Parameters(method = "longValues")
    public void longTest(long testLong) {

        byte[] temp1 = longToBytes(testLong);
        byte[] temp2 = longToBytesNoLeadZeroes(testLong);
        byte[] temp3 = longToBytesLE(testLong);

        toLEByteArray(temp3);

        assertEquals(testLong, byteArrayToLong(temp1));
        assertEquals(testLong, byteArrayToLong(temp2));
        assertEquals(testLong, byteArrayToLong(temp3));
    }

    /**
     * TEST: short <--> Bytes shortToBytes(short) bigEndianToShort(byte) ~ bigEndianToShort(byte,
     * offset)
     */
    @Test
    @Parameters(method = "shortValues")
    public void shortTest(short testShort) {
        byte[] temp1 = shortToBytes(testShort);
        assertEquals(testShort, bigEndianToShort(temp1));
    }

    /**
     * TEST: int <--> Bytes intToBytes(int) intToBytesLE(int) intToBytesBE(int)
     * intToBytesNoLeadZeroes(int) byteArrayToInt(byte) ~ intsToBytes(array, BE) ~
     * intsToBytes(array, byte, BE) ~ bytesToInts(byte, BE) ~ bytesToInts(byte, array, BE)
     */
    @Test
    @Parameters(method = "intValues")
    public void intTest(int testInt) {

        byte[] temp1 = intToBytes(testInt);
        byte[] temp2 = intToBytesLE(testInt);
        byte[] temp3 = intToBytesBE(testInt);
        byte[] temp4 = intToBytesNoLeadZeroes(testInt);

        toLEByteArray(temp2);

        assertEquals(testInt, byteArrayToInt(temp1));
        assertEquals(testInt, byteArrayToInt(temp2));
        assertEquals(testInt, byteArrayToInt(temp3));
        assertEquals(testInt, byteArrayToInt(temp4));
    }

    /**
     * TEST: Byte validation toByte(byte) toLEByteArray(byte) firstNonZeroByte(byte) numBytes(hex)
     * isNullOrZeroArray(byte) isSingleZero(byte) length(byte) stripLeadingZeroes(byte)
     * appendByte(byte1, byte2) oneByteToHexString(byte) ~ matchingNibbleLength(byte1, byte2) ~
     * nibblesToPrettyString(byte) ~ difference(byteSet1, byteSet2)
     */
    @Test
    public void byteTest() {

        // Single 'zero' byte
        String singleZero = "0";
        byte[] temp = bigIntegerToBytes(new BigInteger(singleZero));
        assertEquals(-1, firstNonZeroByte(temp));
        assertEquals(1, numBytes(singleZero));
        assertEquals(1, length(temp));
        assertFalse(isNullOrZeroArray(temp));
        assertTrue(isSingleZero(temp));

        // Null Variable
        byte[] temp0 = bigIntegerToBytes(new BigInteger(singleZero));
        assertEquals(-1, firstNonZeroByte(temp0));
        assertTrue(isNullOrZeroArray(testByte[0]));
        assertNull(stripLeadingZeroes(testByte[0]));

        // Empty Array
        byte[] temp1 = stripLeadingZeroes(testByte[1]);
        assertEquals(-1, firstNonZeroByte(temp1));
        assertEquals(-1, firstNonZeroByte(testByte[1]));
        assertTrue(isNullOrZeroArray(testByte[1]));

        // Leading Non-zero
        byte[] temp2 = stripLeadingZeroes(testByte[2]);
        assertEquals(0, firstNonZeroByte(temp2));
        assertEquals(0, firstNonZeroByte(testByte[2]));
        assertEquals(0, firstNonZeroByte(testByte[3]));
        assertEquals(0, firstNonZeroByte(testByte[4]));

        // Only Zeroes
        assertEquals(-1, firstNonZeroByte(testByte[5]));
        assertFalse(isNullOrZeroArray(testByte[5]));
        assertFalse(isSingleZero(testByte[5]));

        // Leading Zeroes
        byte[] temp3 = stripLeadingZeroes(testByte[6]);
        assertEquals(0, firstNonZeroByte(temp3));
        assertEquals(31, firstNonZeroByte(testByte[6]));

        // n Byte = { 2n Hex || (256^n) - 1 }
        assertEquals(1, numBytes("255"));
        assertEquals(2, numBytes("65535"));
        assertEquals(3, numBytes("16777215"));
        assertEquals(4, numBytes("4294967295"));
        assertEquals(5, numBytes("1099511627775"));

        // TFAE (+ve);
        // 1) numBytes(number)
        // 2) bigInt.length
        // 3) hex.length()/2
        for (int a = 1; a < testBigInt.length; a++) {
            byte[] temp4 = bigIntegerToBytes(testBigInt[a][1]);
            String temp5 = toHexString(temp4);
            assertEquals(temp5.length() / 2, temp4.length);
            assertEquals(temp5.length() / 2, numBytes(testNum[a][1]));
        }

        // Append
        for (int a = 0; a < testHex.length; a++) {
            byte[] temp6 = hexStringToBytes(testHex[a]);
            byte temp7 = toByte(testByte[2]);
            byte[] temp8 = appendByte(temp6, temp7);
            String temp9 = oneByteToHexString(temp7);
            assertEquals(testHex[2].toLowerCase(), temp9);
            assertEquals(toHexString(temp6) + temp9, toHexString(temp8));
        }
    }

    /**
     * TEST: Bit manipulation increment(byte) copyToArray(byte) setBit(byte, pos, val) getBit(byte,
     * pos) and(byte1, byte2) or(byte1, byte2) xor(byte1, byte2) xorAlignRight(byte1, byte2)
     */
    @Test
    public void bitTest() {

        // Increment for all byte size (+ve)
        int increment = 123;
        boolean max = false;
        for (int a = 1; a < testBigInt.length; a++) {

            BigInteger temp1 = testBigInt[a][1];
            byte[] temp2 = bigIntegerToBytes(temp1);
            BigInteger capacity = BigInteger.valueOf((long) Math.pow(255, temp2.length));

            for (int i = 0; i < increment; i++) {
                while (!max) {
                    max = increment(temp2);
                }
                temp1 = bytesToBigInteger(temp2);
                max = false;
            }

            BigInteger temp3 = BigInteger.valueOf(increment).mod(capacity);
            BigInteger temp4 = testBigInt[a][1].add(temp3);
            assertEquals(temp4, temp1);
        }

        // Copy array, convert and assert (+ve)
        for (int a = 1; a < testBigInt.length; a++) {
            byte[] temp5 = copyToArray(testBigInt[a][1]);
            BigInteger temp6 = bytesToBigInteger(temp5);
            assertEquals(testBigInt[a][1], temp6);
        }

        // Set bit, get bit for every bit
        int shifts = 8;
        for (int c = 0; c < shifts; c++) {
            byte[] temp6 = bigIntegerToBytesSigned(testBigInt[1][1], 1);
            setBit(temp6, c, 1);
            assertEquals(1, getBit(temp6, c));
            assertEquals(BigInteger.valueOf((long) Math.pow(2, c)), bytesToBigInteger(temp6));
        }

        // AND + OR with zero (+ve)
        for (int a = 2; a < testBigInt.length; a++) {
            byte[] temp8 = bigIntegerToBytes(testBigInt[a][1]);
            byte[] temp9 = bigIntegerToBytes(testBigInt[1][1], temp8.length);
            byte[] temp10 = or(temp8, temp9);
            byte[] temp11 = and(temp8, temp9);
            assertEquals(testBigInt[a][1], bytesToBigInteger(temp10));
            assertEquals(testBigInt[1][1], bytesToBigInteger(temp11));
            System.out.println(
                    testBigInt[a][1]
                            + " || "
                            + testBigInt[1][1]
                            + " --> "
                            + bytesToBigInteger(temp10));
            System.out.println(
                    testBigInt[a][1]
                            + " && "
                            + testBigInt[1][1]
                            + " --> "
                            + bytesToBigInteger(temp11));
        }

        // 2's compliment -ve BI --> +ve BI
        for (int a = 1; a < testBigInt.length; a++) {
            boolean found = false;
            int rightMost = 0;
            byte[] temp7 = bigIntegerToBytes(testBigInt[a][0]);
            while (!found && rightMost < temp7.length * 8) {
                if (getBit(temp7, rightMost) == 1) {
                    found = true;
                } else {
                    rightMost++;
                }
            }
            if (found) {
                for (int b = rightMost + 1; b < temp7.length * 8; b++) {
                    if (getBit(temp7, b) == 0) {
                        setBit(temp7, b, 1);
                    } else if (getBit(temp7, b) == 1) {
                        setBit(temp7, b, 0);
                    }
                }
            }
            assertEquals(testBigInt[a][1], bytesToBigInteger(temp7));
            System.out.println(testBigInt[a][0] + " --> " + bytesToBigInteger(temp7));
        }

        // 1's compliment | XOR with "FF" * numBytes
        for (int b = 0; b < 2; b++) {
            for (int a = 1; a < testBigInt.length; a++) {
                byte[] temp12 = bigIntegerToBytes(testBigInt[a][b]);
                StringBuilder allForOne = new StringBuilder();
                for (int i = 0; i < temp12.length; i++) {
                    allForOne.append("FF");
                }
                byte[] temp13 = hexStringToBytes(allForOne.toString());
                byte[] temp14 = xor(temp13, temp12);
                byte[] temp15 = xorAlignRight(temp13, temp12);
                for (int c = 0; c < temp12.length * 8; c++) {
                    if (getBit(temp12, c) == 0) {
                        setBit(temp12, c, 1);
                    } else if (getBit(temp12, c) == 1) {
                        setBit(temp12, c, 0);
                    }
                }
                assertEquals(bytesToBigInteger(temp14), bytesToBigInteger(temp12));
                assertEquals(bytesToBigInteger(temp15), bytesToBigInteger(temp12));
            }
        }
    }

    @Test
    public void testAppendByte() {
        byte[] bytes = "tes".getBytes();
        byte b = 0x74;
        Assert.assertArrayEquals("test".getBytes(), ByteUtil.appendByte(bytes, b));
    }

    @Test
    public void testBigIntegerToBytes() {
        byte[] expecteds = new byte[] {(byte) 0xff, (byte) 0xec, 0x78};
        BigInteger b = BigInteger.valueOf(16772216);
        byte[] actuals = ByteUtil.bigIntegerToBytes(b);
        assertArrayEquals(expecteds, actuals);
    }

    @Test
    public void testBigIntegerToBytesSign() {
        {
            BigInteger b = BigInteger.valueOf(-2);
            byte[] actuals = ByteUtil.bigIntegerToBytesSigned(b, 8);
            assertArrayEquals(Hex.decode("fffffffffffffffe"), actuals);
        }
        {
            BigInteger b = BigInteger.valueOf(2);
            byte[] actuals = ByteUtil.bigIntegerToBytesSigned(b, 8);
            assertArrayEquals(Hex.decode("0000000000000002"), actuals);
        }
        {
            BigInteger b = BigInteger.valueOf(0);
            byte[] actuals = ByteUtil.bigIntegerToBytesSigned(b, 8);
            assertArrayEquals(Hex.decode("0000000000000000"), actuals);
        }
        {
            BigInteger b = new BigInteger("eeeeeeeeeeeeee", 16);
            byte[] actuals = ByteUtil.bigIntegerToBytesSigned(b, 8);
            assertArrayEquals(Hex.decode("00eeeeeeeeeeeeee"), actuals);
        }
        {
            BigInteger b = new BigInteger("eeeeeeeeeeeeeeee", 16);
            byte[] actuals = ByteUtil.bigIntegerToBytesSigned(b, 8);
            assertArrayEquals(Hex.decode("eeeeeeeeeeeeeeee"), actuals);
        }
    }

    @Test
    public void testBigIntegerToBytesNegative() {
        byte[] expecteds = new byte[] {(byte) 0xff, 0x0, 0x13, (byte) 0x88};
        BigInteger b = BigInteger.valueOf(-16772216);
        byte[] actuals = ByteUtil.bigIntegerToBytes(b);
        assertArrayEquals(expecteds, actuals);
    }

    @Test
    public void testBigIntegerToBytesZero() {
        byte[] expecteds = new byte[] {0x00};
        BigInteger b = BigInteger.ZERO;
        byte[] actuals = ByteUtil.bigIntegerToBytes(b);
        assertArrayEquals(expecteds, actuals);
    }

    @Test
    public void testToHexString() {
        assertEquals("", ByteUtil.toHexString(null));
    }

    @Test
    public void testCalcPacketLength() {
        byte[] test = new byte[] {0x0f, 0x10, 0x43};
        byte[] expected = new byte[] {0x00, 0x00, 0x00, 0x03};
        assertArrayEquals(expected, ByteUtil.calcPacketLength(test));
    }

    @Test
    public void testByteArrayToInt() {
        assertEquals(0, ByteUtil.byteArrayToInt(null));
        assertEquals(0, ByteUtil.byteArrayToInt(new byte[0]));

        //      byte[] x = new byte[] { 5,1,7,0,8 };
        //      long start = System.currentTimeMillis();
        //      for (int i = 0; i < 100000000; i++) {
        //           ByteArray.read32bit(x, 0);
        //      }
        //      long end = System.currentTimeMillis();
        //      System.out.println(end - start + "ms");
        //
        //      long start1 = System.currentTimeMillis();
        //      for (int i = 0; i < 100000000; i++) {
        //          new BigInteger(1, x).intValue();
        //      }
        //      long end1 = System.currentTimeMillis();
        //      System.out.println(end1 - start1 + "ms");
    }

    @Test
    public void testNumBytes() {
        String test1 = "0";
        String test2 = "1";
        String test3 = "1000000000"; // 3B9ACA00
        int expected1 = 1;
        int expected2 = 1;
        int expected3 = 4;
        assertEquals(expected1, ByteUtil.numBytes(test1));
        assertEquals(expected2, ByteUtil.numBytes(test2));
        assertEquals(expected3, ByteUtil.numBytes(test3));
    }

    @Test
    public void testStripLeadingZeroes() {
        byte[] test1 = null;
        byte[] test2 = new byte[] {};
        byte[] test3 = new byte[] {0x00};
        byte[] test4 = new byte[] {0x00, 0x01};
        byte[] test5 = new byte[] {0x00, 0x00, 0x01};
        byte[] expected1 = null;
        byte[] expected2 = new byte[] {0};
        byte[] expected3 = new byte[] {0};
        byte[] expected4 = new byte[] {0x01};
        byte[] expected5 = new byte[] {0x01};
        assertArrayEquals(expected1, ByteUtil.stripLeadingZeroes(test1));
        assertArrayEquals(expected2, ByteUtil.stripLeadingZeroes(test2));
        assertArrayEquals(expected3, ByteUtil.stripLeadingZeroes(test3));
        assertArrayEquals(expected4, ByteUtil.stripLeadingZeroes(test4));
        assertArrayEquals(expected5, ByteUtil.stripLeadingZeroes(test5));
    }

    @Test
    public void testMatchingNibbleLength1() {
        // a larger than b
        byte[] a = new byte[] {0x00, 0x01};
        byte[] b = new byte[] {0x00};
        int result = ByteUtil.matchingNibbleLength(a, b);
        assertEquals(1, result);
    }

    @Test
    public void testMatchingNibbleLength2() {
        // b larger than a
        byte[] a = new byte[] {0x00};
        byte[] b = new byte[] {0x00, 0x01};
        int result = ByteUtil.matchingNibbleLength(a, b);
        assertEquals(1, result);
    }

    @Test
    public void testMatchingNibbleLength3() {
        // a and b the same length equal
        byte[] a = new byte[] {0x00};
        byte[] b = new byte[] {0x00};
        int result = ByteUtil.matchingNibbleLength(a, b);
        assertEquals(1, result);
    }

    @Test
    public void testMatchingNibbleLength4() {
        // a and b the same length not equal
        byte[] a = new byte[] {0x01};
        byte[] b = new byte[] {0x00};
        int result = ByteUtil.matchingNibbleLength(a, b);
        assertEquals(0, result);
    }

    @Test
    public void testNiceNiblesOutput_1() {
        byte[] test = {7, 0, 7, 5, 7, 0, 7, 0, 7, 9};
        String result = "\\x07\\x00\\x07\\x05\\x07\\x00\\x07\\x00\\x07\\x09";
        assertEquals(result, ByteUtil.nibblesToPrettyString(test));
    }

    @Test
    public void testNiceNiblesOutput_2() {
        byte[] test = {7, 0, 7, 0xf, 7, 0, 0xa, 0, 7, 9};
        String result = "\\x07\\x00\\x07\\x0f\\x07\\x00\\x0a\\x00\\x07\\x09";
        assertEquals(result, ByteUtil.nibblesToPrettyString(test));
    }

    @Test(expected = NullPointerException.class)
    public void testMatchingNibbleLength5() {
        // a == null
        byte[] a = null;
        byte[] b = new byte[] {0x00};
        ByteUtil.matchingNibbleLength(a, b);
    }

    @Test(expected = NullPointerException.class)
    public void testMatchingNibbleLength6() {
        // b == null
        byte[] a = new byte[] {0x00};
        byte[] b = null;
        ByteUtil.matchingNibbleLength(a, b);
    }

    @Test
    public void testMatchingNibbleLength7() {
        // a or b is empty
        byte[] a = new byte[0];
        byte[] b = new byte[] {0x00};
        int result = ByteUtil.matchingNibbleLength(a, b);
        assertEquals(0, result);
    }

    /**
     * This test shows the difference between iterating over, and comparing byte[] vs BigInteger
     * value.
     *
     * <p>Results indicate that the former has ~15x better performance. Therefore this is used in
     * the Miner.mine() method.
     */
    @Test
    public void testIncrementPerformance() {
        boolean testEnabled = false;

        if (testEnabled) {
            byte[] counter1 = new byte[4];
            byte[] max = ByteBuffer.allocate(4).putInt(Integer.MAX_VALUE).array();
            long start1 = System.currentTimeMillis();
            while (ByteUtil.increment(counter1)) {
                if (compareTo(counter1, 0, 4, max, 0, 4) == 0) {
                    break;
                }
            }
            System.out.println(
                    System.currentTimeMillis()
                            - start1
                            + "ms to reach: "
                            + Hex.toHexString(counter1));

            BigInteger counter2 = BigInteger.ZERO;
            long start2 = System.currentTimeMillis();
            while (true) {
                if (counter2.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) == 0) {
                    break;
                }
                counter2 = counter2.add(BigInteger.ONE);
            }
            System.out.println(
                    System.currentTimeMillis()
                            - start2
                            + "ms to reach: "
                            + Hex.toHexString(BigIntegers.asUnsignedByteArray(4, counter2)));
        }
    }

    /** Compares two regions of byte array. */
    public static int compareTo(
            byte[] array1, int offset1, int size1, byte[] array2, int offset2, int size2) {
        byte[] b1 = Arrays.copyOfRange(array1, offset1, offset1 + size1);
        byte[] b2 = Arrays.copyOfRange(array2, offset2, offset2 + size2);

        return Arrays.compare(b1, b2);
    }

    @Test
    public void firstNonZeroByte_1() {

        byte[] data =
                Hex.decode("0000000000000000000000000000000000000000000000000000000000000000");
        int result = ByteUtil.firstNonZeroByte(data);

        assertEquals(-1, result);
    }

    @Test
    public void firstNonZeroByte_2() {

        byte[] data =
                Hex.decode("0000000000000000000000000000000000000000000000000000000000332211");
        int result = ByteUtil.firstNonZeroByte(data);

        assertEquals(29, result);
    }

    @Test
    public void firstNonZeroByte_3() {

        byte[] data =
                Hex.decode("2211009988776655443322110099887766554433221100998877665544332211");
        int result = ByteUtil.firstNonZeroByte(data);

        assertEquals(0, result);
    }

    @Test
    public void setBitTest() {
        /*
           Set on
        */
        byte[] data = ByteBuffer.allocate(4).putInt(0).array();
        int posBit = 24;
        int expected = 16777216;
        int result = -1;
        byte[] ret = ByteUtil.setBit(data, posBit, 1);
        result = ByteUtil.byteArrayToInt(ret);
        assertTrue(expected == result);

        posBit = 25;
        expected = 50331648;
        ret = ByteUtil.setBit(data, posBit, 1);
        result = ByteUtil.byteArrayToInt(ret);
        assertTrue(expected == result);

        posBit = 2;
        expected = 50331652;
        ret = ByteUtil.setBit(data, posBit, 1);
        result = ByteUtil.byteArrayToInt(ret);
        assertTrue(expected == result);

        /*
           Set off
        */
        posBit = 24;
        expected = 33554436;
        ret = ByteUtil.setBit(data, posBit, 0);
        result = ByteUtil.byteArrayToInt(ret);
        assertTrue(expected == result);

        posBit = 25;
        expected = 4;
        ret = ByteUtil.setBit(data, posBit, 0);
        result = ByteUtil.byteArrayToInt(ret);
        assertTrue(expected == result);

        posBit = 2;
        expected = 0;
        ret = ByteUtil.setBit(data, posBit, 0);
        result = ByteUtil.byteArrayToInt(ret);
        assertTrue(expected == result);
    }

    @Test
    public void getBitTest() {
        byte[] data = ByteBuffer.allocate(4).putInt(0).array();
        ByteUtil.setBit(data, 24, 1);
        ByteUtil.setBit(data, 25, 1);
        ByteUtil.setBit(data, 2, 1);

        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < (data.length * 8); i++) {
            int res = ByteUtil.getBit(data, i);
            if (res == 1) {
                if (i != 24 && i != 25 && i != 2) {
                    assertTrue(false);
                } else {
                    found.add(i);
                }
            } else {
                if (i == 24 || i == 25 || i == 2) {
                    assertTrue(false);
                }
            }
        }

        if (found.size() != 3) {
            assertTrue(false);
        }
        assertTrue(found.get(0) == 2);
        assertTrue(found.get(1) == 24);
        assertTrue(found.get(2) == 25);
    }

    @Test
    public void numToBytesTest() {
        byte[] bytes = ByteUtil.intToBytesNoLeadZeroes(-1);
        assertArrayEquals(bytes, Hex.decode("ffffffff"));
        bytes = ByteUtil.intToBytesNoLeadZeroes(1);
        assertArrayEquals(bytes, Hex.decode("01"));
        bytes = ByteUtil.intToBytesNoLeadZeroes(255);
        assertArrayEquals(bytes, Hex.decode("ff"));
        bytes = ByteUtil.intToBytesNoLeadZeroes(256);
        assertArrayEquals(bytes, Hex.decode("0100"));
        bytes = ByteUtil.intToBytesNoLeadZeroes(0);
        assertArrayEquals(bytes, new byte[0]);

        bytes = ByteUtil.intToBytes(-1);
        assertArrayEquals(bytes, Hex.decode("ffffffff"));
        bytes = ByteUtil.intToBytes(1);
        assertArrayEquals(bytes, Hex.decode("00000001"));
        bytes = ByteUtil.intToBytes(255);
        assertArrayEquals(bytes, Hex.decode("000000ff"));
        bytes = ByteUtil.intToBytes(256);
        assertArrayEquals(bytes, Hex.decode("00000100"));
        bytes = ByteUtil.intToBytes(0);
        assertArrayEquals(bytes, Hex.decode("00000000"));

        bytes = ByteUtil.longToBytesNoLeadZeroes(-1);
        assertArrayEquals(bytes, Hex.decode("ffffffffffffffff"));
        bytes = ByteUtil.longToBytesNoLeadZeroes(1);
        assertArrayEquals(bytes, Hex.decode("01"));
        bytes = ByteUtil.longToBytesNoLeadZeroes(255);
        assertArrayEquals(bytes, Hex.decode("ff"));
        bytes = ByteUtil.longToBytesNoLeadZeroes(1L << 32);
        assertArrayEquals(bytes, Hex.decode("0100000000"));
        bytes = ByteUtil.longToBytesNoLeadZeroes(0);
        assertArrayEquals(bytes, new byte[0]);

        bytes = ByteUtil.longToBytes(-1);
        assertArrayEquals(bytes, Hex.decode("ffffffffffffffff"));
        bytes = ByteUtil.longToBytes(1);
        assertArrayEquals(bytes, Hex.decode("0000000000000001"));
        bytes = ByteUtil.longToBytes(255);
        assertArrayEquals(bytes, Hex.decode("00000000000000ff"));
        bytes = ByteUtil.longToBytes(256);
        assertArrayEquals(bytes, Hex.decode("0000000000000100"));
        bytes = ByteUtil.longToBytes(0);
        assertArrayEquals(bytes, Hex.decode("0000000000000000"));
    }

    @Test
    public void testHexStringToBytes() {
        {
            String str = "0000";
            byte[] actuals = ByteUtil.hexStringToBytes(str);
            byte[] expected = new byte[] {0, 0};
            assertArrayEquals(expected, actuals);
        }
        {
            String str = "0x0000";
            byte[] actuals = ByteUtil.hexStringToBytes(str);
            byte[] expected = new byte[] {0, 0};
            assertArrayEquals(expected, actuals);
        }
        {
            String str = "0x45a6";
            byte[] actuals = ByteUtil.hexStringToBytes(str);
            byte[] expected = new byte[] {69, -90};
            assertArrayEquals(expected, actuals);
        }
        {
            String str = "1963093cee500c081443e1045c40264b670517af";
            byte[] actuals = ByteUtil.hexStringToBytes(str);
            byte[] expected = Hex.decode(str);
            assertArrayEquals(expected, actuals);
        }
        {
            String str = "0x"; // Empty
            byte[] actuals = ByteUtil.hexStringToBytes(str);
            byte[] expected = new byte[] {};
            assertArrayEquals(expected, actuals);
        }
        {
            String str = "0"; // Same as 0x00
            byte[] actuals = ByteUtil.hexStringToBytes(str);
            byte[] expected = new byte[] {0};
            assertArrayEquals(expected, actuals);
        }
        {
            String str = "0x00"; // This case shouldn't be empty array
            byte[] actuals = ByteUtil.hexStringToBytes(str);
            byte[] expected = new byte[] {0};
            assertArrayEquals(expected, actuals);
        }
        {
            String str = "0xd"; // Should work with odd length, adding leading 0
            byte[] actuals = ByteUtil.hexStringToBytes(str);
            byte[] expected = new byte[] {13};
            assertArrayEquals(expected, actuals);
        }
        {
            String str = "0xd0d"; // Should work with odd length, adding leading 0
            byte[] actuals = ByteUtil.hexStringToBytes(str);
            byte[] expected = new byte[] {13, 13};
            assertArrayEquals(expected, actuals);
        }
    }
}

package org.aion.base.util;

import org.junit.Test;

import javax.sound.midi.SysexMessage;
import java.math.BigInteger;
import java.util.Set;

import static org.aion.base.util.ByteUtil.*;
import static org.junit.Assert.*;

public class ByteUtilTest {

    private final int size1 = 7;
    private final int size2 = 6;

    private final String[] testHex = {
            null,                                                                   // 0 - Null
            "",                                                                     // 1 - Empty
            "eF",                                                                   // 2 - One Byte
            "aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44",     // 3 - Upper/Lower
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",     // 4 - Negative (-1)
            "0000000000000000000000000000000000000000000000000000000000000000",     // 5 - Zeroes
            "0000000000000000000000000000000000000000000000000000000000000001",     // 6 - Positive (+1)
    };  // 1byte

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
            { null, null },
            { "-00000000000000000000", "00000000000000000000" },
            { "-00000000000000000001", "00000000000000000001" },
            { "-10000000000000000000", "10000000000000000000" },
            { "-20000000000000000000", "20000000000000000000" },
            { "-30000000000000000000", "30000000000000000000" },
            { "-99999999999999999999", "99999999999999999999" },
    };

    private final BigInteger[][] testBigInt = {
            { null, null },
            { new BigInteger(testNum[1][0]), new BigInteger(testNum[1][1])},
            { new BigInteger(testNum[2][0]), new BigInteger(testNum[2][1])},
            { new BigInteger(testNum[3][0]), new BigInteger(testNum[3][1])},
            { new BigInteger(testNum[4][0]), new BigInteger(testNum[4][1])},
            { new BigInteger(testNum[5][0]), new BigInteger(testNum[5][1])},
            { new BigInteger(testNum[6][0]), new BigInteger(testNum[6][1])},
    };

    private final long[][] testLong = {
            { -0000000000000000000L, 0000000000000000000L },                        // 0 - Zeroes
            { -0000000000000000001L, 0000000000000000001L },                        // 1 - Lead Zeroes
            { -1000000000000000000L, 1000000000000000000L },                        // 2 - Increment1
            { -2000000000000000000L, 2000000000000000000L },                        // 3 - Increment2
            { -3000000000000000000L, 3000000000000000000L },                        // 4 - Increment3
            { -9223372036854775808L, 9223372036854775807L },                        // 5 - Min / Max
    }; // 8bytes

    private final int[][] testInt = {
            { -0000000000, 0000000000 },
            { -0000000001, 0000000001 },
            { -1000000000, 1000000000 },
            { -1500000000, 1500000000 },
            { -2000000000, 2000000000 },
            { -2147483648, 2147483647 },
    }; // 4bytes

    private final short[][] testShort = {
            { -00000, 00000 },
            { -00001, 00001 },
            { -10000, 10000 },
            { -20000, 20000 },
            { -30000, 30000 },
            { -32768, 32767 },
    }; // 2bytes


    /**
     * TEST: hex <--> Bytes
     * hexStringToBytes(hex)
     * toHexString(byte)
     * toHexStringWithPrefix(byte)
     */
    @Test
    public void hexTest() {
        byte[] temp0 = hexStringToBytes(testHex[0]);
        assertEquals("", toHexString(temp0));
        assertEquals("0x", toHexStringWithPrefix(temp0));

        for(int a = 1; a < testHex.length; a++) {
            byte[] temp1 = hexStringToBytes(testHex[a]);
            assertEquals(testHex[a].toLowerCase(), toHexString(temp1));
            assertEquals("0x" + testHex[a].toLowerCase(), toHexStringWithPrefix(temp1));
        }
    }

    /**
     * TEST: BI <--> Bytes (+ve)
     * bigIntegerToBytes(BI)
     * bigIntegerToBytes(BI, num)
     * bigIntegerToBytesSigned(BI, num)
     * bytesToBigInteger(byte)
     */

    @Test
    public void bigIntegerTest() {
        for(int b = 1; b < 2; b++) {
            for(int a = 0; a < testBigInt.length; a++) {
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

    /**
     * TEST: long <--> Bytes
     * longToBytes(long)
     * longToBytesNoLeadZeroes(long)
     * byteArrayToLong(byte)
     * longToBytesLE(long)
     */
    @Test
    public void longTest() {
        for(int b = 0; b < 2; b++) {
            for (int a = 0; a < testLong.length; a++) {
                byte[] temp1 = longToBytes(testLong[a][b]);
                byte[] temp2 = longToBytesNoLeadZeroes(testLong[a][b]);
                byte[] temp3 = longToBytesLE(testLong[a][b]);

                toLEByteArray(temp3);

                assertEquals(testLong[a][b], byteArrayToLong(temp1));
                assertEquals(testLong[a][b], byteArrayToLong(temp2));
                assertEquals(testLong[a][b], byteArrayToLong(temp3));
            }
        }
    }

    /**
     * TEST: short <--> Bytes
     * shortToBytes(short)
     * bigEndianToShort(byte)
     * ~ bigEndianToShort(byte, offset)
     */

    @Test
    public void shortTest() {
        for(int b = 0; b < 2; b++) {
            for(int a = 0; a < testShort.length; a++) {
                byte[] temp1 = shortToBytes(testShort[a][b]);
                assertEquals(testShort[a][b], bigEndianToShort(temp1));
            }
        }
    }

    /**
     * TEST: int <--> Bytes
     * intToBytes(int)
     * intToBytesLE(int)
     * intToBytesBE(int)
     * intToBytesNoLeadZeroes(int)
     * byteArrayToInt(byte)
     * ~ intsToBytes(array, BE)
     * ~ intsToBytes(array, byte, BE)
     * ~ bytesToInts(byte, BE)
     * ~ bytesToInts(byte, array, BE)
     */
    @Test
    public void intTest() {
        for(int b = 0; b < 2; b++) {
            for(int a = 0; a < testInt.length; a++) {
                byte[] temp1 = intToBytes(testInt[a][b]);
                byte[] temp2 = intToBytesLE(testInt[a][b]);
                byte[] temp3 = intToBytesBE(testInt[a][b]);
                byte[] temp4 = intToBytesNoLeadZeroes(testInt[a][b]);

                toLEByteArray(temp2);

                assertEquals(testInt[a][b], byteArrayToInt(temp1));
                assertEquals(testInt[a][b], byteArrayToInt(temp2));
                assertEquals(testInt[a][b], byteArrayToInt(temp3));
                assertEquals(testInt[a][b], byteArrayToInt(temp4));
            }
        }
    }

    /**
     * TEST: Byte validation
     * toByte(byte)
     * toLEByteArray(byte)
     * firstNonZeroByte(byte)
     * numBytes(hex)
     * isNullOrZeroArray(byte)
     * isSingleZero(byte)
     * length(byte)
     * stripLeadingZeroes(byte)
     * appendByte(byte1, byte2)
     * oneByteToHexString(byte)
     * ~ matchingNibbleLength(byte1, byte2)
     * ~ nibblesToPrettyString(byte)
     * ~ difference(byteSet1, byteSet2)
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
        for(int a = 1; a < testBigInt.length; a++) {
            byte[] temp4 = bigIntegerToBytes(testBigInt[a][1]);
            String temp5 = toHexString(temp4);
            assertEquals(temp5.length()/2, temp4.length);
            assertEquals(temp5.length()/2, numBytes(testNum[a][1]));
        }

        // Append
        for(int a = 0; a < size1; a++) {
            byte[] temp6 = hexStringToBytes(testHex[a]);
            byte temp7 = toByte(testByte[2]);
            byte[] temp8 = appendByte(temp6, temp7);
            String temp9 = oneByteToHexString(temp7);
            assertEquals(testHex[2].toLowerCase(), temp9);
            assertEquals(toHexString(temp6) + temp9, toHexString(temp8));
        }
    }

    /**
     * TEST: Bit manipulation
     * increment(byte)
     * copyToArray(byte)
     * setBit(byte, pos, val)
     * getBit(byte, pos)
     * and(byte1, byte2)
     * or(byte1, byte2)
     * xor(byte1, byte2)
     * xorAlignRight(byte1, byte2)
     */
    @Test
    public void bitTest() {

        // Increment for all byte size (+ve)
        int increment = 123;
        boolean max = false;
        for(int a = 1; a < testBigInt.length; a++) {

            BigInteger temp1 = testBigInt[a][1];
            byte[] temp2 = bigIntegerToBytes(temp1);
            BigInteger capacity = BigInteger.valueOf( (long) Math.pow(255, temp2.length) );

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
        for(int a = 1; a < testBigInt.length; a++) {
            byte[] temp5 = copyToArray(testBigInt[a][1]);
            BigInteger temp6 = bytesToBigInteger(temp5);
            assertEquals(testBigInt[a][1], temp6);
        }

        // Set bit, get bit for every bit
        int shifts = 8;
        for(int c = 0; c < shifts; c++) {
            byte[] temp6 = bigIntegerToBytesSigned(testBigInt[1][1], 1);
            setBit(temp6, c, 1);
            assertEquals(1, getBit(temp6, c));
            assertEquals(BigInteger.valueOf((long) Math.pow(2, c)), bytesToBigInteger(temp6));
        }

        // 2's compliment -ve BI --> +ve BI
        for(int a = 1; a < testBigInt.length; a++) {
            boolean found = false;
            int rightMost = 0;
            byte[] temp7 = bigIntegerToBytes(testBigInt[a][0]);
            while(!found && rightMost < temp7.length*8) {
                if(getBit(temp7, rightMost) == 1) {
                    found = true;
                } else {
                    rightMost++;
                }
            }
            if(found) {
                for(int b = rightMost+1; b < temp7.length*8; b++) {
                    if(getBit(temp7, b) == 0) {
                        setBit(temp7, b, 1);
                    } else if(getBit(temp7, b) == 1) {
                        setBit(temp7, b, 0);
                    }
                }
            }
            assertEquals(testBigInt[a][1], bytesToBigInteger(temp7));
            System.out.println(testBigInt[a][0] + " --> " + bytesToBigInteger(temp7));
        }

        // AND + OR with zero (+ve)
        for(int b = 1; b < 2; b++) {
            for(int a = 2; a < testBigInt.length; a++) {
                byte[] temp8 = bigIntegerToBytes(testBigInt[a][b]);
                byte[] temp9 = bigIntegerToBytes(testBigInt[1][b], temp8.length);
                byte[] temp10 = or(temp8, temp9);
                byte[] temp11 = and(temp8, temp9);
                assertEquals(testBigInt[a][b], bytesToBigInteger(temp10));
                assertEquals(testBigInt[1][b], bytesToBigInteger(temp11));
                System.out.println(testBigInt[a][b] + " || " + testBigInt[1][b] + " --> " + bytesToBigInteger(temp10));
                System.out.println(testBigInt[a][b] + " && " + testBigInt[1][b] + " --> " + bytesToBigInteger(temp11));
            }
        }

        // 1's compliment | XOR with "FF" * numBytes
        for(int b = 0; b < 2; b++) {
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
}
package org.aion.base.type;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class Hash256Test {

    private final String[] hashHex = {
            null,                                                                   // 0 - Null
            "",                                                                     // 1 - Empty
            "eE55fF66eE55fF66eE55fF66eE55fF66",                                     // 2 - Short
            "aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44",     // 3 - Upper/Lower
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",     // 4 - Negative (-1)
            "0000000000000000000000000000000000000000000000000000000000000000",     // 5 - Zeroes
            "0000000000000000000000000000000000000000000000000000000000000001",     // 6 - Positive (+1)
    };

    private final byte[][] hashByte = {                 // Changes every time
            null,
            ByteUtil.hexStringToBytes(hashHex[1]),
            ByteUtil.hexStringToBytes(hashHex[2]),
            ByteUtil.hexStringToBytes(hashHex[3]),
            ByteUtil.hexStringToBytes(hashHex[4]),
            Hash256.ZERO_HASH().toBytes(),
            ByteUtil.hexStringToBytes(hashHex[6])
    };

    private final ByteArrayWrapper[] hashArray = {      // Same as hashHex
            null,
            new ByteArrayWrapper(new byte[0]),
            new ByteArrayWrapper(hashByte[2]),
            new ByteArrayWrapper(hashByte[3]),
            new ByteArrayWrapper(hashByte[4]),
            new ByteArrayWrapper(new byte[32]),
            new ByteArrayWrapper(hashByte[6])
    };

    /**
     * Test hash wrap function for each input type; String(Hex), Byte, ByteArrayWrapper
     * For each input type:
     * 1. Wrap the input data
     * 2. Convert and Wrap as other input type
     * 3. Assert they are all equal
     */
    @Test
    public void testWrap() {

        Hash256 tempHex;
        Hash256 tempByte;
        Hash256 tempArray;

        System.out.println("\nHex hash test:");
        for(int a = 0; a < hashHex.length; a++) {
            try {
                tempHex = Hash256.wrap(hashHex[a]);
                tempByte = Hash256.wrap(tempHex.toBytes());
                tempArray = Hash256.wrap(tempHex.toByteArrayWrapper());

                assertTrue(tempHex.equals(tempByte));
                assertTrue(tempByte.equals(tempArray));
                assertTrue(tempArray.equals(tempHex));
                assertEquals(tempHex.toString(), hashHex[a].toLowerCase());

                System.out.println("Test " + a + ": Valid " + tempHex.toString());
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + a + ": Invalid");
            }
        }

        System.out.println("\nByte hash test:");
        for(int a = 0; a < hashByte.length; a++) {
            try {
                tempByte = Hash256.wrap(hashByte[a]);
                tempArray = Hash256.wrap(tempByte.toByteArrayWrapper());
                tempHex = Hash256.wrap(tempByte.toString());

                assertTrue(tempByte.equals(tempArray));
                assertTrue(tempArray.equals(tempHex));
                assertTrue(tempHex.equals(tempByte));
                //assertEquals(tempByte.toBytes(), hashByte[a]);

                System.out.println("Test " + a + ": Valid " + tempByte.toBytes());
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + a + ": Invalid");
            }
        }

        System.out.println("\nArray hash test:");
        for(int a = 0; a < hashArray.length; a++) {
            try {
                tempArray = Hash256.wrap(hashArray[a]);
                tempHex = Hash256.wrap(tempArray.toString());
                tempByte = Hash256.wrap(tempArray.toBytes());

                assertTrue(tempArray.equals(tempHex));
                assertTrue(tempHex.equals(tempByte));
                assertTrue(tempByte.equals(tempArray));
                assertEquals(tempArray.toByteArrayWrapper(), hashArray[a]);

                System.out.println("Test " + a + ": Valid " + tempArray.toByteArrayWrapper());
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + a + ": Invalid");
            }
        }
    }

    /**
     * Test hash comparison; A compareTo B
     * For each input type:
     * 1. Wrap the two inputs
     * 2. Assert (-ve: A < B && +ve: A > B)
     * 3. Increment Up/Down
     */
    @Test
    public void testCompare() {

        System.out.println("\nHex hash test:");
        for(int b = 3; b < 6; b++) {
            try {
                int temp = Hash256.wrap(hashHex[b]).compareTo(Hash256.wrap(hashHex[b + 1]));
                boolean same = Hash256.wrap(hashHex[b]).equals(Hash256.wrap(hashHex[b + 1]));
                boolean negative = temp < 0;
                System.out.println("Test " + b + " & " + (b+1) + " >> " + temp);
                assertFalse(same);
                assertTrue(negative);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }
        for(int b = 6; b > 3; b--) {
            try {
                int temp = Hash256.wrap(hashHex[b]).compareTo(Hash256.wrap(hashHex[b - 1]));
                boolean same = Hash256.wrap(hashHex[b]).equals(Hash256.wrap(hashHex[b - 1]));
                boolean positive = temp > 0;
                System.out.println("Test " + b + " & " + (b-1) + " >> " + temp);
                assertFalse(same);
                assertTrue(positive);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }

        System.out.println("\nByte hash test:");
        for(int b = 3; b < 6; b++) {
            try {
                int temp = Hash256.wrap(hashByte[b]).compareTo(Hash256.wrap(hashByte[b + 1]));
                boolean same = Hash256.wrap(hashByte[b]).equals(Hash256.wrap(hashByte[b + 1]));
                boolean negative = temp < 0;
                System.out.println("Test " + b + " & " + (b+1) + " >> " + temp);
                assertFalse(same);
                assertTrue(negative);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }
        for(int b = 6; b > 3; b--) {
            try {
                int temp = Hash256.wrap(hashByte[b]).compareTo(Hash256.wrap(hashByte[b - 1]));
                boolean same = Hash256.wrap(hashByte[b]).equals(Hash256.wrap(hashByte[b - 1]));
                boolean positive = temp > 0;
                System.out.println("Test " + b + " & " + (b-1) + " >> " + temp);
                assertFalse(same);
                assertTrue(positive);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }

        System.out.println("\nArray hash test:");
        for(int b = 3; b < 6; b++) {
            try {
                int temp = Hash256.wrap(hashArray[b]).compareTo(Hash256.wrap(hashArray[b + 1]));
                boolean same = Hash256.wrap(hashArray[b]).equals(Hash256.wrap(hashArray[b + 1]));
                boolean negative = temp < 0;
                System.out.println("Test " + b + " & " + (b+1) + " >> " + temp);
                assertFalse(same);
                assertTrue(negative);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }
        for(int b = 6; b > 3; b--) {
            try {
                int temp = Hash256.wrap(hashArray[b]).compareTo(Hash256.wrap(hashArray[b - 1]));
                boolean same = Hash256.wrap(hashArray[b]).equals(Hash256.wrap(hashArray[b - 1]));
                boolean positive = temp > 0;
                System.out.println("Test " + b + " & " + (b-1) + " >> " + temp);
                assertFalse(same);
                assertTrue(positive);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }
    }
}
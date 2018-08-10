package org.aion.base.type;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class AddressTest {

    private final String[] addrHex = {
            null,                                                                   // 0 - Null
            "",                                                                     // 1 - Empty
            "eE55fF66eE55fF66eE55fF66eE55fF66",                                     // 2 - Short
            "aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44",     // 3 - Upper/Lower
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",     // 4 - Negative (-1)
            "0000000000000000000000000000000000000000000000000000000000000000",     // 5 - Zeroes
            "0000000000000000000000000000000000000000000000000000000000000001",     // 6 - Positive (+1)
    };

    private final byte[][] addrByte = {                 // Changes every time
            null,
            Address.EMPTY_ADDRESS().toBytes(),
            ByteUtil.hexStringToBytes(addrHex[2]),
            ByteUtil.hexStringToBytes(addrHex[3]),
            ByteUtil.hexStringToBytes(addrHex[4]),
            Address.ZERO_ADDRESS().toBytes(),
            ByteUtil.hexStringToBytes(addrHex[6])
    };

    private final ByteArrayWrapper[] addrArray = {      // Same as addrHex
            null,
            new ByteArrayWrapper(new byte[0]),
            new ByteArrayWrapper(addrByte[2]),
            new ByteArrayWrapper(addrByte[3]),
            new ByteArrayWrapper(addrByte[4]),
            new ByteArrayWrapper(new byte[32]),
            new ByteArrayWrapper(addrByte[6])
    };


    /**
     * Test address wrap function for each input type; String(Hex), Byte, ByteArrayWrapper
     * For each input type:
     * 1. Wrap the input data
     * 2. Clone, Convert and Wrap as other input type
     * 3. Assert they are all equal
     */
    @Test
    public void testWrap() {

        Address tempHex;
        Address tempByte;
        Address tempArray;

        System.out.println("\nHex address test:");
        for(int a = 0; a < addrHex.length; a++) {
            try {
                tempHex = Address.wrap(addrHex[a]);
                tempByte = Address.wrap(tempHex.clone().toBytes());
                tempArray = Address.wrap(tempHex.clone().toByteArrayWrapper());

                assertTrue(tempHex.equals(tempByte));
                assertTrue(tempByte.equals(tempArray));
                assertTrue(tempArray.equals(tempHex));
                assertEquals(tempHex.toString(), addrHex[a].toLowerCase());

                System.out.println("Test " + a + ": Valid " + tempHex.toString());
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + a + ": Invalid");
            }
        }

        System.out.println("\nByte address test:");
        for(int a = 0; a < addrByte.length; a++) {
            try {
                tempByte = Address.wrap(addrByte[a]);
                tempArray = Address.wrap(tempByte.clone().toByteArrayWrapper());
                tempHex = Address.wrap(tempByte.clone().toString());

                assertTrue(tempByte.equals(tempArray));
                assertTrue(tempArray.equals(tempHex));
                assertTrue(tempHex.equals(tempByte));
                //assertEquals(tempByte.toBytes(), addrByte[a]);

                System.out.println("Test " + a + ": Valid " + tempByte);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + a + ": Invalid");
            }
        }

        System.out.println("\nArray address test:");
        for(int a = 0; a < addrArray.length; a++) {
            try {
                tempArray = Address.wrap(addrArray[a]);
                tempHex = Address.wrap(tempArray.clone().toString());
                tempByte = Address.wrap(tempArray.clone().toBytes());

                assertTrue(tempArray.equals(tempHex));
                assertTrue(tempHex.equals(tempByte));
                assertTrue(tempByte.equals(tempArray));
                assertEquals(tempArray.toByteArrayWrapper(), addrArray[a]);

                System.out.println("Test " + a + ": Valid " + tempArray.toByteArrayWrapper());
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + a + ": Invalid");
            }
        }
    }

    /**
     * Test address comparison; A compareTo B
     * For each input type:
     * 1. Wrap the two inputs
     * 2. Assert (-ve: A < B && +ve: A > B)
     * 3. Increment Up/Down
     */
    @Test
    public void testCompare() {

        System.out.println("\nHex address test:");
        for(int b = 3; b < 6; b++) {
            try {
                int temp = Address.wrap(addrHex[b]).compareTo(Address.wrap(addrHex[b + 1]));
                boolean same = Address.wrap(addrHex[b]).equals(Address.wrap(addrHex[b + 1]));
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
                int temp = Address.wrap(addrHex[b]).compareTo(Address.wrap(addrHex[b - 1]));
                boolean same = Address.wrap(addrHex[b]).equals(Address.wrap(addrHex[b - 1]));
                boolean positive = temp > 0;
                System.out.println("Test " + b + " & " + (b-1) + " >> " + temp);
                assertFalse(same);
                assertTrue(positive);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }

        System.out.println("\nByte address test:");
        for(int b = 3; b < 6; b++) {
            try {
                int temp = Address.wrap(addrByte[b]).compareTo(addrByte[b + 1]);
                boolean same = Address.wrap(addrByte[b]).equals(Address.wrap(addrByte[b + 1]));
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
                int temp = Address.wrap(addrByte[b]).compareTo(addrByte[b - 1]);
                boolean same = Address.wrap(addrByte[b]).equals(Address.wrap(addrByte[b - 1]));
                boolean positive = temp > 0;
                System.out.println("Test " + b + " & " + (b-1) + " >> " + temp);
                assertFalse(same);
                assertTrue(positive);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }

        System.out.println("\nArray address test:");
        for(int b = 3; b < 6; b++) {
            try {
                int temp = Address.wrap(addrArray[b]).compareTo(Address.wrap(addrArray[b + 1]));
                boolean same = Address.wrap(addrArray[b]).equals(Address.wrap(addrArray[b + 1]));
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
                int temp = Address.wrap(addrArray[b]).compareTo(Address.wrap(addrArray[b - 1]));
                boolean same = Address.wrap(addrArray[b]).equals(Address.wrap(addrArray[b - 1]));
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
/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.base.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.junit.Test;

public class AionAddressTest {

    private final String[] addrHex = {
        null, // 0 - Null
        "", // 1 - Empty
        "eE55fF66eE55fF66eE55fF66eE55fF66", // 2 - Short
        "aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44", // 3 - Upper/Lower
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", // 4 - Negative (-1)
        "0000000000000000000000000000000000000000000000000000000000000000", // 5 - Zeroes
        "0000000000000000000000000000000000000000000000000000000000000001", // 6 - Positive (+1)
    };

    private final byte[][] addrByte = { // Changes every time
        null,
        AionAddress.EMPTY_ADDRESS().toBytes(),
        ByteUtil.hexStringToBytes(addrHex[2]),
        ByteUtil.hexStringToBytes(addrHex[3]),
        ByteUtil.hexStringToBytes(addrHex[4]),
        AionAddress.ZERO_ADDRESS().toBytes(),
        ByteUtil.hexStringToBytes(addrHex[6])
    };

    private final ByteArrayWrapper[] addrArray = { // Same as addrHex
        null,
        new ByteArrayWrapper(new byte[0]),
        new ByteArrayWrapper(addrByte[2]),
        new ByteArrayWrapper(addrByte[3]),
        new ByteArrayWrapper(addrByte[4]),
        new ByteArrayWrapper(new byte[32]),
        new ByteArrayWrapper(addrByte[6])
    };

    /**
     * Test address wrap function for each input type; String(Hex), Byte, ByteArrayWrapper For each
     * input type: 1. Wrap the input data 2. Clone, Convert and Wrap as other input type 3. Assert
     * they are all equal
     */
    @Test
    public void testWrap() {

        AionAddress tempHex;
        AionAddress tempByte;
        AionAddress tempArray;

        System.out.println("\nHex address test:");
        for (int a = 0; a < addrHex.length; a++) {
            try {
                tempHex = AionAddress.wrap(addrHex[a]);
                tempByte = AionAddress.wrap(tempHex.clone().toBytes());
                tempArray = AionAddress.wrap(tempHex.clone().toByteArrayWrapper());

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
        for (int a = 0; a < addrByte.length; a++) {
            try {
                tempByte = AionAddress.wrap(addrByte[a]);
                tempArray = AionAddress.wrap(tempByte.clone().toByteArrayWrapper());
                tempHex = AionAddress.wrap(tempByte.clone().toString());

                assertTrue(tempByte.equals(tempArray));
                assertTrue(tempArray.equals(tempHex));
                assertTrue(tempHex.equals(tempByte));
                // assertEquals(tempByte.toBytes(), addrByte[a]);

                System.out.println("Test " + a + ": Valid " + tempByte);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + a + ": Invalid");
            }
        }

        System.out.println("\nArray address test:");
        for (int a = 0; a < addrArray.length; a++) {
            try {
                tempArray = AionAddress.wrap(addrArray[a]);
                tempHex = AionAddress.wrap(tempArray.clone().toString());
                tempByte = AionAddress.wrap(tempArray.clone().toBytes());

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
     * Test address comparison; A compareTo B For each input type: 1. Wrap the two inputs 2. Assert
     * (-ve: A < B && +ve: A > B) 3. Increment Up/Down
     */
    @Test
    public void testCompare() {

        System.out.println("\nHex address test:");
        for (int b = 3; b < 6; b++) {
            try {
                int temp = AionAddress.wrap(addrHex[b]).compareTo(AionAddress.wrap(addrHex[b + 1]));
                boolean same = AionAddress.wrap(addrHex[b]).equals(AionAddress.wrap(addrHex[b + 1]));
                boolean negative = temp < 0;
                System.out.println("Test " + b + " & " + (b + 1) + " >> " + temp);
                assertFalse(same);
                assertTrue(negative);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }
        for (int b = 6; b > 3; b--) {
            try {
                int temp = AionAddress.wrap(addrHex[b]).compareTo(AionAddress.wrap(addrHex[b - 1]));
                boolean same = AionAddress.wrap(addrHex[b]).equals(AionAddress.wrap(addrHex[b - 1]));
                boolean positive = temp > 0;
                System.out.println("Test " + b + " & " + (b - 1) + " >> " + temp);
                assertFalse(same);
                assertTrue(positive);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }

        System.out.println("\nByte address test:");
        for (int b = 3; b < 6; b++) {
            try {
                int temp = AionAddress.wrap(addrByte[b]).compareTo(addrByte[b + 1]);
                boolean same = AionAddress.wrap(addrByte[b]).equals(AionAddress.wrap(addrByte[b + 1]));
                boolean negative = temp < 0;
                System.out.println("Test " + b + " & " + (b + 1) + " >> " + temp);
                assertFalse(same);
                assertTrue(negative);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }
        for (int b = 6; b > 3; b--) {
            try {
                int temp = AionAddress.wrap(addrByte[b]).compareTo(addrByte[b - 1]);
                boolean same = AionAddress.wrap(addrByte[b]).equals(AionAddress.wrap(addrByte[b - 1]));
                boolean positive = temp > 0;
                System.out.println("Test " + b + " & " + (b - 1) + " >> " + temp);
                assertFalse(same);
                assertTrue(positive);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }

        System.out.println("\nArray address test:");
        for (int b = 3; b < 6; b++) {
            try {
                int temp = AionAddress.wrap(addrArray[b]).compareTo(AionAddress.wrap(addrArray[b + 1]));
                boolean same = AionAddress.wrap(addrArray[b]).equals(AionAddress.wrap(addrArray[b + 1]));
                boolean negative = temp < 0;
                System.out.println("Test " + b + " & " + (b + 1) + " >> " + temp);
                assertFalse(same);
                assertTrue(negative);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }
        for (int b = 6; b > 3; b--) {
            try {
                int temp = AionAddress.wrap(addrArray[b]).compareTo(AionAddress.wrap(addrArray[b - 1]));
                boolean same = AionAddress.wrap(addrArray[b]).equals(AionAddress.wrap(addrArray[b - 1]));
                boolean positive = temp > 0;
                System.out.println("Test " + b + " & " + (b - 1) + " >> " + temp);
                assertFalse(same);
                assertTrue(positive);
            } catch (IllegalArgumentException e) {
                System.out.println("Test " + b + ": Input Invalid");
            }
        }
    }
}

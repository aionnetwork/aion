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

package org.aion.precompiled.contracts.ATB;

import java.math.BigInteger;
import org.junit.Test;

public class BridgeDeserializerTest {

    /**
     * Tries to trigger an out of bounds exception on the first occurrence of parseMeta using some
     * integer overflow.
     *
     * <p>No assertions -- we are testing whether or not an exception gets thrown.
     */
    @Test
    public void testParseAddressListIntegerOverflow1() {
        byte[] array = new byte[36];
        byte[] maxInt = toBytes(Integer.MAX_VALUE, 16);
        System.arraycopy(maxInt, 0, array, 4, 16);
        BridgeDeserializer.parseAddressList(array);
    }

    /**
     * Tries to trigger an out of bounds exception on the second occurrence of parseMeta using some
     * trickier integer overflowing.
     *
     * <p>No assertions -- we are testing whether or not an exception gets thrown.
     */
    @Test
    public void testParseAddressListIntegerOverflow2() {
        byte[] array = new byte[36];
        byte[] almostMaxInt = toBytes(Integer.MAX_VALUE - 4, 16);
        System.arraycopy(almostMaxInt, 0, array, 4, 16);
        BridgeDeserializer.parseAddressList(array);
    }

    /**
     * Since the logic gives us the invariant: end <= call.length and we access i + elementLength
     * inside a loop that loops until end-1, this test gets some numbers aligned so that end ==
     * call.length, the best place we can trigger an out of bounds.
     *
     * <p>No assertions -- we are testing whether or not an exception gets thrown.
     */
    @Test
    public void testParseAddressListIntegerOverflow3() {
        byte[] array = new byte[16_425];
        byte[] listOffset = toBytes(21, 16);
        System.arraycopy(listOffset, 0, array, 4, 16);
        byte[] listLength = toBytes(512, 16);
        System.arraycopy(listLength, 0, array, 25, 16);
        BridgeDeserializer.parseAddressList(array);
    }

    /** Returns byte array length numBytes of integer, truncating if need be. */
    private static byte[] toBytes(int integer, int numBytes) {
        byte[] alignedBytes = new byte[numBytes];
        byte[] unalignedBytes = BigInteger.valueOf(integer).toByteArray();
        int len = unalignedBytes.length;
        System.arraycopy(unalignedBytes, 0, alignedBytes, numBytes - len, len);
        return alignedBytes;
    }
}

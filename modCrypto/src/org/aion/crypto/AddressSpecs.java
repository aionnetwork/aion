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

package org.aion.crypto;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Optional;
import org.aion.util.bytes.ByteUtil;

/** A set of static functions to define the creation of addresses */
public class AddressSpecs {

    public static final byte A0_IDENTIFIER = ByteUtil.hexStringToBytes("0xA0")[0];

    private AddressSpecs() {}

    /**
     * Returns an address of with identifier A0, given the public key of the account (this is
     * currently our only account type)
     */
    public static byte[] computeA0Address(byte[] publicKey) {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.put(A0_IDENTIFIER);
        // [1:]
        buf.put(HashUtil.h256(publicKey), 1, 31);
        return buf.array();
    }

    public static Optional<String> checksummedAddress(String address) {
        if (address == null) return Optional.empty();
        address = address.replaceFirst("^0x", "");
        if (address.length() != 64) return Optional.empty();

        byte[] h;
        try {
            h = HashUtil.h256(ByteUtil.hexStringToBytes(address));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
        if (h == null) return Optional.empty(); // address is invalid
        BitSet b = BitSet.valueOf(h);
        char[] caddr = address.toCharArray();
        for (int i = 0; i < 64; i++) {
            if (Character.isDigit(caddr[i])) continue;

            if (Character.isAlphabetic(caddr[i])) {
                caddr[i] =
                        b.get(i)
                                ? Character.toUpperCase(caddr[i])
                                : Character.toLowerCase(caddr[i]);
                continue;
            }
        }
        return Optional.of(String.valueOf(caddr));
    }
}

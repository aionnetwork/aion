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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */
package org.aion.rlp;

import java.math.BigInteger;

public class Utils {

    static final byte[] encodingTable = {
        (byte) '0',
        (byte) '1',
        (byte) '2',
        (byte) '3',
        (byte) '4',
        (byte) '5',
        (byte) '6',
        (byte) '7',
        (byte) '8',
        (byte) '9',
        (byte) 'a',
        (byte) 'b',
        (byte) 'c',
        (byte) 'd',
        (byte) 'e',
        (byte) 'f'
    };

    static byte[] concatenate(byte[] a, byte[] b) {
        byte[] ret = new byte[a.length + b.length];
        System.arraycopy(a, 0, ret, 0, a.length);
        System.arraycopy(b, 0, ret, a.length, b.length);
        return ret;
    }

    static byte[] asUnsignedByteArray(BigInteger bi) {
        byte[] bytes = bi.toByteArray();

        if (bytes[0] == 0) {
            byte[] tmp = new byte[bytes.length - 1];

            System.arraycopy(bytes, 1, tmp, 0, tmp.length);

            return tmp;
        }

        return bytes;
    }

    static byte[] hexEncode(byte[] in) {
        return hexEncode(in, false);
    }

    static byte[] hexEncode(byte[] in, boolean withTerminatorByte) {
        if (in == null) {
            return null;
        }
        byte[] ret = new byte[in.length * 2 + (withTerminatorByte ? 1 : 0)];

        for (int i = 0; i < in.length; i++) {
            int v = in[i] & 0xff;
            ret[i * 2] = encodingTable[v >>> 4];
            ret[i * 2 + 1] = encodingTable[v & 0xf];
        }

        return ret;
    }
}

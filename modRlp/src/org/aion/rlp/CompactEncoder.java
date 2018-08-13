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

import static java.util.Arrays.copyOf;
import static java.util.Arrays.copyOfRange;
import static org.aion.base.util.ByteUtil.appendByte;
import static org.aion.rlp.Utils.hexEncode;

import java.util.HashMap;
import java.util.Map;

/**
 * Compact encoding of hex sequence with optional terminator
 *
 * <p>The traditional compact way of encoding a hex string is to convert it into binary - that is, a
 * string like 0f1248 would become three bytes 15, 18, 72. However, this approach has one slight
 * problem: what if the length of the hex string is odd? In that case, there is no way to
 * distinguish between, say, 0f1248 and f1248.
 *
 * <p>Additionally, our application in the Merkle Patricia tree requires the additional feature that
 * a hex string can also have a special "terminator symbol" at the end (denoted by the 'T'). A
 * terminator symbol can occur only once, and only at the end.
 *
 * <p>An alternative way of thinking about this to not think of there being a terminator symbol, but
 * instead treat bit specifying the existence of the terminator symbol as a bit specifying that the
 * given node encodes a final node, where the value is an actual value, rather than the hash of yet
 * another node.
 *
 * <p>To solve both of these issues, we force the first nibble of the final byte-stream to encode
 * two flags, specifying oddness of length (ignoring the 'T' symbol) and terminator status; these
 * are placed, respectively, into the two lowest significant bits of the first nibble. In the case
 * of an even-length hex string, we must introduce a second nibble (of value zero), in addition to
 * the encoded flags added as a first nibble, to ensure the resulting hex-string is still even in
 * length and thus is representable by a whole number of bytes.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>[ 1, 2, 3, 4, 5 ] '\x11\x23\x45'
 *   <li>[ 0, 1, 2, 3, 4, 5 ] '\x00\x01\x23\x45'
 *   <li>[ 0, 15, 1, 12, 11, 8, T ] '\x20\x0f\x1c\xb8'
 *   <li>[ 15, 1, 12, 11, 8, T ] '\x3f\x1c\xb8'
 * </ul>
 */
public class CompactEncoder {

    private static final byte TERMINATOR = 16;
    private static final Map<Character, Byte> hexMap = new HashMap<>();

    static {
        hexMap.put('0', (byte) 0x0);
        hexMap.put('1', (byte) 0x1);
        hexMap.put('2', (byte) 0x2);
        hexMap.put('3', (byte) 0x3);
        hexMap.put('4', (byte) 0x4);
        hexMap.put('5', (byte) 0x5);
        hexMap.put('6', (byte) 0x6);
        hexMap.put('7', (byte) 0x7);
        hexMap.put('8', (byte) 0x8);
        hexMap.put('9', (byte) 0x9);
        hexMap.put('a', (byte) 0xa);
        hexMap.put('b', (byte) 0xb);
        hexMap.put('c', (byte) 0xc);
        hexMap.put('d', (byte) 0xd);
        hexMap.put('e', (byte) 0xe);
        hexMap.put('f', (byte) 0xf);
    }

    /**
     * Pack nibbles to binary
     *
     * @param nibbles sequence. may have a terminator
     * @return hex-encoded byte array
     * @throws NullPointerException when given a null input
     */
    public static byte[] packNibbles(byte[] nibbles) {
        int terminator = 0;

        if (nibbles.length > 0 && nibbles[nibbles.length - 1] == TERMINATOR) {
            terminator = 1;
        }
        int oddlen = (nibbles.length - terminator) % 2;
        int flag = 2 * terminator + oddlen;

        int len = terminator == 0 ? nibbles.length : nibbles.length - 1;
        int start;
        byte[] output = new byte[len / 2 + 1];

        if (oddlen != 0) {
            output[0] = (byte) (16 * flag + nibbles[0]);
            start = 1;
        } else {
            output[0] = (byte) (16 * flag);
            start = 0;
        }

        for (int i = start, index = 1; i < len; i += 2, index++) {
            output[index] = (byte) (16 * nibbles[i] + nibbles[i + 1]);
        }

        return output;
    }

    /** @throws NullPointerException when given a null input */
    public static boolean hasTerminator(byte[] packedKey) {
        return ((packedKey[0] >> 4) & 2) != 0;
    }

    /**
     * Unpack a binary string to its nibbles equivalent
     *
     * @param str of binary data
     * @return array of nibbles in byte-format
     */
    public static byte[] unpackToNibbles(byte[] str) {
        byte[] base = binToNibbles(str);
        base = copyOf(base, base.length - 1);
        if (base[0] >= 2) {
            base = appendByte(base, TERMINATOR);
        }
        if (base[0] % 2 == 1) {
            base = copyOfRange(base, 1, base.length);
        } else {
            base = copyOfRange(base, 2, base.length);
        }
        return base;
    }

    /**
     * Transforms a binary array to hexadecimal format + terminator
     *
     * @param str byte[]
     * @return array with each individual nibble adding a terminator at the end
     */
    public static byte[] binToNibbles(byte[] str) {
        byte[] hexEncodedTerminated = hexEncode(str, true);

        update(hexEncodedTerminated, hexEncodedTerminated.length - 1);
        hexEncodedTerminated[hexEncodedTerminated.length - 1] = TERMINATOR;

        return hexEncodedTerminated;
    }

    public static byte[] binToNibblesNoTerminator(byte[] str) {
        byte[] hexEncoded = hexEncode(str);

        update(hexEncoded, hexEncoded.length);

        return hexEncoded;
    }

    private static void update(byte[] hexEncoded, int length) {
        for (int i = 0; i < length; ++i) {
            byte b = hexEncoded[i];
            hexEncoded[i] = hexMap.get((char) b);
        }
    }
}

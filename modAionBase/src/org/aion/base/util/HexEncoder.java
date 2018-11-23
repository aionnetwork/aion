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

package org.aion.base.util;

import java.io.IOException;
import java.io.OutputStream;

/** A streaming Hex encoder. */
public class HexEncoder {

    protected final byte[] encodingTable = {
        (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6',
                (byte) '7',
        (byte) '8', (byte) '9', (byte) 'a', (byte) 'b', (byte) 'c', (byte) 'd', (byte) 'e',
                (byte) 'f'
    };

    /*
     * set up the decoding table.
     */
    protected final byte[] decodingTable = new byte[128];

    protected void initialiseDecodingTable() {
        for (int i = 0; i < decodingTable.length; i++) {
            decodingTable[i] = (byte) 0xff;
        }

        for (int i = 0; i < encodingTable.length; i++) {
            decodingTable[encodingTable[i]] = (byte) i;
        }

        decodingTable['A'] = decodingTable['a'];
        decodingTable['B'] = decodingTable['b'];
        decodingTable['C'] = decodingTable['c'];
        decodingTable['D'] = decodingTable['d'];
        decodingTable['E'] = decodingTable['e'];
        decodingTable['F'] = decodingTable['f'];
    }

    public HexEncoder() {
        initialiseDecodingTable();
    }

    /**
     * encode the input data producing a Hex output stream.
     *
     * @return the number of bytes produced.
     */
    public int encode(byte[] data, int off, int length, OutputStream out) {
        for (int i = off; i < (off + length); i++) {
            int v = data[i] & 0xff;
            try {
                out.write(encodingTable[(v >>> 4)]);
                out.write(encodingTable[v & 0xf]);
            } catch (Exception e) {
            }
        }

        return length * 2;
    }

    private static boolean ignore(char c) {
        return c == '\n' || c == '\r' || c == '\t' || c == ' ';
    }

    /**
     * decode the Hex encoded byte data writing it to the given output stream, whitespace characters
     * will be ignored.
     *
     * @return the number of bytes produced.
     */
    public int decode(byte[] data, int off, int length, OutputStream out) throws IOException {
        byte b1, b2;
        int outLen = 0;

        int end = off + length;

        while (end > off) {
            if (!ignore((char) data[end - 1])) {
                break;
            }

            end--;
        }

        int i = off;
        while (i < end) {
            while (i < end && ignore((char) data[i])) {
                i++;
            }

            b1 = decodingTable[data[i++]];

            while (i < end && ignore((char) data[i])) {
                i++;
            }

            b2 = decodingTable[data[i++]];

            if ((b1 | b2) < 0) {
                throw new IOException("invalid characters encountered in Hex data");
            }

            try {
                out.write((b1 << 4) | b2);
            } catch (Exception e) {

            }

            outLen++;
        }

        return outLen;
    }

    /**
     * decode the Hex encoded String data writing it to the given output stream, whitespace
     * characters will be ignored.
     *
     * @return the number of bytes produced.
     */
    public int decode(String data, OutputStream out) throws IOException {
        byte b1, b2;
        int length = 0;

        int end = data.length();

        while (end > 0) {
            if (!ignore(data.charAt(end - 1))) {
                break;
            }

            end--;
        }

        int i = 0;
        while (i < end) {
            while (i < end && ignore(data.charAt(i))) {
                i++;
            }

            b1 = decodingTable[data.charAt(i++)];

            while (i < end && ignore(data.charAt(i))) {
                i++;
            }

            b2 = decodingTable[data.charAt(i++)];

            if ((b1 | b2) < 0) {
                throw new IOException("invalid characters encountered in Hex string");
            }

            out.write((b1 << 4) | b2);

            length++;
        }

        return length;
    }
}

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Utility class for converting hex data to bytes and back again. */
public class Hex {

    private static final HexEncoder encoder = new HexEncoder();

    public static String toHexString(byte[] data) {
        return toHexString(data, 0, data.length);
    }

    public static String toHexString(byte[] data, int off, int length) {
        byte[] encoded = encode(data, off, length);
        return new String(encoded);
    }

    /**
     * encode the input data producing a Hex encoded byte array.
     *
     * @return a byte array containing the Hex encoded data.
     */
    public static byte[] encode(byte[] data) {
        return encode(data, 0, data.length);
    }

    /**
     * encode the input data producing a Hex encoded byte array.
     *
     * @return a byte array containing the Hex encoded data.
     */
    public static byte[] encode(byte[] data, int off, int length) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        encoder.encode(data, off, length, bOut);

        return bOut.toByteArray();
    }

    /**
     * Hex encode the byte data writing it to the given output stream.
     *
     * @return the number of bytes produced.
     */
    public static int encode(byte[] data, OutputStream out) throws IOException {
        return encoder.encode(data, 0, data.length, out);
    }

    /**
     * Hex encode the byte data writing it to the given output stream.
     *
     * @return the number of bytes produced.
     */
    public static int encode(byte[] data, int off, int length, OutputStream out)
            throws IOException {
        return encoder.encode(data, off, length, out);
    }

    /**
     * decode the Hex encoded input data. It is assumed the input data is valid.
     *
     * @return a byte array representing the decoded data.
     */
    public static byte[] decode(byte[] data) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        try {
            encoder.decode(data, 0, data.length, bOut);
        } catch (IOException e) {
            System.err.println("Hex decode failed! " + data);
            return null;
        }

        return bOut.toByteArray();
    }

    /**
     * decode the Hex encoded String data - whitespace will be ignored.
     *
     * @return a byte array representing the decoded data.
     */
    public static byte[] decode(String data) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        try {
            encoder.decode(data, bOut);
        } catch (IOException e) {
            System.err.println("Hex decode failed! " + data);
            return null;
        }
        return bOut.toByteArray();
    }

    /**
     * decode the Hex encoded String data writing it to the given output stream, whitespace
     * characters will be ignored.
     *
     * @return the number of bytes produced.
     */
    public static int decode(String data, OutputStream out) throws IOException {
        return encoder.decode(data, out);
    }
}

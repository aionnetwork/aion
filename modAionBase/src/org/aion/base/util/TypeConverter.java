/*******************************************************************************
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
 ******************************************************************************/
package org.aion.base.util;

import java.math.BigInteger;

public class TypeConverter {

//    public static byte[] StringNumberAsBytes(String input) {
//        return ByteUtil.bigIntegerToBytes(StringDecimalToBigInteger(input));
//    }

    public static BigInteger StringNumberAsBigInt(String input) {
        if (input.startsWith("0x")) {
            return TypeConverter.StringHexToBigInteger(input);
        } else {
            return TypeConverter.StringDecimalToBigInteger(input);
        }
    }

    public static BigInteger StringHexToBigInteger(String input) {
        String hexa = input.startsWith("0x") ? input.substring(2) : input;
        return new BigInteger(hexa, 16);
    }

    private static BigInteger StringDecimalToBigInteger(String input) {
        return new BigInteger(input);
    }

    public static byte[] StringHexToByteArray(String x) {
        if (x.startsWith("0x")) {
            x = x.substring(2);
        }
        if (x.length() % 2 != 0) {
            x = "0" + x;
        }
        return Hex.decode(x);
    }

    public static String toJsonHex(byte[] x) {
        return "0x" + Hex.toHexString(x);
    }

    public static String toJsonHex(String x) {
        return x.startsWith("0x") ? x : "0x" + x;
    }

    public static String toJsonHex(long n) {
        return "0x" + Long.toHexString(n);
    }

    public static String toJsonHex(BigInteger n) {
        return "0x" + n.toString(16);
    }
}

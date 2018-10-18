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
 *     H2 Group.
 */
package org.aion.db.utils.slices;

import java.nio.ByteBuffer;

public final class VariableLengthQuantity {

    private VariableLengthQuantity() {
    }

    public static int variableLengthSize(int value) {
        int size = 1;
        while ((value & (~0x7f)) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    public static int variableLengthSize(long value) {
        int size = 1;
        while ((value & (~0x7f)) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    public static void writeVariableLengthInt(int value, SliceOutput sliceOutput) {
        int highBitMask = 0x80;
        if (value < (1 << 7) && value >= 0) {
            sliceOutput.writeByte(value);
        } else if (value < (1 << 14) && value > 0) {
            sliceOutput.writeByte(value | highBitMask);
            sliceOutput.writeByte(value >>> 7);
        } else if (value < (1 << 21) && value > 0) {
            sliceOutput.writeByte(value | highBitMask);
            sliceOutput.writeByte((value >>> 7) | highBitMask);
            sliceOutput.writeByte(value >>> 14);
        } else if (value < (1 << 28) && value > 0) {
            sliceOutput.writeByte(value | highBitMask);
            sliceOutput.writeByte((value >>> 7) | highBitMask);
            sliceOutput.writeByte((value >>> 14) | highBitMask);
            sliceOutput.writeByte(value >>> 21);
        } else {
            sliceOutput.writeByte(value | highBitMask);
            sliceOutput.writeByte((value >>> 7) | highBitMask);
            sliceOutput.writeByte((value >>> 14) | highBitMask);
            sliceOutput.writeByte((value >>> 21) | highBitMask);
            sliceOutput.writeByte(value >>> 28);
        }
    }

    public static void writeVariableLengthLong(long value, SliceOutput sliceOutput) {
        // while value more than the first 7 bits set
        while ((value & (~0x7f)) != 0) {
            sliceOutput.writeByte((int) ((value & 0x7f) | 0x80));
            value >>>= 7;
        }
        sliceOutput.writeByte((int) value);
    }

    public static int readVariableLengthInt(SliceInput sliceInput) {
        int result = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int b = sliceInput.readUnsignedByte();

            // add the lower 7 bits to the result
            result |= ((b & 0x7f) << shift);

            // if high bit is not set, this is the last byte in the number
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new NumberFormatException("last byte of variable length int has high bit set");
    }

    public static int readVariableLengthInt(ByteBuffer sliceInput) {
        int result = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int b = sliceInput.get();

            // add the lower 7 bits to the result
            result |= ((b & 0x7f) << shift);

            // if high bit is not set, this is the last byte in the number
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new NumberFormatException("last byte of variable length int has high bit set");
    }

    public static long readVariableLengthLong(SliceInput sliceInput) {
        long result = 0;
        for (int shift = 0; shift <= 63; shift += 7) {
            long b = sliceInput.readUnsignedByte();

            // add the lower 7 bits to the result
            result |= ((b & 0x7f) << shift);

            // if high bit is not set, this is the last byte in the number
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new NumberFormatException("last byte of variable length int has high bit set");
    }
}

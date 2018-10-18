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

import java.util.Arrays;

public final class FastByteComparisons {

    /**
     * Check if two byte arrays are equal.
     */
    public static boolean equal(byte[] array1, byte[] array2) {
        return Arrays.equals(array1, array2);
    }

    /**
     * Compares two byte arrays.
     */
    public static int compareTo(byte[] array1, byte[] array2) {
        return Arrays.compare(array1, array2);
    }

    /**
     * Compares two regions of byte array.
     */
    public static int compareTo(byte[] array1, int offset1, int size1, byte[] array2, int offset2,
        int size2) {
        byte[] b1 = Arrays.copyOfRange(array1, offset1, offset1 + size1);
        byte[] b2 = Arrays.copyOfRange(array2, offset2, offset2 + size2);

        return Arrays.compare(b1, b2);
    }
}

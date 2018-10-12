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

package org.aion.precompiled;

import javax.annotation.Nonnull;

public class PrecompiledUtilities {

    /**
     * Returns input as a byte array of length length, padding with zero bytes as needed to achieve
     * the desired length. Returns null if input.length is larger than the specified length to pad
     * to.
     *
     * @param input The input array to pad.
     * @param length The length of the newly padded array.
     * @return input zero-padded to desired length or null if input.length > length.
     */
    public static byte[] pad(@Nonnull final byte[] input, final int length) {
        if (input.length > length) {
            return null;
        }

        if (input.length == length) {
            return input;
        }

        byte[] out = new byte[length];
        System.arraycopy(input, 0, out, out.length - input.length, input.length);
        return out;
    }
}

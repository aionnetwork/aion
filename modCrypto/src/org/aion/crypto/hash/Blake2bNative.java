/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>Contributors: Aion foundation.
 *
 * <p>****************************************************************************
 */
package org.aion.crypto.hash;

public class Blake2bNative {

    public static native byte[] blake256(byte[] in);

    /*
    Generate hashes to validate an Equihash solution
     */
    public static native byte[][] genSolutionHash(
            byte[] personalization, byte[] nonce, int[] indices, byte[] header);

    public static byte[] blake256(byte[] in1, byte[] in2) {
        byte[] arr = new byte[in1.length + in2.length];
        System.arraycopy(in1, 0, arr, 0, in1.length);
        System.arraycopy(in2, 0, arr, in1.length, in2.length);

        return blake256(arr);
    }

    public static byte[][] getSolutionHash(
            byte[] personalization, byte[] nonce, int[] indices, byte[] header) {
        return genSolutionHash(personalization, nonce, indices, header);
    }
}

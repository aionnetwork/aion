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

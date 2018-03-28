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
package org.aion.crypto;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author jin
 */
public class HashBench {

    @Test
    public void bench() {

        final byte[] input = HashUtil.h256("test".getBytes());
        final int COUNT = 1000;

        byte[] outputJ = new byte[32];
        byte[] outputN = new byte[32];

        // warm up
        for (int i = 0; i < COUNT; i++) {
            HashUtil.blake256(input);
            HashUtil.blake256Native(input);
        }

        // blake2b
        long ts = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            outputJ = HashUtil.blake256(input);
        }
        long te = System.nanoTime();
        System.out.println(" Blake2b       : " + (te - ts) / COUNT + " ns / call");

        // blake2b native
        ts = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            outputN = HashUtil.blake256Native(input);
        }
        te = System.nanoTime();
        System.out.println(" Blake2b native: " + (te - ts) / COUNT + " ns / call");

        assertArrayEquals(outputJ, outputN);
    }
}
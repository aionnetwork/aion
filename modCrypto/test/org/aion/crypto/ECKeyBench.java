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
package org.aion.crypto;

import org.aion.crypto.ecdsa.ECKeySecp256k1;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.junit.Test;

/** @author jin */
public class ECKeyBench {

    @Test
    public void bench() {

        final ECKey key1 = new ECKeySecp256k1();
        final ECKey key2 = new ECKeyEd25519();

        final byte[] input = HashUtil.h256("test".getBytes());
        final int COUNT = 1000;

        // warm up
        for (int i = 0; i < COUNT; i++) {
            key1.sign(input);
            key2.sign(input);
        }

        // ECDSA
        long ts = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            key1.sign(input);
        }
        long te = System.nanoTime();
        System.out.println(" ECDSA   sign: " + (te - ts) / COUNT + " ns / call");

        // ED25519
        ts = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            key2.sign(input);
        }
        te = System.nanoTime();
        System.out.println(" Ed25519 sign: " + (te - ts) / COUNT + " ns / call");
    }
}

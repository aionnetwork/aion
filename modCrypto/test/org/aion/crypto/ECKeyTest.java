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

import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class ECKeyTest {

    @Test
    public void testSecp256k1() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.SECP256K1);

        ECKey key = ECKeyFac.inst().create();
        ECKey key2 = ECKeyFac.inst().fromPrivate(key.getPrivKeyBytes());

        System.out.println(key);
        System.out.println(key2);

        assertArrayEquals(key.getAddress(), key2.getAddress());
        assertArrayEquals(key.getPrivKeyBytes(), key2.getPrivKeyBytes());
    }

    @Test
    public void testED25519() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);

        ECKey key = ECKeyFac.inst().create();
        ECKey key2 = ECKeyFac.inst().fromPrivate(key.getPrivKeyBytes());

        System.out.println(key);
        System.out.println(key2);

        assertArrayEquals(key.getAddress(), key2.getAddress());
        assertArrayEquals(key.getPrivKeyBytes(), key2.getPrivKeyBytes());
    }

    @AfterClass
    public static void teardown() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
    }
}

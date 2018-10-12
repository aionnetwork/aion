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

import static org.junit.Assert.assertEquals;

import org.aion.base.util.Hex;
import org.junit.Test;

public class HashTest {

    @Test
    public void testSha256() {
        String expected = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

        String hash = Hex.toHexString(HashUtil.sha256("test".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);
    }

    @Test
    public void testKeccak256() {
        String expected = "9c22ff5f21f0b81b113e63f7db6da94fedef11b2119b4088b89664fb9a3cb658";

        String hash = Hex.toHexString(HashUtil.keccak256("test".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);

        hash = Hex.toHexString(HashUtil.keccak256("te".getBytes(), "st".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);
    }

    @Test
    public void testBlake256() {
        String expected = "928b20366943e2afd11ebc0eae2e53a93bf177a4fcf35bcc64d503704e65e202";

        String hash = Hex.toHexString(HashUtil.blake256("test".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);

        hash = Hex.toHexString(HashUtil.blake256("te".getBytes(), "st".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);
    }

    @Test
    public void testBlake256Native() {
        String expected = "928b20366943e2afd11ebc0eae2e53a93bf177a4fcf35bcc64d503704e65e202";

        String hash = Hex.toHexString(HashUtil.blake256Native("test".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);

        hash = Hex.toHexString(HashUtil.blake256Native("te".getBytes(), "st".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);
    }
}

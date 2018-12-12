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

package org.aion.precompiled.contracts;

import static junit.framework.TestCase.assertEquals;

import java.nio.charset.StandardCharsets;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.junit.Before;
import org.junit.Test;

public class KeccakHashTest {
    private static final long INPUT_NRG = 1000;

    private byte[] byteArray1 = "a0010101010101010101010101".getBytes();
    private byte[] shortByteArray = "".getBytes();
    private KeccakHash keccakHasher;

    @Before
    public void setUp() {
        keccakHasher = new KeccakHash();
    }

    @Test
    public void testKeccak256() {
        PrecompiledTransactionResult res = keccakHasher.execute(byteArray1, INPUT_NRG);
        byte[] output = res.getOutput();

        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);

        System.out.println(
                "The keccak256 hash for '"
                        + new String(byteArray1, StandardCharsets.UTF_8)
                        + "' is:");
        System.out.print("      ");
        for (byte b : output) {
            System.out.print(b + " ");
        }
        System.out.println();
    }

    @Test
    public void invalidInputLength() {
        PrecompiledTransactionResult res2 = keccakHasher.execute(shortByteArray, INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, res2.getResultCode());
    }

    @Test
    public void insufficientNRG() {
        PrecompiledTransactionResult res2 = keccakHasher.execute(byteArray1, 30);
        assertEquals(PrecompiledResultCode.OUT_OF_NRG, res2.getResultCode());
    }
}

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
package org.aion;

import org.aion.equihash.EquiUtils;
import org.aion.equihash.EquiValidator;
import org.aion.equihash.Equihash;
//import org.aion.equihash.SimpleEquiValidator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EquihashValidatorTest {

    @Test
    public void validate(){
        EquiValidator validate = new EquiValidator(210,9);
        //SimpleEquiValidator simple = SimpleEquiValidator.INSTANCE;

        byte[] header = {
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0
        };

        byte[] nonce = {
                1, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
        };

        Equihash e = new Equihash(210,9);

        int[][] sol = e.getSolutionsForNonce(header, nonce);

        //Intentionally break;
       // sol[1][0] += 1;

        byte[] minimal = EquiUtils.getMinimalFromIndices(sol[1], 21);

        long start = System.nanoTime();
        boolean isValid = validate.isValidSolution(minimal, header, nonce);
        long stop = System.nanoTime();
        System.out.println("isValid: " + isValid);
        assertEquals(true, isValid);
        System.out.println("Execution: " + (stop-start));

        //To be added for optimized validator comparison

//        System.out.println("----------------------------------");
//
//        start = System.nanoTime();
//        isValid = simple.isValid(minimal, header, nonce);
//        stop = System.nanoTime();
//        System.out.println("isValid Simple: " + isValid);
//        //assertEquals(true, isValid);
//        System.out.println("Execution: " + (stop-start));

    }
}

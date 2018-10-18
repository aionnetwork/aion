///*******************************************************************************
// * Copyright (c) 2017-2018 Aion foundation.
// *
// *     This file is part of the aion network project.
// *
// *     The aion network project is free software: you can redistribute it
// *     and/or modify it under the terms of the GNU General Public License
// *     as published by the Free Software Foundation, either version 3 of
// *     the License, or any later version.
// *
// *     The aion network project is distributed in the hope that it will
// *     be useful, but WITHOUT ANY WARRANTY; without even the implied
// *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
// *     See the GNU General Public License for more details.
// *
// *     You should have received a copy of the GNU General Public License
// *     along with the aion network project source files.
// *     If not, see <https://www.gnu.org/licenses/>.
// *
// *     The aion network project leverages useful source code from other
// *     open source projects. We greatly appreciate the effort that was
// *     invested in these projects and we thank the individual contributors
// *     for their work. For provenance information and contributors
// *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
// *
// * Contributors to the aion source files in decreasing order of code volume:
// *     Aion foundation.
// *     <ether.camp> team through the ethereumJ library.
// *     Ether.Camp Inc. (US) team through Ethereum Harmony.
// *     John Tromp through the Equihash solver.
// *     Samuel Neves through the BLAKE2 implementation.
// *     Zcash project team.
// *     Bitcoinj team.
// ******************************************************************************/
//package org.aion.zero.impl.valid;
//
//import org.aion.base.util.ByteArrayWrapper;
//import org.aion.equihash.EquiUtils;
//import org.aion.equihash.EquiValidator;
//import org.aion.equihash.Equihash;
//import org.aion.equihash.OptimizedEquiValidator;
//import org.aion.zero.impl.valid.EquihashSolutionRule;
//import org.aion.zero.types.A0BlockHeader;
//import org.junit.Before;
//import org.junit.Test;
//import org.mockito.MockitoAnnotations;
//
//import java.math.BigInteger;
//
//import static com.google.common.truth.Truth.assertThat;
//import static org.junit.Assert.assertThat;
//
//public class EquihashSolutionRuleTest {
//    @Before
//    public void before() {
//        MockitoAnnotations.initMocks(this);
//    }
//
//    // perhaps we should generate a solution by hand
//    @Test
//    public void testProperSolution() {
//        // given that all our inputs are deterministic, for given nonce, there should always
//        // be a valid output solution
//        final int n = 210;
//        final int k = 9;
//        final BigInteger givenNonce = new BigInteger("21");
//
//        // assume a 32-byte nonce (fixed)
//        byte[] unpaddedNonceBytes = givenNonce.toByteArray();
//        byte[] nonceBytes = new byte[32];
//
//        System.arraycopy(unpaddedNonceBytes, 0, nonceBytes, 32 - unpaddedNonceBytes.length, unpaddedNonceBytes.length);
//        System.out.println(new ByteArrayWrapper(nonceBytes));
//
//        A0BlockHeader header = new A0BlockHeader.Builder().build();
//        header.setNonce(nonceBytes);
//        byte[] headerBytes = header.getHeaderBytes(true);
//
//        Equihash equihash = new Equihash(n, k);
//        int[][] solutions = equihash.getSolutionsForNonce(headerBytes, header.getNonce());
//
//        byte[] compressedSolution = (new EquiUtils()).getMinimalFromIndices(solutions[0], n/(k+1));
//        header.setSolution(compressedSolution);
//
////        EquihashSolutionRule rule = new EquihashSolutionRule(new OptimizedEquiValidator(n, k));
////        boolean result = rule.validate(header);
////        assertThat(result).isTrue();
////        assertThat(rule.getErrors()).isEmpty();
//    }
//}

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
package org.aion.zero.impl.core;

import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.core.RewardsCalculator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;

import static org.mockito.Mockito.when;

public class RewardsTest {

    final long blocks = 259200;

    final BigInteger blockReward = new BigInteger("1500000000000000000");

    @Mock
    BlockConstants mockConstants;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    // not really a test, just seeing if delta matches mining report
    @Test
    public void testGetDelta() {
        when(mockConstants.getRampUpLowerBound()).thenReturn(0L);
        when(mockConstants.getRampUpUpperBound()).thenReturn(blocks);
        when(mockConstants.getBlockReward()).thenReturn(blockReward);

        BigInteger total = BigInteger.ZERO;
        RewardsCalculator calc = new RewardsCalculator(mockConstants);
        BigInteger delta = calc.getDelta();
        for (int i = 0; i < 4; i++) {
            System.out.println(i + " " + BigInteger.valueOf(i).multiply(delta));
        }

        System.out.println(259199 + " " + BigInteger.valueOf(259199).multiply(delta));
    }
}

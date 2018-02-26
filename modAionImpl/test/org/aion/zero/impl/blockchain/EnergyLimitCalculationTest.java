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
package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;

import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test suite for
 * {@link ChainConfiguration#calcEnergyLimit(A0BlockHeader)}
 */
@RunWith(Parameterized.class)
public class EnergyLimitCalculationTest {
    
    public static class Energies {
        public long energyConsumed;
        public long energyLimit;
        
        public Energies(long energyConsumed, long energyLimit) {
            this.energyConsumed = energyConsumed;
            this.energyLimit = energyLimit;
        }
    }

    @Mock
    A0BlockHeader mockHeader;
    
    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Parameter
    public Energies energies;
    
    @Parameter(1)
    public long expected;
    
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            // lower bound
            {new Energies(0, 0), new BlockConstants().getEnergyLowerBound().longValue()},
            // lower bound with energyLimit
            {new Energies(0, 5000000), 5000000},
            // upper bound (<=) for energyLimit NOT increasing
            {new Energies(4000000, 5000000), 5000000},
            /**
             * Upper bound for energyLimit increasing, also expect
             * energyLimit to increase by energyLimit/1024
             */
            {new Energies(4000001, 5000000), 5004882},
            /**
             * Upper bound for energyLimit use
             */
            {new Energies(5000000, 5000000), 5004882},
        });
    }
    
    @Test
    public void test() {
        when(mockHeader.getEnergyLimit()).thenReturn(energies.energyLimit);
        when(mockHeader.getEnergyConsumed()).thenReturn(energies.energyConsumed);
        
        ChainConfiguration cc = new ChainConfiguration();
        long energyLimit = cc.calcEnergyLimit(mockHeader);
        assertThat(energyLimit).isEqualTo(expected);
    }
}
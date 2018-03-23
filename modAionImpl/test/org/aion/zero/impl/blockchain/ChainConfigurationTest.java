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

import org.aion.equihash.EquiUtils;
import org.aion.equihash.Equihash;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;


public class ChainConfigurationTest {

    @Mock
    A0BlockHeader header;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void testValidation() {
        int n = 210;
        int k = 9;
        byte[] nonce = {1,0,0,0,0,0,0,
                        0,0,0,0,0,0,0,
                        0,0,0,0,0,0,0,
                        0,0,0,0,0,0,0};
        // setup mock
        A0BlockHeader.Builder builder = new A0BlockHeader.Builder();
        builder.withDifficulty(BigInteger.valueOf(1).toByteArray());
        builder.withNonce(nonce);
        A0BlockHeader header = builder.build();
        
        Equihash equihash = new Equihash(n, k);
     
        int[][] solutions;
        
        while (true) {
            solutions = equihash.getSolutionsForNonce(header.getHeaderBytes(true), header.getNonce());
            if (solutions.length > 0)
                break;
        }
        
        // compress solution
        byte[] compressedSolution = EquiUtils.getMinimalFromIndices(solutions[0], n/(k+1));
        header.setSolution(compressedSolution);
        
        ChainConfiguration chainConfig = new ChainConfiguration();
        BlockHeaderValidator<A0BlockHeader> blockHeaderValidator = chainConfig.createBlockHeaderValidator();
        blockHeaderValidator.validate(header);
    }

    @Test
    public void testIntegrationEnergyLimitCalc() {
        when(header.getEnergyLimit()).thenReturn(10000000L);

        ChainConfiguration config = new ChainConfiguration();
        long out = config.calcEnergyLimit(header);
        assertThat(out).isEqualTo(10000000L);
    }

    // assuming 100000 block ramp
    @Test
    public void testRampUpFunctionBoundaries() {
        long upperBound = 259200L;

        ChainConfiguration config = new ChainConfiguration();
        BigInteger increment = config.getConstants().getBlockReward().divide(BigInteger.valueOf(upperBound));

        // UPPER BOUND
        when(header.getNumber()).thenReturn(upperBound);
        BigInteger blockReward100000 = config.getRewardsCalculator().calculateReward(header);

        when(header.getNumber()).thenReturn(upperBound + 1);
        BigInteger blockReward100001 = config.getRewardsCalculator().calculateReward(header);

        // check that at the upper bound of our range (which is not included) blockReward is capped
        assertEquals(blockReward100000.doubleValue(), config.getConstants().getBlockReward().doubleValue(), 10000);

        // check that for the block after, the block reward is still the same
        assertThat(blockReward100001).isEqualTo(config.getConstants().getBlockReward());

        // check that for an arbitrarily large block, the block reward is still the same
        when(header.getNumber()).thenReturn(upperBound + 100000);
        BigInteger blockReward6700000 = config.getRewardsCalculator().calculateReward(header);
        assertThat(blockReward6700000).isEqualTo(blockReward100001);

        // LOWER BOUNDS
        when(header.getNumber()).thenReturn(0l);
        BigInteger blockReward0 = config.getRewardsCalculator().calculateReward(header);
        assertThat(blockReward0).isEqualTo(BigInteger.ZERO);

        // first block (should have gas value of increment)
        when(header.getNumber()).thenReturn(1l);
        BigInteger blockReward1 = config.getRewardsCalculator().calculateReward(header);
        assertThat(blockReward1).isEqualTo(increment);
    }
}

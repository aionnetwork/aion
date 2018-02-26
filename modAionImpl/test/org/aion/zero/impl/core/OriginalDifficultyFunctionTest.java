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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;

import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.core.DiffCalc;
import org.aion.mcf.types.AbstractBlockHeader;
import org.junit.runner.RunWith;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class OriginalDifficultyFunctionTest {

    protected static class InputParameters {
        public final BigInteger parentDifficulty;
        public final long timeDelta;
        
        public InputParameters(BigInteger parentDifficulty, long timeDelta) {
            this.parentDifficulty = parentDifficulty;
            this.timeDelta = timeDelta;
        }
    }

    @Mock
    protected AbstractBlockHeader mockHeader;

    @Mock
    protected AbstractBlockHeader parentMockHeader;
    
    @Parameter
    public InputParameters inputParameters;
    
    @Parameter(1)
    public BigInteger expectedDifficulty;

    @Parameters
    public static Collection<Object[]> data() {
        BlockConstants constants = new BlockConstants();

        return Arrays.asList(new Object[][] {
                {new InputParameters(new BigInteger("1000000"), 0), new BigInteger("1000488")},
                {new InputParameters(new BigInteger("1000000"), 10), new BigInteger("999512")},
                // try a arbitrary high value to verify that the result stays consistent
                {new InputParameters(new BigInteger("1000000"), 1000000), new BigInteger("999512")},
                // check that difficulty has a lower bound
                {new InputParameters(new BigInteger("0"), 0), constants.getMinimumDifficulty()},
        });
    }

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    
    @Test
    public void test() {
        DiffCalc dc = new DiffCalc(new BlockConstants());

        when(mockHeader.getTimestamp()).thenReturn(inputParameters.timeDelta);
        when(parentMockHeader.getTimestamp()).thenReturn(0L);
        when(parentMockHeader.getDifficulty()).thenReturn(inputParameters.parentDifficulty.toByteArray());
        when(parentMockHeader.getDifficultyBI()).thenReturn(inputParameters.parentDifficulty);

        BigInteger difficulty = dc.calcDifficulty(mockHeader, parentMockHeader);
        assertThat(difficulty).isEqualTo(expectedDifficulty);
    }
}

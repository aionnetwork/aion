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
import org.aion.zero.impl.core.DiffCalc;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(Parameterized.class)
public class ScalingDifficultyFunctionTest {

    public static class InputParameters {
        public long currentTimestamp;
        public long parentTimestamp;
        public BigInteger parentDifficulty;

        public InputParameters(long currentTimestamp, long parentTimestamp, BigInteger parentDifficulty) {
            this.currentTimestamp = currentTimestamp;
            this.parentTimestamp = parentTimestamp;
            this.parentDifficulty = parentDifficulty;
        }
    }

    @Parameterized.Parameter
    public InputParameters inputParameters;

    @Parameterized.Parameter(1)
    public BigInteger expectedDifficulty;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        BlockConstants constants = new BlockConstants();

        List<Object[]> arr = new ArrayList<>(Arrays.asList(new Object[][] {
                // special case test lower bound of difficulty
                {new InputParameters(0, 0, new BigInteger("0")), constants.getMinimumDifficulty()},
                {new InputParameters(0, 0, new BigInteger("1000000")), new BigInteger("1000488")},
                {new InputParameters(1, 0, new BigInteger("1000000")), new BigInteger("1000488")},
                {new InputParameters(2, 0, new BigInteger("1000000")), new BigInteger("1000488")},
                {new InputParameters(3, 0, new BigInteger("1000000")), new BigInteger("1000488")},
                {new InputParameters(4, 0, new BigInteger("1000000")), new BigInteger("1000488")},
                {new InputParameters(5, 0, new BigInteger("1000000")), new BigInteger("1000488")},
                {new InputParameters(6, 0, new BigInteger("1000000")), new BigInteger("1000000")},
                {new InputParameters(7, 0, new BigInteger("1000000")), new BigInteger("1000000")},
                {new InputParameters(8, 0, new BigInteger("1000000")), new BigInteger("1000000")},
                {new InputParameters(9, 0, new BigInteger("1000000")), new BigInteger("1000000")},
                {new InputParameters(10, 0, new BigInteger("1000000")), new BigInteger("1000000")},
                {new InputParameters(11, 0, new BigInteger("1000000")), new BigInteger("1000000")},
                {new InputParameters(12, 0, new BigInteger("1000000")), new BigInteger("1000000")},
                {new InputParameters(13, 0, new BigInteger("1000000")), new BigInteger("1000000")},
                {new InputParameters(14, 0, new BigInteger("1000000")), new BigInteger("1000000")},
        }));

        /*
         * Procedurally generate the correct difficulty for each "step"
         * starts from 15
         */
        for (int i = 1; i < 99; i++) {
            long value = 1000000 - i * 488;
            arr.add(new Object[]{
                    new InputParameters(i*10 + 5, 0, new BigInteger("1000000")), BigInteger.valueOf(value)
            });
        }

        // generate tests beyond 99 (max)
        arr.add(new Object[]{
                new InputParameters(1000, 0, new BigInteger("1000000")), BigInteger.valueOf(1000000 - (99 * 488))
        });

        return arr;
    }

    @Test
    public void test() {

        DiffCalc dc = new DiffCalc(new BlockConstants());
        BigInteger difficulty = dc.calcDifficultyTarget(
                BigInteger.valueOf(inputParameters.currentTimestamp),
                BigInteger.valueOf(inputParameters.parentTimestamp),
                inputParameters.parentDifficulty);
        assertThat(difficulty).isEqualTo(expectedDifficulty);
    }
}

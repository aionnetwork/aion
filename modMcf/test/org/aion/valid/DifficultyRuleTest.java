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

package org.aion.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.mcf.types.AbstractBlockHeader.NONCE_LENGTH;
import static org.aion.mcf.types.AbstractBlockHeader.SOLUTIONSIZE;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.blockchain.valid.IValidRule.RuleError;
import org.aion.mcf.core.IDifficultyCalculator;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.valid.AionDifficultyRule;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link AionDifficultyRule}.
 *
 * @author Jay Tseng
 */
public class DifficultyRuleTest {
    private A0BlockHeader grandParentHeader;
    private A0BlockHeader parentHeader;
    private A0BlockHeader currentHeader;
    @Mock IChainCfg<IAionBlock, AionTransaction> mockChainCfg;
    @Mock IDifficultyCalculator mockDiffCalculator;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        grandParentHeader =
                new A0BlockHeader(
                        (byte) 0x01,
                        1,
                        new byte[32],
                        AionAddress.ZERO_ADDRESS(),
                        new byte[256],
                        ByteUtil.intToBytes(1),
                        null,
                        1,
                        1,
                        1,
                        new byte[NONCE_LENGTH],
                        new byte[SOLUTIONSIZE]);
        parentHeader =
                new A0BlockHeader(
                        (byte) 0x01,
                        2,
                        new byte[32],
                        AionAddress.ZERO_ADDRESS(),
                        new byte[256],
                        ByteUtil.intToBytes(1),
                        null,
                        1,
                        1,
                        11,
                        new byte[NONCE_LENGTH],
                        new byte[SOLUTIONSIZE]);
    }

    /**
     * Checks if the {@link AionDifficultyRule#validate} returns {@code false} when the difficulty
     * data is <b>greater</b> than the difficulty data length.
     */
    @Test
    public void testInvalidDifficultyLength() {

        currentHeader =
                new A0BlockHeader(
                        (byte) 0x01,
                        3,
                        new byte[32],
                        AionAddress.ZERO_ADDRESS(),
                        new byte[256],
                        new byte[17],
                        null,
                        1,
                        1,
                        3,
                        new byte[NONCE_LENGTH],
                        new byte[SOLUTIONSIZE]);

        List<RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual =
                new AionDifficultyRule(mockChainCfg)
                        .validate(grandParentHeader, parentHeader, currentHeader, errors);

        // test output
        assertThat(actual).isFalse();
    }

    /**
     * Checks if the {@link AionDifficultyRule#validate} returns {@code true} when the difficulty
     * data is <b>equal</b> the difficulty data length.
     */
    @Test
    public void testDifficultyLength() {

        currentHeader =
                new A0BlockHeader(
                        (byte) 0x01,
                        3,
                        new byte[32],
                        AionAddress.ZERO_ADDRESS(),
                        new byte[256],
                        ByteUtil.bigIntegerToBytes(BigInteger.ONE, 16),
                        null,
                        1,
                        1,
                        3,
                        new byte[NONCE_LENGTH],
                        new byte[SOLUTIONSIZE]);

        List<RuleError> errors = new LinkedList<>();

        when(mockChainCfg.getDifficultyCalculator()).thenReturn(mockDiffCalculator);
        when(mockDiffCalculator.calculateDifficulty(parentHeader, grandParentHeader))
                .thenReturn(BigInteger.ONE);

        // generate output
        boolean actual =
                new AionDifficultyRule(mockChainCfg)
                        .validate(grandParentHeader, parentHeader, currentHeader, errors);

        // test output
        assertThat(actual).isTrue();
    }
}

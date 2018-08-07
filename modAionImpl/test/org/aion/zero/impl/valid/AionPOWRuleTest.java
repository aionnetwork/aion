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
package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AionPOWRuleTest {

    @Mock
    A0BlockHeader mockHeader;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBelowPOWMaximumBoundary() {
        final byte[] predefinedHash = new byte[32];
        final long predefinedTimestamp = 0;
        final byte[] predefinedNonce = new byte[32];
        final byte[] predefinedSolution = new byte[1408];
        final BigInteger difficulty = BigInteger.ONE;

        when(mockHeader.getTimestamp()).thenReturn(predefinedTimestamp);
        when(mockHeader.getNonce()).thenReturn(predefinedNonce);
        when(mockHeader.getSolution()).thenReturn(predefinedSolution);
        when(mockHeader.getMineHash()).thenReturn(predefinedHash);

        // recall that this will essentially not do anything to the valid space
        // so basically all bytes are valid
        when(mockHeader.getPowBoundaryBI()).thenReturn(BigInteger.ONE.shiftLeft(256).divide(difficulty));
        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionPOWRule rule = new AionPOWRule();
        boolean result = rule.validate(mockHeader, errors);

        assertThat(result).isTrue();
        assertThat(errors).isEmpty();
    }

    @Test
    public void testBelowPOWHalfBoundary() {
        // yes this will produce a value smaller than the boundary
        final byte[] predefinedHash = ByteUtil.hexStringToBytes("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        final long predefinedTimestamp = 1;
        final byte[] predefinedNonce = ByteUtil.hexStringToBytes("0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        final byte[] predefinedSolution = new byte[1408];
        final BigInteger difficulty = BigInteger.TWO;

        when(mockHeader.getTimestamp()).thenReturn(predefinedTimestamp);
        when(mockHeader.getNonce()).thenReturn(predefinedNonce);
        when(mockHeader.getSolution()).thenReturn(predefinedSolution);
        when(mockHeader.getMineHash()).thenReturn(predefinedHash);

        // recall that this will essentially not do anything to the valid space
        // so basically all bytes are valid
        when(mockHeader.getPowBoundaryBI()).thenReturn(BigInteger.ONE.shiftLeft(256).divide(difficulty));
        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionPOWRule rule = new AionPOWRule();
        boolean result = rule.validate(mockHeader, errors);

        assertThat(result).isTrue();
        assertThat(errors).isEmpty();
    }

    @Test
    public void testAbovePOWHalfBoundary() {
        // this produces a result larger than the boundary
        final byte[] predefinedHash = new byte[32];
        final long predefinedTimestamp = 0;
        final byte[] predefinedNonce = new byte[32];
        final byte[] predefinedSolution = new byte[1408];
        final BigInteger difficulty = BigInteger.TWO;

        when(mockHeader.getTimestamp()).thenReturn(predefinedTimestamp);
        when(mockHeader.getNonce()).thenReturn(predefinedNonce);
        when(mockHeader.getSolution()).thenReturn(predefinedSolution);
        when(mockHeader.getMineHash()).thenReturn(predefinedHash);

        // recall that this will essentially not do anything to the valid space
        // so basically all bytes are valid
        when(mockHeader.getPowBoundaryBI()).thenReturn(BigInteger.ONE.shiftLeft(128).divide(difficulty));
        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionPOWRule rule = new AionPOWRule();
        boolean result = rule.validate(mockHeader, errors);

        assertThat(result).isFalse();
        assertThat(errors).isNotEmpty();
    }
}

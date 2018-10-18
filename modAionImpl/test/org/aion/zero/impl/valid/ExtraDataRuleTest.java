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
 */
package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.zero.api.BlockConstants;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExtraDataRuleTest {

    private final BlockConstants constants = new BlockConstants();

    @Mock
    A0BlockHeader mockHeader;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEmptyByteArray() {
        byte[] EMPTY_BYTE_ARR = new byte[0];

        when(mockHeader.getExtraData()).thenReturn(EMPTY_BYTE_ARR);

        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionExtraDataRule dataRule = new AionExtraDataRule(constants.getMaximumExtraDataSize());
        boolean res = dataRule.validate(mockHeader, errors);
        assertThat(res).isEqualTo(true);
        assertThat(errors).isEmpty();
    }

    // even though this should never happen in production
    @Test
    public void testNullByteArray() {
        when(mockHeader.getExtraData()).thenReturn(null);

        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionExtraDataRule dataRule = new AionExtraDataRule(constants.getMaximumExtraDataSize());
        boolean res = dataRule.validate(mockHeader, errors);
        assertThat(res).isEqualTo(true);
        assertThat(errors).isEmpty();
    }

    @Test
    public void testInvalidLargerByteArray() {
        byte[] LARGE_BYTE_ARRAY = new byte[33];

        when(mockHeader.getExtraData()).thenReturn(LARGE_BYTE_ARRAY);

        List<IValidRule.RuleError> errors = new ArrayList<>();

        AionExtraDataRule dataRule = new AionExtraDataRule(constants.getMaximumExtraDataSize());
        boolean res = dataRule.validate(mockHeader, errors);
        assertThat(res).isEqualTo(false);
        assertThat(errors).isNotEmpty();
    }
}

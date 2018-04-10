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

import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.valid.EnergyLimitRule;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link EnergyLimitRule}
 */
public class EnergyLimitRuleTest {

    private final BlockConstants constants = new BlockConstants();

    @Test
    public void testEnergyLimitBounds() {
        final long INITIAL_VAL = 2000000L;
        final long DIVISOR = 1024;
        EnergyLimitRule rule = new EnergyLimitRule(
                constants.getEnergyDivisorLimitLong(),
                constants.getEnergyLowerBoundLong());

        A0BlockHeader parentHeader = new A0BlockHeader.Builder()
                .withEnergyLimit(INITIAL_VAL)
                .build();

        long boundShiftLimit = INITIAL_VAL / DIVISOR;

        A0BlockHeader upperCurrentBlock = new A0BlockHeader.Builder()
                .withEnergyLimit(INITIAL_VAL + boundShiftLimit)
                .build();

        List<IValidRule.RuleError> errors = new ArrayList<>();

        // upper bound
        boolean res = rule.validate(upperCurrentBlock, parentHeader, errors);
        assertThat(res).isEqualTo(true);
        assertThat(errors).isEmpty();
        errors.clear();

        A0BlockHeader invalidCurrentHeader = new A0BlockHeader.Builder()
                .withEnergyLimit(INITIAL_VAL + boundShiftLimit + 1)
                .build();

        res = rule.validate(invalidCurrentHeader, parentHeader, errors);
        assertThat(res).isEqualTo(false);
        assertThat(errors).isNotEmpty();
        errors.clear();

        // lower bound
        A0BlockHeader lowerCurrentHeader = new A0BlockHeader.Builder()
                .withEnergyLimit(INITIAL_VAL - boundShiftLimit)
                .build();

        res = rule.validate(lowerCurrentHeader, parentHeader, errors);
        assertThat(res).isEqualTo(true);
        assertThat(errors).isEmpty();
        errors.clear();

        A0BlockHeader invalidLowerCurrentHeader = new A0BlockHeader.Builder()
                .withEnergyLimit(INITIAL_VAL - boundShiftLimit - 1)
                .build();

        res = rule.validate(invalidLowerCurrentHeader, parentHeader, errors);
        assertThat(res).isEqualTo(false);
        assertThat(errors).isNotEmpty();
        errors.clear();
    }

    @Test
    public void testEnergyLimitLowerBound() {
        final long INITIAL_VAL = 0l;

        A0BlockHeader parentHeader = new A0BlockHeader.Builder()
                .withEnergyLimit(0l)
                .build();

        A0BlockHeader currentHeader = new A0BlockHeader.Builder()
                .withEnergyLimit(1l)
                .build();

        List<IValidRule.RuleError> errors = new ArrayList<>();

        EnergyLimitRule rule = new EnergyLimitRule(
                constants.getEnergyDivisorLimitLong(),
                constants.getEnergyLowerBoundLong());
        boolean res = rule.validate(currentHeader, parentHeader, errors);
        assertThat(res).isEqualTo(false);
        assertThat(errors).isNotEmpty();
    }
}

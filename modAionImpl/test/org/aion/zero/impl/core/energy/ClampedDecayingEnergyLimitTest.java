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
package org.aion.zero.impl.core.energy;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(Parameterized.class)
public class ClampedDecayingEnergyLimitTest {

    private static final long energyLimitDivisor = 1024L;
    private static final long energyLowerBound = 5000L;
    private static final long clampUpperBound = 15_000_000L;
    private static final long clampLowerBound = 7_000_000L;
    private final ClampedDecayStrategy strategy = new ClampedDecayStrategy(
        energyLowerBound,
        energyLimitDivisor,
        clampUpperBound,
        clampLowerBound);
    private final long parentEnergyLimit;
    private final long parentEnergyConsumed;
    private final long expectedDelta;
    @Mock
    A0BlockHeader mockHeader;

    public ClampedDecayingEnergyLimitTest(final long parentEnergyLimit,
        final long parentEnergyConsumed,
        final long expectedDelta) {
        this.parentEnergyLimit = parentEnergyLimit;
        this.parentEnergyConsumed = parentEnergyConsumed;
        this.expectedDelta = expectedDelta;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {10_000_000L, 0L, -(10_000_000L / energyLimitDivisor)},
            {10_000_000L, 7_500_000L, 0L},
            {10_000_000L, 10_000_000L, 10_000_000L / 3 / energyLimitDivisor},
            {6_000_000L, 0L, 6_000_000L / energyLimitDivisor},
            {16_000_000L, 16_000_000L, -16_000_000L / energyLimitDivisor}
        });
    }

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    private void assertLimits(long energyLimit, long energyConsumed, long expected) {
        when(mockHeader.getEnergyLimit()).thenReturn(energyLimit);
        when(mockHeader.getEnergyConsumed()).thenReturn(energyConsumed);

        long limit = strategy.getEnergyLimit(mockHeader);
        assertThat(limit).isEqualTo(energyLimit + expected);
    }

    @Test
    public void test() {
        assertLimits(this.parentEnergyLimit, this.parentEnergyConsumed, this.expectedDelta);
    }
}

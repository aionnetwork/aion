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

import org.aion.zero.types.A0BlockHeader;

public class MonotonicallyIncreasingStrategy extends AbstractEnergyStrategyLimit {

    public MonotonicallyIncreasingStrategy(long energyLowerBound,
        long energyDivisorLimit) {
        super(energyLowerBound, energyDivisorLimit);
    }

    /**
     * Monotonically increasing strategy, this is the original function that was used for the
     * duration of the test-net
     */
    protected static long monotonicallyIncreasingEnergyStrategy(final long parentEnergyLimit,
        final long parentEnergyUsed,
        final long blockLowerLimit,
        final long energyLimitDivisor) {
        long threshold = parentEnergyLimit * 4 / 5;
        if (parentEnergyUsed > threshold) {
            return Math.max(blockLowerLimit,
                parentEnergyLimit + (parentEnergyLimit / energyLimitDivisor));
        }
        return parentEnergyLimit;
    }

    @Override
    protected long getEnergyLimitInternal(A0BlockHeader header) {
        return monotonicallyIncreasingEnergyStrategy(header.getEnergyLimit(),
            header.getEnergyConsumed(),
            this.getEnergyLowerBound(),
            this.getEnergyDivisorLimit());
    }
}

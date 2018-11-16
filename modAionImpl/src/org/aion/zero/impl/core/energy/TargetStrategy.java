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

public class TargetStrategy extends AbstractEnergyStrategyLimit {

    private long target;

    public TargetStrategy(
            final long energyLowerBound, final long energyDivisorLimit, final long target) {
        super(energyLowerBound, energyDivisorLimit);

        assert (target >= 0);
        this.target = target;
    }

    @Override
    protected long getEnergyLimitInternal(A0BlockHeader header) {
        return targetedEnergyLimitStrategy(
                header.getEnergyLimit(), this.getEnergyDivisorLimit(), this.target);
    }

    protected static long targetedEnergyLimitStrategy(
            final long parentEnergyLimit, final long energyLimitDivisor, final long targetLimit) {

        // find the distance between the targetLimit and parentEnergyLimit
        // capped by the targetLimit (the bounds in which the shift is valid)
        long delta =
                Math.min(
                        parentEnergyLimit / energyLimitDivisor,
                        Math.abs(targetLimit - parentEnergyLimit));

        if (parentEnergyLimit > targetLimit) delta = -delta;

        // clamp at the block lower limit
        return parentEnergyLimit + delta;
    }
}

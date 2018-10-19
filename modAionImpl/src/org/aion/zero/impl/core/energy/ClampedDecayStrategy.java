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

public class ClampedDecayStrategy extends DecayStrategy {

    private long clampUpperBound;

    private long clampLowerBound;

    public ClampedDecayStrategy(
            long energyLowerBound,
            long energyDivisorLimit,
            long clampUpperBound,
            long clampLowerBound) {
        super(energyLowerBound, energyDivisorLimit);

        assert (clampLowerBound >= 0);
        assert (clampUpperBound >= clampLowerBound);

        this.clampUpperBound = clampUpperBound;
        this.clampLowerBound = clampLowerBound;
    }

    /**
     * Same behaviour as the decay strategy, but adds clamps to ensure that energy is eventually
     * consistent within a desired range
     */
    @Override
    protected long getEnergyLimitInternal(A0BlockHeader header) {
        long out = super.getEnergyLimitInternal(header);
        long prevEnergyLimit = header.getEnergyLimit();

        // clamps
        if (prevEnergyLimit < this.clampLowerBound)
            return prevEnergyLimit + prevEnergyLimit / this.getEnergyDivisorLimit();

        if (prevEnergyLimit > this.clampUpperBound)
            return prevEnergyLimit - prevEnergyLimit / this.getEnergyDivisorLimit();

        return out;
    }
}

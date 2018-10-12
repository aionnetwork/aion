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

public abstract class AbstractEnergyStrategyLimit {

    private long energyLowerBound;
    private long energyDivisorLimit;

    public AbstractEnergyStrategyLimit(long energyLowerBound, long energyDivisorLimit) {
        assert (energyLowerBound >= 0);
        assert (energyDivisorLimit > 0);

        this.energyLowerBound = energyLowerBound;
        this.energyDivisorLimit = energyDivisorLimit;
    }

    protected long getEnergyLowerBound() {
        return this.energyLowerBound;
    }

    protected long getEnergyDivisorLimit() {
        return this.energyDivisorLimit;
    }

    public long getEnergyLimit(A0BlockHeader header) {
        return Math.max(getEnergyLimitInternal(header), this.energyLowerBound);
    }

    protected abstract long getEnergyLimitInternal(A0BlockHeader header);
}
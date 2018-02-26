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
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl.valid;

import org.aion.mcf.valid.DependentBlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;

import java.math.BigInteger;

/**
 * Energy limit rule is defined as the following (no documentation yet)
 * <p>
 * if EnergyLimit(n) > MIN_ENERGY EnergyLimit(n-1) - EnergyLimit(n-1)/1024 <=
 * EnergyLimit(n) <= EnergyLimit(n-1) + EnergyLimit(n-1)/1024
 * <p>
 * This rule depends on the parent to implement
 */
public class EnergyLimitRule extends DependentBlockHeaderRule<A0BlockHeader> {

    private final BigInteger energyLimitDivisor;
    private final BigInteger energyLimitLowerBounds;

    public EnergyLimitRule(BigInteger energyLimitDivisor, BigInteger energyLimitLowerBounds) {
        this.energyLimitDivisor = energyLimitDivisor;
        this.energyLimitLowerBounds = energyLimitLowerBounds;
    }

    @Override
    public boolean validate(A0BlockHeader header, A0BlockHeader parent) {
        errors.clear();

        BigInteger energyLimit = BigInteger.valueOf(header.getEnergyLimit());
        BigInteger parentEnergyLimit = BigInteger.valueOf(parent.getEnergyLimit());
        BigInteger parentEnergyQuotient = parentEnergyLimit.divide(energyLimitDivisor);

        if (energyLimit.compareTo(this.energyLimitLowerBounds) < 0) {
            StringBuilder builder = new StringBuilder();
            builder.append("Proposed energyLimit ").append(energyLimit).append(" is below the minimum expected ")
                    .append(this.energyLimitLowerBounds);
            errors.add(builder.toString());
            return false;
        }

        // check the upper bound
        int res = energyLimit.compareTo(parentEnergyLimit);
        if (res == 0) { // if energy did not change
            return true;
        } else if (res > 0) {
            BigInteger parentUpperBound = parentEnergyLimit.add(parentEnergyQuotient);
            boolean isBounded = energyLimit.compareTo(parentUpperBound) <= 0;

            if (!isBounded) {
                StringBuilder builder = new StringBuilder();
                builder.append("Difference exceeds upper energyLimit bounds. Given ").append(energyLimit)
                        .append(", expected ").append(parentUpperBound);
                errors.add(builder.toString());
                return false;
            }

        } else {
            BigInteger parentLowerBound = parentEnergyLimit.subtract(parentEnergyQuotient);
            boolean isBounded = energyLimit.compareTo(parentLowerBound) >= 0;

            if (!isBounded) {
                StringBuilder builder = new StringBuilder();
                builder.append("Difference exceeds lower energyLimit bounds. Given ").append(energyLimit)
                        .append(", expected ").append(parentLowerBound);
                errors.add(builder.toString());
                return false;
            }
        }
        return true;
    }
}

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

package org.aion.zero.impl.core;

import java.math.BigInteger;

import org.aion.zero.api.BlockConstants;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * Multiple implementations for calculating the rewards
 * 
 * @author yao
 *
 */
public class RewardsCalculator {
    private BlockConstants constants;
    private BigInteger m;

    public RewardsCalculator(BlockConstants constants) {
        this.constants = constants;

        // pre-calculate the desired increment
        long delta = constants.getRampUpUpperBound() - constants.getRampUpLowerBound();
        assert (delta > 0);

        this.m = this.constants.getRampUpEndValue()
                .subtract(this.constants.getRampUpStartValue())
                .divide(BigInteger.valueOf(delta));
    }

    /**
     * Linear ramp function that falls off after the upper bound
     */
    public BigInteger calculateReward(AbstractBlockHeader blockHeader) {
        long number = blockHeader.getNumber();
        if (number <= this.constants.getRampUpUpperBound()) {
            return BigInteger.valueOf(number).multiply(m).add(this.constants.getRampUpStartValue());
        } else {
            return this.constants.getBlockReward();
        }
    }

    public BigInteger getDelta() {
        return m;
    }
}

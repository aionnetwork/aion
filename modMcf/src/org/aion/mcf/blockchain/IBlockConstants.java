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
package org.aion.mcf.blockchain;

import java.math.BigInteger;

/**
 * Interface for declaring general block related constant files. This can
 * change depending on the chain configuration that is loaded.
 */
public interface IBlockConstants {

    /**
     * Upper bound of the extra data that may be placed into a block
     */
    int getMaximumExtraDataSize();

    /**
     * Lower bound of difficulty
     */
    BigInteger getMinimumDifficulty();

    /**
     * The divisor for energy, energy being the units consumed by
     * VM operations and Transactions
     */
    BigInteger getEnergyDivisorLimit();

    /**
     * The divisor for energy (long)
     */
    long getEnergyDivisorLimitLong();

    /**
     * The divisor for difficulty
     */
    BigInteger getDifficultyBoundDivisor();

    /**
     * Divisor for difficulty (long)
     */
    long getDifficultyBoundDivisorLong();

    /**
     * The intended block time, specified in seconds
     *
     * @return
     */
    int getDurationLimit();

    /**
     * The reward for mining a new block
     *
     * @return
     */
    BigInteger getBlockReward();

    /**
     * The lower bound of expected block time, blocktimes lower than this indicate
     * the necessity of a difficulty increase.
     */
    long getBlockTimeLowerBound();

    /**
     * The upper bound of expected block time, blocktimes higher than this indicate
     * the necessity of a difficulty decrease.
     */
    long getBlockTimeUpperBound();

    /**
     * The lower bound of an energyLimit value
     */
    BigInteger getEnergyLowerBound();

    /**
     * Lower bound of an energyLimit value (long)
     */
    long getEnergyLowerBoundLong();

    /**
     * Maximum accepted clock drift difference (seconds)
     */
    long getClockDriftBufferTime();

    BigInteger getRampUpStartValue();

    BigInteger getRampUpEndValue();
}

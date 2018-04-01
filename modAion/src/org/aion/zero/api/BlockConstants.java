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
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.api;

import java.math.BigInteger;

import org.aion.base.type.Address;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.mcf.types.AbstractBlockHeader;

public class BlockConstants implements IBlockConstants {

    /**
     * Number of bytes that is allowed in {@link AbstractBlockHeader#extraData}
     * field
     */
    private static final int MAXIMUM_EXTRA_DATA_SIZE = 32;

    /**
     * The lower bound of difficulty, this value is currently set to 32 to
     * accomodate for single node testing.
     */
    private static final long MINIMUM_DIFFICULTY_LONG = 16;
    private static final BigInteger MINIMUM_DIFFICULTY = BigInteger.valueOf(MINIMUM_DIFFICULTY_LONG);

    /**
     * Divisor for the maximum increase or decrease in energyLimit from one
     * block to the next.
     */
    private static final long ENERGY_LIMIT_DIVISOR_LONG = 1024;
    private static final long DIFFICULTY_BOUND_DIVISOR_LONG = 2048;
    private static final BigInteger ENERGY_LIMIT_DIVISOR = BigInteger.valueOf(ENERGY_LIMIT_DIVISOR_LONG);
    private static final BigInteger DIFFICULTY_BOUND_DIVISOR = BigInteger.valueOf(DIFFICULTY_BOUND_DIVISOR_LONG);

    /**
     * The lowest possible value of energy, cannot be lower than this bound
     */
    private static final long ENERGY_LOWER_BOUND_LONG = 5000;
    private static final BigInteger ENERGY_LOWER_BOUND = BigInteger.valueOf(ENERGY_LOWER_BOUND_LONG);

    public static int DURATION_LIMIT = 8;

    /**
     * Rewards not set yet, but this is the projected amount based on a 10
     * second block time
     */
    private static final BigInteger BLOCK_REWARD = new BigInteger("1500000000000000000");

    private static final int BLOCK_TIME_LOWER_BOUND = 5;
    private static final int BLOCK_TIME_UPPER_BOUND = 15;

    /**
     * Constants for ramp-up, the ramp-up function will apply within this range
     * TODO: This will need to be revamped to actual values
     */
    private static final long RAMP_UP_LOWER_BOUND = 0;
    private static final long RAMP_UP_UPPER_BOUND = 259200; // 1 month

    /**
     * Desired block time
     * 
     * @return
     */
    private static final long EXPECTED_BLOCK_TIME = 10;

    /**
     * Desired future elapsed time, when receiving a block, allow for at most this
     * amount of seconds relative to local timestamp before rejecting the block
     * as invalid. This accounts for clock drift between different clocks on the
     * network.
     */
    private static final long CLOCK_DRIFT_BUFFER_TIME = 1;

    @Override
    public int getMaximumExtraDataSize() {
        return MAXIMUM_EXTRA_DATA_SIZE;
    }

    @Override
    public BigInteger getMinimumDifficulty() {
        return MINIMUM_DIFFICULTY;
    }

    @Override
    public BigInteger getEnergyDivisorLimit() {
        return ENERGY_LIMIT_DIVISOR;
    }

    @Override
    public long getEnergyDivisorLimitLong() {
        return ENERGY_LIMIT_DIVISOR_LONG;
    }

    @Override
    public BigInteger getDifficultyBoundDivisor() {
        return DIFFICULTY_BOUND_DIVISOR;
    }

    @Override
    public long getDifficultyBoundDivisorLong() {
        return DIFFICULTY_BOUND_DIVISOR_LONG;
    }

    @Override
    public int getDurationLimit() {
        return DURATION_LIMIT;
    }

    /**
     * TODO: the parameters supplied are currently TEST PARAMETERS For a
     * production system this must be adjusted properly.
     */
    @Override
    public BigInteger getBlockReward() {
        return BLOCK_REWARD;
    }

    /**
     * TODO: the parameters supplied are currently TEST PARAMETERS For a
     * production system this must be adjusted properly.
     */
    @Override
    public long getBlockTimeLowerBound() {
        return BLOCK_TIME_LOWER_BOUND;
    }

    /**
     * TODO: the parameters supplied are currently TEST PARAMETERS For a
     * production system this must be adjusted properly.
     */
    @Override
    public long getBlockTimeUpperBound() {
        return BLOCK_TIME_UPPER_BOUND;
    }

    /**
     * The lower bound for calculation of the ramp-up function
     * 
     * TODO: the parameters supplied here are TEST PARAMETERS For a production
     * system this must be adjusted properly.
     */
    public long getRampUpLowerBound() {
        return RAMP_UP_LOWER_BOUND;
    }

    /**
     * The upper bound for calculation of the ramp-up function
     * 
     * TODO: the parameters supplied here are TEST PARAMETERS. For a production
     * system this must be adjusted properly.
     */
    public long getRampUpUpperBound() {
        return RAMP_UP_UPPER_BOUND;
    }

    /**
     * The lower bound for energy calculations, energy should not go below this
     * value
     */
    public long getEnergyLowerBoundLong() {
        return ENERGY_LOWER_BOUND_LONG;
    }

    public BigInteger getEnergyLowerBound() {
        return ENERGY_LOWER_BOUND;
    }

    public long getExpectedBlockTime() {
        return EXPECTED_BLOCK_TIME;
    }

    public long getClockDriftBufferTime() {
        return CLOCK_DRIFT_BUFFER_TIME;
    }

    /**
     * TODO: this address needs to be defined
     *
     * Public portion of the keypair that is held by bridging nodes. How the key
     * is derived is left up to bridging implementation. All messages concerning
     * token movement into the network must be accompanied by a signature signed
     * with the private portion of this keypair.
     *
     * @return {@code address} of the sk owning this pair. Also referred to as
     *         the owner's address.
     */
    public Address getTokenBridgingAddress() {
        return Address.ZERO_ADDRESS();
    }
}

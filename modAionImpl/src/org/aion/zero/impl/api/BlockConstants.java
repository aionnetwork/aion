package org.aion.zero.impl.api;

import java.math.BigInteger;
import org.aion.zero.impl.types.IBlockConstants;

public class BlockConstants implements IBlockConstants {

    /** Number of bytes that is allowed in {@link AbstractBlockHeader} field */
    private static final int MAXIMUM_EXTRA_DATA_SIZE = 32;

    /**
     * The lower bound of the difficulty of a mining block, this value is currently set to 16 to accommodate for single
     * node testing.
     */
    private static final long MINIMUM_MINING_DIFFICULTY_LONG = 16;

    private static final BigInteger MINIMUM_MINING_DIFFICULTY =
            BigInteger.valueOf(MINIMUM_MINING_DIFFICULTY_LONG);

    /**
     * The lower bound of the difficulty of a staking block, this value is currently set to 10^22, based on the fact that
     * the minimum stake is 10^21 nAmps, and our target block time is 10 seconds.
     */

    private static final BigInteger MINIMUM_STAKING_DIFFICULTY = BigInteger.TEN.pow(22);

    /** Divisor for the maximum increase or decrease in energyLimit from one block to the next. */
    private static final long ENERGY_LIMIT_DIVISOR_LONG = 1024;

    private static final long DIFFICULTY_BOUND_DIVISOR_LONG = 2048;
    private static final BigInteger ENERGY_LIMIT_DIVISOR =
            BigInteger.valueOf(ENERGY_LIMIT_DIVISOR_LONG);
    private static final BigInteger DIFFICULTY_BOUND_DIVISOR =
            BigInteger.valueOf(DIFFICULTY_BOUND_DIVISOR_LONG);

    /**
     * The lowest possible value of energy, cannot be lower than this bound
     *
     * <p>This value is derived from (at minimum) the ability to send 50 Pure / Ambient-Cost
     * transactions
     */
    private static final long ENERGY_LOWER_BOUND_LONG = 1050000;

    private static final BigInteger ENERGY_LOWER_BOUND =
            BigInteger.valueOf(ENERGY_LOWER_BOUND_LONG);

    public static int DURATION_LIMIT = 8;

    /** Rewards not set yet, but this is the projected amount based on a 10 second block time */
    private static final BigInteger BLOCK_REWARD = new BigInteger("1497989283243310185");

    private static final int BLOCK_TIME_LOWER_BOUND = 5;
    private static final int BLOCK_TIME_UPPER_BOUND = 15;

    /** Constants for ramp-up, the ramp-up function will apply within this range */
    private static final long RAMP_UP_LOWER_BOUND = 0;

    private static final long RAMP_UP_UPPER_BOUND = 259200; // 1 month

    /**
     * Ramp-up initial parameter, this value is calculates based on the monetary policy paper,
     * assuming:
     */
    private static final BigInteger RAMP_UP_START_VALUE = new BigInteger("748994641621655092");

    private static final BigInteger RAMP_UP_END_VALUE = BLOCK_REWARD;

    /** Desired block time */
    private static final long EXPECTED_BLOCK_TIME = 10;

    /**
     * Desired future elapsed time, when receiving a block, allow for at most this amount of seconds
     * relative to local timestamp before rejecting the block as invalid. This accounts for clock
     * drift between different clocks on the network.
     */
    private static final long CLOCK_DRIFT_BUFFER_TIME = 1;

    @Override
    public int getMaximumExtraDataSize() {
        return MAXIMUM_EXTRA_DATA_SIZE;
    }

    @Override
    public BigInteger getMinimumMiningDifficulty() {
        return MINIMUM_MINING_DIFFICULTY;
    }

    @Override
    public BigInteger getMinimumStakingDifficulty() {
        return MINIMUM_STAKING_DIFFICULTY;
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
     * TODO: the parameters supplied are currently TEST PARAMETERS For a production system this must
     * be adjusted properly.
     */
    @Override
    public BigInteger getBlockReward() {
        return BLOCK_REWARD;
    }

    /**
     * TODO: the parameters supplied are currently TEST PARAMETERS For a production system this must
     * be adjusted properly.
     */
    @Override
    public long getBlockTimeLowerBound() {
        return BLOCK_TIME_LOWER_BOUND;
    }

    /**
     * TODO: the parameters supplied are currently TEST PARAMETERS For a production system this must
     * be adjusted properly.
     */
    @Override
    public long getBlockTimeUpperBound() {
        return BLOCK_TIME_UPPER_BOUND;
    }

    /**
     * The lower bound for calculation of the ramp-up function
     *
     * <p>TODO: the parameters supplied here are TEST PARAMETERS For a production system this must
     * be adjusted properly.
     */
    public long getRampUpLowerBound() {
        return RAMP_UP_LOWER_BOUND;
    }

    /**
     * The upper bound for calculation of the ramp-up function
     *
     * <p>TODO: the parameters supplied here are TEST PARAMETERS. For a production system this must
     * be adjusted properly.
     */
    public long getRampUpUpperBound() {
        return RAMP_UP_UPPER_BOUND;
    }

    public static long getAnnum() {
        return 3110400L;
    }

    public static int getInterestBasePoint() {
        return 100;
    }

    /** The lower bound for energy calculations, energy should not go below this value */
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

    @Override
    public BigInteger getRampUpStartValue() {
        return RAMP_UP_START_VALUE;
    }

    @Override
    public BigInteger getRampUpEndValue() {
        return RAMP_UP_END_VALUE;
    }
}

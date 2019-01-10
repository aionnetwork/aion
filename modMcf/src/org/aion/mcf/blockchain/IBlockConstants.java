package org.aion.mcf.blockchain;

import java.math.BigInteger;

/**
 * Interface for declaring general block related constant files. This can change depending on the
 * chain configuration that is loaded.
 */
public interface IBlockConstants {

    /** Upper bound of the extra data that may be placed into a block */
    int getMaximumExtraDataSize();

    /** Lower bound of difficulty */
    BigInteger getMinimumDifficulty();

    /** The divisor for energy, energy being the units consumed by VM operations and Transactions */
    BigInteger getEnergyDivisorLimit();

    /** The divisor for energy (long) */
    long getEnergyDivisorLimitLong();

    /** The divisor for difficulty */
    BigInteger getDifficultyBoundDivisor();

    /** Divisor for difficulty (long) */
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
     * The lower bound of expected block time, blocktimes lower than this indicate the necessity of
     * a difficulty increase.
     */
    long getBlockTimeLowerBound();

    /**
     * The upper bound of expected block time, blocktimes higher than this indicate the necessity of
     * a difficulty decrease.
     */
    long getBlockTimeUpperBound();

    /** The lower bound of an energyLimit value */
    BigInteger getEnergyLowerBound();

    /** Lower bound of an energyLimit value (long) */
    long getEnergyLowerBoundLong();

    /** Maximum accepted clock drift difference (seconds) */
    long getClockDriftBufferTime();

    BigInteger getRampUpStartValue();

    BigInteger getRampUpEndValue();
}

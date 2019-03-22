package org.aion.zero.api;

import com.google.common.annotations.VisibleForTesting;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @implNote The monetary calculator give the reward base given arguments.
 * @author Jay Tseng
 */
public final class MonetaryCalculator {

    private BigInteger initialSupply;
    private BigInteger inflationRate;
    private static long BLOCK_ANNUM;
    private static long STARTING_BLOCK_NUM;

    private static long CURRENT_TERM;
    private static BigInteger CURRENT_REWARD = BigInteger.ZERO;

    private static class Holder {
        static void setInst(MonetaryCalculator i) {
            inst = i;
        }

        static MonetaryCalculator inst = null;
    }

    private static int COMPOUND_YEAR_MAX = 128;

    private static Map<Integer, BigInteger> COMPOUND_LOOKUP_TABLE =
            new HashMap<>(COMPOUND_YEAR_MAX);

    /**
     * Initialize the MonetaryCalculator class and store into the holder.
     *
     * @param initialSupply The initial token supply before the monetary start calculating.
     * @param interestBasePoint Same as the bank interest base unit. each bae point equal to 0.01%.
     *     e.g. the 1% interest rate require to input 100.
     * @param blockAnnum The definition of the blocks of each annual year.
     * @param startingBlockNum The target block number for triggering the monetary calculator. The
     *     reward will be changed after the trigger block.
     * @exception IllegalArgumentException The given argument has limited boundary.
     */
    public static MonetaryCalculator init(
            BigInteger initialSupply,
            int interestBasePoint,
            long blockAnnum,
            long startingBlockNum) {

        if (Holder.inst == null) {
            Holder.setInst(
                    new MonetaryCalculator(
                            initialSupply, interestBasePoint, blockAnnum, startingBlockNum));
        }

        return Holder.inst;
    }

    private MonetaryCalculator(
            final BigInteger initialSupply,
            final int interestBasePoint,
            final long blockAnnum,
            final long startingBlockNum) {

        if ((initialSupply == null || initialSupply.signum() != 1)
                || interestBasePoint < 1
                || blockAnnum < 1
                || startingBlockNum < 0) {
            String s =
                    "InitialSupply: "
                            + Objects.requireNonNull(initialSupply).toString()
                            + " InterestBasePoint: "
                            + interestBasePoint
                            + " BlockAnnum: "
                            + blockAnnum
                            + " StartingBlockNum:"
                            + startingBlockNum;
            throw new IllegalArgumentException(s);
        }

        this.initialSupply = initialSupply;
        BLOCK_ANNUM = blockAnnum;
        STARTING_BLOCK_NUM = startingBlockNum;
        inflationRate = BigInteger.valueOf(10_000L + interestBasePoint);

        for (int i = 0; i < COMPOUND_YEAR_MAX; i++) {
            COMPOUND_LOOKUP_TABLE.put(i, calculateCompound(i));
        }
    }

    /**
     * Given the block number desire to calculate the reward. The basic term will be 1 then
     * increased by the annum of blocks. Should call {@link MonetaryCalculator.init()} before use
     * it.
     *
     * @param blockNum The block number for calculating the reward.
     * @return The current token reward.
     * @exception IllegalArgumentException The blockNum should not less or equal to the targeting
     *     block number or the inputing block number exceed the max compound term (128 yrs).
     * @exception NullPointerException the method should be called after the Holder initialized.
     */
    public static BigInteger getCurrentReward(final long blockNum) {
        if (Holder.inst == null) {
            throw new NullPointerException(
                    "No MonetaryCalculator Holder instance, please initialize it before the call.");
        }

        if (blockNum <= STARTING_BLOCK_NUM) {
            throw new IllegalArgumentException("Invalid blockNum:" + blockNum);
        }

        int term = (int) ((blockNum - STARTING_BLOCK_NUM - 1) / BLOCK_ANNUM + 1);

        if (term != CURRENT_TERM) {
            CURRENT_REWARD = COMPOUND_LOOKUP_TABLE.get(term);
            if (CURRENT_REWARD == null) {
                // if the term go over them the max compound yrs then no reward.
                CURRENT_REWARD = COMPOUND_LOOKUP_TABLE.get(0);
            }
            CURRENT_TERM = term;
        }

        return CURRENT_REWARD;
    }

    private BigInteger calculateCompound(long term) {
        BigInteger divider = BigInteger.valueOf(10000);
        BigInteger compound = divider.multiply(initialSupply);
        BigInteger preCompound = compound;
        for (long i = 0; i < term; i++) {
            preCompound = compound;
            compound = preCompound.multiply(inflationRate).divide(divider);
        }

        compound = compound.subtract(preCompound);

        return compound.divide(BigInteger.valueOf(BLOCK_ANNUM)).divide(divider);
    }

    @VisibleForTesting
    BigInteger testCalculateCompound(long term) {
        return calculateCompound(term);
    }

    @VisibleForTesting
    static void destory() {
        Holder.inst = null;
    }
}

package org.aion.zero.api;

import com.google.common.annotations.VisibleForTesting;

import java.math.BigInteger;
import java.util.Objects;

/**
 * @implNote The monetary calculator give the reward base given arguments.
 * @author Jay Tseng
 */
public final class MonetaryCalculator {

    private static BigInteger INITIALSUPPLY;
    private static BigInteger INFLATIONRATE = null;
    private static BigInteger DEVIDER = BigInteger.valueOf(10000);
    private static long BLOCKANNUM;
    private static long STARTINGBLOCKNUM;

    private static long CURRENTTERM;
    private static BigInteger CURRENTREWARD = BigInteger.ZERO;

    private static class Holder {
        static void setInst(MonetaryCalculator i) {
            inst = i;
        }

        static MonetaryCalculator inst = null;
    }

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

        INITIALSUPPLY = initialSupply;
        BLOCKANNUM = blockAnnum;
        STARTINGBLOCKNUM = startingBlockNum;
        INFLATIONRATE = BigInteger.valueOf(10000 + interestBasePoint);
    }

    /**
     * Given the block number desire to calculate the reward. The basic term will be 1 then
     * increased by the annum of blocks. Should call {@link MonetaryCalculator.init()} before use
     * it.
     *
     * @param blockNum The block number for calculating the reward.
     * @return The current token reward.
     * @exception IllegalArgumentException The blockNum should not less or equal to the targeting
     *     block number.
     * @exception Exception the method should be called after the Holder initialized.
     */
    public static BigInteger getCurrentReward(final long blockNum) {
        if (Holder.inst == null) {
            throw new NullPointerException(
                    "No MonetaryCalculator Holder instance, please initial it before the call.");
        }

        if (blockNum <= STARTINGBLOCKNUM) {
            throw new IllegalArgumentException("Invalid blockNum:" + blockNum);
        }

        long term = (blockNum - STARTINGBLOCKNUM - 1) / BLOCKANNUM + 1;

        if (term != CURRENTTERM) {
            CURRENTREWARD = calculateCompound(term);
            CURRENTTERM = term;
        }

        return CURRENTREWARD;
    }

    private static BigInteger calculateCompound(long term) {
        BigInteger compound = DEVIDER.multiply(INITIALSUPPLY);
        BigInteger preCompound = compound;
        for (long i = 0; i < term; i++) {
            preCompound = compound;
            compound = preCompound.multiply(INFLATIONRATE).divide(DEVIDER);
        }

        compound = compound.subtract(preCompound);

        return compound.divide(BigInteger.valueOf(BLOCKANNUM)).divide(DEVIDER);
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

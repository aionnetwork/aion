package org.aion.zero.impl.core;

import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.types.A0BlockHeader;

/**
 * @author yao
 */
public class EnergyLimitStrategy {

    private long blockLowerLimit;
    private long energyLimitDivisor;
    private static final long targetLimit = 10000000L;
    private volatile boolean initialized = false;


    public EnergyLimitStrategy() {
        // nothing
    }

    public void setConstants(ChainConfiguration config) {
        IBlockConstants constants = config.getConstants();
        this.blockLowerLimit = constants.getEnergyLowerBoundLong();
        this.energyLimitDivisor = constants.getEnergyDivisorLimitLong();
        this.initialized = true;
    }


    // --------------------------------------------- targetted limit strategy
    // strategy shifts the energy limit towards a certain bound

    public long targetEnergyLimitStrategy(A0BlockHeader header) {
        return targetEnergyLimitStrategy(header.getEnergyLimit());
    }

    public long targetEnergyLimitStrategy(final long parentEnergyLimit) {
        if (!initialized)
            throw new IllegalStateException();

        return clampMin(targetedEnergyLimitStrategy(parentEnergyLimit,
                this.blockLowerLimit,
                this.energyLimitDivisor,
                targetLimit));
    }

    public long clampMin(long energy) {
        return Math.max(energy, this.blockLowerLimit);
    }

    // following are underlying strategy implementations, do not use this
    // instead prefer to instantiate

    /**
     * Underlying implementation, do not use this, instead instantiate an
     * object of {@link EnergyLimitStrategy} and set the desired values at runtime
     * by providing {@link ChainConfiguration}
     */
    public static long targetedEnergyLimitStrategy(final long parentEnergyLimit,
                                                   final long blockLowerLimit,
                                                   final long energyLimitDivisor,
                                                   final long targetLimit) {

        // find the distance between the targetLimit and parentEnergyLimit
        // capped by the targetLimit (the bounds in which the shift is valid)
        long delta = Math.min(parentEnergyLimit / energyLimitDivisor,
                Math.abs(targetLimit - parentEnergyLimit));

        if (parentEnergyLimit > targetLimit)
            delta = -delta;

        // clamp at the block lower limit
        return Math.max(blockLowerLimit, parentEnergyLimit + delta);
    }

    /**
     * Alternative strategy, specify an upper and lower bound for energy, and allow
     * the function to adjust within those bounds.
     */
    public static long clampedDecayingEnergyLimitStrategy(final long pastEnergyLimit,
                                                          final long pastEnergyConsumed,
                                                          final long clampUpperBound,
                                                          final long clampLowerBound,
                                                          final long energyLimitDivisor) {
        // clamps
        if (pastEnergyLimit < clampLowerBound)
            return pastEnergyLimit + pastEnergyLimit / energyLimitDivisor;

        if (pastEnergyLimit > clampLowerBound)
            return pastEnergyLimit - pastEnergyLimit / energyLimitDivisor;

        // implies this function can go beyond the clamps, but we should
        // self correct at next iteration anyways
        return decayingEnergyLimitStrategy(pastEnergyLimit, pastEnergyConsumed, energyLimitDivisor);
    }


    /**
     * <p>A distance based energy limit strategy for upwards movement, coupled with
     * a persistant decay parameter. Will always return a non-negative result.</p>
     *
     * <p>Function has an upword movement bound of 1/3 * d, and a downward movement
     * bound of d, d = energy limit movement bounds</p>
     */
    public static long decayingEnergyLimitStrategy(final long pastEnergyLimit,
                                                   final long pastEnergyConsumed,
                                                   final long energyLimitDivisor) {
        // set upwards boundary to 75%
        long gu = (pastEnergyConsumed * 4) / 3;
        long fn = (gu - pastEnergyLimit) / energyLimitDivisor;
        return Math.max(0, pastEnergyLimit + fn);
    }

    /**
     * Monotonically increasing strategy, this is the original function that
     * was used for the duration of the test-net
     *
     * Do not use this, instead instantiate an object of {@link EnergyLimitStrategy}
     * and set the desired values at runtime by providing {@link ChainConfiguration}
     */
    public static long monotonicallyIncreasingEnergyStrategy(final long parentEnergyLimit,
                                                             final long parentEnergyUsed,
                                                             final long blockLowerLimit,
                                                             final long energyLimitDivisor) {
        long threshold = parentEnergyLimit * 4 / 5;
        if (parentEnergyUsed > threshold) {
            return Math.max(blockLowerLimit,
                    parentEnergyLimit + (parentEnergyLimit / energyLimitDivisor));
        }
        return parentEnergyLimit;
    }
}
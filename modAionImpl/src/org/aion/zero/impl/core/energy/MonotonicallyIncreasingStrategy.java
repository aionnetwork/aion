package org.aion.zero.impl.core.energy;

import org.aion.mcf.blockchain.BlockHeader;

public class MonotonicallyIncreasingStrategy extends AbstractEnergyStrategyLimit {

    public MonotonicallyIncreasingStrategy(long energyLowerBound, long energyDivisorLimit) {
        super(energyLowerBound, energyDivisorLimit);
    }

    @Override
    protected long getEnergyLimitInternal(BlockHeader header) {
        return monotonicallyIncreasingEnergyStrategy(
                header.getEnergyLimit(),
                header.getEnergyConsumed(),
                this.getEnergyLowerBound(),
                this.getEnergyDivisorLimit());
    }

    /**
     * Monotonically increasing strategy, this is the original function that was used for the
     * duration of the test-net
     */
    protected static long monotonicallyIncreasingEnergyStrategy(
            final long parentEnergyLimit,
            final long parentEnergyUsed,
            final long blockLowerLimit,
            final long energyLimitDivisor) {
        long threshold = parentEnergyLimit * 4 / 5;
        if (parentEnergyUsed > threshold) {
            return Math.max(
                    blockLowerLimit, parentEnergyLimit + (parentEnergyLimit / energyLimitDivisor));
        }
        return parentEnergyLimit;
    }
}

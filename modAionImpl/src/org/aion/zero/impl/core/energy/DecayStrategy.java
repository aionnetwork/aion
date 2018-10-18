package org.aion.zero.impl.core.energy;

import org.aion.zero.types.A0BlockHeader;

public class DecayStrategy extends AbstractEnergyStrategyLimit {

    public DecayStrategy(long energyLowerBound,
                         long energyDivisorLimit) {
        super(energyLowerBound, energyDivisorLimit);
    }

    @Override
    protected long getEnergyLimitInternal(A0BlockHeader header) {
        return decayingEnergyLimitStrategy(header.getEnergyLimit(),
                header.getEnergyConsumed(),
                this.getEnergyDivisorLimit());
    }

    /**
     * <p>A distance based energy limit strategy for upwards movement, coupled with
     * a persistant decay parameter. Will always return a non-negative result.</p>
     *
     * <p>Function has an upword movement bound of 1/3 * d, and a downward movement
     * bound of d, d = energy limit movement bounds</p>
     */
    protected static long decayingEnergyLimitStrategy(final long pastEnergyLimit,
                                                      final long pastEnergyConsumed,
                                                      final long energyLimitDivisor) {
        // set upwards boundary to 75%
        long gu = (pastEnergyConsumed * 4) / 3;
        long fn = (gu - pastEnergyLimit) / energyLimitDivisor;
        return Math.max(0, pastEnergyLimit + fn);
    }
}

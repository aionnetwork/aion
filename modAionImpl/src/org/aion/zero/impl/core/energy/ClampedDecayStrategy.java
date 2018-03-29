package org.aion.zero.impl.core.energy;

import org.aion.zero.types.A0BlockHeader;

public class ClampedDecayStrategy extends DecayStrategy {

    private long clampUpperBound;

    private long clampLowerBound;

    public ClampedDecayStrategy(long energyLowerBound,
                                long energyDivisorLimit,
                                long clampUpperBound,
                                long clampLowerBound) {
        super(energyLowerBound, energyDivisorLimit);

        assert(clampLowerBound >= 0);
        assert(clampUpperBound >= clampLowerBound);

        this.clampUpperBound = clampUpperBound;
        this.clampLowerBound = clampLowerBound;
    }

    /**
     * Same behaviour as the decay strategy, but adds clamps to ensure that
     * energy is eventually consistent within a desired range
     */
    @Override
    protected long getEnergyLimitInternal(A0BlockHeader header) {
        long out = super.getEnergyLimit(header);
        long prevEnergyLimit = header.getEnergyLimit();

        // clamps
        if (prevEnergyLimit < this.clampLowerBound)
            return prevEnergyLimit + prevEnergyLimit / this.getEnergyDivisorLimit();

        if (prevEnergyLimit > this.clampUpperBound)
            return prevEnergyLimit - prevEnergyLimit / this.getEnergyDivisorLimit();

        return out;
    }
}

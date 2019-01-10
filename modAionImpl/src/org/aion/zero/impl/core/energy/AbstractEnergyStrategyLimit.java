package org.aion.zero.impl.core.energy;

import org.aion.zero.types.A0BlockHeader;

public abstract class AbstractEnergyStrategyLimit {

    private long energyLowerBound;
    private long energyDivisorLimit;

    public AbstractEnergyStrategyLimit(long energyLowerBound, long energyDivisorLimit) {
        assert (energyLowerBound >= 0);
        assert (energyDivisorLimit > 0);

        this.energyLowerBound = energyLowerBound;
        this.energyDivisorLimit = energyDivisorLimit;
    }

    protected long getEnergyLowerBound() {
        return this.energyLowerBound;
    }

    protected long getEnergyDivisorLimit() {
        return this.energyDivisorLimit;
    }

    public long getEnergyLimit(A0BlockHeader header) {
        return Math.max(getEnergyLimitInternal(header), this.energyLowerBound);
    }

    protected abstract long getEnergyLimitInternal(A0BlockHeader header);
}

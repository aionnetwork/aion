package org.aion.zero.impl.core.energy;

import org.aion.zero.types.A0BlockHeader;

public class TargetStrategy extends AbstractEnergyStrategyLimit {

    private long target;

    public TargetStrategy(
            final long energyLowerBound, final long energyDivisorLimit, final long target) {
        super(energyLowerBound, energyDivisorLimit);

        assert (target >= 0);
        this.target = target;
    }

    @Override
    protected long getEnergyLimitInternal(A0BlockHeader header) {
        return targetedEnergyLimitStrategy(
                header.getEnergyLimit(), this.getEnergyDivisorLimit(), this.target);
    }

    protected static long targetedEnergyLimitStrategy(
            final long parentEnergyLimit, final long energyLimitDivisor, final long targetLimit) {

        // find the distance between the targetLimit and parentEnergyLimit
        // capped by the targetLimit (the bounds in which the shift is valid)
        long delta =
                Math.min(
                        parentEnergyLimit / energyLimitDivisor,
                        Math.abs(targetLimit - parentEnergyLimit));

        if (parentEnergyLimit > targetLimit) delta = -delta;

        // clamp at the block lower limit
        return parentEnergyLimit + delta;
    }
}

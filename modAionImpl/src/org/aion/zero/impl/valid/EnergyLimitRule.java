package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.mcf.valid.DependentBlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;

/**
 * Energy limit rule is defined as the following (no documentation yet)
 *
 * <p>if EnergyLimit(n) > MIN_ENERGY EnergyLimit(n-1) - EnergyLimit(n-1)/1024 <= EnergyLimit(n) <=
 * EnergyLimit(n-1) + EnergyLimit(n-1)/1024
 *
 * <p>This rule depends on the parent to implement
 */
public class EnergyLimitRule extends DependentBlockHeaderRule<A0BlockHeader> {

    private final long energyLimitDivisor;
    private final long energyLimitLowerBounds;

    public EnergyLimitRule(long energyLimitDivisor, long energyLimitLowerBounds) {
        this.energyLimitDivisor = energyLimitDivisor;
        this.energyLimitLowerBounds = energyLimitLowerBounds;
    }

    @Override
    public boolean validate(A0BlockHeader header, A0BlockHeader parent, List<RuleError> errors) {
        long energyLimit = header.getEnergyLimit();
        long parentEnergyLimit = parent.getEnergyLimit();
        long parentEnergyQuotient = parentEnergyLimit / this.energyLimitDivisor;

        // check that energy is atleast equal to lower bounds, otherwise block is invalid
        if (energyLimit < this.energyLimitLowerBounds) {
            addError(
                    "energyLimit ("
                            + energyLimit
                            + ") lower than lower bound ("
                            + this.energyLimitLowerBounds
                            + ")",
                    errors);
            return false;
        }

        // magnitude of distance between parent energy and current energy
        long energyDeltaMag = Math.abs(energyLimit - parentEnergyLimit);
        if (energyDeltaMag > parentEnergyQuotient) {
            addError(
                    "energyLimit ("
                            + energyLimit
                            + ") of current block has delta ("
                            + energyDeltaMag
                            + ") greater than bounds ("
                            + parentEnergyQuotient
                            + ")",
                    errors);
            return false;
        }
        return true;
    }
}

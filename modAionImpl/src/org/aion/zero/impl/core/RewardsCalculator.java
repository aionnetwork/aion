package org.aion.zero.impl.core;

import java.math.BigInteger;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.zero.api.BlockConstants;

/**
 * Multiple implementations for calculating the rewards
 *
 * @author yao
 */
public class RewardsCalculator {
    private BlockConstants constants;
    private BigInteger m;

    public RewardsCalculator(BlockConstants constants) {
        this.constants = constants;

        // pre-calculate the desired increment
        long delta = constants.getRampUpUpperBound() - constants.getRampUpLowerBound();
        assert (delta > 0);

        this.m =
                this.constants
                        .getRampUpEndValue()
                        .subtract(this.constants.getRampUpStartValue())
                        .divide(BigInteger.valueOf(delta));
    }

    /** Linear ramp function that falls off after the upper bound */
    public BigInteger calculateReward(AbstractBlockHeader blockHeader) {
        long number = blockHeader.getNumber();
        if (number <= this.constants.getRampUpUpperBound()) {
            return BigInteger.valueOf(number).multiply(m).add(this.constants.getRampUpStartValue());
        } else {
            return this.constants.getBlockReward();
        }
    }

    public BigInteger getDelta() {
        return m;
    }
}

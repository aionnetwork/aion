package org.aion.zero.impl.core;

import java.math.BigInteger;
import java.util.Objects;

import org.aion.zero.api.BlockConstants;
import org.aion.zero.api.MonetaryCalculator;

/**
 * Multiple implementations for calculating the rewards
 *
 * @author yao
 */
public class RewardsCalculator {
    private static BlockConstants constants;
    private static BigInteger m;
    private static long monetaryChangeBlkNum;
    private static boolean monetaryUpdate;

    public RewardsCalculator(
            BlockConstants constants, Long monetaryUpdateBlkNum, BigInteger initialSupply) {
        RewardsCalculator.constants = constants;

        // pre-calculate the desired increment
        long delta = constants.getRampUpUpperBound() - constants.getRampUpLowerBound();
        assert (delta > 0);

        m =
                RewardsCalculator.constants
                        .getRampUpEndValue()
                        .subtract(RewardsCalculator.constants.getRampUpStartValue())
                        .divide(BigInteger.valueOf(delta));

        if (monetaryUpdateBlkNum != null) {
            monetaryChangeBlkNum = monetaryUpdateBlkNum;
            monetaryUpdate = true;
            // Must init the MonetaryCalculator object before use it.
            MonetaryCalculator.init(
                    calculateTotalSupply(initialSupply, monetaryChangeBlkNum),
                    BlockConstants.getInterestBasePoint(),
                    BlockConstants.getAnnum(),
                    monetaryChangeBlkNum);
        }
    }

    private BigInteger calculateTotalSupply(BigInteger initialSupply, long monetaryChangeBlkNum) {
        if (monetaryChangeBlkNum < 1) {
            // No need to calculation if no monetary policy change or start from scratch.
            return initialSupply;
        } else {
            BigInteger ts = initialSupply;
            for (long i = 1; i <= monetaryChangeBlkNum; i++) {
                ts = ts.add(Objects.requireNonNull(calculateRewardInternal(i)));
            }
            return ts;
        }
    }

    public BigInteger calculateReward(long number) {
        return calculateRewardInternal(number);
    }

    private static BigInteger calculateRewardInternal(long number) {
        if (monetaryUpdate && number > monetaryChangeBlkNum) {
            return MonetaryCalculator.getCurrentReward(number);
        } else {
            if (number <= constants.getRampUpUpperBound()) {
                return BigInteger.valueOf(number).multiply(m).add(constants.getRampUpStartValue());
            } else {
                return constants.getBlockReward();
            }
        }
    }
}

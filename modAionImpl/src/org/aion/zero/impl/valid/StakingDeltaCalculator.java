package org.aion.zero.impl.valid;

import org.aion.crypto.HashUtil;
import org.aion.util.math.FixedPoint;
import org.aion.util.math.LogApproximator;

import java.math.BigInteger;

import static java.lang.Long.max;

public class StakingDeltaCalculator {

    private static final BigInteger boundary = BigInteger.ONE.shiftLeft(256);
    private static final FixedPoint logBoundary = LogApproximator.log(boundary);
    
    // returns how long a block producer would have to wait, which is
    // (difficulty * log(2^256 / hash(seed))) / stake
    public static long calculateDelta(byte[] seed, BigInteger difficulty, BigInteger stake) {
        BigInteger dividend = new BigInteger(1, HashUtil.h256(seed));

        FixedPoint logDifference = logBoundary.subtract(LogApproximator.log(dividend));

        BigInteger delta = logDifference.multiplyInteger(difficulty).toBigInteger().divide(stake);

        return max(delta.longValueExact(), 1);
    }
}

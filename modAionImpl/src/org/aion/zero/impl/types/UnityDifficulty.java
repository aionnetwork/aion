package org.aion.zero.impl.types;

import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import org.aion.mcf.blockchain.Difficulty;

public class UnityDifficulty implements Difficulty {
    private final BigInteger totalDifficulty;
    private final BigInteger totalMiningDifficulty;
    private final BigInteger totalStakingDifficulty;

    public UnityDifficulty() {
        totalDifficulty = ZERO;
        totalMiningDifficulty = ZERO;
        totalStakingDifficulty = ZERO;
    }

    public UnityDifficulty(UnityDifficulty ud) {
        totalDifficulty = ud.totalDifficulty;
        totalMiningDifficulty = ud.totalMiningDifficulty;
        totalStakingDifficulty = ud.totalStakingDifficulty;
    }

    public UnityDifficulty(BigInteger tmd, BigInteger tsd) {
        totalMiningDifficulty = tmd;
        totalStakingDifficulty = tsd;
        totalDifficulty = totalMiningDifficulty.multiply(totalStakingDifficulty);
    }

    @Override
    public String toString() {
        return "TotalDifficulty: "
                + totalDifficulty
                + " TotalMiningDifficulty: "
                + totalMiningDifficulty
                + " TotalStakingDifficulty: "
                + totalStakingDifficulty;
    }

    public BigInteger getTotalDifficulty() {
        return totalDifficulty;
    }

    public BigInteger getTotalMiningDifficulty() {
        return totalMiningDifficulty;
    }

    public BigInteger getTotalStakingDifficulty() {
        return totalStakingDifficulty;
    }
}

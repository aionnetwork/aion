package org.aion.zero.impl.types;

import java.math.BigInteger;
import org.aion.util.types.AddressUtils;

public class GenesisStakingBlock extends StakingBlock {

    //TODO: [unity] The GENESIS_DIFFICULTY will be changed before go production.
    private final static BigInteger GENESIS_DIFFICULTY = BigInteger.valueOf(2_000_000_000L);

    //TODO: [unity] Hard code the genesis staking block, might refactor it before the feature release.
    public GenesisStakingBlock(byte[] extraData) {
        super(
                new byte[32],
                AddressUtils.ZERO_ADDRESS,
                new byte[256],
                GENESIS_DIFFICULTY.toByteArray(),
                0L,
                0L,
                extraData,
                15_000_000L,
                new byte[64]);
        this.setStakingDifficulty(GENESIS_DIFFICULTY);
        this.setCumulativeDifficulty(GENESIS_DIFFICULTY);
    }

    public static BigInteger getGenesisDifficulty() {
        return GENESIS_DIFFICULTY;
    }
}

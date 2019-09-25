package org.aion.zero.impl.types;

import java.math.BigInteger;
import org.aion.util.types.AddressUtils;

public class GenesisStakingBlock extends StakingBlock {

    //TODO: [unity] The GENESIS_DIFFICULTY will be changed before go production.
    private static BigInteger GENESIS_DIFFICULTY;

    public GenesisStakingBlock(byte[] extraData, BigInteger genesisDiff) {
        super(
                new byte[32],
                AddressUtils.ZERO_ADDRESS,
                new byte[256],
                genesisDiff.toByteArray(),
                0L,
                0L,
                extraData,
                15_000_000L,
                new byte[64]);

        GENESIS_DIFFICULTY = genesisDiff;
    }

    public static BigInteger getGenesisDifficulty() {
        return GENESIS_DIFFICULTY;
    }
}

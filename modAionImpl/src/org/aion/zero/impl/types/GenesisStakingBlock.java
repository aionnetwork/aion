package org.aion.zero.impl.types;

import java.math.BigInteger;
import org.aion.util.types.AddressUtils;

public class GenesisStakingBlock extends StakingBlock {

    public GenesisStakingBlock(BigInteger genesisDiff) {
        super(
                new byte[32],
                AddressUtils.ZERO_ADDRESS,
                new byte[256],
                genesisDiff.toByteArray(),
                0L,
                0L,
                new byte[0],
                15_000_000L,
                new byte[64]);
    }

    @Override
    public boolean isGenesis() {
        return true;
    }
}

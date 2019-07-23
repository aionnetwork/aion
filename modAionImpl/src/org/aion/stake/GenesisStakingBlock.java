package org.aion.stake;

import java.math.BigInteger;
import org.aion.mcf.exceptions.HeaderStructureException;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.types.StakingBlock;

public class GenesisStakingBlock extends StakingBlock {

    //TODO: [unity] Hard code the genesis staking block, might refactor it before the feature release.
    public GenesisStakingBlock(byte[] extraData) throws HeaderStructureException {
        super(
                new byte[32],
                AddressUtils.ZERO_ADDRESS,
                new byte[256],
                BigInteger.valueOf(2_000_000_000L).toByteArray(),
                0L,
                0L,
                extraData,
                15_000_000L,
                new byte[64]);
    }

    public StakingBlock build() {
        return new StakingBlock(this);
    }
}

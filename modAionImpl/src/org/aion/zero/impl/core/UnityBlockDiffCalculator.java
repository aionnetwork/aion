package org.aion.zero.impl.core;

import static org.aion.util.biginteger.BIUtil.max;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.IBlockConstants;
import org.aion.util.math.FixedPoint;
import org.aion.zero.impl.types.AbstractBlockHeader.BlockSealType;
import org.aion.stake.GenesisStakingBlock;

public class UnityBlockDiffCalculator {
    private static FixedPoint difficultyIncreaseRate = new FixedPoint(new BigDecimal("1.05"));
    private static FixedPoint difficultyDecreaseRate = new FixedPoint(new BigDecimal("0.952381"));

    // Our barrier should be log2*40 = 13.862943611, 
    // but we only compare it against integer values, so we use 14
    private static long barrier = 14;

    private IBlockConstants constants;

    public UnityBlockDiffCalculator(IBlockConstants _constants) {
        constants = _constants;
    }

    public BigInteger calcDifficulty(BlockHeader parent, BlockHeader grandParent) {

        // If not parent pos block, return the initial difficulty
        if (parent == null) {
            return constants.getMinimumDifficulty();
        }

        BigInteger pd = parent.getDifficultyBI();

        if (grandParent == null) {
            return pd;
        }

        long timeDelta = parent.getTimestamp() - grandParent.getTimestamp();
        if (timeDelta < 1) {
            throw new IllegalStateException("Invalid parent timestamp & grandparent timestamp diff!");
        }

        BigInteger newDiff;
        if (timeDelta >= barrier) {
            newDiff = difficultyDecreaseRate.multiplyInteger(pd).toBigInteger();
        } else {
            newDiff = difficultyIncreaseRate.multiplyInteger(pd).toBigInteger();

            // Unity protocol, increasing one difficulty if the difficulty changes too small can not
            // be adjusted by the controlRate.
            if (newDiff.equals(pd)) {
                newDiff = newDiff.add(BigInteger.ONE);
            }
        }

        if (parent.getSealType() == BlockSealType.SEAL_POS_BLOCK.getSealId()) {
            return max(GenesisStakingBlock.getGenesisDifficulty(), newDiff);
        } else if (parent.getSealType() == BlockSealType.SEAL_POW_BLOCK.getSealId()) {
            return max(constants.getMinimumDifficulty() , newDiff);
        } else {
            throw  new IllegalStateException("Invalid block seal type!");
        }
    }
}
